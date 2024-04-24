package com.twilio.sync.test.unit.entities

import com.twilio.sync.entities.SyncMapImpl
import com.twilio.sync.repository.OpenOptions
import com.twilio.sync.repository.SyncRepository
import com.twilio.sync.subscriptions.SubscriptionManager
import com.twilio.sync.utils.CollectionMetadata
import com.twilio.test.util.ExcludeFromInstrumentedTests
import com.twilio.test.util.runTest
import com.twilio.test.util.setupTestLogging
import com.twilio.test.util.testCoroutineScope
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason
import com.twilio.util.StateMachine.Matcher.Companion.any
import com.twilio.util.TwilioException
import com.twilio.util.toTwilioException
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

@ExcludeFromInstrumentedTests
class SyncMapImplTest {

    private lateinit var syncMap: SyncMapImpl

    @RelaxedMockK
    private lateinit var subscriptionManager: SubscriptionManager

    @RelaxedMockK
    private lateinit var syncRepository: SyncRepository

    @RelaxedMockK
    private lateinit var collectionMetadata: CollectionMetadata

    @BeforeTest
    fun setUp() {
        setupTestLogging()
        MockKAnnotations.init(this, relaxUnitFun = true)
        syncMap = SyncMapImpl(
            coroutineScope = testCoroutineScope,
            subscriptionManager = subscriptionManager,
            repository = syncRepository
        )
    }

    @Test
    fun open() = runTest {
        val uniqueName = "testUniqueName"
        every { collectionMetadata.uniqueName } returns uniqueName
        performMapOpenSetup()

        assertEquals(uniqueName, syncMap.uniqueName)
    }

    @Test
    fun openTimeout() = runTest {
        val openOptions = OpenOptions.CreateNew(uniqueName = null)
        val expectedException = TwilioException(ErrorInfo(ErrorReason.Timeout))
        coEvery { syncRepository.getCollectionMetadata(any(), openOptions) } returns flow { throw expectedException }

        val actualException = assertFails { syncMap.open(openOptions) }.toTwilioException(ErrorReason.Unknown)

        assertEquals(expectedException.errorInfo, actualException.errorInfo)
    }

    @Test
    fun openError() = runTest {
        val openOptions = OpenOptions.CreateNew(uniqueName = null)
        coEvery { syncRepository.getCollectionMetadata(any(), openOptions) } returns flowOf(null)

        val actualException = assertFails { syncMap.open(openOptions) }.toTwilioException(ErrorReason.Unknown)

        assertEquals(ErrorInfo(ErrorReason.OpenCollectionError), actualException.errorInfo)
    }

    @Test
    fun removeMap() = runTest {

        coEvery { syncRepository.removeCollection(any(), any()) } returns Unit
        performMapOpenSetup()
        syncMap.removeMap()

        assertEquals(true, syncMap.isRemoved)
    }

    private suspend fun performMapOpenSetup() {
        coEvery { syncRepository.getCollectionMetadata(any(), any()) } returns flowOf(collectionMetadata)
        syncMap.open(openOptions = OpenOptions.CreateNew(uniqueName = null))
    }
}
