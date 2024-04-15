package com.twilio.nativesyncjava.util

import android.net.Uri
import android.util.Log
import com.twilio.sync.testapp.BuildConfig
import java.net.URL
import kotlin.time.Duration

private val TAG = "Utils"

fun generateUserName() = generateRandomString("username").also { Log.i(TAG, "generateUserName: $it") }

fun generateRandomString(prefix: String = "randomString"): String {
    val number = (Math.random() * 1_000_000).toInt()
    return "$prefix%06d".format(number)
}

@JvmOverloads
fun getToken(identity: String = generateUserName()) = requestToken(identity)

fun requestToken(
    identity: String = generateUserName(),
    ttl: Duration? = null,
    accessTokenServiceUrl: String = BuildConfig.ACCESS_TOKEN_SERVICE_URL
): String {
    val url = Uri.parse(accessTokenServiceUrl).buildUpon().apply {
        appendQueryParameter("identity", identity)
        ttl?.inWholeSeconds?.let { appendQueryParameter("ttl", "$it") }
    }

    val token = URL("$url").readText()
    return token
}
