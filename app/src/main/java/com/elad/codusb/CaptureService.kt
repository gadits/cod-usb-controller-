package com.elad.codusb

import android.app.Activity
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.WindowManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Takes ONE picture of the screen (with the user's permission), finds the 6-digit pairing code
 * and the port in it with on-device text recognition, and hands them to the pairing screen.
 * The picture is never saved or sent anywhere.
 */
class CaptureService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var done = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = Notif.simple(this, Notif.CH_STATUS, "קורא את קוד הצימוד מהמסך...", true)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(Notif.ID_CAPTURE, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(Notif.ID_CAPTURE, n)
        }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        else intent?.getParcelableExtra(EXTRA_DATA)
        if (resultCode != Activity.RESULT_OK || data == null) {
            fail("לא התקבל אישור לצילום המסך")
            return START_NOT_STICKY
        }
        try {
            capture(resultCode, data)
        } catch (t: Throwable) {
            Starter.log(this, "צילום המסך נכשל: ${t.javaClass.simpleName}: ${t.message}")
            fail("לא הצלחתי לצלם את המסך – הקלד את הקוד ידנית")
        }
        return START_NOT_STICKY
    }

    private fun capture(resultCode: Int, data: Intent) {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val p = mpm.getMediaProjection(resultCode, data) ?: throw IllegalStateException("no projection")
        projection = p
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {}
        }, handler)

        val bounds = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        val w = bounds.width()
        val h = bounds.height()
        val dpi = resources.displayMetrics.densityDpi
        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        reader = r
        // Frames only arrive when the screen changes, so keep the latest one and use it after a
        // short pause (the permission dialog needs a moment to disappear).
        r.setOnImageAvailableListener({ ir ->
            try {
                ir.acquireLatestImage()?.let { img ->
                    synchronized(this) {
                        latest?.close()
                        latest = img
                    }
                }
            } catch (_: Throwable) {
            }
        }, handler)
        tryTake(w, h, attempt = 0)
        display = p.createVirtualDisplay(
            "codusb-capture", w, h, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, handler
        )
    }

    @Volatile
    private var latest: android.media.Image? = null

    private fun tryTake(w: Int, h: Int, attempt: Int) {
        handler.postDelayed({
            if (done) return@postDelayed
            val img = synchronized(this) { latest.also { latest = null } }
            if (img == null) {
                if (attempt < 8) tryTake(w, h, attempt + 1)
                else {
                    done = true
                    Starter.log(this, "לא התקבלה תמונה מהמסך")
                    fail("לא התקבלה תמונה מהמסך – הקלד את הקוד ידנית")
                }
                return@postDelayed
            }
            done = true
            val bmp = try {
                val plane = img.planes[0]
                val rowPadding = plane.rowStride - plane.pixelStride * w
                val full = Bitmap.createBitmap(w + rowPadding / plane.pixelStride, h, Bitmap.Config.ARGB_8888)
                full.copyPixelsFromBuffer(plane.buffer)
                Bitmap.createBitmap(full, 0, 0, w, h)
            } catch (t: Throwable) {
                Starter.log(this, "המרת התמונה נכשלה: ${t.message}")
                null
            } finally {
                img.close()
            }
            stopCapture()
            if (bmp == null) fail("לא הצלחתי לקרוא את המסך – הקלד את הקוד ידנית") else recognize(bmp)
        }, if (attempt == 0) 900L else 500L)
    }

    private fun recognize(bmp: Bitmap) {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { text ->
                val found = parse(text.text)
                if (found == null) {
                    Starter.log(this, "לא נמצא קוד בתמונה")
                    fail("לא מצאתי קוד בן 6 ספרות על המסך. ודא שחלון הצימוד פתוח ונסה שוב")
                } else {
                    Starter.log(this, "הקוד נקרא מהמסך" + (found.second?.let { " (יציאה $it)" } ?: ""))
                    PairingUi.codeRead(found.first, found.second ?: 0)
                    finishService()
                }
            }
            .addOnFailureListener {
                Starter.log(this, "זיהוי הטקסט נכשל: ${it.message}")
                fail("זיהוי הקוד נכשל – הקלד אותו ידנית")
            }
    }

    private fun stopCapture() {
        synchronized(this) {
            latest?.close()
            latest = null
        }
        try {
            display?.release()
        } catch (_: Throwable) {
        }
        try {
            reader?.close()
        } catch (_: Throwable) {
        }
        try {
            projection?.stop()
        } catch (_: Throwable) {
        }
        display = null
        reader = null
        projection = null
    }

    private fun fail(msg: String) {
        Starter.log(this, msg)
        stopCapture()
        PairingUi.update(msg, PairingUi.Kind.ERROR)
        finishService()
    }

    private fun finishService() {
        handler.removeCallbacksAndMessages(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RESULT = "result"
        const val EXTRA_DATA = "data"

        private val CODE = Regex("(?<![\\d.:])(\\d{6})(?![\\d.:])")
        private val PORT = Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\s*:\\s*(\\d{2,5})")

        /** Finds (code, port) in recognised screen text. */
        fun parse(all: String): Pair<String, Int?>? {
            val clean = all.replace(Regex("(?<=\\d) (?=\\d{3}\\b)"), "") // "123 456" → "123456"
            val code = CODE.find(clean)?.groupValues?.get(1) ?: return null
            val port = PORT.find(all)?.groupValues?.get(1)?.toIntOrNull()
            return code to port
        }
    }
}
