package com.twilio.twilsock.util

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import com.twilio.util.ApplicationContextHolder
import com.twilio.util.logger
import java.io.InputStreamReader
import java.util.*

class ProxyInfo {

    private val applicationContext = ApplicationContextHolder.applicationContext

    var host: String? = null
        private set

    var port = -1
        private set

    var user: String? = null
        private set

    var password: String? = null
        private set

    fun update() {
        try {
            readProxyConfigFromFile()
            return
        } catch (e: Exception) {
            logger.i("Cannot read proxy config from file: ", e)
        }

        try {
            readProxyConfigFromSystem()
        } catch (e: Exception) {
            logger.i("Cannot read proxy config from system: ", e)
        }
    }

    @Throws(Exception::class)
    private fun readProxyConfigFromSystem() {
        val m = ConnectivityManager::class.java.getMethod("getDefaultProxy")
        val manager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val proxy = m.invoke(manager) as? android.net.ProxyInfo ?: return
        parseHostParts(proxy.host)
        port = proxy.port
    }

    @Throws(Exception::class)
    private fun readProxyConfigFromFile() {
        applicationContext.assets.open("proxysettings.properties").use { inputStream ->
            val reader = InputStreamReader(inputStream, "UTF-8")
            val props = Properties()

            props.load(reader)
            parseHostParts(props.getProperty("host", null))

            port = props.getProperty("port", port.toString()).toInt()
            user = props.getProperty("user", user)
            password = props.getProperty("password", password)
        }
    }

    private fun parseHostParts(proxy: String?) {
        host = proxy

        host?.split('@')
            ?.takeIf { it.size >= 2 }
            ?.let {
                user = it[0]
                host = it[1]
            }

        user?.split(':')
            ?.takeIf { it.size >= 2 }
            ?.let {
                user = it[0]
                password = it[1]
            }
    }

    init {
        update()
    }
}
