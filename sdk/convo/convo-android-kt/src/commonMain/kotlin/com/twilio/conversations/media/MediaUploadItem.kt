//
//  Twilio Conversations Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
@file:Suppress("DEPRECATION")

package com.twilio.conversations.media

import com.twilio.conversations.MediaCategory
import com.twilio.conversations.MediaUploadListener
import io.ktor.utils.io.core.Input

/** @suppress */
class MediaUploadItem internal constructor(
    val input: Input,
    val contentType: String,
    val category: MediaCategory,
    val filename: String? = null,
    val listener: MediaUploadListener? = null,
)
