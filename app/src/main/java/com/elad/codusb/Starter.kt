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
    private const val PID_FILE = "/data/local/tmp/codusb_hid.pid"
    /** Kills the helper by its saved pid, then by name as a fallback. */
    private const val KILL_HELPER =
        "if [ -f $PID_FILE ]; then kill \$(cat $PID_FILE) 2>/dev/null; rm -f $PID_FILE; fi; pkill $PROC 2>/dev/null"
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

    private val PAD_WORDS = Regex("controller|gamepad|game ?pad|joystick|joypad|xbox|x-box|easysmx|memo|gamesir|8bitdo", RegexOption.IGNORE_CASE)

    private fun isOurs(d: InputDevice) = d.vendorId == VIRTUAL_VID && d.productId == VIRTUAL_PID

    /** A real controller: reports gamepad/joystick/d-pad input, or is an external device with a controller-like name. */
    private fun isRealPad(d: InputDevice): Boolean {
        if (isOurs(d) || d.isVirtual) return false
        val s = d.sources
        val padSource = (s and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (s and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        val dpadExternal = d.isExternal && (s and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
        val namedExternal = d.isExternal && PAD_WORDS.containsMatchIn(d.name ?: "")
        return padSource || dpadExternal || namedExternal
    }

    /** Real game controllers that Android currently sees (excluding ours). */
    fun realGamepads(ctx: Context): List<String> {
        val im = ctx.getSystemService(InputManager::class.java)
        return im.inputDeviceIds.toList().mapNotNull { id ->
            val d = im.getInputDevice(id) ?: return@mapNotNull null
            if (isRealPad(d)) d.name else null
        }.distinct()
    }

    /** One line per external input device, for the log (helps when a controller isn't recognised). */
    fun describeExternalDevices(ctx: Context): String {
        val im = ctx.getSystemService(InputManager::class.java)
        return im.inputDeviceIds.toList().mapNotNull { id ->
            val d = im.getInputDevice(id) ?: return@mapNotNull null
            if (!d.isExternal || isOurs(d)) return@mapNotNull null
            "${d.name} [%04x:%04x, sources=0x%x]".format(d.vendorId, d.productId, d.sources)
        }.distinct().joinToString(" | ").ifEmpty { "אין התקנים חיצוניים" }
    }

    fun describeDevice(d: InputDevice): String =
        "${d.name} [%04x:%04x, sources=0x%x]".format(d.vendorId, d.productId, d.sources)

    fun isRealPadId(ctx: Context, id: Int): Boolean {
        val d = ctx.getSystemService(InputManager::class.java).getInputDevice(id) ?: return false
        return isRealPad(d)
    }

    fun isXiaomi(): Boolean {
        val m = (android.os.Build.MANUFACTURER + " " + android.os.Build.BRAND).lowercase(Locale.US)
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")
    }

    fun notificationsAllowed(ctx: Context): Boolean =
        ctx.getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled()

    /** Verified by the system: notifications on and the pairing channel allowed to pop up on screen. */
    fun pairingPopupsAllowed(ctx: Context): Boolean {
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
        val ch = nm.getNotificationChannel(Notif.CH_PAIR) ?: return false
        return nm.areNotificationsEnabled() && ch.importance >= android.app.NotificationManager.IMPORTANCE_HIGH
    }

    fun isBatteryUnrestricted(ctx: Context): Boolean =
        ctx.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    /** MIUI/HyperOS stores "Autostart" as hidden AppOp 10008. Null if it can't be read. */
    fun miuiAutostartAllowed(ctx: Context): Boolean? {
        if (!isXiaomi()) return null
        return try {
            val ops = ctx.getSystemService(android.app.AppOpsManager::class.java)
            val method = android.app.AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java
            )
            val mode = method.invoke(ops, 10008, android.os.Process.myUid(), ctx.packageName) as Int
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (_: Throwable) {
            null
        }
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

    /** The wireless-debugging port as Android itself records it (readable on most phones). */
    private fun tlsPortProperty(): Int? = try {
        val c = Class.forName("android.os.SystemProperties")
        (c.getMethod("get", String::class.java).invoke(null, "service.adb.tls.port") as String)
            .toIntOrNull()?.takeIf { it > 0 }
    } catch (_: Throwable) {
        null
    }

    fun connect(ctx: Context, timeoutMs: Long): AdbManager? {
        val m = AdbManager.get(ctx)
        try {
            m.disconnect()
        } catch (_: Throwable) {
        }
        val deadline = System.currentTimeMillis() + timeoutMs
        var round = 0
        while (System.currentTimeMillis() < deadline) {
            round++
            // three independent ways to learn the port, so one failing source doesn't stop us
            val fromProp = tlsPortProperty()
            val fromMdns = AdbMdns.findPort(ctx, AdbMdns.CONNECT, if (fromProp != null) 3_000 else 10_000)
            val fromLast = prefs(ctx).getInt("last_connect_port", 0).takeIf { it > 0 }
            val ports = listOfNotNull(fromProp, fromMdns, fromLast).distinct()
            if (round <= 2 || ports.isEmpty()) {
                log(ctx, "חיבור (ניסיון $round): מערכת=${fromProp ?: "-"} רשת=${fromMdns ?: "-"} אחרון=${fromLast ?: "-"}")
            }
            for (port in ports) {
                for (host in listOfNotNull(LOCALHOST, AdbMdns.lastHost).distinct()) {
                    try {
                        if (m.connect(host, port)) {
                            prefs(ctx).edit().putInt("last_connect_port", port).apply()
                            return m
                        }
                    } catch (t: Throwable) {
                        if (round <= 2) log(ctx, "חיבור נכשל ($host:$port): ${t.javaClass.simpleName}: ${t.message}")
                        try {
                            m.disconnect()
                        } catch (_: Throwable) {
                        }
                    }
                }
            }
            Thread.sleep(2_000)
        }
        log(ctx, "לא הצלחתי להתחבר אחרי $round ניסיונות")
        return null
    }

    fun shell(m: AdbManager, cmd: String): String {
        val stream = m.openStream("shell:$cmd")
        val out = java.io.ByteArrayOutputStream()
        return try {
            val input = stream.openInputStream()
            val buf = ByteArray(4096)
            try {
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
            } catch (_: java.io.IOException) {
                // libadb reports "Stream closed" when the command ends: that is the normal end of output
            }
            out.toString(Charsets.UTF_8.name()).trim()
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
        // Start fully detached (own session via setsid), then keep this shell open until the
        // helper has written its pid file. Closing the shell too early used to kill the helper
        // before it even started.
        val out = shell(
            m,
            "$KILL_HELPER; rm -f /data/local/tmp/codusb_hid.log; " +
                "S=\$(command -v setsid); " +
                "CLASSPATH='$apk' \$S nohup app_process /system/bin --nice-name=$PROC " +
                "com.elad.codusb.HidServer </dev/null >/dev/null 2>&1 & " +
                "i=0; while [ \$i -lt 50 ] && [ ! -s $PID_FILE ]; do sleep 0.1; i=\$((i+1)); done; " +
                "sleep 0.5; echo started=\$(cat $PID_FILE 2>/dev/null)"
        )
        log(ctx, "הפעלת מנוע השלט: $out")
        repeat(16) {
            if (isVirtualPresent(ctx)) return true
            Thread.sleep(500)
        }
        // diagnostics: is the helper process alive, and does the system see the device?
        try {
            val diag = shell(
                m,
                "echo pid=\$(cat $PID_FILE 2>/dev/null); ps -A -o PID,NAME | grep -i codusb; " +
                    "echo inputs=\$(dumpsys input | grep -c 'Xbox Wireless Controller'); " +
                    "tail -5 /data/local/tmp/codusb_hid.log"
            )
            log(ctx, "אבחון: $diag")
        } catch (t: Throwable) {
            log(ctx, "אבחון נכשל: ${t.message}")
        }
        return false
    }

    /** Start, then make sure the controller is still there a moment later; one retry if not. */
    fun startHelperReliably(ctx: Context, m: AdbManager): Boolean {
        repeat(2) { attempt ->
            if (startHelper(ctx, m)) {
                Thread.sleep(2_500)
                if (isVirtualPresent(ctx)) return true
                log(ctx, "השלט נעלם אחרי ההפעלה – מנסה שוב")
            } else if (attempt == 0) {
                log(ctx, "ניסיון נוסף להפעלת השלט")
            }
        }
        return false
    }

    fun stopHelper(m: AdbManager) {
        shell(m, "$KILL_HELPER; true")
    }

    /** Called right after a successful pairing. */
    @Synchronized
    fun afterPairing(ctx: Context, progress: (String) -> Unit): String {
        progress("מתחבר...")
        Thread.sleep(1_500) // give the system a moment after pairing
        val m = connect(ctx, 45_000)
            ?: return "הצימוד נשמר ✓ אבל החיבור לא הצליח כרגע. ודא ש'ניפוי באגים אלחוטי' דולק ולחץ A במסך הראשי – אין צורך לצמד שוב."
        val granted = grantSecureSettings(ctx, m)
        progress("יוצר שלט וירטואלי...")
        val ok = startHelperReliably(ctx, m)
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
            ?: return if (enabled) "לא הצלחתי להתחבר. אם הופיע חלון 'לאפשר ניפוי באגים אלחוטי ברשת זו?' – סמן 'אפשר תמיד' ואשר, ואז לחץ A."
            else "הדלק 'ניפוי באגים אלחוטי' באפשרויות מפתח ולחץ 'הפעל עכשיו'."

        if (!hasSecureSettings(ctx)) grantSecureSettings(ctx, m)

        progress("יוצר שלט וירטואלי...")
        return if (startHelperReliably(ctx, m)) "השלט הווירטואלי פעיל ✓ חבר את השלט ופתח את המשחק."
        else "השלט הווירטואלי לא נוצר. פתח את האפליקציה לפרטים."
    }

    @Synchronized
    fun fullStop(ctx: Context): String {
        if (!isVirtualPresent(ctx)) return "השלט הווירטואלי כבר כבוי."
        enableWirelessDebugging(ctx)
        val m = connect(ctx, 20_000) ?: return "לא הצלחתי להתחבר כדי לכבות. ודא ש'ניפוי באגים אלחוטי' דולק."
        return try {
            stopHelper(m)
            var gone = false
            repeat(10) {
                if (!gone) {
                    Thread.sleep(300)
                    gone = !isVirtualPresent(ctx)
                }
            }
            if (gone) "השלט הווירטואלי כובה." else "הכיבוי נכשל."
        } catch (t: Throwable) {
            "שגיאה: ${t.message}"
        }
    }
}
