//
//  Twilio Conversations Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.conversations

import com.twilio.util.ErrorInfo

/**
 * Listener interface for media uploading progress reporting.
 */
interface MediaUploadListener {

    /**
     * Callback to report start of a media uploading.
     */
    fun onStarted() = Unit

    /**
     * Callback to report the running progress of a media uploading.
     *
     * @param bytesSent Number of completed bytes.
     */
    fun onProgress(bytesSent: Long) = Unit

    /**
     * Callback to report successful completion of a media uploading.
     *
     * @param mediaSid SID of uploaded media.
     */
    fun onCompleted(mediaSid: String) = Unit

    /**
     * Callback to report error while media uploading.
     *
     * @param errorInfo Object containing error information.
     */
    fun onFailed(errorInfo: ErrorInfo) = Unit
}

/** @suppress */
inline fun MediaUploadListener(
    crossinline onStarted: () -> Unit = {},
    crossinline onProgress: (bytesSent: Long) -> Unit = {},
    crossinline onCompleted: (mediaSid: String) -> Unit = {},
    crossinline onFailed: (errorInfo: ErrorInfo) -> Unit = {},
) = object : MediaUploadListener {

    override fun onStarted() = onStarted()

    override fun onProgress(bytesSent: Long) = onProgress(bytesSent)

    override fun onCompleted(mediaSid: String) = onCompleted(mediaSid)

    override fun onFailed(errorInfo: ErrorInfo) = onFailed(errorInfo)
}

/** @suppress */
class MediaUploadListenerBuilder {

    private var _onStarted: () -> Unit = {}

    private var _onProgress: (Long) -> Unit = {}

    private var _onCompleted: (String) -> Unit = {}

    private var _onFailed: (ErrorInfo) -> Unit = {}

    fun onStarted(block: () -> Unit) {
        _onStarted = block
    }

    fun onProgress(block: (bytesSent: Long) -> Unit) {
        _onProgress = block
    }

    fun onCompleted(block: (mediaSid: String) -> Unit) {
        _onCompleted = block
    }

    fun onFailed(block: (ErrorInfo) -> Unit) {
        _onFailed = block
    }

    @PublishedApi
    internal fun build() = MediaUploadListener(_onStarted, _onProgress, _onCompleted, _onFailed)
}
