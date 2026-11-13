package com.elad.codusb

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import org.conscrypt.Conscrypt
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.security.Security

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        L.init(this)
        WifiWatch.register(this)
        try {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        } catch (_: Throwable) {
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("L")
            } catch (_: Throwable) {
            }
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(Notif.CH_PAIR, L.t("צימוד", "Pairing"), NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(Notif.CH_STATUS, L.t("סטטוס השלט", "Controller status"), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
}
