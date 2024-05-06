//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.client.java

import com.twilio.sync.client.java.utils.ListenerNotifier
import com.twilio.sync.client.java.utils.SuccessListener
import com.twilio.sync.client.java.utils.toSyncMapJavaItem
import com.twilio.sync.entities.SyncMap
import com.twilio.sync.utils.SyncIterator

internal class SyncMapIteratorJavaImpl(
    private val notifyListener: ListenerNotifier,
    private val syncIterator: SyncIterator<SyncMap.Item>
) : SyncIteratorJava<SyncMapJava.Item> {

    override fun next() = syncIterator.next().toSyncMapJavaItem()

    override fun hasNext(callback: SuccessListener<Boolean>) = notifyListener(callback) {
        return@notifyListener syncIterator.hasNext()
    }

    override fun close() = syncIterator.close()
}
