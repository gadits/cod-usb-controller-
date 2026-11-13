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
                    post(progress(L.t("מצמד...", "Pairing..."), L.t("רק רגע", "One moment")))
                }
            }
            ACTION_CODE -> {
                val code = intent.getStringExtra(EXTRA_CODE) ?: ""
                val port = intent.getIntExtra(EXTRA_PORT, 0)
                if (!busy) {
                    busy = true
                    thread(name = "codusb-pair") { pair(if (port > 0) "$code $port" else code) }
                }
            }
            ACTION_STOP -> finish(null)
            else -> if (!running) begin()
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
        val remoteInput = RemoteInput.Builder(KEY_CODE).setLabel(L.t("הקלד כאן את 6 הספרות", "Type the 6 digits here")).build()
        val reply = Notification.Action.Builder(null, ACTION_LABEL, replyIntent)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .build()
        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, PairingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return base(title, text, L.t("⬇  לחץ '$ACTION_LABEL'", "⬇  Tap '$ACTION_LABEL'"), alert)
            .addAction(reply)
            .addAction(Notification.Action.Builder(null, L.t("ביטול", "Cancel"), stopIntent).build())
            .build()
    }

    private fun progress(title: String, text: String): Notification = base(title, text, null, false).build()

    private fun post(n: Notification) = nm.notify(Notif.ID_PAIR, n)

    // ---------- flow ----------

    private fun begin() {
        running = true
        PairingUi.done = false
        PairingUi.update(L.t("בחר בהגדרות 'צמד מכשיר באמצעות קוד צימוד'", "In Settings choose 'Pair device with pairing code'"), PairingUi.Kind.INFO)
        val n = input(
            L.t("ממתין לחלון הצימוד", "Waiting for the pairing window"),
            L.t("בחר 'צמד מכשיר באמצעות קוד צימוד'. התראה זו תופיע שוב כשהקוד יוצג.", "Choose 'Pair device with pairing code'. This notification pops up again when the code is shown."),
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
                if (!detectedOnce) Starter.log(this, L.t("חלון הצימוד זוהה", "Pairing window detected"))
                detectedOnce = true
                PairingUi.update(L.t("חלון הצימוד זוהה ✓ הקלד כאן את 6 הספרות", "Pairing window detected ✓ type the 6 digits here"), PairingUi.Kind.OK)
                // pop up over the Settings dialog, ready for the code
                // pop up over the dialog — unless the code box is already on screen (split screen)
                post(input(L.t("הקלד כאן את קוד הצימוד", "Type the pairing code here"), L.t("הזן את הקוד בן 6 הספרות המוצג בחלון הצימוד", "Enter the 6-digit code shown in the pairing window"), !PairingUi.inSplit))
            }
        }.also { it.start() }

        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ if (!busy) finish(L.t("הצימוד בוטל (עברו 15 דקות).", "Pairing cancelled (15 minutes passed).")) }, 15 * 60_000L)
    }

    /** [text] = "123456", or "123456 40373" (code + port) as a manual fallback. */
    private fun pair(text: String) {
        try {
            val numbers = Regex("\\d+").findAll(text).map { it.value }.toList()
            val code = numbers.firstOrNull { it.length == 6 } ?: ""
            val manualPort = numbers.firstOrNull { it.length in 4..5 }?.toIntOrNull() ?: 0
            if (code.length != 6) {
                PairingUi.update(L.t("הקוד צריך להיות 6 ספרות", "The code must be 6 digits"), PairingUi.Kind.ERROR)
                post(input(L.t("הקוד צריך להיות 6 ספרות", "The code must be 6 digits"), L.t("נסה שוב – הקלד את 6 הספרות מהחלון", "Try again – type the 6 digits from the window"), true))
                return
            }
            PairingUi.update(L.t("מצמד...", "Pairing..."), PairingUi.Kind.BUSY)
            post(progress(L.t("מצמד...", "Pairing..."), L.t("רק רגע", "One moment")))

            var port = if (manualPort > 0) manualPort else pairingPort
            if (port <= 0) {
                PairingUi.update(L.t("מחפש את חלון הצימוד...", "Looking for the pairing window..."), PairingUi.Kind.BUSY)
                val until = System.currentTimeMillis() + 8_000
                while (pairingPort <= 0 && System.currentTimeMillis() < until) Thread.sleep(200)
                port = pairingPort
            }
            if (port <= 0) {
                PairingUi.update(L.t("לא זיהיתי את חלון הצימוד – ודא שפתחת 'צמד מכשיר באמצעות קוד צימוד'", "Pairing window not found – make sure 'Pair device with pairing code' is open"), PairingUi.Kind.ERROR)
                post(
                    input(
                        L.t("לא זיהיתי את חלון הצימוד", "Pairing window not found"),
                        L.t("הקלד את הקוד, רווח, ואת המספר שאחרי ':' בשורת 'כתובת IP ויציאה'. לדוגמה: 123456 40373", "Type the code, a space, and the number after ':' in the 'IP address & Port' line. Example: 123456 40373"),
                        true
                    )
                )
                return
            }

            // try the local address first, then this device's Wi-Fi address; never wait forever
            val hosts = listOfNotNull("127.0.0.1", AdbMdns.lastHost).distinct()
            var ok = false
            for (host in hosts) {
                PairingUi.update(L.t("מצמד...", "Pairing..."), PairingUi.Kind.BUSY)
                Starter.log(this, L.t("מנסה לצמד: $host:$port", "Trying to pair: $host:$port"))
                val r = pairWithTimeout(host, port, code, 15_000)
                if (r == true) {
                    ok = true
                    break
                }
                Starter.log(this, if (r == null) L.t("אין תשובה מ-$host:$port (15 שניות)", "No answer from $host:$port (15 seconds)") else L.t("הצימוד נדחה ב-$host:$port", "Pairing refused at $host:$port"))
                if (r == false) break // a real answer (wrong/expired code): no point trying another address
            }
            if (!ok) {
                PairingUi.update(
                    L.t("הצימוד לא הצליח. ודא שחלון הקוד עדיין פתוח בהגדרות (אם נסגר – פתח קוד חדש) והקלד שוב", "Pairing failed. Make sure the code window is still open in Settings (if it closed – open a new code) and try again"),
                    PairingUi.Kind.ERROR
                )
                post(input(L.t("הקוד לא התקבל", "Code not accepted"), L.t("קוד שגוי או שפג תוקפו. סגור את החלון, פתח קוד חדש והקלד שוב", "Wrong or expired code. Close the window, open a new code and try again"), true))
                return
            }

            Starter.setPaired(this, true)
            running = false
            Starter.log(this, L.t("הצימוד הצליח", "Pairing succeeded"))
            mdns?.stop()
            mdns = null
            PairingUi.update(L.t("✓ צומד! מפעיל את השלט...", "✓ Paired! Starting the controller..."), PairingUi.Kind.BUSY)
            post(progress(L.t("✓ צומד!", "✓ Paired!"), L.t("מפעיל את השלט...", "Starting the controller...")))
            // the whole "connect + start" step is capped at one minute
            val ex = java.util.concurrent.Executors.newSingleThreadExecutor()
            val result = try {
                ex.submit<String> {
                    Starter.afterPairing(this) { msg ->
                        PairingUi.update(msg, PairingUi.Kind.BUSY)
                        post(progress(L.t("✓ צומד!", "✓ Paired!"), msg))
                    }
                }.get(60, java.util.concurrent.TimeUnit.SECONDS)
            } catch (_: java.util.concurrent.TimeoutException) {
                L.t("הצימוד נשמר ✓ אבל ההפעלה לקחה יותר מדי זמן. לחץ A במסך הראשי – אין צורך לצמד שוב.", "Pairing saved ✓ but starting took too long. Press A on the main screen – no need to pair again.")
            } catch (t: Throwable) {
                L.t("הצימוד נשמר ✓ אבל ההפעלה נכשלה (${t.message}). לחץ A במסך הראשי.", "Pairing saved ✓ but starting failed (${t.message}). Press A on the main screen.")
            } finally {
                ex.shutdownNow()
            }
            Starter.log(this, result)
            val good = Starter.isVirtualPresent(this)
            PairingUi.done = good
            PairingUi.update(if (good) "✓ $result" else result, if (good) PairingUi.Kind.OK else PairingUi.Kind.ERROR)
            handler.post { finish(result) }
        } finally {
            busy = false
        }
    }

    /** true = paired, false = refused (wrong/expired code), null = no answer in time. */
    private fun pairWithTimeout(host: String, port: Int, code: String, ms: Long): Boolean? {
        val ex = java.util.concurrent.Executors.newSingleThreadExecutor()
        return try {
            ex.submit<Boolean> { AdbManager.get(this).pair(host, port, code) }
                .get(ms, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (_: java.util.concurrent.TimeoutException) {
            null
        } catch (e: java.util.concurrent.ExecutionException) {
            val c = e.cause
            Starter.log(this, L.t("שגיאת צימוד: ${c?.javaClass?.simpleName}: ${c?.message}", "Pairing error: ${c?.javaClass?.simpleName}: ${c?.message}"))
            // connection problems are "no answer" (try the next address); anything else is a refusal
            if (c is java.io.IOException && c !is javax.net.ssl.SSLException) null else false
        } catch (t: Throwable) {
            Starter.log(this, L.t("שגיאת צימוד: ${t.message}", "Pairing error: ${t.message}"))
            null
        } finally {
            ex.shutdownNow()
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
        const val ACTION_CODE = "com.elad.codusb.PAIR_CODE"
        const val EXTRA_CODE = "code"
        const val EXTRA_PORT = "port"
        const val KEY_CODE = "code"
        val ACTION_LABEL get() = L.t("הקלד קוד צימוד", "Type pairing code")
    }
}
