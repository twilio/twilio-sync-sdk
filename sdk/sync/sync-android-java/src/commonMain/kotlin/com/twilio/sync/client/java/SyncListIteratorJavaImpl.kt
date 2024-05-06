//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.client.java

import com.twilio.sync.client.java.utils.ListenerNotifier
import com.twilio.sync.client.java.utils.SuccessListener
import com.twilio.sync.client.java.utils.toSyncListJavaItem
import com.twilio.sync.entities.SyncList
import com.twilio.sync.utils.SyncIterator

internal class SyncListIteratorJavaImpl(
    private val notifyListener: ListenerNotifier,
    private val syncIterator: SyncIterator<SyncList.Item>
) : SyncIteratorJava<SyncListJava.Item> {

    override fun next() = syncIterator.next().toSyncListJavaItem()

    override fun hasNext(callback: SuccessListener<Boolean>) = notifyListener(callback) {
        return@notifyListener syncIterator.hasNext()
    }

    override fun close() = syncIterator.close()
}
