package com.elad.codusb

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import java.util.concurrent.Executors

/**
 * Battery saver (Xiaomi): when the screen turns off with a controller plugged in, turns OTG off so the phone
 * stops powering the controller; when the screen turns back on, turns OTG on again.
 * Screen on/off can only be heard by a running app, hence this small always-on service.
 */
class OtgWatchService : Service() {
    private val worker = Executors.newSingleThreadExecutor()

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val ctx = applicationContext
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> worker.execute {
                    if (!Starter.otgSaver(ctx) || Starter.realGamepads(ctx).isEmpty()) return@execute
                    Starter.log(ctx, L.t("המסך כבה – מכבה OTG כדי לחסוך בסוללה", "Screen off – turning OTG off to save battery"))
                    Starter.log(ctx, Starter.otgSet(ctx, false))
                }
                Intent.ACTION_SCREEN_ON -> worker.execute {
                    if (Starter.prefs(ctx).getString("otg_keys", "").isNullOrEmpty()) return@execute
                    Starter.log(ctx, L.t("המסך נדלק – מדליק OTG", "Screen on – turning OTG back on"))
                    Starter.log(ctx, Starter.otgSet(ctx, true))
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val n = Notif.simple(this, Notif.CH_STATUS,
            L.t("חיסכון בסוללה: OTG ייכבה כשהמסך כבוי", "Battery saver: OTG turns off while the screen is off"), true)
        if (Build.VERSION.SDK_INT >= 34) startForeground(Notif.ID_OTG, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(Notif.ID_OTG, n)
        registerReceiver(screen, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Starter.otgSaver(this)) { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    override fun onDestroy() {
        try { unregisterReceiver(screen) } catch (_: Throwable) {}
        worker.shutdown()
        super.onDestroy()
    }
}
