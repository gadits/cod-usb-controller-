package com.elad.codusb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/**
 * Auto app: Android turns Wireless debugging off whenever Wi-Fi drops or changes.
 * The system wakes this receiver every time Wi-Fi connects (even with the app closed),
 * and it turns Wireless debugging straight back on.
 */
class WifiWatch : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (!Starter.isPaired(ctx) || !Starter.hasSecureSettings(ctx)) return
        try {
            if (Starter.enableWirelessDebugging(ctx)) {
                Starter.log(ctx, L.t("WiFi התחבר – ניפוי באגים אלחוטי הודלק מחדש", "Wi-Fi connected – Wireless debugging turned back on"))
            }
        } catch (_: Throwable) {
        }
    }

    companion object {
        /** Ask the system to wake [WifiWatch] on every Wi-Fi connection. Safe to call many times. */
        fun register(ctx: Context) {
            if (!Flavor.AUTO) return
            try {
                val pi = PendingIntent.getBroadcast(
                    ctx, 41, Intent(ctx, WifiWatch::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                val req = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build()
                ctx.getSystemService(ConnectivityManager::class.java).registerNetworkCallback(req, pi)
            } catch (_: Throwable) {
            }
        }
    }
}
