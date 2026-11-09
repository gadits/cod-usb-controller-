package com.elad.codusb

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
 * The code is typed into [PairingOverlay]; this service finds the pairing port via mDNS,
 * pairs, then connects and creates the virtual controller.
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> begin()
            ACTION_CODE -> {
                val code = intent.getStringExtra(EXTRA_CODE) ?: ""
                val port = intent.getIntExtra(EXTRA_PORT, 0)
                if (!busy) {
                    busy = true
                    thread(name = "codusb-pair") { pair(code, port) }
                }
            }
            ACTION_STOP -> finish()
            else -> if (mdns == null) begin()
        }
        return START_NOT_STICKY
    }

    private fun begin() {
        val n = Notif.simple(this, Notif.CH_STATUS, "צימוד פעיל – הקלד את הקוד בתיבה שעל המסך", true)
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
            pairingPort = port
            if (!busy) {
                if (!detectedOnce) Starter.log(this, "חלון הצימוד זוהה (יציאה $port)")
                detectedOnce = true
                PairingOverlay.dialogDetected()
            }
        }.also { it.start() }

        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (!detectedOnce && !busy) {
                PairingOverlay.setStatus(
                    "עדיין לא זוהה חלון צימוד. ודא שפתחת 'צימוד מכשיר באמצעות קוד צימוד'",
                    PairingOverlay.Kind.INFO
                )
            }
        }, 30_000L)
        handler.postDelayed({ if (!busy) finish() }, 15 * 60_000L)
    }

    private fun pair(code: String, manualPort: Int) {
        try {
            var port = if (manualPort > 0) manualPort else pairingPort
            if (port <= 0) {
                PairingOverlay.setStatus("מחפש את חלון הצימוד...", PairingOverlay.Kind.BUSY)
                val until = System.currentTimeMillis() + 8_000
                while (pairingPort <= 0 && System.currentTimeMillis() < until) Thread.sleep(200)
                port = pairingPort
            }
            if (port <= 0) {
                PairingOverlay.askPort()
                return
            }

            val ok = try {
                AdbManager.get(this).pair("127.0.0.1", port, code)
            } catch (t: Throwable) {
                Starter.log(this, "צימוד נכשל: ${t.javaClass.simpleName}: ${t.message}")
                false
            }
            if (!ok) {
                PairingOverlay.codeRejected("הצימוד נכשל – קוד שגוי או שפג תוקפו. פתח קוד חדש והקלד שוב")
                return
            }

            Starter.setPaired(this, true)
            Starter.log(this, "הצימוד הצליח")
            mdns?.stop()
            mdns = null
            PairingOverlay.setStatus("✓ צומד! מתחבר...", PairingOverlay.Kind.OK)
            val result = Starter.afterPairing(this) { msg ->
                PairingOverlay.setStatus(msg, PairingOverlay.Kind.BUSY)
            }
            Starter.log(this, result)
            PairingOverlay.finishWith(result, Starter.isVirtualPresent(this))
            handler.post { stopService() }
        } finally {
            busy = false
        }
    }

    private fun stopService() {
        handler.removeCallbacksAndMessages(null)
        mdns?.stop()
        mdns = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun finish() {
        PairingOverlay.hide()
        stopService()
    }

    override fun onDestroy() {
        mdns?.stop()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.elad.codusb.PAIR_START"
        const val ACTION_CODE = "com.elad.codusb.PAIR_CODE"
        const val ACTION_STOP = "com.elad.codusb.PAIR_STOP"
        const val EXTRA_CODE = "code"
        const val EXTRA_PORT = "port"
    }
}
