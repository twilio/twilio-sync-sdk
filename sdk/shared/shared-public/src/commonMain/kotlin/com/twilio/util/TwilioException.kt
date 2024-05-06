//
//  Twilio Utils
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.util

/** Human readable error reason. */
enum class ErrorReason(val value: Int, val description: String) {
    Unknown(0, "Unknown error"),
    Cancelled(1, "Job was cancelled"),
    Timeout(2, "Timeout occurred"),
    MediaUploadError(3, "Media upload error"),
    MediaFetchError(4, "Media fetch error"),
    TooManyAttachments(5, "Too many attachments. See ConversationLimits.mediaAttachmentsCountLimit"),
    UnsupportedEmailBodyContentType(6, "Unsupported email body content type. See ConversationLimits.emailBodiesAllowedContentTypes"),
    UnsupportedEmailHistoryContentType(7, "Unsupported email history content type. See ConversationLimits.emailHistoriesAllowedContentTypes"),
    TransportDisconnected(8, "Transport has been disconnected"),
    NetworkBecameUnreachable(9, "Network became unreachable"),
    Unauthorized(10, "Unauthorized"),
    TokenExpired(11, "Token expired"),
    TokenUpdatedLocally(12, "Transport disconnected, token updated locally and will be used at reconnect"),
    TooManyRequests(13, "Request failed with 429 reply"),
    HostnameUnverified(14, "Failed on ssl handshake: CERTIFICATE_VERIFY_FAILED"),
    SslHandshakeError(15, "Failed on ssl handshake: UNAUTHORIZED"),
    CloseMessageReceived(16, "Twilsock received a close message from the server"),
    CannotParse(17, "Error parsing incoming message"),
    RetrierReachedMaxAttempsCount(18, "Retrier reached max attempts count"),
    RetrierReachedMaxTime(19, "Retrier reached max time"),
    ContentTemplatesFetchError(20, "Content templates fetch error"),
    CommandRecoverableError(21, "Command finished with recoverable error"),
    CommandPermanentError(22, "Command finished with permanent error"),
    NoContentSid(23, "Cannot return content data for this message, because contentSid is null"),
    ContentMediaDownloadError(24, "Media download error"),
    CreateClientError(25, "Create client error"),
    UpdateTokenError(26, "Update token error"),
    OpenStreamError(27, "Open stream error. Probably stream has been just removed remotely"),
    OpenDocumentError(28, "Open document error. Probably document has been just removed remotely"),
    OpenCollectionError(29, "Open map error. Probably map has been just removed remotely"),
    MutateOperationAborted(30, "Mutate operation has been aborted. Mutator returned `null` value"),
    MutateCollectionItemNotFound(31, "Mutate operation failed: Collection item not found"),
    MismatchedLastUserAccount(32, "Last user account does not match the current user account from twilio token. " +
            "Create client with matching twilio token or set the useLastUserCache parameter to false."),
    ClientShutdown(33, "SyncClient already shutdown"),
    IteratorError(34, "Error while iterating over the collection"),
}

/**
 * Exception class that contains [ErrorInfo] object with error details.
 *
 * @constructor Create TwilioException from [ErrorInfo] and cause.
 */
class TwilioException(
    val errorInfo: ErrorInfo,
    cause: Throwable? = null
) : Exception("$errorInfo", cause)
