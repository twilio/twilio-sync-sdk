package com.twilio.twilsock.client

import com.twilio.twilsock.util.ConnectivityMonitor
import com.twilio.twilsock.util.ConnectivityMonitorImpl
import com.twilio.util.newSerialCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@Suppress("FunctionName")
fun TwilsockFactory(
    url: String,
    useProxy: Boolean = false,
    authData: AuthData,
    clientMetadata: ClientMetadata = ClientMetadata(),
    coroutineScope: CoroutineScope = CoroutineScope(newSerialCoroutineContext() + SupervisorJob()),
    continuationTokenStorage: ContinuationTokenStorage? = null,
    connectivityMonitor: ConnectivityMonitor? = null,
    twilsockTransportFactory: TwilsockTransportFactory? = null,
) : Twilsock = TwilsockImpl(
    coroutineScope,
    url,
    useProxy,
    authData,
    clientMetadata,
    continuationTokenStorage ?: ContinuationTokenStorageImpl(),
    connectivityMonitor ?: ConnectivityMonitorImpl(coroutineScope),
    twilsockTransportFactory ?: ::TwilsockTransportFactory
)
