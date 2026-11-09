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
    private val handler = Handler(Looper.getMainLooper())

    private val doneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = render()
    }

    private val deviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = render()
        override fun onInputDeviceRemoved(deviceId: Int) = render()
        override fun onInputDeviceChanged(deviceId: Int) {}
    }

    private fun dp(v: Int) = Retro.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Retro.FACE
        window.navigationBarColor = Retro.BODY
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(28))
        }
        scroll = ScrollView(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Retro.BODY_LIGHT, Retro.BODY, Color.parseColor("#B8B8B8"))
            )
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

    // ---------- UI ----------

    private fun render() {
        val y = scroll.scrollY
        content.removeAllViews()
        content.addView(header())
        content.addView(statusPanel(), gap())
        content.addView(setupPanel(), gap())
        content.addView(bootPanel(), gap())
        content.addView(logPanel(), gap())
        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun gap() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) }

    private fun header(): View {
        val logo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = Retro.rounded(Retro.FACE, Retro.dpf(this@MainActivity, 14f), Retro.FACE_EDGE, dp(2))
            setPadding(dp(16), dp(16), dp(16), dp(14))
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            addView(TextView(this@MainActivity).apply {
                text = "COD USB"
                textSize = 26f
                typeface = Retro.pixel(this@MainActivity)
                setTextColor(Retro.RED_TEXT)
                setShadowLayer(0.01f, dp(3).toFloat(), dp(3).toFloat(), Retro.RED_DARK)
                gravity = Gravity.CENTER
            })
            addView(TextView(this@MainActivity).apply {
                text = "CONTROLLER"
                textSize = 11f
                letterSpacing = 0.3f
                typeface = Retro.pixel(this@MainActivity)
                setTextColor(Retro.STRIPE_LIGHT)
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, 0)
            })
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(logo)
            addView(TextView(this@MainActivity).apply {
                text = "כל שלט USB ב-Call of Duty Mobile"
                textSize = 14f
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                setTextColor(Color.parseColor("#4A4A4A"))
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
            })
        }
    }

    private fun statusPanel(): View {
        val active = Starter.isVirtualPresent(this)
        val pads = Starter.realGamepads(this)
        val panel = Retro.faceplate(this, null)
        panel.addView(Retro.stripes(this))
        panel.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, dp(2))
            addView(Retro.led(this@MainActivity, active).apply {
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(12) }
            })
            addView(TextView(this@MainActivity).apply {
                text = if (active) "השלט הווירטואלי פעיל" else "השלט הווירטואלי כבוי"
                textSize = 20f
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                setTextColor(if (active) Retro.LED_GREEN else Retro.TEXT_LIGHT)
            })
        })
        panel.addView(TextView(this).apply {
            text = if (pads.isEmpty()) "לא זוהה שלט מחובר כרגע" else "🎮 " + pads.joinToString(" · ")
            textSize = 14f
            setTextColor(Retro.TEXT_DIM)
            setPadding(dp(30), dp(2), 0, dp(14))
        })

        // A (start) on the right, B (stop) on the left — like the real controller
        panel.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(Retro.roundButton(this@MainActivity, "A", "הפעל") {
                startForegroundService(Intent(this@MainActivity, StartService::class.java))
                toast("מפעיל... התוצאה תופיע בהתראה")
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(Retro.roundButton(this@MainActivity, "B", "כבה") {
                startForegroundService(
                    Intent(this@MainActivity, StartService::class.java).setAction(StartService.ACTION_STOP)
                )
                toast("מכבה...")
            }, LinearLayout.LayoutParams(0, -2, 1f))
        })

        val auto = Starter.autoStart(this)
        panel.addView(Retro.pill(this, if (auto) "✓  הפעלה אוטומטית בהדלקה: פועל" else "הפעלה אוטומטית בהדלקה: כבוי", auto) {
            Starter.setAutoStart(this, !auto)
            render()
        }.apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(18) })
        return panel
    }

    private fun setupPanel(): View {
        val devOn = Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        val paired = Starter.isPaired(this)
        val wss = Starter.hasSecureSettings(this)
        val overlay = Settings.canDrawOverlays(this)

        val panel = Retro.faceplate(this, "הגדרה חד-פעמית")
        panel.addView(Retro.ledRow(this, devOn, if (devOn) "אפשרויות מפתח פעילות" else "אפשרויות מפתח כבויות"))
        panel.addView(Retro.ledRow(this, paired, if (paired) "צימוד בוצע" else "עוד לא בוצע צימוד"))
        panel.addView(
            Retro.ledRow(
                this, wss,
                if (wss) "הפעלה אחרי הדלקה מאושרת" else "הפעלה אחרי הדלקה עוד לא מאושרת"
            )
        )
        if (!paired) {
            panel.addView(TextView(this).apply {
                text = "לחץ 'התחל צימוד'. באפשרויות מפתח הדלק 'ניפוי באגים אלחוטי', היכנס אליו ובחר " +
                    "'צימוד מכשיר באמצעות קוד צימוד'. תיבה גדולה תופיע למעלה – הקלד בה את הקוד."
                textSize = 14f
                setTextColor(Retro.TEXT_DIM)
                setPadding(0, dp(8), 0, 0)
            })
        }
        if (!devOn) {
            panel.addView(Retro.pill(this, "1. פתח 'מידע על הטלפון'") {
                open(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
                toast("לחץ 7 פעמים על 'גרסת MIUI/HyperOS' או 'מספר Build'")
            })
        }
        panel.addView(Retro.pill(this, if (overlay) "✓  חלון קוד מעל ההגדרות: מאושר" else "אשר הצגת חלון הקוד מעל ההגדרות", overlay) {
            if (!overlay) requestOverlay()
        })
        panel.addView(Retro.pill(this, if (paired) "✓  צומד – לחץ לצימוד מחדש" else "התחל צימוד", paired) {
            startPairing()
        })
        return panel
    }

    private fun bootPanel(): View {
        val battery = Starter.isBatteryUnrestricted(this)
        val panel = Retro.faceplate(this, "כדי שזה יעלה לבד אחרי הדלקה")
        if (Starter.isXiaomi()) {
            val auto = Starter.autostartOk(this)
            panel.addView(Retro.pill(this, if (auto) "✓  הפעלה אוטומטית: מאושרת" else "אשר הפעלה אוטומטית", auto) {
                openXiaomiAutostart()
            })
        }
        panel.addView(Retro.pill(this, if (battery) "✓  הגבלת סוללה: מבוטלת" else "בטל הגבלת סוללה", battery) {
            if (!battery) {
                open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } else toast("כבר מבוטל ✓")
        })
        panel.addView(TextView(this).apply {
            text = "צריך להיות מחובר ל-WiFi בזמן ההפעלה."
            textSize = 13f
            setTextColor(Retro.TEXT_DIM)
            setPadding(0, dp(10), 0, 0)
        })
        return panel
    }

    private fun logPanel(): View {
        val panel = Retro.faceplate(this, "יומן")
        panel.addView(TextView(this).apply {
            text = Starter.readLog(this@MainActivity).ifEmpty { "—" }
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(Retro.TEXT_DIM)
            textDirection = View.TEXT_DIRECTION_RTL
            background = Retro.rounded(Color.BLACK, Retro.dpf(this@MainActivity, 10f))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        })
        return panel
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
                .setTitle("חלון הקוד")
                .setMessage(
                    "כדי שתיבת הקוד תופיע מעל מסך ההגדרות, צריך לאשר 'הצגה מעל אפליקציות אחרות'.\n\n" +
                        "אפשר גם להמשיך בלי, ולהקליד את הקוד בהתראה."
                )
                .setPositiveButton("אשר עכשיו") { _, _ -> requestOverlay() }
                .setNegativeButton("המשך עם התראה") { _, _ -> beginPairing() }
                .show()
            return
        }
        beginPairing()
    }

    private fun beginPairing() {
        startForegroundService(Intent(this, PairingService::class.java).setAction(PairingService.ACTION_START))
        val dev = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
        open(dev)
        toast("הדלק 'ניפוי באגים אלחוטי' ← צימוד באמצעות קוד")
    }

    private fun openXiaomiAutostart() {
        Starter.prefs(this).edit().putBoolean("autostart_visited", true).apply()
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
