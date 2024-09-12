package com.twilio.conversations.test.integration

import com.twilio.conversations.MediaCategory
import com.twilio.conversations.media.MediaTransportImpl
import com.twilio.conversations.test.util.asInput
import com.twilio.conversations.test.util.createTestHttpClient
import com.twilio.util.TwilioException
import com.twilio.util.ErrorReason
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class MediaTransportAndroidTest {

//    @Test // TODO: automate switching device online/offine
    fun noConnection() = runBlocking {
        val httpClient = createTestHttpClient()

        val transport = MediaTransportImpl(
            token = "doesn't matter as we are offline",
            serviceUrl = "https://example.com",
            mediaSetUrl = "https://example.com",
            productId = "doesn't matter as we are offline",
            httpClient,
        )
        val result = runCatching { transport.uploadFile("file1", "text/plain", MediaCategory.MEDIA.value, "ABC".asInput()) }

        val error = result.exceptionOrNull()
        assertIs<TwilioException>(error)
        assertEquals(ErrorReason.MediaUploadError, error.errorInfo.reason)
    }
}
