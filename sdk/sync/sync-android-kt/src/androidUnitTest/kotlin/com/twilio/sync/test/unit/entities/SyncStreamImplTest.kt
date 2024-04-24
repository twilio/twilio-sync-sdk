package com.twilio.sync.test.unit.entities

import com.twilio.sync.entities.SyncStreamImpl
import com.twilio.sync.repository.OpenOptions
import com.twilio.sync.repository.StreamMessagePublishedNotification
import com.twilio.sync.repository.SyncRepository
import com.twilio.sync.sqldelight.cache.persistent.StreamCacheMetadata
import com.twilio.sync.subscriptions.SubscriptionManager
import com.twilio.test.util.ExcludeFromInstrumentedTests
import com.twilio.test.util.runTest
import com.twilio.test.util.setupTestLogging
import com.twilio.test.util.testCoroutineContext
import com.twilio.test.util.testCoroutineScope
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason.OpenStreamError
import com.twilio.util.ErrorReason.Timeout
import com.twilio.util.ErrorReason.Unknown
import com.twilio.util.TwilioException
import com.twilio.util.toTwilioException
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.RelaxedMockK
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant

@ExcludeFromInstrumentedTests
class SyncStreamImplTest {

    private lateinit var syncStream: SyncStreamImpl

    @RelaxedMockK
    private lateinit var subscriptionManager: SubscriptionManager

    @RelaxedMockK
    private lateinit var syncRepository: SyncRepository

    private val messagesFlow = MutableSharedFlow<StreamMessagePublishedNotification>()

    @BeforeTest
    fun setUp() {
        setupTestLogging()
        MockKAnnotations.init(this, relaxUnitFun = true)
        coEvery { syncRepository.getStreamMessages(any()) } returns messagesFlow

        syncStream = SyncStreamImpl(
            coroutineScope = testCoroutineScope,
            subscriptionManager = subscriptionManager,
            repository = syncRepository
        )
    }

    @Test
    fun open() = runTest {
        val openOptions = OpenOptions.CreateNew(uniqueName = null)
        val metadata = StreamCacheMetadata(
            sid = "TO000",
            uniqueName = "testUniqueName",
            dateExpires = Instant.DISTANT_PAST,
        )
        coEvery { syncRepository.getStreamMetadata(openOptions) } returns flowOf(metadata)
        syncStream.open(openOptions)

        assertEquals(syncStream.sid, metadata.sid)
        assertEquals(syncStream.uniqueName, metadata.uniqueName)
        assertEquals(syncStream.dateExpires, metadata.dateExpires)
    }

    @Test
    fun openTimeout() = runTest {
        val openOptions = OpenOptions.CreateNew(uniqueName = null)
        val expectedException = TwilioException(ErrorInfo(Timeout))
        coEvery { syncRepository.getStreamMetadata(openOptions) } returns flow { throw expectedException }

        val actualException = assertFails { syncStream.open(openOptions) }.toTwilioException(Unknown)

        assertEquals(expectedException.errorInfo, actualException.errorInfo)
    }

    @Test
    fun openError() = runTest {
        val openOptions = OpenOptions.CreateNew(uniqueName = null)
        coEvery { syncRepository.getStreamMetadata(openOptions) } returns flowOf(null)

        val actualException = assertFails { syncStream.open(openOptions) }.toTwilioException(Unknown)

        assertEquals(ErrorInfo(OpenStreamError), actualException.errorInfo)
    }
}
