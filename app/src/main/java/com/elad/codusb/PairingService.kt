package com.elad.codusb

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlin.concurrent.thread

/**
 * One-time pairing with the device's own wireless debugging.
 * The pairing code is typed straight into the notification, so the Settings screen
 * with the code can stay open.
 */
class PairingService : Service() {

    @Volatile
    private var pairingPort = -1
    @Volatile
    private var busy = false
    private var mdns: AdbMdns? = null
    private val handler = Handler(Looper.getMainLooper())
    private val nm by lazy { getSystemService(NotificationManager::class.java) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REPLY -> {
                val code = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(KEY_CODE)?.toString()?.filter { it.isDigit() } ?: ""
                if (!busy) thread { pair(code) }
            }
            ACTION_STOP -> finish(null)
            else -> begin()
        }
        return START_NOT_STICKY
    }

    private fun begin() {
        val n = Notif.simple(
            this, Notif.CH_PAIR,
            "מחפש... פתח 'ניפוי באגים אלחוטי' ← 'צימוד מכשיר באמצעות קוד צימוד'.",
            true
        )
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notif.ID_PAIR, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notif.ID_PAIR, n)
        }
        mdns?.stop()
        mdns = AdbMdns(this, AdbMdns.PAIRING) { port ->
            if (port != pairingPort && !busy) {
                pairingPort = port
                nm.notify(Notif.ID_PAIR, inputNotification("נמצא! הקלד כאן את קוד הצימוד בן 6 הספרות:"))
            }
        }.also { it.start() }
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ if (!busy) finish("הצימוד בוטל (עברו 10 דקות). נסה שוב מהאפליקציה.") }, 10 * 60_000L)
    }

    private fun inputNotification(text: String): Notification {
        val replyIntent = PendingIntent.getService(
            this, 1,
            Intent(this, PairingService::class.java).setAction(ACTION_REPLY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val remoteInput = RemoteInput.Builder(KEY_CODE).setLabel("קוד צימוד").build()
        val action = Notification.Action.Builder(null, "הקלד קוד", replyIntent)
            .addRemoteInput(remoteInput)
            .build()
        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, PairingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, Notif.CH_PAIR)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("צימוד שלט USB ל-COD")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .addAction(action)
            .addAction(Notification.Action.Builder(null, "ביטול", stopIntent).build())
            .setOngoing(true)
            .setOnlyAlertOnce(false)
            .build()
    }

    private fun pair(code: String) {
        if (code.length != 6 || pairingPort <= 0) {
            nm.notify(Notif.ID_PAIR, inputNotification("הקוד צריך להיות 6 ספרות. נסה שוב:"))
            return
        }
        busy = true
        nm.notify(Notif.ID_PAIR, Notif.simple(this, Notif.CH_PAIR, "מצמד...", true))
        val ok = try {
            AdbManager.get(this).pair("127.0.0.1", pairingPort, code)
        } catch (t: Throwable) {
            Starter.log(this, "צימוד נכשל: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
        if (!ok) {
            busy = false
            nm.notify(
                Notif.ID_PAIR,
                inputNotification("הצימוד נכשל (קוד שגוי או שפג תוקפו). פתח קוד חדש והקלד אותו כאן:")
            )
            return
        }
        Starter.setPaired(this, true)
        Starter.log(this, "הצימוד הצליח")
        mdns?.stop()
        val result = Starter.afterPairing(this) { msg ->
            nm.notify(Notif.ID_PAIR, Notif.simple(this, Notif.CH_PAIR, msg, true))
        }
        Starter.log(this, result)
        finish(result)
    }

    private fun finish(message: String?) {
        handler.removeCallbacksAndMessages(null)
        mdns?.stop()
        mdns = null
        busy = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (message != null) Notif.result(this, message)
        stopSelf()
    }

    override fun onDestroy() {
        mdns?.stop()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.elad.codusb.PAIR_START"
        const val ACTION_REPLY = "com.elad.codusb.PAIR_REPLY"
        const val ACTION_STOP = "com.elad.codusb.PAIR_STOP"
        const val KEY_CODE = "code"
    }
}
