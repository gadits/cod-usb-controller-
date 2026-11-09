package com.elad.codusb

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private var setupCard: View? = null
    private val handler = Handler(Looper.getMainLooper())

    private val doneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = render()
    }

    private val deviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = render()
        override fun onInputDeviceRemoved(deviceId: Int) = render()
        override fun onInputDeviceChanged(deviceId: Int) {}
    }

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.BG_TOP
        window.navigationBarColor = Ui.BG_BOTTOM
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(32))
            clipChildren = false
            clipToPadding = false
        }
        scroll = ScrollView(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Ui.BG_TOP, Color.parseColor("#E9EBEE"), Ui.BG_BOTTOM)
            )
            isVerticalScrollBarEnabled = false
            clipChildren = false
            addView(content)
        }
        setContentView(scroll)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(doneReceiver, IntentFilter(StartService.ACTION_DONE), Context.RECEIVER_NOT_EXPORTED)
        getSystemService(InputManager::class.java).registerInputDeviceListener(deviceListener, handler)
        render()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(doneReceiver)
        } catch (_: Throwable) {
        }
        getSystemService(InputManager::class.java).unregisterInputDeviceListener(deviceListener)
    }

    // ---------- screen ----------

    private fun render() {
        val y = scroll.scrollY
        content.removeAllViews()
        content.addView(header())
        content.addView(statusCard(), gap(10))
        content.addView(controlsCard(), gap(18))
        setupCard = setupCard().also { content.addView(it, gap(18)) }
        content.addView(bootCard(), gap(18))
        content.addView(logCard(), gap(18))
        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun gap(top: Int) = LinearLayout.LayoutParams(-1, -2).apply {
        topMargin = dp(top)
        leftMargin = dp(2)
        rightMargin = dp(2)
    }

    private fun header(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(6), dp(6), dp(6), dp(4))
        addView(TextView(this@MainActivity).apply {
            text = "COD USB"
            textSize = 30f
            typeface = Ui.BLACK
            setTextColor(Ui.INK)
        })
        addView(Ui.text(this@MainActivity, "כל שלט USB ב-Call of Duty Mobile", 15f, Ui.INK_SOFT, Ui.MEDIUM))
    }

    private fun statusCard(): View {
        val active = Starter.isVirtualPresent(this)
        val pads = Starter.realGamepads(this)
        val card = Ui.card(this, null)
        card.gravity = Gravity.CENTER_HORIZONTAL
        card.addView(Ui.statusOrb(this, active, if (active) "פעיל" else "כבוי"))
        card.addView(TextView(this).apply {
            text = if (active) "השלט הווירטואלי פעיל – אפשר לשחק" else "השלט הווירטואלי כבוי"
            textSize = 18f
            typeface = Ui.BOLD
            gravity = Gravity.CENTER
            setTextColor(if (active) Ui.GREEN_DEEP else Ui.INK)
            setPadding(0, dp(4), 0, dp(10))
        })
        // chip with the controllers Android sees right now
        card.addView(TextView(this).apply {
            text = if (pads.isEmpty()) "לא מחובר שלט כרגע" else "מחובר: " + pads.joinToString(" · ")
            textSize = 13.5f
            typeface = Ui.MEDIUM
            gravity = Gravity.CENTER
            setTextColor(Ui.INK_SOFT)
            background = Ui.rounded(Ui.WELL, Ui.dpf(this@MainActivity, 14f), Ui.SHELL_EDGE, 1)
            setPadding(dp(14), dp(7), dp(14), dp(7))
        }, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL })
        return card
    }

    private fun controlsCard(): View {
        val card = Ui.card(this, "שליטה")
        val size = 86
        val a = Ui.faceButton(this, "A", "הפעל", Ui.GREEN, Ui.GREEN_DEEP, size) {
            startForegroundService(Intent(this, StartService::class.java))
            toast("מפעיל... התוצאה תופיע בהתראה")
        }
        val b = Ui.faceButton(this, "B", "כבה", Ui.RED, Ui.RED_DEEP, size) {
            startForegroundService(Intent(this, StartService::class.java).setAction(StartService.ACTION_STOP))
            toast("מכבה...")
        }
        val x = Ui.faceButton(this, "X", "צימוד", Ui.BLUE, Ui.BLUE_DEEP, size) { startPairing() }
        val y = Ui.faceButton(this, "Y", "הגדרות", Ui.YELLOW, Ui.YELLOW_DEEP, size) {
            setupCard?.let { v -> scroll.smoothScrollTo(0, v.top - dp(12)) }
        }
        card.addView(Ui.diamond(this, y, x, b, a, size, 16), LinearLayout.LayoutParams(-1, -2))

        val auto = Starter.autoStart(this)
        card.addView(Ui.bumper(this, if (auto) "הפעלה אוטומטית בהדלקה: פועלת" else "הפעלה אוטומטית בהדלקה: כבויה", auto) {
            Starter.setAutoStart(this, !auto)
            render()
        })
        return card
    }

    private fun setupCard(): View {
        val devOn = Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        val paired = Starter.isPaired(this)
        val wss = Starter.hasSecureSettings(this)
        val overlay = Settings.canDrawOverlays(this)

        val card = Ui.card(this, "הגדרה חד-פעמית", Ui.BLUE)
        card.addView(Ui.lightRow(this, devOn, if (devOn) "אפשרויות מפתח פעילות" else "אפשרויות מפתח כבויות"))
        card.addView(Ui.lightRow(this, paired, if (paired) "צימוד בוצע" else "עוד לא בוצע צימוד"))
        card.addView(Ui.lightRow(this, wss, if (wss) "הפעלה אחרי הדלקה מאושרת" else "הפעלה אחרי הדלקה לא מאושרת"))
        if (!paired) {
            card.addView(
                Ui.text(
                    this,
                    "לחץ 'התחל צימוד'. באפשרויות מפתח הדלק 'ניפוי באגים אלחוטי', היכנס אליו ובחר " +
                        "'צימוד מכשיר באמצעות קוד צימוד'. תיבה תופיע למעלה – הקלד בה את הקוד."
                ).apply { setPadding(0, dp(6), 0, 0) }
            )
        }
        if (!devOn) {
            card.addView(Ui.bumper(this, "פתח 'מידע על הטלפון'") {
                open(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
                toast("לחץ 7 פעמים על 'גרסת MIUI/HyperOS' או 'מספר Build'")
            })
        }
        card.addView(Ui.bumper(this, if (overlay) "חלון קוד מעל ההגדרות: מאושר" else "אשר חלון קוד מעל ההגדרות", overlay) {
            if (!overlay) requestOverlay()
        })
        card.addView(Ui.bumper(this, if (paired) "צומד – לחץ לצימוד מחדש" else "התחל צימוד", paired) {
            startPairing()
        })
        return card
    }

    private fun bootCard(): View {
        val battery = Starter.isBatteryUnrestricted(this)
        val card = Ui.card(this, "עלייה אוטומטית אחרי הדלקה", Ui.YELLOW)
        if (Starter.isXiaomi()) {
            // green only when the system itself confirms the permission
            val auto = Starter.miuiAutostartAllowed(this)
            val label = when (auto) {
                true -> "הפעלה אוטומטית: מאושרת"
                false -> "אשר הפעלה אוטומטית"
                null -> "אשר הפעלה אוטומטית (לא ניתן לאמת)"
            }
            card.addView(Ui.bumper(this, label, auto == true) { openXiaomiAutostart() })
        }
        card.addView(Ui.bumper(this, if (battery) "הגבלת סוללה: מבוטלת" else "בטל הגבלת סוללה", battery) {
            if (!battery) {
                open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } else toast("כבר מבוטל ✓")
        })
        card.addView(Ui.text(this, "צריך להיות מחובר ל-WiFi בזמן ההפעלה.", 13f, Ui.INK_FAINT).apply {
            setPadding(0, dp(12), 0, 0)
        })
        return card
    }

    private fun logCard(): View {
        val card = Ui.card(this, "יומן", Ui.INK_FAINT)
        card.addView(TextView(this).apply {
            text = Starter.readLog(this@MainActivity).ifEmpty { "—" }
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(Ui.INK_SOFT)
            textDirection = View.TEXT_DIRECTION_RTL
            background = Ui.rounded(Ui.WELL, Ui.dpf(this@MainActivity, 16f), Ui.SHELL_EDGE, 1)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        })
        return card
    }

    // ---------- actions ----------

    private fun requestOverlay() {
        open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        toast("הפעל 'הצגה מעל אפליקציות אחרות'")
    }

    private fun startPairing() {
        if (!Starter.isWifiConnected(this)) {
            toast("צריך להיות מחובר ל-WiFi")
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("צריך אישור אחד")
                .setMessage(
                    "תיבת הקלדת הקוד מופיעה מעל מסך ההגדרות.\n" +
                        "לשם כך יש לאשר 'הצגה מעל אפליקציות אחרות' לאפליקציה, ואז ללחוץ שוב על צימוד."
                )
                .setPositiveButton("לאישור") { _, _ -> requestOverlay() }
                .setNegativeButton("ביטול", null)
                .show()
            return
        }
        // show the code box now, while we are in the foreground (also needed on Xiaomi)
        if (!PairingOverlay.show(this)) {
            toast("לא הצלחתי להציג את תיבת הקוד")
            return
        }
        startForegroundService(Intent(this, PairingService::class.java).setAction(PairingService.ACTION_START))
        val dev = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
        open(dev)
        toast("ניפוי באגים אלחוטי ← צימוד מכשיר באמצעות קוד צימוד")
    }

    private fun openXiaomiAutostart() {
        val miui = Intent().setComponent(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
        )
        if (!open(miui)) {
            open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun open(intent: Intent): Boolean = try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Throwable) {
        false
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
