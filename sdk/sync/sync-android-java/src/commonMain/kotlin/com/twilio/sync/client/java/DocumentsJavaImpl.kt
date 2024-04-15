//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
package com.twilio.sync.client.java

import com.twilio.sync.client.Documents
import com.twilio.sync.client.java.utils.ListenerNotifier
import com.twilio.sync.client.java.utils.SuccessListener
import com.twilio.sync.client.java.utils.SyncMutator
import com.twilio.util.json
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

internal class DocumentsJavaImpl(
    private val coroutineScope: CoroutineScope,
    private val listenersDispatcher: CoroutineDispatcher,
    private val documents: Documents
) : DocumentsJava {

    private val notifyListener = ListenerNotifier(coroutineScope, listenersDispatcher)

    override fun create(
        uniqueName: String?,
        ttlSeconds: Long,
        listener: SyncDocumentJava.Listener?,
        output: SuccessListener<SyncDocumentJava>
    ) = notifyListener(output) {
        val syncDocument = documents.create(uniqueName, ttlSeconds.seconds)
        val syncDocumentJava = SyncDocumentJavaImpl(coroutineScope, listenersDispatcher, syncDocument)
        listener?.let { syncDocumentJava.addListener(it) }
        return@notifyListener syncDocumentJava
    }

    override fun openOrCreate(
        uniqueName: String,
        ttlSeconds: Long,
        listener: SyncDocumentJava.Listener?,
        output: SuccessListener<SyncDocumentJava>
    ) = notifyListener(output) {
        val syncDocument = documents.openOrCreate(uniqueName, ttlSeconds.seconds)
        val syncDocumentJava = SyncDocumentJavaImpl(coroutineScope, listenersDispatcher, syncDocument)
        listener?.let { syncDocumentJava.addListener(it) }
        return@notifyListener syncDocumentJava
    }

    override fun openExisting(
        sidOrUniqueName: String,
        listener: SyncDocumentJava.Listener?,
        output: SuccessListener<SyncDocumentJava>
    ) = notifyListener(output) {
        val syncDocument = documents.openExisting(sidOrUniqueName)
        val syncDocumentJava = SyncDocumentJavaImpl(coroutineScope, listenersDispatcher, syncDocument)
        listener?.let { syncDocumentJava.addListener(it) }
        return@notifyListener syncDocumentJava
    }

    override fun setTtl(
        sidOrUniqueName: String,
        ttlSeconds: Long,
        callback: SuccessListener<Unit>
    ) = notifyListener(callback) {
        documents.setTtl(sidOrUniqueName, ttlSeconds.seconds)
    }

    override fun updateDocument(
        sidOrUniqueName: String,
        jsonData: String,
        callback: SuccessListener<Unit>
    ) = notifyListener(callback) {
        documents.updateDocument(sidOrUniqueName, json.decodeFromString(jsonData))
    }

    override fun updateDocumentWithTtl(
        sidOrUniqueName: String,
        jsonData: String,
        ttlSeconds: Long,
        callback: SuccessListener<Unit>
    ) = notifyListener(callback) {
        documents.updateDocumentWithTtl(sidOrUniqueName, json.decodeFromString(jsonData), ttlSeconds.seconds)
    }

    override fun mutateDocument(
        sidOrUniqueName: String,
        mutator: SyncMutator,
        callback: SuccessListener<String>
    ) = notifyListener(callback) {
        documents.mutateDocument(sidOrUniqueName) { currentData ->
            mutator.mutate(currentData.toString())
                ?.let { json.decodeFromString(it) }
        }.toString()
    }

    override fun mutateDocumentWithTtl(
        sidOrUniqueName: String,
        ttlSeconds: Long,
        mutator: SyncMutator,
        callback: SuccessListener<String>
    ) = notifyListener(callback) {
        documents.mutateDocumentWithTtl(sidOrUniqueName, ttlSeconds.seconds) { currentData ->
            mutator.mutate(currentData.toString())
                ?.let { json.decodeFromString(it) }
        }.toString()
    }

    override fun remove(sidOrUniqueName: String, callback: SuccessListener<Unit>) = notifyListener(callback) {
        documents.remove(sidOrUniqueName)
    }
}
