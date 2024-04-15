//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.utils

internal expect fun readCertificates(deferCertificateTrustToPlatform: Boolean): List<String>
