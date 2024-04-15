//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
@file:OptIn(InternalTwilioApi::class)

package com.twilio.sync.client

import com.twilio.sync.cache.SyncCache
import com.twilio.sync.cache.SyncCacheCleaner
import com.twilio.sync.cache.persistent.DriverFactory
import com.twilio.sync.cache.persistent.databaseName
import com.twilio.sync.operations.ConfigurationLinks
import com.twilio.sync.operations.ConfigurationResponse
import com.twilio.sync.operations.GetConfigurationOperation
import com.twilio.sync.operations.SyncOperationsFactory
import com.twilio.sync.repository.SyncRepository
import com.twilio.sync.sqldelight.cache.persistent.Links
import com.twilio.sync.subscriptions.SubscriptionManager
import com.twilio.sync.utils.SyncConfig
import com.twilio.sync.utils.configurationUrl
import com.twilio.sync.utils.defaultCommandsConfig
import com.twilio.sync.utils.defaultSubscriptionsConfig
import com.twilio.sync.utils.isUnauthorised
import com.twilio.sync.utils.readCertificates
import com.twilio.sync.utils.toSyncConfigInternal
import com.twilio.sync.utils.twilsockUrl
import com.twilio.twilsock.client.AuthData
import com.twilio.twilsock.client.ClientMetadata
import com.twilio.twilsock.client.Twilsock
import com.twilio.twilsock.client.TwilsockFactory
import com.twilio.twilsock.commands.CommandsConfig
import com.twilio.twilsock.commands.CommandsScheduler
import com.twilio.twilsock.util.ConnectivityMonitor
import com.twilio.util.AccountDescriptor
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason.CreateClientError
import com.twilio.util.ErrorReason.UpdateTokenError
import com.twilio.util.InternalTwilioApi
import com.twilio.util.NextLong
import com.twilio.util.TwilioException
import com.twilio.util.TwilioLogger
import com.twilio.util.await
import com.twilio.util.getOrThrowTwilioException
import com.twilio.util.logger
import com.twilio.util.newSerialCoroutineContext
import com.twilio.util.toTwilioException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

private val logger = TwilioLogger.getLogger("SyncClientFactory")

@OptIn(InternalTwilioApi::class)
internal suspend fun createSyncClient(
    useLastUserCache: Boolean = true,
    config: SyncConfig = SyncConfig(),
    clientMetadata: ClientMetadata,
    connectivityMonitor: ConnectivityMonitor? = null,
    tokenProvider: suspend () -> String,
): SyncClient {
    val context = newSerialCoroutineContext()
    val coroutineScope = CoroutineScope(context + SupervisorJob())

    val result = runCatching {
        createInternal(
            context,
            coroutineScope,
            useLastUserCache,
            config,
            clientMetadata,
            connectivityMonitor,
            tokenProvider
        )
    }
    return result.getOrThrowTwilioException(CreateClientError)
}

@InternalTwilioApi
suspend fun createInternal(
    context: CoroutineContext,
    coroutineScope: CoroutineScope,
    useLastUserCache: Boolean = true,
    syncConfig: SyncConfig,
    clientMetadata: ClientMetadata,
    connectivityMonitor: ConnectivityMonitor?,
    tokenProvider: suspend () -> String,
): SyncClient = withContext(context) {

    val certificates = readCertificates(syncConfig.syncClientConfig.deferCertificateTrustToPlatform)

    val twilsock = createTwilsock(
        coroutineScope,
        syncConfig.twilsockUrl,
        syncConfig.syncClientConfig.useProxy,
        certificates,
        clientMetadata,
        connectivityMonitor,
        tokenProvider,
    )

    twilsock.connect()

    return@withContext try {
        createInternalWithTwilsock(
            twilsock,
            coroutineScope,
            useLastUserCache,
            syncConfig,
            tokenProvider,
        )
    } catch (t: Throwable) {
        logger.e("Error creating SyncClient: ", t)

        runCatching { twilsock.disconnect() }
            .onFailure { logger.w("Error in twilsock.disconnect: ", it) }

        throw t
    }
}

