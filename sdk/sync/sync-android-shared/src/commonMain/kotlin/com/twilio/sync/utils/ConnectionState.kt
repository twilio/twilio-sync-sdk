//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.utils

import kotlin.native.ObjCName

/**
 * Represents underlying transport connection state.
 */
enum class ConnectionState {

    /** Transport is not working. */
    Disconnected,

    /** Transport is trying to connect and register or trying to recover. */
    Connecting,

    /** Transport is working. */
    Connected,

    /** Transport was not enabled because authentication token is invalid or not authorized. */
    Denied,

    /** Error in connecting or sending transport message. Possibly due to offline. */
    Error,

    /** Server has rejected enabling transport and customer action is required. */
    @ObjCName("PermanentError")
    FatalError,
}
