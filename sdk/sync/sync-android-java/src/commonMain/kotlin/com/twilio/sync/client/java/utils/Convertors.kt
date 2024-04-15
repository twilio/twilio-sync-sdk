//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
package com.twilio.sync.client.java.utils

import com.twilio.sync.client.java.SyncListJava
import com.twilio.sync.client.java.SyncMapJava
import com.twilio.sync.client.java.SyncStreamJava
import com.twilio.sync.entities.SyncList
import com.twilio.sync.entities.SyncMap
import com.twilio.sync.entities.SyncStream
import com.twilio.sync.subscriptions.SubscriptionState

internal fun SyncList.Item.toSyncListJavaItem() = SyncListJava.Item(
    index = index,
    jsonData = data.toString(),
    dateCreated = dateCreated.toEpochMilliseconds(),
    dateUpdated = dateUpdated.toEpochMilliseconds(),
    dateExpires = dateExpires?.toEpochMilliseconds(),
)

internal fun SyncMap.Item.toSyncMapJavaItem() = SyncMapJava.Item(
    key = key,
    jsonData = data.toString(),
    dateCreated = dateCreated.toEpochMilliseconds(),
    dateUpdated = dateUpdated.toEpochMilliseconds(),
    dateExpires = dateExpires?.toEpochMilliseconds(),
)

internal fun SyncStream.Message.toSyncStreamJavaMessage() = SyncStreamJava.Message(sid, data.toString())

internal fun SubscriptionState.toSubscriptionStateJava() = when(this) {
    SubscriptionState.Unsubscribed -> SubscriptionStateJava.Unsubscribed

    SubscriptionState.Pending -> SubscriptionStateJava.Pending

    SubscriptionState.Subscribing -> SubscriptionStateJava.Subscribing

    SubscriptionState.Established -> SubscriptionStateJava.Established

    is SubscriptionState.Failed -> SubscriptionStateJava.Failed
}
