//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.client.java.utils

/**
 * Mutator is a functional object that you provide to modify entity data in a controlled manner.
 *
 * Override [mutate] to perform your modifications. This method will be provided with the
 * previous data contents and should return new desired contents or null to abort mutate operation.
 */
interface SyncMutator {

    /**
     * Override this method to provide your own implementation of data mutator.
     *
     * This method can be invoked more than once in case of data collision, i.e. if data on backend
     * is modified while the mutate operation is executing.
     *
     * This method is invoked on a background thread.
     *
     * @param  currentJsonData Current contents of the entity to modify as a serialized JSON object
     * @return                 New contents of the entity as a serialized JSON object that will replace
     *                         the old contents or null to abort the edit entirely.
     */
    fun mutate(currentJsonData: String?): String?
}
