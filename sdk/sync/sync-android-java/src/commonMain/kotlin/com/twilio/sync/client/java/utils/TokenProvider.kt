//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.client.java.utils

import com.twilio.sync.client.java.SyncClientJava

/** Provides authentication tokens for [SyncClientJava]. */
interface TokenProvider {

    /**
     * Provides new authentication token for [SyncClientJava].
     *
     * This methods is called multiple times during [SyncClientJava] lifecycle.
     * First while initialising the [SyncClientJava] and after each time when token is about to expire.
     *
     * This method is called on a background thread. So network requests can be performed in implementation.
     *
     * @return Access token containing at least a Sync Grant to access sync features.
     */
    fun getToken(): String
}
