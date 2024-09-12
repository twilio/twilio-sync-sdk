//
//  Twilio Conversations Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.conversations.media

import com.twilio.util.ErrorReason.MediaUploadError
import com.twilio.util.ExpirableCache
import com.twilio.util.logger
import com.twilio.util.toListenableInput
import com.twilio.util.toTwilioException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal open class MediaClient(
    private val transport: MediaTransport,
    maxActiveUploads: Int,
    downloadUrlsCacheFlushInterval: Long = 180_000
) {
    private val semaphore = Semaphore(maxActiveUploads)

    private val temporaryDownloadUrlsCache = ExpirableCache<String, String>(downloadUrlsCacheFlushInterval)

    suspend fun upload(items: List<MediaUploadItem>): List<String> = coroutineScope {
        val jobs = items.map { item ->
            async {
                semaphore.withPermit { uploadItem(item) }
            }
        }

        jobs.awaitAll()
    }

    private suspend fun uploadItem(item: MediaUploadItem): String {
        item.listener?.onStarted()

        // io.ktor.client.content.ProgressListener notifies about sent bytes
        // of all content, including headers etc. So our custom
        // ListenableInput fits better for our use case.
        val input = item.input.toListenableInput { bytesRead ->
            item.listener?.onProgress(bytesRead)
        }

        val result = runCatching { transport.uploadFile(item.filename, item.contentType, item.category.value, input) }

        val mediaSid = result.getOrElse { t ->
            val exception = t.toTwilioException(MediaUploadError)
            logger.e("Error uploading file:", exception)
            item.listener?.onFailed(exception.errorInfo)
            throw exception
        }

        logger.d("File uploaded successfully: ${item.filename}")
        item.listener?.onCompleted(mediaSid)
        return mediaSid
    }

    suspend fun downloadJsonMediaAsText(mediaSid: String): String {
        logger.d("downloadJsonMedia: $mediaSid")
        val url = getTemporaryContentUrl(mediaSid)
        return transport.downloadFileAsText(url)
    }

    suspend fun getTemporaryContentUrl(mediaSid: String) = temporaryDownloadUrlsCache.getOrPut(mediaSid) {
        logger.d("getTemporaryContentUrl: $mediaSid")
        transport.getTemporaryContentUrl(mediaSid)
    }

    suspend fun getTemporaryContentUrlList(mediaSids: List<String>): Map<String, String> {
        val cachedUrls = mutableMapOf<String, String>()
        val sidsForRequest = mutableListOf<String>()

        mediaSids.forEach { sid ->
            temporaryDownloadUrlsCache[sid]?.let { cachedUrls[sid] = it } ?: sidsForRequest.add(sid)
        }

        if (sidsForRequest.isEmpty()) {
            logger.d("getTemporaryContentUrlList: All urls are cached: ${cachedUrls.keys}")
            return cachedUrls
        }

        logger.d("getTemporaryContentUrlList: cached sids: ${cachedUrls.keys}}")
        logger.d("getTemporaryContentUrlList: sidsForRequest: $sidsForRequest")

        val requestedUrls = transport.getTemporaryContentUrlList(sidsForRequest)
        temporaryDownloadUrlsCache.putAll(requestedUrls)

        return cachedUrls + requestedUrls
    }

    fun updateToken(token: String) {
        transport.token = token
    }

    fun shutdown() = transport.shutdown()
}
