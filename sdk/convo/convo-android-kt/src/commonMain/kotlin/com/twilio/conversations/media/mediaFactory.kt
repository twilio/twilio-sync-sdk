//
//  Twilio Conversations Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.conversations.media

import io.ktor.client.HttpClient

internal expect fun createHttpClient(
    httpConnectionTimeout: Int,
    certificates: String,
    useCertificates: Boolean = true,
    useProxy: Boolean = false
): HttpClient
