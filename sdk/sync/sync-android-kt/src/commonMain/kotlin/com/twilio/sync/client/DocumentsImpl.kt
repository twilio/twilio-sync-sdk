//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.client

import com.twilio.sync.entities.SyncDocument
import com.twilio.sync.entities.SyncDocumentImpl
import com.twilio.sync.entities.SyncDocumentSynchronizer
import com.twilio.sync.repository.OpenOptions
import com.twilio.sync.repository.SyncRepository
import com.twilio.sync.subscriptions.SubscriptionManager
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject

internal class DocumentsImpl(
    private val coroutineScope: CoroutineScope,
    private val subscriptionManager: SubscriptionManager,
    private val repository: SyncRepository,
) : Documents {

    override suspend fun create(uniqueName: String?, ttl: Duration) =
        open(OpenOptions.CreateNew(uniqueName, ttl))

    override suspend fun openOrCreate(uniqueName: String, ttl: Duration) =
        open(OpenOptions.OpenOrCreate(uniqueName, ttl))

    override suspend fun openExisting(sidOrUniqueName: String) =
        open(OpenOptions.OpenExisting(sidOrUniqueName))

    override suspend fun setTtl(sidOrUniqueName: String, ttl: Duration) {
        repository.setDocumentTtl(sidOrUniqueName, ttl)
    }

    override suspend fun updateDocument(sidOrUniqueName: String, data: JsonObject) {
        repository.setDocumentData(sidOrUniqueName, data, ttl = null)
    }

    override suspend fun updateDocumentWithTtl(sidOrUniqueName: String, data: JsonObject, ttl: Duration) {
        repository.setDocumentData(sidOrUniqueName, data, ttl)
    }

    override suspend fun mutateDocument(
        sidOrUniqueName: String,
        mutator: suspend (currentData: JsonObject) -> JsonObject?
    ): JsonObject {
        val metadata = repository.mutateDocumentData(sidOrUniqueName, ttl = null, mutator)
        return metadata.documentData
    }

    override suspend fun mutateDocumentWithTtl(
        sidOrUniqueName: String,
        ttl: Duration,
        mutator: suspend (currentData: JsonObject) -> JsonObject?
    ): JsonObject {
        val metadata = repository.mutateDocumentData(sidOrUniqueName, ttl, mutator)
        return metadata.documentData
    }

    override suspend fun remove(sidOrUniqueName: String) {
        repository.removeDocument(sidOrUniqueName)
    }

    private suspend fun open(options: OpenOptions): SyncDocument {
        val document = SyncDocumentImpl(
            coroutineScope = coroutineScope,
            subscriptionManager = subscriptionManager,
            repository = repository,
        )

        document.open(options)
        return SyncDocumentSynchronizer(coroutineScope, document)
    }
}
