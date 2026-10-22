package com.wifi.quickconnect

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow

sealed class ConnectResult {
    data class Success(val ssid: String) : ConnectResult()
    data class WrongPassword(val ssid: String) : ConnectResult()
    data class Failed(val reason: String) : ConnectResult()
}

data class WifiNetwork(
    val ssid: String,
    val levelDbm: Int,
    val isSecured: Boolean
)

class WifiConnector(private val context: Context) {

    private val wifiManager: WifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager: ConnectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** Returns currently visible networks, deduplicated and sorted by signal strength. */
    fun scanResults(): List<WifiNetwork> {
        @Suppress("MissingPermission")
        val results: List<ScanResult> = try {
            wifiManager.scanResults
        } catch (e: SecurityException) {
            emptyList()
        }

        return results
            .filter { it.SSID.isNotBlank() }
            .groupBy { it.SSID }
            .map { (ssid, group) ->
                val best = group.maxByOrNull { it.level }!!
                WifiNetwork(
                    ssid = ssid,
                    levelDbm = best.level,
                    isSecured = best.capabilities?.let {
                        it.contains("WPA") || it.contains("WEP") || it.contains("PSK") || it.contains("EAP")
                    } ?: true
                )
            }
            .sortedByDescending { it.levelDbm }
    }

    fun startScan(): Boolean = wifiManager.startScan()

    /**
     * Connects to [ssid] with [password]. On Android 10+ this uses WifiNetworkSpecifier,
     * which shows a brief system confirmation sheet (required by the OS — apps can no
     * longer connect silently), then reports success/failure/wrong-password back here.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun connectModern(ssid: String, password: String): kotlinx.coroutines.flow.Flow<ConnectResult> = callbackFlow {
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(password)
            .build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(ConnectResult.Success(ssid))
                close()
            }

            override fun onUnavailable() {
                // System could not associate — almost always means a wrong
                // passphrase, the network being out of range, or the user
                // dismissing the system confirmation sheet.
                trySend(ConnectResult.WrongPassword(ssid))
                close()
            }
        }

        connectivityManager.requestNetwork(request, callback, 15_000)

        awaitClose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (_: IllegalArgumentException) {
                // already unregistered
            }
        }
    }

    /** Legacy path for Android 9 and below (direct WifiConfiguration API). */
    @Suppress("DEPRECATION")
    fun connectLegacy(ssid: String, password: String): ConnectResult {
        val config = WifiConfiguration().apply {
            SSID = "\"$ssid\""
            preSharedKey = "\"$password\""
        }
        val netId = wifiManager.addNetwork(config)
        if (netId == -1) return ConnectResult.Failed("নেটওয়ার্ক কনফিগারেশন ব্যর্থ হয়েছে।")

        wifiManager.disconnect()
        val enabled = wifiManager.enableNetwork(netId, true)
        wifiManager.reconnect()

        return if (enabled) ConnectResult.Success(ssid)
        else ConnectResult.WrongPassword(ssid)
    }
}
