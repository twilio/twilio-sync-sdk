//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.client

import com.twilio.sync.cache.SyncCacheCleaner
import com.twilio.sync.repository.SyncRepository
import com.twilio.sync.subscriptions.SubscriptionManager
import com.twilio.sync.utils.ConnectionState
import com.twilio.sync.utils.ConnectionState.Connected
import com.twilio.sync.utils.ConnectionState.Connecting
import com.twilio.sync.utils.ConnectionState.Denied
import com.twilio.sync.utils.ConnectionState.Disconnected
import com.twilio.sync.utils.ConnectionState.Error
import com.twilio.sync.utils.ConnectionState.FatalError
import com.twilio.sync.utils.SyncConfig
import com.twilio.sync.utils.isUnauthorised
import com.twilio.twilsock.client.Twilsock
import com.twilio.util.AccountDescriptor
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason
import com.twilio.util.ErrorReason.ClientShutdown
import com.twilio.util.InternalTwilioApi
import com.twilio.util.TwilioException
import com.twilio.util.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private val shutdownScope = CoroutineScope(SupervisorJob() +  Dispatchers.Default)

internal class SyncClientImpl(
    private val coroutineScope: CoroutineScope,
    private val config: SyncConfig,
    private val twilsock: Twilsock,
    private val subscriptionManager: SubscriptionManager,
    private val repository: SyncRepository,
    private val accountStorage: AccountStorage,
    private val accountDescriptor: AccountDescriptor,
) : SyncClient {

    override val documents = DocumentsImpl(coroutineScope, subscriptionManager, repository)

    override val lists = ListsImpl(coroutineScope, subscriptionManager, repository)

    override val maps = MapsImpl(coroutineScope, subscriptionManager, repository)

    override val streams = StreamsImpl(coroutineScope, subscriptionManager, repository)

    override val events = Events()

    override val connectionState: ConnectionState get() = events.onConnectionStateChanged.value

    init {
        logger.d { "init" }

        twilsock.addObserver {
            onDisconnected = {
                when (events.onConnectionStateChanged.value) {
                    Denied, FatalError -> Unit // We are already in terminal state. Do nothing.
                    else -> events.onConnectionStateChanged.value = Disconnected
                }
            }
            onConnecting = { events.onConnectionStateChanged.value = Connecting }
            onConnected = {
                events.onConnectionStateChanged.value = Connected
                checkAccountMismatch()
            }
            onNonFatalError = { errorInfo ->
                events.onConnectionStateChanged.value = Error
                coroutineScope.launch { events.onError.emit(errorInfo) }
            }
            onFatalError = { errorInfo ->
                events.onConnectionStateChanged.value = if (errorInfo.isUnauthorised) Denied else FatalError
                coroutineScope.launch { events.onError.emit(errorInfo) }
            }
        }
    }

    private fun checkAccountMismatch() {
        logger.d { "checkAccountMismatch" }

        val realAccount = checkNotNull(twilsock.accountDescriptor) { "checkAccountMismatch: accountDescriptor is null" }
        val accountChanged = realAccount != accountDescriptor

        if (!accountChanged) {
            return
        }

        logger.w { "accountChanged lastUserAccount: $accountDescriptor; realAccount: $realAccount" }

        val errorInfo = ErrorInfo(
            reason = ErrorReason.MismatchedLastUserAccount,
            message = "lastUserAccount: $accountDescriptor; realAccount: $realAccount"
        )

        shutdownOnUnauthorised(errorInfo)
    }

    override fun shutdown(logout: Boolean) = shutdown(logout, cause = null)

    @OptIn(InternalTwilioApi::class)
    private fun shutdownOnUnauthorised(errorInfo: ErrorInfo) = coroutineScope.launch {
        logger.d { "shutdownOnUnauthorised" }

        events.onError.emit(errorInfo)
        shutdown(logout = true, cause = TwilioException(errorInfo))

        if (config.cacheConfig.dropAllCachesOnUnauthorised) {
            SyncCacheCleaner.clearAllCaches()
        }
    }

    private fun shutdown(logout: Boolean, cause: TwilioException?) {
        logger.d { "shutdown begin" }
        twilsock.disconnect()
        repository.close()

        if (logout) {
            accountStorage.clear()
        }

        shutdownScope.launch {
            logger.d { "shutdownScope: start shutdown" }

            coroutineScope.cancel(
                message = ClientShutdown.description,
                cause = TwilioException(ErrorInfo(ClientShutdown), cause)
            )

            logger.d { "shutdownScope: end shutdown" }
        }

        logger.d { "shutdown end" }
    }

    inner class Events : SyncClient.Events {
        
        private val initialConnectionState = if (twilsock.isConnected) Connected else Disconnected

        override val onConnectionStateChanged = MutableStateFlow(initialConnectionState)

        override val onError = MutableSharedFlow<ErrorInfo>()
    }
}
