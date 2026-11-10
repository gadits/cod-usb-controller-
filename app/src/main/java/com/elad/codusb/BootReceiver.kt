package com.elad.codusb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Starter.isPaired(context) || !Starter.autoStart(context)) return
        Starter.log(context, L.t("המכשיר הודלק – מפעיל אוטומטית", "Device started – starting automatically"))
        try {
            context.startForegroundService(Intent(context, StartService::class.java))
        } catch (t: Throwable) {
            Starter.log(context, L.t("הפעלה אוטומטית נכשלה: ${t.message}", "Auto start failed: ${t.message}"))
        }
    }
}
