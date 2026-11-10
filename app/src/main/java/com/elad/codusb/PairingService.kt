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
import android.widget.RemoteViews
import kotlin.concurrent.thread

/**
 * One-time pairing with the device's own wireless debugging.
 *
 * Why a notification: Android closes the pairing dialog as soon as the Settings screen is left,
 * and since Android 12 it hides other apps' floating windows over Settings. The system's own
 * pop-up notification with an input field is the one thing that can be used on top of the dialog
 * without closing it — so it is made as big and clear as Android allows: coloured, large text,
 * and it pops up by itself the moment the dialog opens.
 */
class PairingService : Service() {

    @Volatile
    private var pairingPort = -1
    @Volatile
    private var busy = false
    @Volatile
    private var detectedOnce = false
    private var mdns: AdbMdns? = null
    private val handler = Handler(Looper.getMainLooper())
    private val nm by lazy { getSystemService(NotificationManager::class.java) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_CODE)?.toString() ?: ""
                if (!busy) {
                    busy = true
                    thread(name = "codusb-pair") { pair(text) }
                } else {
                    post(progress("מצמד...", "רק רגע"))
                }
            }
            ACTION_STOP -> finish(null)
            else -> begin()
        }
        return START_NOT_STICKY
    }

    // ---------- notifications ----------

    private fun views(title: String, text: String, hint: String?): RemoteViews =
        RemoteViews(packageName, R.layout.notif_pair).apply {
            setTextViewText(R.id.n_title, title)
            setTextViewText(R.id.n_text, text)
            if (hint != null) {
                setTextViewText(R.id.n_hint, hint)
                setViewVisibility(R.id.n_hint, android.view.View.VISIBLE)
            } else {
                setViewVisibility(R.id.n_hint, android.view.View.GONE)
            }
        }

    private fun base(title: String, text: String, hint: String?, alert: Boolean): Notification.Builder {
        val v = views(title, text, hint)
        return Notification.Builder(this, Notif.CH_PAIR)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(v)
            .setCustomBigContentView(v)
            .setCustomHeadsUpContentView(v)
            .setColor(Ui.GREEN_DEEP)
            .setColorized(true)
            .setOngoing(true)
            .setOnlyAlertOnce(!alert)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setPriority(Notification.PRIORITY_MAX)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        // no content intent on purpose: opening the app would close the pairing dialog
    }

    /** Notification with the code input. [alert] = pop up on screen again. */
    private fun input(title: String, text: String, alert: Boolean): Notification {
        val replyIntent = PendingIntent.getService(
            this, 1,
            Intent(this, PairingService::class.java).setAction(ACTION_REPLY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val remoteInput = RemoteInput.Builder(KEY_CODE).setLabel("הקלד כאן את 6 הספרות").build()
        val reply = Notification.Action.Builder(null, ACTION_LABEL, replyIntent)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .build()
        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, PairingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return base(title, text, "⬇  לחץ '$ACTION_LABEL'", alert)
            .addAction(reply)
            .addAction(Notification.Action.Builder(null, "ביטול", stopIntent).build())
            .build()
    }

    private fun progress(title: String, text: String): Notification = base(title, text, null, false).build()

    private fun post(n: Notification) = nm.notify(Notif.ID_PAIR, n)

    // ---------- flow ----------

    private fun begin() {
        running = true
        val n = input(
            "צימוד – שלב אחרון",
            "בחר 'צימוד מכשיר באמצעות קוד צימוד'. ההתראה הזו תקפוץ שוב כשהקוד יופיע.",
            false
        )
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notif.ID_PAIR, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notif.ID_PAIR, n)
        }
        pairingPort = -1
        detectedOnce = false
        busy = false
        mdns?.stop()
        mdns = AdbMdns(this, AdbMdns.PAIRING) { port ->
            // a new port means a newly opened dialog: always keep the latest
            val isNew = port != pairingPort
            pairingPort = port
            if (isNew && !busy) {
                if (!detectedOnce) Starter.log(this, "חלון הצימוד זוהה")
                detectedOnce = true
                // pop up over the Settings dialog, ready for the code
                post(input("הקלד כאן את קוד הצימוד", "את 6 הספרות שמופיעות בחלון שמאחורי ההתראה", true))
            }
        }.also { it.start() }

        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ if (!busy) finish("הצימוד בוטל (עברו 15 דקות).") }, 15 * 60_000L)
    }

    /** [text] = "123456", or "123456 40373" (code + port) as a manual fallback. */
    private fun pair(text: String) {
        try {
            val numbers = Regex("\\d+").findAll(text).map { it.value }.toList()
            val code = numbers.firstOrNull { it.length == 6 } ?: ""
            val manualPort = numbers.firstOrNull { it.length in 4..5 }?.toIntOrNull() ?: 0
            if (code.length != 6) {
                post(input("הקוד צריך להיות 6 ספרות", "נסה שוב – הקלד את 6 הספרות מהחלון", true))
                return
            }
            post(progress("מצמד...", "רק רגע"))

            var port = if (manualPort > 0) manualPort else pairingPort
            if (port <= 0) {
                val until = System.currentTimeMillis() + 8_000
                while (pairingPort <= 0 && System.currentTimeMillis() < until) Thread.sleep(200)
                port = pairingPort
            }
            if (port <= 0) {
                post(
                    input(
                        "לא זיהיתי את חלון הצימוד",
                        "הקלד את הקוד, רווח, ואת המספר שאחרי ':' בשורת 'כתובת IP ויציאה'. לדוגמה: 123456 40373",
                        true
                    )
                )
                return
            }

            val ok = try {
                AdbManager.get(this).pair("127.0.0.1", port, code)
            } catch (t: Throwable) {
                Starter.log(this, "צימוד נכשל: ${t.javaClass.simpleName}: ${t.message}")
                false
            }
            if (!ok) {
                post(input("הקוד לא התקבל", "קוד שגוי או שפג תוקפו. סגור את החלון, פתח קוד חדש והקלד שוב", true))
                return
            }

            Starter.setPaired(this, true)
            running = false
            Starter.log(this, "הצימוד הצליח")
            mdns?.stop()
            mdns = null
            post(progress("✓ צומד!", "מפעיל את השלט..."))
            val result = Starter.afterPairing(this) { msg -> post(progress("✓ צומד!", msg)) }
            Starter.log(this, result)
            handler.post { finish(result) }
        } finally {
            busy = false
        }
    }

    private fun finish(message: String?) {
        running = false
        handler.removeCallbacksAndMessages(null)
        mdns?.stop()
        mdns = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (message != null) Notif.result(this, message)
        sendBroadcast(Intent(StartService.ACTION_DONE).setPackage(packageName))
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        mdns?.stop()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        @Volatile
        var running = false

        const val ACTION_START = "com.elad.codusb.PAIR_START"
        const val ACTION_REPLY = "com.elad.codusb.PAIR_REPLY"
        const val ACTION_STOP = "com.elad.codusb.PAIR_STOP"
        const val KEY_CODE = "code"
        const val ACTION_LABEL = "הקלד קוד צימוד"
    }
}
