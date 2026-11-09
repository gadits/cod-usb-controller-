package com.elad.codusb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Starter.isPaired(context) || !Starter.autoStart(context)) return
        Starter.log(context, "המכשיר הודלק – מפעיל אוטומטית")
        try {
            context.startForegroundService(Intent(context, StartService::class.java))
        } catch (t: Throwable) {
            Starter.log(context, "הפעלה אוטומטית נכשלה: ${t.message}")
        }
    }
}
