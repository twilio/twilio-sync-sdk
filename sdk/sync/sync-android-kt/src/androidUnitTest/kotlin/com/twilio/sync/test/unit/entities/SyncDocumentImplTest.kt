package com.twilio.sync.test.unit.entities

import com.twilio.sync.entities.SyncDocumentImpl
import com.twilio.sync.repository.OpenOptions
import com.twilio.sync.repository.SyncRepository
import com.twilio.sync.sqldelight.cache.persistent.DocumentCacheMetadata
import com.twilio.sync.subscriptions.SubscriptionManager
import com.twilio.test.util.ExcludeFromInstrumentedTests
import com.twilio.test.util.runTest
import com.twilio.test.util.setupTestLogging
import com.twilio.test.util.testCoroutineScope
import com.twilio.util.StateMachine.Matcher.Companion.any
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.JsonObject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@ExcludeFromInstrumentedTests
class SyncDocumentImplTest {

    private lateinit var syncDocument: SyncDocumentImpl

    @RelaxedMockK
    private lateinit var subscriptionManager: SubscriptionManager

    @RelaxedMockK
    private lateinit var syncRepository: SyncRepository

    @RelaxedMockK
    private lateinit var documentCacheMetaData: DocumentCacheMetadata

    @BeforeTest
    fun setUp() {
        setupTestLogging()
        MockKAnnotations.init(this, relaxUnitFun = true)
        syncDocument = SyncDocumentImpl(
            coroutineScope = testCoroutineScope,
            subscriptionManager = subscriptionManager,
            repository = syncRepository
        )
    }

    @Test
    fun open() = runTest {
        val updatedEventFlow = syncDocument.events.onUpdated
        val jsonObject: JsonObject = mockk()
        every { documentCacheMetaData.documentData } returns jsonObject
        performDocumentOpenSetup()

        updatedEventFlow.first()
        assertEquals(jsonObject, syncDocument.data)
    }

    @Test
    fun setData() = runTest {
        val jsonObject: JsonObject = mockk()

        every { documentCacheMetaData.documentData } returns jsonObject
        coEvery { syncRepository.setDocumentData(any(), jsonObject, any()) } returns documentCacheMetaData

        performDocumentOpenSetup()
        syncDocument.setData(jsonObject)

        assertEquals(jsonObject, syncDocument.data)
    }

    @Test
    fun mutateData() = runTest {
        val jsonObject: JsonObject = mockk()

        every { documentCacheMetaData.documentData } returns jsonObject
        coEvery { 
            syncRepository.mutateDocumentData(any<DocumentCacheMetadata>(), any(), any())
        } returns documentCacheMetaData

        performDocumentOpenSetup()

        syncDocument.mutateData { jsonObject }

        assertEquals(jsonObject, syncDocument.data)
    }

    @Test
    fun removeDocument() = runTest {

        coEvery { syncRepository.removeDocument(any()) } returns Unit
        performDocumentOpenSetup()
        syncDocument.removeDocument()

        assertEquals(true, syncDocument.isRemoved)
    }

    private suspend fun performDocumentOpenSetup() {
        coEvery { syncRepository.getDocumentMetadata(any()) } returns flowOf(documentCacheMetaData)
        syncDocument.open(openOptions = OpenOptions.CreateNew(uniqueName = null))
    }
}
