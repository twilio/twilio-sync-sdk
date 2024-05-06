//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.client

import com.twilio.sync.utils.Synchronizer
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject

internal class DocumentsSynchronizer(
    scope: CoroutineScope,
    documents: Documents,
) : Synchronizer<Documents>(scope, documents), Documents {

    override suspend fun create(uniqueName: String?, ttl: Duration) =
        doSynchronizeSuspend { create(uniqueName, ttl) }

    override suspend fun openOrCreate(uniqueName: String, ttl: Duration) =
        doSynchronizeSuspend { openOrCreate(uniqueName, ttl) }

    override suspend fun openExisting(sidOrUniqueName: String) =
        doSynchronizeSuspend { openExisting(sidOrUniqueName) }

    override suspend fun setTtl(sidOrUniqueName: String, ttl: Duration) =
        doSynchronizeSuspend { setTtl(sidOrUniqueName, ttl) }

    override suspend fun remove(sidOrUniqueName: String) =
        doSynchronizeSuspend { remove(sidOrUniqueName) }

    override suspend fun updateDocument(sidOrUniqueName: String, data: JsonObject) =
        doSynchronizeSuspend { updateDocument(sidOrUniqueName, data) }

    override suspend fun updateDocumentWithTtl(sidOrUniqueName: String, data: JsonObject, ttl: Duration) =
        doSynchronizeSuspend { updateDocumentWithTtl(sidOrUniqueName, data, ttl) }

    override suspend fun mutateDocument(
        sidOrUniqueName: String,
        mutator: suspend (currentData: JsonObject) -> JsonObject?
    ) = doSynchronizeSuspend { mutateDocument(sidOrUniqueName, mutator) }

    override suspend fun mutateDocumentWithTtl(
        sidOrUniqueName: String,
        ttl: Duration,
        mutator: suspend (currentData: JsonObject) -> JsonObject?
    ): JsonObject = doSynchronizeSuspend { mutateDocumentWithTtl(sidOrUniqueName, ttl, mutator) }
}
