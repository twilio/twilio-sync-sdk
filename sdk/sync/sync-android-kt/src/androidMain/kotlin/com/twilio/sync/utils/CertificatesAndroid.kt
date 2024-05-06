//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.utils

import android.content.Context
import android.util.Base64
import com.twilio.sync.client.R
import com.twilio.util.ApplicationContextHolder
import com.twilio.util.TwilioLogger
import com.twilio.util.splitCertificates
import java.io.IOException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateEncodingException
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

private val logger by lazy { TwilioLogger.getLogger("Certificates") }

internal actual fun readCertificates(deferCertificateTrustToPlatform: Boolean): List<String> {
    val context = ApplicationContextHolder.applicationContext
    val certificates = readCertificateStore(context, deferCertificateTrustToPlatform)
    return splitCertificates(certificates)
}

// Get Android CA store OR our pinned cert resource
private fun readCertificateStore(context: Context, deferCertificateTrustToPlatform: Boolean): String {
    logger.d { "readCertificateStore deferCertificateTrustToPlatform: $deferCertificateTrustToPlatform" }

    val certs = mutableListOf<X509Certificate>()
    var chainIncomplete = false

    if (deferCertificateTrustToPlatform) {
        try {
            val factory = TrustManagerFactory.getInstance("X509")
            factory.init(null as KeyStore?)
            factory.trustManagers.forEach { tm ->
                val sslTrustManager = tm as X509TrustManager?
                sslTrustManager?.let { certs.addAll(it.acceptedIssuers) }
            }
        } catch (e: NoSuchAlgorithmException) {
            logger.w("Not found X509 trust manager - fallback to embedded CAs", e)
            chainIncomplete = true
        } catch (e: KeyStoreException) {
            logger.w("Exception in keystore - should NOT happen, fallback to embedded CAs", e)
            chainIncomplete = true
        }
    }

    val certificates = StringBuilder()

    try {
        if (certs.isEmpty() || chainIncomplete) {
            logger.d { "certs.size: ${certs.size}; chainIncomplete: $chainIncomplete" }
            return fallbackCopy(context)
        }

        try {
            certs.forEach { c ->
                certificates.append("\n-----BEGIN CERTIFICATE-----\n")
                certificates.append(Base64.encodeToString(c.encoded, Base64.DEFAULT))
                certificates.append("-----END CERTIFICATE-----\n")
            }
        } catch (e: CertificateEncodingException) {
            logger.e("SSL CA store error - fallback to default", e)
            return fallbackCopy(context)
        }
    } catch (e: IOException) {
       logger.e("Unable to install SSL certificate - connections will fail", e)
    }

    return certificates.toString()
}

@Throws(IOException::class)
private fun fallbackCopy(context: Context): String {
    logger.d("fallbackCopy")
    return context.resources.openRawResource(R.raw.rootcert).reader().use { it.readText() }
}
