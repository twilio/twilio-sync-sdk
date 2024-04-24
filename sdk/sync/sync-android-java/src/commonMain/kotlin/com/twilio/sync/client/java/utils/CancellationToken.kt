//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.client.java.utils

/**
 * Interface for cancelling a network request.
 */
interface CancellationToken {

    /**
     * Cancels the network request.
     *
     * Request could be already transmitted to the backend or not.
     * In case when it has been transmitted cancellation doesn't
     * rollback any actions made by request, just ignores the response.
     */
    fun cancel()
}
