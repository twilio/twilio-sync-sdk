//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.utils

internal expect fun readCertificates(deferCertificateTrustToPlatform: Boolean): List<String>
