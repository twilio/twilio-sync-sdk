//
//  Twilio Conversations Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.conversations.media

import com.twilio.util.ApplicationDispatcher
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason.MediaFetchError
import com.twilio.util.toTwilioException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

interface CancellationToken {
    fun cancel()
}

interface MediaClientIos {

    fun upload(items: List<MediaUploadItem>, completion: (List<String>?, ErrorInfo?) -> Unit): CancellationToken

    fun getTemporaryContentUrl(mediaSid: String, completion: (String?, ErrorInfo?) -> Unit): CancellationToken

    fun shutdown()
}

internal class MediaClientIosImpl (
    transport: MediaTransport,
    maxActiveUploads: Int,
): MediaClientIos, MediaClient(transport, maxActiveUploads) {

    private val coroutineScope = CoroutineScope(ApplicationDispatcher + SupervisorJob())

    override fun upload(items: List<MediaUploadItem>, completion: (List<String>?, ErrorInfo?) -> Unit): CancellationToken {
        val job = coroutineScope.launch {
            val result = runCatching { upload(items) }
            val mediaSids = result.getOrElse { t ->
                val errorData = t.toTwilioException(MediaFetchError).errorInfo
                completion(null, errorData)
                return@launch
            }
            completion(mediaSids, null)
        }
        return job.toCancellationToken()
    }

    override fun getTemporaryContentUrl(mediaSid: String, completion: (String?, ErrorInfo?) -> Unit): CancellationToken {
        val job = coroutineScope.launch {
            val result = runCatching { getTemporaryContentUrl(mediaSid) }
            val url = result.getOrElse { t ->
                val errorData = t.toTwilioException(MediaFetchError).errorInfo
                completion(null, errorData)
                return@launch
            }
            completion(url, null)
        }
        return job.toCancellationToken()
    }
}

private fun Job.toCancellationToken() = object : CancellationToken {
    override fun cancel() = this@toCancellationToken.cancel()
}
