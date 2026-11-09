package com.elad.codusb

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlin.concurrent.thread

/** Runs the full start (or stop) in the background and shows the result as a notification. */
class StartService : Service() {

    @Volatile
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val stop = intent?.action == ACTION_STOP
        val n = Notif.simple(this, Notif.CH_STATUS, if (stop) "מכבה..." else "מפעיל שלט וירטואלי...", true)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notif.ID_START, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notif.ID_START, n)
        }
        if (running) return START_NOT_STICKY
        running = true
        val nm = getSystemService(NotificationManager::class.java)
        thread {
            val result = try {
                if (stop) Starter.fullStop(this)
                else Starter.fullStart(this) { msg ->
                    nm.notify(Notif.ID_START, Notif.simple(this, Notif.CH_STATUS, msg, true))
                }
            } catch (t: Throwable) {
                "שגיאה: ${t.javaClass.simpleName}: ${t.message}"
            }
            Starter.log(this, result)
            stopForeground(STOP_FOREGROUND_REMOVE)
            Notif.result(this, result)
            sendBroadcast(Intent(ACTION_DONE).setPackage(packageName))
            running = false
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        const val ACTION_STOP = "com.elad.codusb.STOP"
        const val ACTION_DONE = "com.elad.codusb.DONE"
    }
}
