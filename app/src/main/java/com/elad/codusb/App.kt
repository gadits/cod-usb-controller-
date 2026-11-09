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
            NotificationChannel(Notif.CH_PAIR, "צימוד", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(Notif.CH_STATUS, "סטטוס השלט", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
}
