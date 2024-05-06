//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.utils

// We don't bundle any certificates on iOS, always deffer to system certificates instead.
// See TwilsockTransport implementation for iOS.
internal actual fun readCertificates(deferCertificateTrustToPlatform: Boolean): List<String> = emptyList()
