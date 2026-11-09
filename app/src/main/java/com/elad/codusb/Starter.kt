package com.elad.codusb

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.view.InputDevice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Core logic: connect to the device's own ADB and start/stop the virtual controller. */
object Starter {

    const val VIRTUAL_NAME = "Xbox Wireless Controller"
    private const val VIRTUAL_VID = 0x0929
    private const val VIRTUAL_PID = 0x1a85
    private const val PROC = "codusb_hid"
    private const val LOCALHOST = "127.0.0.1"

    fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("codusb", Context.MODE_PRIVATE)

    fun isPaired(ctx: Context) = prefs(ctx).getBoolean("paired", false)
    fun setPaired(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("paired", v).apply()
    fun autoStart(ctx: Context) = prefs(ctx).getBoolean("auto_start", true)
    fun setAutoStart(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("auto_start", v).apply()

    fun log(ctx: Context, msg: String) {
        val time = SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(Date())
        val old = prefs(ctx).getString("log", "") ?: ""
        val lines = ("$time  $msg\n$old").lines().take(25).joinToString("\n")
        prefs(ctx).edit().putString("log", lines).apply()
    }

    fun readLog(ctx: Context): String = prefs(ctx).getString("log", "") ?: ""

    fun hasSecureSettings(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun isVirtualPresent(ctx: Context): Boolean {
        val im = ctx.getSystemService(InputManager::class.java)
        return im.inputDeviceIds.any { id ->
            val d = im.getInputDevice(id) ?: return@any false
            d.name == VIRTUAL_NAME && d.vendorId == VIRTUAL_VID && d.productId == VIRTUAL_PID
        }
    }

    /** Real game controllers that Android currently sees (excluding ours). */
    fun realGamepads(ctx: Context): List<String> {
        val im = ctx.getSystemService(InputManager::class.java)
        return im.inputDeviceIds.mapNotNull { id ->
            val d = im.getInputDevice(id) ?: return@mapNotNull null
            val pad = (d.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (d.sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            val ours = d.vendorId == VIRTUAL_VID && d.productId == VIRTUAL_PID
            if (pad && !ours) d.name else null
        }.distinct()
    }

    fun isWifiConnected(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        return cm.allNetworks.any { n ->
            cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    /** Turns on wireless debugging by itself (only after WRITE_SECURE_SETTINGS was granted). */
    fun enableWirelessDebugging(ctx: Context): Boolean {
        if (!hasSecureSettings(ctx)) return false
        return try {
            val cr = ctx.contentResolver
            Settings.Global.putInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1)
            Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
            Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
            true
        } catch (t: Throwable) {
            log(ctx, "שגיאה בהפעלת ניפוי באגים אלחוטי: ${t.message}")
            false
        }
    }

    fun connect(ctx: Context, timeoutMs: Long): AdbManager? {
        val m = AdbManager.get(ctx)
        try {
            m.disconnect()
        } catch (_: Throwable) {
        }
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val port = AdbMdns.findPort(ctx, AdbMdns.CONNECT, 8_000)
            if (port != null) {
                try {
                    if (m.connect(LOCALHOST, port)) return m
                } catch (t: Throwable) {
                    log(ctx, "חיבור נכשל ($port): ${t.message}")
                }
            }
            Thread.sleep(2_000)
        }
        return null
    }

    fun shell(m: AdbManager, cmd: String): String {
        val stream = m.openStream("shell:$cmd")
        return try {
            stream.openInputStream().readBytes().toString(Charsets.UTF_8).trim()
        } finally {
            try {
                stream.close()
            } catch (_: Throwable) {
            }
        }
    }

    fun grantSecureSettings(ctx: Context, m: AdbManager): Boolean {
        val out = try {
            shell(m, "pm grant ${ctx.packageName} android.permission.WRITE_SECURE_SETTINGS 2>&1")
        } catch (t: Throwable) {
            t.message ?: "error"
        }
        if (out.isNotEmpty()) log(ctx, "pm grant: $out")
        return hasSecureSettings(ctx)
    }

    fun startHelper(ctx: Context, m: AdbManager): Boolean {
        val apk = ctx.applicationInfo.sourceDir
        shell(
            m,
            "pkill $PROC; CLASSPATH='$apk' nohup app_process /system/bin --nice-name=$PROC " +
                "com.elad.codusb.HidServer </dev/null >/dev/null 2>&1 &"
        )
        repeat(16) {
            if (isVirtualPresent(ctx)) return true
            Thread.sleep(500)
        }
        try {
            val err = shell(m, "cat /data/local/tmp/codusb_hid.log 2>&1 | tail -5")
            if (err.isNotEmpty()) log(ctx, "helper: $err")
        } catch (_: Throwable) {
        }
        return false
    }

    fun stopHelper(m: AdbManager) {
        shell(m, "pkill $PROC")
    }

    /** Called right after a successful pairing. */
    @Synchronized
    fun afterPairing(ctx: Context, progress: (String) -> Unit): String {
        progress("מתחבר...")
        val m = connect(ctx, 30_000) ?: return "הצימוד הצליח אבל החיבור נכשל. ודא ש'ניפוי באגים אלחוטי' דולק ולחץ 'הפעל עכשיו'."
        val granted = grantSecureSettings(ctx, m)
        progress("יוצר שלט וירטואלי...")
        val ok = startHelper(ctx, m)
        val base = if (ok) "הכל מוכן ✓ השלט הווירטואלי פעיל." else "הצימוד הצליח, אבל השלט הווירטואלי לא נוצר. פתח את האפליקציה לפרטים."
        return if (granted) base
        else "$base\nשים לב: ההפעלה האוטומטית אחרי הדלקה לא אושרה (בשיאומי צריך להדליק 'ניפוי באגים USB (הגדרות אבטחה)' ולצמד שוב)."
    }

    /** Full automatic start: Wi-Fi, wireless debugging, connect, create controller. */
    @Synchronized
    fun fullStart(ctx: Context, progress: (String) -> Unit): String {
        if (isVirtualPresent(ctx)) return "השלט הווירטואלי כבר פעיל ✓"
        if (!isPaired(ctx)) return "צריך קודם לבצע את ההגדרה החד-פעמית (צימוד) באפליקציה."

        if (!isWifiConnected(ctx)) {
            progress("ממתין ל-WiFi...")
            val until = System.currentTimeMillis() + 180_000
            while (!isWifiConnected(ctx) && System.currentTimeMillis() < until) Thread.sleep(2_000)
            if (!isWifiConnected(ctx)) return "אין חיבור WiFi. התחבר ל-WiFi ולחץ 'הפעל עכשיו'."
            Thread.sleep(3_000)
        }

        val enabled = enableWirelessDebugging(ctx)
        progress("מתחבר...")
        val m = connect(ctx, if (enabled) 60_000 else 20_000)
            ?: return if (enabled) "לא הצלחתי להתחבר לניפוי הבאגים האלחוטי. נסה שוב בעוד רגע."
            else "הדלק 'ניפוי באגים אלחוטי' באפשרויות מפתח ולחץ 'הפעל עכשיו'."

        if (!hasSecureSettings(ctx)) grantSecureSettings(ctx, m)

        progress("יוצר שלט וירטואלי...")
        return if (startHelper(ctx, m)) "השלט הווירטואלי פעיל ✓ חבר את השלט ופתח את המשחק."
        else "השלט הווירטואלי לא נוצר. פתח את האפליקציה לפרטים."
    }

    @Synchronized
    fun fullStop(ctx: Context): String {
        if (!isVirtualPresent(ctx)) return "השלט הווירטואלי כבר כבוי."
        enableWirelessDebugging(ctx)
        val m = connect(ctx, 20_000) ?: return "לא הצלחתי להתחבר כדי לכבות. ודא ש'ניפוי באגים אלחוטי' דולק."
        return try {
            stopHelper(m)
            Thread.sleep(800)
            if (isVirtualPresent(ctx)) "הכיבוי נכשל." else "השלט הווירטואלי כובה."
        } catch (t: Throwable) {
            "שגיאה: ${t.message}"
        }
    }
}
