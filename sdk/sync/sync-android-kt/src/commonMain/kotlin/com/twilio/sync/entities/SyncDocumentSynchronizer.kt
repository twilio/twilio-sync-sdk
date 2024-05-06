//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.entities

import com.twilio.sync.utils.Synchronizer
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject

internal class SyncDocumentSynchronizer(
    scope: CoroutineScope,
    document: SyncDocument,
) : Synchronizer<SyncDocument>(scope, document), SyncDocument {

    override val sid get() = delegate.sid
    override val uniqueName get() = delegate.uniqueName
    override val subscriptionState get() = delegate.subscriptionState
    override val data get() = delegate.data
    override val dateCreated get() = delegate.dateCreated
    override val dateUpdated get() = delegate.dateUpdated
    override val dateExpires get() = delegate.dateExpires
    override val isRemoved get() = delegate.isRemoved
    override val events get() = delegate.events

    override suspend fun setTtl(ttl: Duration) =
        doSynchronizeSuspend { setTtl(ttl) }

    override suspend fun setData(data: JsonObject) =
        doSynchronizeSuspend { setData(data) }

    override suspend fun setDataWithTtl(data: JsonObject, ttl: Duration) =
        doSynchronizeSuspend { setDataWithTtl(data, ttl) }

    override suspend fun mutateData(mutator: suspend (currentData: JsonObject) -> JsonObject?) =
        doSynchronizeSuspend { mutateData(mutator) }

    override suspend fun mutateDataWithTtl(ttl: Duration, mutator: suspend (currentData: JsonObject) -> JsonObject?) =
        doSynchronizeSuspend { mutateDataWithTtl(ttl, mutator) }

    override suspend fun removeDocument() =
        doSynchronizeSuspend { removeDocument() }

    override fun close() =
        doSynchronizeAsync { close() }
}
