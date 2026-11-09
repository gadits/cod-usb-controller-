package com.elad.codusb

import android.Manifest
import android.app.Activity
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
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var padsText: TextView
    private lateinit var setupText: TextView
    private lateinit var logText: TextView
    private lateinit var pairButton: Button
    private lateinit var devButton: Button
    private val handler = Handler(Looper.getMainLooper())

    private val doneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refresh()
    }

    private val deviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = refresh()
        override fun onInputDeviceRemoved(deviceId: Int) = refresh()
        override fun onInputDeviceChanged(deviceId: Int) = refresh()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(24))
            setBackgroundColor(Color.parseColor("#F4F6F4"))
        }

        root.addView(TextView(this).apply {
            text = "שלט USB ל-Call of Duty Mobile"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#1B5E20"))
        })

        // Status
        val statusCard = card(root)
        statusText = TextView(this).apply { textSize = 18f; setTypeface(typeface, Typeface.BOLD) }
        padsText = TextView(this).apply { textSize = 14f; setPadding(0, dp(6), 0, dp(6)) }
        statusCard.addView(statusText)
        statusCard.addView(padsText)
        statusCard.addView(button("▶  הפעל עכשיו", "#2E7D32") {
            startForegroundService(Intent(this, StartService::class.java))
            toast("מפעיל... התוצאה תופיע בהתראה")
        })
        statusCard.addView(button("■  כבה", "#757575") {
            startForegroundService(Intent(this, StartService::class.java).setAction(StartService.ACTION_STOP))
            toast("מכבה...")
        })
        statusCard.addView(CheckBox(this).apply {
            text = "הפעלה אוטומטית כשהמכשיר נדלק"
            isChecked = Starter.autoStart(this@MainActivity)
            setOnCheckedChangeListener { _, c -> Starter.setAutoStart(this@MainActivity, c) }
        })

        // One-time setup
        val setupCard = card(root)
        setupCard.addView(title("הגדרה חד-פעמית"))
        setupText = TextView(this).apply { textSize = 14f; setPadding(0, dp(4), 0, dp(4)) }
        setupCard.addView(setupText)
        devButton = button("1. פתח 'מידע על הטלפון'", "#1565C0") {
            open(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
            toast("לחץ 7 פעמים על 'גרסת MIUI/HyperOS' או 'מספר Build'")
        }
        setupCard.addView(devButton)
        pairButton = button("2. התחל צימוד", "#1565C0") { startPairing() }
        setupCard.addView(pairButton)

        // Xiaomi / battery
        val sysCard = card(root)
        sysCard.addView(title("כדי שזה יעבוד גם אחרי הדלקה"))
        sysCard.addView(TextView(this).apply {
            textSize = 14f
            text = "• בשיאומי: אפשר 'הפעלה אוטומטית' לאפליקציה.\n" +
                "• בכל מכשיר: בטל הגבלת סוללה לאפליקציה.\n" +
                "• צריך להיות מחובר ל-WiFi בזמן ההפעלה."
        })
        sysCard.addView(button("הרשאת הפעלה אוטומטית (שיאומי)", "#EF6C00") { openXiaomiAutostart() })
        sysCard.addView(button("בטל הגבלת סוללה", "#EF6C00") { requestBatteryExemption() })

        // Log
        val logCard = card(root)
        logCard.addView(title("יומן"))
        logText = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            textDirection = View.TEXT_DIRECTION_RTL
        }
        logCard.addView(logText)

        setContentView(ScrollView(this).apply { addView(root) })

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
        refresh()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(doneReceiver)
        } catch (_: Throwable) {
        }
        getSystemService(InputManager::class.java).unregisterInputDeviceListener(deviceListener)
    }

    private fun refresh() {
        val active = Starter.isVirtualPresent(this)
        statusText.text = if (active) "● השלט הווירטואלי פעיל" else "● השלט הווירטואלי כבוי"
        statusText.setTextColor(Color.parseColor(if (active) "#2E7D32" else "#C62828"))
        val pads = Starter.realGamepads(this)
        padsText.text = if (pads.isEmpty()) "לא זוהה שלט מחובר כרגע."
        else "שלטים מחוברים: " + pads.joinToString(", ")

        val devOn = Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        val paired = Starter.isPaired(this)
        val wss = Starter.hasSecureSettings(this)
        setupText.text = buildString {
            append(if (devOn) "✓ אפשרויות מפתח פעילות\n" else "✗ אפשרויות מפתח כבויות – שלב 1\n")
            append(if (paired) "✓ צימוד בוצע\n" else "✗ עוד לא בוצע צימוד – שלב 2\n")
            append(
                if (wss) "✓ הפעלה אוטומטית אחרי הדלקה מאושרת"
                else "✗ הפעלה אוטומטית אחרי הדלקה עוד לא מאושרת (מתבצע בזמן הצימוד)"
            )
            if (!paired) {
                append(
                    "\n\nשלב 2: לחץ 'התחל צימוד'. ייפתחו אפשרויות מפתח. הדלק 'ניפוי באגים אלחוטי', " +
                        "היכנס אליו ובחר 'צימוד מכשיר באמצעות קוד צימוד'. " +
                        "הקלד את הקוד בהתראה שתופיע למעלה (משוך את ההתראה למטה)."
                )
            }
        }
        devButton.visibility = if (devOn) View.GONE else View.VISIBLE
        pairButton.text = if (paired) "צמד מחדש" else "2. התחל צימוד"
        logText.text = Starter.readLog(this).ifEmpty { "—" }
    }

    private fun startPairing() {
        if (!Starter.isWifiConnected(this)) {
            toast("צריך להיות מחובר ל-WiFi")
            return
        }
        startForegroundService(Intent(this, PairingService::class.java).setAction(PairingService.ACTION_START))
        val dev = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
        open(dev)
        toast("הדלק 'ניפוי באגים אלחוטי' ← צימוד באמצעות קוד")
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

    private fun requestBatteryExemption() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            toast("כבר מבוטל ✓")
            return
        }
        open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
    }

    private fun open(intent: Intent): Boolean = try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Throwable) {
        false
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun card(parent: LinearLayout): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(14).toFloat()
            }
            elevation = dp(2).toFloat()
        }
        parent.addView(c, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        return c
    }

    private fun title(s: String) = TextView(this).apply {
        text = s
        textSize = 17f
        setTypeface(typeface, Typeface.BOLD)
    }

    private fun button(label: String, color: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 16f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = dp(10).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) }
        setOnClickListener { onClick() }
    }
}
