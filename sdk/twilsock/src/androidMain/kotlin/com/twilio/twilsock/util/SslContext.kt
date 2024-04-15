package com.twilio.twilsock.util

import com.twilio.util.TwilioLogger
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

@Suppress("FunctionName")
fun SslContext(certificates: List<String>): SSLContext {
    val ks = KeyStore.getInstance(KeyStore.getDefaultType())
    ks.load(null, null)

    // CertificateFactory
    val cf = CertificateFactory.getInstance("X.509")

    certificates.forEachIndexed { index, certificate ->
        val stream = ByteArrayInputStream(certificate.trim().encodeToByteArray())

        try {
            val ca = cf.generateCertificate(stream)
            ks.setCertificateEntry("entry$index", ca)
        } catch (e: Exception) {
            TwilioLogger.getLogger("SslContextHelper").e("Error parsing: \n$certificate", e)
        }
    }

    // TrustManagerFactory
    val algorithm = TrustManagerFactory.getDefaultAlgorithm()
    val tmf = TrustManagerFactory.getInstance(algorithm)
    // Create a TrustManager that trusts the CAs in our KeyStore
    tmf.init(ks)

    // Create a SSLContext with the certificate that uses tmf (TrustManager)
    return SSLContext.getInstance("TLS").apply {
        init(null, tmf.trustManagers, SecureRandom())
    }
}
