//
//  Twilio Twilsock Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.twilsock.util

import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkRequest
import android.os.Build
import com.twilio.util.ApplicationContextHolder
import com.twilio.util.logger
import kotlin.properties.Delegates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal actual class ConnectivityMonitorImpl actual constructor(private val coroutineScope: CoroutineScope) :
    ConnectivityMonitor {

    private val connectivityManager: ConnectivityManager? = try {
        val context = ApplicationContextHolder.applicationContext
        context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
    } catch (t: Throwable) { // Possible fix for RTDSDK-4218
        logger.w("Cannot get ConnectivityManager, considering network as always available", t)
        null
    }

    override var isNetworkAvailable by Delegates.observable(initNetworkAvailable()) { _, old, new ->
        if (old != new) {
            coroutineScope.launch { onChanged() }
        }
    }

    override val defaultNetworkId: String?
        get() = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                connectivityManager?.activeNetwork?.toString()
            } else {
                null
            }
        } catch (e: Exception) {
            logger.w("Cannot get defaultNetworkId", e)
            null
        }

    override var onChanged: () -> Unit = {}
    override var onDefaultNetworkChanged: (networkId: String?) -> Unit = {}

    private val connectionStatusCallback by lazy { ConnectionStatusCallback() }
    private val defaultNetworkCallback by lazy { DefaultNetworkCallback() }

    override fun start() {
        try {
            connectivityManager?.registerNetworkCallback(NetworkRequest.Builder().build(), connectionStatusCallback)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager?.registerDefaultNetworkCallback(defaultNetworkCallback)
            }
        } catch (e: Exception) {
            logger.w("Cannot registerNetworkCallback (probably app doesn't have ACCESS_NETWORK_STATE " +
                    "permission? Considering network as always available)", e)
            isNetworkAvailable = true
        }
    }

    override fun stop() {
        try {
            connectivityManager?.unregisterNetworkCallback(connectionStatusCallback)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager?.unregisterNetworkCallback(defaultNetworkCallback)
            }
        } catch (e: Exception) {
            logger.w("Cannot unregisterNetworkCallback (probably app doesn't have ACCESS_NETWORK_STATE " +
                    "permission?", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun initNetworkAvailable(): Boolean = try {
        when {
            // ConnectivityManager unavailable. Considering network as always available
            connectivityManager == null -> true

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                val network = connectivityManager.activeNetwork
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                capabilities?.hasCapability(NET_CAPABILITY_INTERNET) ?: false
            }

            else -> {
                val activeNetwork = connectivityManager.activeNetworkInfo // Deprecated in 29
                activeNetwork != null && activeNetwork.isConnectedOrConnecting // // Deprecated in 28
            }
        }
    } catch (e: Exception) {
        logger.w("Cannot read current network state (probably app doesn't have ACCESS_NETWORK_STATE " +
                    "permission? Considering network as available)", e)
        true
    }

    private inner class ConnectionStatusCallback : ConnectivityManager.NetworkCallback() {

        private val activeNetworks = mutableListOf<Network>()

        override fun onLost(network: Network) {
            activeNetworks.removeAll { activeNetwork -> activeNetwork == network }
            isNetworkAvailable = activeNetworks.isNotEmpty()
        }

        override fun onAvailable(network: Network) {
            if (activeNetworks.none { activeNetwork -> activeNetwork == network }) {
                activeNetworks.add(network)
            }
            isNetworkAvailable = activeNetworks.isNotEmpty()
        }
    }

    private inner class DefaultNetworkCallback : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            logger.d { "Default network changed to: $network" }
            coroutineScope.launch { onDefaultNetworkChanged(network.toString()) }
        }

        override fun onLost(network: Network) {
            logger.d { "Default network lost: $network" }
            coroutineScope.launch { onDefaultNetworkChanged(null) }
        }
    }
}
