//
//  Twilio Utils
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.util

import com.twilio.util.ErrorReason.Unknown
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmStatic

/**
 * Representation of an Error object.
 *
 * This object encapsulates information about an SDK error and is passed around to listener callbacks.
 *
 * @constructor Create ErrorInfo from reason, status, error code and corresponding message.
 * @param  reason  Error category as a [ErrorReason].
 * @param  status  Error category as a classifier. Local client errors get status 0, network related
 *                 errors have their HTTP error code as a status.
 * @param  code    Unique twilio code for this error.
 *                 See also: [Error and Warning Dictionary](https://www.twilio.com/docs/api/errors)
 * @param  message Human readable description for the [status] property.
 * @param  description Human readable description for the [code] property.
 */
@Serializable
data class ErrorInfo(
    val reason: ErrorReason = Unknown,
    val status: Int = 0,
    val code: Int = 0,
    val message: String = "",
    val description: String = ""
) {
    /**
     * Create ErrorInfo from error code and corresponding message.
     * The status will be set to #CLIENT_ERROR.
     *
     * @param  errorCode    Unique twilio code for this error.
     *                      See also: [Error and Warning Dictionary](https://www.twilio.com/docs/api/errors)
     * @param  errorMessage Human readable description for the [status] property.
     */
    constructor(errorCode: Int, errorMessage: String)
            : this(Unknown, CLIENT_ERROR, errorCode, errorMessage)

    /**
     * Create ErrorInfo from status, error code and corresponding message.
     *
     * @param  errorStatus  Error category as a classifier. Local client errors get status 0, network related
     *                 errors have their HTTP error code as a status.
     * @param  errorCode    Unique twilio code for this error.
     *                      See also: [Error and Warning Dictionary](https://www.twilio.com/docs/api/errors)
     * @param  errorMessage Human readable description for the [status] property.
     */
    constructor(errorStatus: Int, errorCode: Int, errorMessage: String)
            : this(Unknown, errorStatus, errorCode, errorMessage)

    /**
     * Create ErrorInfo from status, error code and corresponding message.
     *
     * @param  errorStatus  Error category as a classifier. Local client errors get status 0, network related
     *                 errors have their HTTP error code as a status.
     * @param  errorCode    Unique twilio code for this error.
     *                      See also: [Error and Warning Dictionary](https://www.twilio.com/docs/api/errors)
     * @param  errorMessage Human readable description for the [status] property.
     * @param  errorDescription Human readable description for the [code] property.
     */
    constructor(errorStatus: Int, errorCode: Int, errorMessage: String, errorDescription: String)
            : this(Unknown, errorStatus, errorCode, errorMessage, errorDescription)

    /**
     * Convert ErrorInfo to string representation, for example for logging.
     *
     * @return String representation of ErrorInfo object.
     */
    override fun toString() = "${reason.description} $status:$code $message $description"

    companion object {
        /**
         * This status is set if error occured in the SDK and is not related to network operations.
         */
        @JvmStatic
        val CLIENT_ERROR = 0
        // Keep in sync with Error.h in jni!
        /**
         * This code is signaled to the listener of [Conversation.getMessageByIndex] if general error
         * occurs and message could not be retrieved.
         */
        @JvmStatic
        val CANNOT_GET_MESSAGE_BY_INDEX = -4

        /**
         * This code is signaled to the listener of [ConversationsClient.updateToken] if updated token
         * does not match the original token.
         *
         *
         * This error often indicates that you have updated token with a different identity,
         * which is not allowed - you cannot change client identity mid-flight.
         *
         *
         * If this error is returned, you should shutdown and re-create ConversationsClient.
         */
        @JvmStatic
        val MISMATCHING_TOKEN_UPDATE = -5

        /**
         * This code is signaled when an attempt is made to query conversation participants or messages without
         * synchronizing first.
         */
        @JvmStatic
        val CONVERSATION_NOT_SYNCHRONIZED = -6

        /**
         * This code is signaled to the listener of [ConversationsClient.getConversation] if conversations
         * with specified sid or uniqueName could not be found.
         */
        @JvmStatic
        val CONVERSATION_NOT_FOUND = -8

        /**
         * This code is signaled to the listener of [Conversation.UnsentMessage.send] if email
         * body content type is not supported.
         *
         * @see [ConversationLimits.emailBodiesAllowedContentTypes]
         */
        @JvmStatic
        val UNSUPPORTED_EMAIL_BODY_CONTENT_TYPE = -9

        /**
         * This code is signaled to the listener of [Conversation.UnsentMessage.send] if email
         * history content type is not supported.
         *
         * @see [ConversationLimits.emailHistoriesAllowedContentTypes]
         */
        @JvmStatic
        val UNSUPPORTED_EMAIL_HISTORY_CONTENT_TYPE = -10

        /**
         * This code is signaled to the listener of [Conversation.UnsentMessage.send] if the
         * message has too many attachments.
         *
         * @see [ConversationLimits.mediaAttachmentsCountLimit]
         */
        @JvmStatic
        val TOO_MANY_ATTACHMENTS = -11

        /**
         * This code is signaled to the listener of [Conversation.UnsentMessage.send] if an
         * error occurred while uploading one or more attachments.
         */
        @JvmStatic
        val MEDIA_UPLOAD_ERROR = -12
    }
}