internal suspend fun createInternalWithTwilsock(
    twilsock: Twilsock,
    coroutineScope: CoroutineScope,
    useLastUserCache: Boolean = true,
    syncConfig: SyncConfig,
    tokenProvider: suspend () -> String,
): SyncClient {
    val accountStorage = AccountStorage()
    val localAccountUsed = useLastUserCache
            && !accountStorage.isEmpty // we have cached account
            // when we don't use persistent cache we cannot get links from it, but offline
            // client doesn't make sense in this case anyway
            && syncConfig.cacheConfig.usePersistentCache

    logger.d("useLastUserCache = $useLastUserCache; localAccountUsed = $localAccountUsed;")

    val accountDescriptor = if (localAccountUsed) {
        accountStorage.account
    } else {
        requestAccountDescriptor(
            twilsock,
            dropAllCachesOnUnauthorised = syncConfig.cacheConfig.dropAllCachesOnUnauthorised,
            timeout = syncConfig.syncClientConfig.commandTimeout
        )
    }

    logger.d("accountDescriptor: $accountDescriptor")

    SyncCacheCleaner.cleanupOnLogin(accountDescriptor, syncConfig.cacheConfig)

    val databaseDriver = DriverFactory().createDriver(
        accountDescriptor = accountDescriptor,
        isInMemoryDatabase = !syncConfig.cacheConfig.usePersistentCache,
    )

    val cache = SyncCache(
        accountDescriptor.databaseName,
        databaseDriver,
    )

    val commandsConfig = syncConfig.defaultCommandsConfig()

    val links = cache.getOrPutLinks {
        // We don't have Links in cache which means we never be connected with this AccountDescriptor before
        // (or cache has been cleared, but we clean AccountStorage while cleaning cache as well)
        //
        // In this case we have localAccountUsed === false. So we never get here and still returning client immediately
        // when localAccountUsed === true
        check(!localAccountUsed) { "Cannot use localAccount: links are not cached" }

        val url = syncConfig.configurationUrl
        val configurationResponse = requestConfiguration(coroutineScope, twilsock, url, commandsConfig)
        return@getOrPutLinks configurationResponse.links.toLinks()
    }

    val config = syncConfig.toSyncConfigInternal(
        subscriptionsConfig = syncConfig.defaultSubscriptionsConfig(links.subscriptions),
        commandsConfig = commandsConfig,
        configurationLinks = links.toConfigurationLinks()
    )

    val subscriptionManager = SubscriptionManager(
        coroutineScope,
        twilsock,
        config.subscriptionsConfig,
    )

    val operationsFactory = SyncOperationsFactory(
        coroutineScope,
        config.commandsConfig,
    )

    val scheduler = CommandsScheduler(
        coroutineScope,
        twilsock,
        config.commandsConfig,
    )

    val repository = SyncRepository(
        coroutineScope,
        cache,
        scheduler,
        subscriptionManager.remoteEventsFlow,
        operationsFactory,
        config.links,
    )

    if (syncConfig.cacheConfig.usePersistentCache) {
        // Save accountDescriptor to cache only if we use persistent cache,
        // otherwise we will not be able to get links from cache next time
        accountStorage.account = accountDescriptor
    }

    val syncClient = SyncClientImpl(
        coroutineScope,
        syncConfig,
        twilsock,
        subscriptionManager,
        repository,
        accountStorage,
        accountDescriptor,
    )

    fun tryUpdateToken() {
        twilsock.tryUpdateToken(coroutineScope, tokenProvider) { errorInfo ->
            syncClient.events.onError.emit(errorInfo)
        }
    }

    twilsock.addObserver {
        onTokenAboutToExpire = ::tryUpdateToken
        onTokenExpired = ::tryUpdateToken
    }

    return SyncClientSynchronizer(coroutineScope, syncClient)
}

