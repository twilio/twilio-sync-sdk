//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.utils

import com.twilio.util.ErrorInfo
import io.ktor.http.HttpStatusCode

internal const val kUpdateIsForbidden = 51232
internal const val kNameAlreadyExist = 54301
internal const val kKeyAlreadyExist = 54208
internal const val kMapItemNotFound = 54201
internal const val kListItemNotFound = 54151

internal val ErrorInfo.isUnauthorised: Boolean
    get() = HttpStatusCode.fromValue(status) == HttpStatusCode.Unauthorized
            || HttpStatusCode.fromValue(status) == HttpStatusCode.Gone
            || (HttpStatusCode.fromValue(status) == HttpStatusCode.PermanentRedirect && code == kUpdateIsForbidden) // RTDSDK-3448

internal val ErrorInfo.isPreConditionFailed: Boolean
    get() = HttpStatusCode.fromValue(status) == HttpStatusCode.PreconditionFailed

internal val ErrorInfo.isNameAlreadyExist: Boolean
    get() = HttpStatusCode.fromValue(status) == HttpStatusCode.Conflict && code == kNameAlreadyExist

internal val ErrorInfo.isKeyAlreadyExist: Boolean
    get() = HttpStatusCode.fromValue(status) == HttpStatusCode.Conflict && code == kKeyAlreadyExist

internal val ErrorInfo.isItemNotFound: Boolean
    get() = HttpStatusCode.fromValue(status) == HttpStatusCode.NotFound
            && (code == kMapItemNotFound || code == kListItemNotFound)
