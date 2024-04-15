//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.client.java.utils

import com.twilio.util.ErrorInfo

/**
 * Interface for a generic listener object.
 *
 * @param <T> Type of the object that will be passed to [onSuccess].
 */
interface SuccessListener<T : Any?> {

    /**
     * Callback to report successful status of an asynchronous call to the back end.
     * @param result Successful return value.
     */
    fun onSuccess(result: T)

    /**
     * Callback to report error status of an asynchronous call to the back end.
     * @param errorInfo Object containing error information.
     */
    fun onFailure(errorInfo: ErrorInfo) {}
}