internal suspend fun createTwilsock(
    coroutineScope: CoroutineScope,
    twilsockUrl: String,
    useProxy: Boolean,
    certificates: List<String>,
    clientMetadata: ClientMetadata,
    connectivityMonitor: ConnectivityMonitor?,
    tokenProvider: suspend () -> String
): Twilsock {
    val authData = AuthData(
        token = getToken(tokenProvider),
        activeGrant = "data_sync",
        notificationProductId = "data_sync",
        certificates = certificates,
    )

    val twilsock = TwilsockFactory(
        twilsockUrl, useProxy, authData, clientMetadata, coroutineScope, connectivityMonitor = connectivityMonitor)

    return twilsock
}

private suspend fun requestConfiguration(
    coroutineScope: CoroutineScope,
    twilsock: Twilsock,
    configurationUrl: String,
    commandsConfig: CommandsConfig,
): ConfigurationResponse {
    val operation = GetConfigurationOperation(coroutineScope, commandsConfig, configurationUrl)
    operation.execute(twilsock)
    return operation.awaitResult()
}

private suspend fun requestAccountDescriptor(
    twilsock: Twilsock,
    dropAllCachesOnUnauthorised: Boolean,
    timeout: Duration
): AccountDescriptor {
    twilsock.accountDescriptor?.let { return it }
    check(!twilsock.isConnected) { "AccountDescriptor is null on connected twilsock" }

    val onTwilsockConnectFinished = CompletableDeferred<ErrorInfo?>()

    val observer = twilsock.addObserver {
        onConnected = { onTwilsockConnectFinished.complete(null) }
        onFatalError = { onTwilsockConnectFinished.complete(it) }
    }

    check(!twilsock.isConnected) { "We are on single thread. So twilsock must be still not connected" }

    val fatalError = onTwilsockConnectFinished.await(timeout)
    observer.unsubscribe()

    if (dropAllCachesOnUnauthorised && fatalError?.isUnauthorised == true) {
        SyncCacheCleaner.clearAllCaches()
    }

    if (fatalError != null) {
        throw TwilioException(fatalError)
    }

    return checkNotNull(twilsock.accountDescriptor) { "Twilsock is connected, but AccountDescriptor is null" }
}

private inline fun Twilsock.tryUpdateToken(
    coroutineScope: CoroutineScope,
    crossinline tokenProvider: suspend () -> String,
    crossinline onError: suspend (ErrorInfo) -> Unit,
) = coroutineScope.launch {
    logger.d("tryUpdateToken")

    val result = runCatching {
        val token = getToken(tokenProvider)
        logger.d("Got new token from tokenProvider")

        updateToken(token)
        logger.d("Token updated successfully")
    }

    result.onFailure { t ->
        logger.w("Error updating token: ", t)

        val errorInfo = t.toTwilioException(UpdateTokenError).errorInfo
        onError(errorInfo)
    }
}

private suspend inline fun getToken(crossinline tokenProvider: suspend () -> String): String {
    val token = withContext(Dispatchers.Default) { tokenProvider() }
    logger.d { "getToken: $token" }

    if (token.isEmpty()) {
        throw TwilioException(
            ErrorInfo(
                UpdateTokenError,
                message = "Token provider has returned an empty token or thrown an exception"
            )
        )
    }

    return token
}

private fun ConfigurationLinks.toLinks() = Links(
    id = 0,
    subscriptions = subscriptions,
    documents = documents,
    document = document,
    maps = maps,
    map = map,
    mapItems = mapItems,
    mapItem = mapItem,
    lists = lists,
    list = list,
    listItems = listItems,
    listItem = listItem,
    streams = streams,
    stream = stream,
    streamMessages = streamMessages,
    insightsItems = insightsItems
)

private fun Links.toConfigurationLinks() = ConfigurationLinks(
    subscriptions = subscriptions,
    documents = documents,
    document = document,
    maps = maps,
    map = map,
    mapItems = mapItems,
    mapItem = mapItem,
    lists = lists,
    list = list,
    listItems = listItems,
    listItem = listItem,
    streams = streams,
    stream = stream,
    streamMessages = streamMessages,
    insightsItems = insightsItems,
)
