//
//  Twilio Conversations Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.conversations.test.integration

import com.twilio.conversations.MediaCategory
import com.twilio.conversations.media.MediaTransportImpl
import com.twilio.conversations.test.util.InfiniteInput
import com.twilio.conversations.test.util.InputThrowsException
import com.twilio.conversations.test.util.asInput
import com.twilio.conversations.test.util.createTestHttpClient
import com.twilio.conversations.test.util.kTestMediaServiceUrl
import com.twilio.conversations.test.util.kTestMediaSetServiceUrl
import com.twilio.conversations.test.util.testProductId
import com.twilio.test.util.requestToken
import com.twilio.util.ErrorReason.MediaFetchError
import com.twilio.util.ErrorReason.MediaUploadError
import com.twilio.util.TwilioException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.core.use
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class MediaTransportTest {

    lateinit var httpClient: HttpClient

    @BeforeTest
    fun setUp() {
        httpClient = createTestHttpClient()
    }

    @AfterTest
    fun tearDown() {
        httpClient.close()
    }

    @Test
    fun uploadFileSucceeded() = runBlocking {
        val transport = MediaTransportImpl(requestToken(), kTestMediaServiceUrl, kTestMediaSetServiceUrl, testProductId, httpClient)
        val mediaSid = transport.uploadFile("file1", "text/plain", MediaCategory.MEDIA.value, "ABC".asInput())

        assertTrue(mediaSid.startsWith("ME"))
    }

    @Test
    fun uploadFileInvalidToken() = runBlocking {
        val transport = MediaTransportImpl("invalidToken", kTestMediaServiceUrl, kTestMediaSetServiceUrl, testProductId, httpClient)
        val result = runCatching { transport.uploadFile("file1", "text/plain", MediaCategory.MEDIA.value, "ABC".asInput()) }

        val error = result.exceptionOrNull()
        assertIs<TwilioException>(error)
        assertEquals(MediaUploadError, error.errorInfo.reason)
        assertEquals(HttpStatusCode.Forbidden.value, error.errorInfo.status)
        assertEquals(20101, error.errorInfo.code)
        assertEquals("Scoped account service forbids authentication", error.errorInfo.message)
    }

    @Test
    fun uploadFileOverSizeLimit() = runBlocking { // Actual size limit is about 150MB
        val transport = MediaTransportImpl(requestToken(), kTestMediaServiceUrl, kTestMediaSetServiceUrl, testProductId, httpClient)
        val result = runCatching { transport.uploadFile("file1", "text/plain", MediaCategory.MEDIA.value, InfiniteInput()) }

        val error = result.exceptionOrNull()
        assertIs<TwilioException>(error)
        assertEquals(MediaUploadError, error.errorInfo.reason)
    }

    @Test
    fun inputThrowsException() = runBlocking {
        val transport = MediaTransportImpl(requestToken(), kTestMediaServiceUrl, kTestMediaSetServiceUrl, testProductId, httpClient)

        val exception = IllegalStateException("input cannot read")
        val input = InputThrowsException(exception)
        val result = runCatching { transport.uploadFile("file1", "text/plain", MediaCategory.MEDIA.value, input) }

        val error = result.exceptionOrNull()
        assertIs<TwilioException>(error)
        assertEquals(MediaUploadError, error.errorInfo.reason)

        val cause = error.cause
        assertIs<IllegalStateException>(cause)
        assertEquals(exception.message, cause.message)
    }

    @Test
    fun getTemporaryContentUrlSucceeded() = runBlocking {
        val content = "ABC"
        val transport = MediaTransportImpl(requestToken(), kTestMediaServiceUrl, kTestMediaSetServiceUrl, testProductId, httpClient)
        val mediaSid = transport.uploadFile("file1", "text/plain", MediaCategory.MEDIA.value, content.asInput())

        val url = transport.getTemporaryContentUrl(mediaSid)
        val downloadedContent = HttpClient().use { it.get(url).body<String>() }

        assertEquals(content, downloadedContent)
    }

    @Test
    fun getTemporaryContentUrlInvalidMediaSid() = runBlocking {
        val transport = MediaTransportImpl(requestToken(), kTestMediaServiceUrl, kTestMediaSetServiceUrl, testProductId, httpClient)

        val result = runCatching { transport.getTemporaryContentUrl("invalid mediaSid") }

        val error = result.exceptionOrNull()
        assertIs<TwilioException>(error)
        assertEquals(MediaFetchError, error.errorInfo.reason)
        assertEquals(HttpStatusCode.BadRequest.value, error.errorInfo.status)
        assertEquals(4000, error.errorInfo.code)
        assertEquals("Invalid URI parameter: Sid", error.errorInfo.message)
    }

    @Test
    fun getTemporaryContentUrlMediaSidNotFound() = runBlocking {
        val transport = MediaTransportImpl(requestToken(), kTestMediaServiceUrl, kTestMediaSetServiceUrl, testProductId, httpClient)

        val notExistingMediaSid = "ME67a50fcf57d19d81184a6127b45a2a4a"
        val result = runCatching { transport.getTemporaryContentUrl(notExistingMediaSid) }

        val error = result.exceptionOrNull()
        assertIs<TwilioException>(error)
        assertEquals(MediaFetchError, error.errorInfo.reason)
        assertEquals(HttpStatusCode.NotFound.value, error.errorInfo.status)
        assertEquals(4041, error.errorInfo.code)
        assertEquals("Media not found", error.errorInfo.message)
    }
}
