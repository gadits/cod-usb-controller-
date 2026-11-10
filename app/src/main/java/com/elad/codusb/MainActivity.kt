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
        override fun onInputDeviceAdded(deviceId: Int) {
            val d = getSystemService(InputManager::class.java).getInputDevice(deviceId)
            if (d != null && d.isExternal && Starter.isRealPadId(this@MainActivity, deviceId)) {
                Starter.log(this@MainActivity, "שלט חובר: ${Starter.describeDevice(d)}")
                autoStartForController()
            } else if (d != null && d.isExternal && !Starter.isVirtualPresent(this@MainActivity)) {
                Starter.log(this@MainActivity, "התקן חובר (לא זוהה כשלט): ${Starter.describeDevice(d)}")
            }
            render()
        }

        override fun onInputDeviceRemoved(deviceId: Int) = render()
        override fun onInputDeviceChanged(deviceId: Int) {}
    }

    /** A controller was plugged in: switch the virtual controller on if it isn't already. */
    private fun autoStartForController() {
        if (Starter.isPaired(this) && !Starter.isVirtualPresent(this)) {
            Starter.prefs(this).edit().putBoolean("manual_off", false).apply()
            Starter.log(this, "מפעיל את השלט הווירטואלי אוטומטית")
            startForegroundService(Intent(this, StartService::class.java))
        }
    }

    /** Write the list of connected external devices to the log when it changes. */
    private fun logDevicesIfChanged() {
        val now = Starter.describeExternalDevices(this)
        val p = Starter.prefs(this)
        if (p.getString("last_devices", null) != now) {
            p.edit().putString("last_devices", now).apply()
            Starter.log(this, "התקנים מחוברים: $now")
        }
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
        logDevicesIfChanged()
        // controller already plugged in when the app opens → switch on (unless the user turned it off with B)
        if (Starter.realGamepads(this).isNotEmpty() && !Starter.prefs(this).getBoolean("manual_off", false)) {
            autoStartForController()
        }
        render()
    }

    override fun onPause() {
        super.onPause()
        leftForSettings = true
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
            text = "© כל הזכויות שמורות לגדיץ"
            textSize = 12.5f
            typeface = Ui.MEDIUM
            setTextColor(Ui.INK_FAINT)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(6))
        }, LinearLayout.LayoutParams(-1, -2))
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

        // the orb sums it all up: ready / plug in a controller / off
        val (label, glow, line) = when {
            active && pads.isNotEmpty() -> Triple("מוכן!", Ui.GREEN, "הכל מוכן – אפשר לשחק")
            active -> Triple("חבר שלט", Ui.YELLOW, "השלט הווירטואלי פעיל – חבר שלט USB")
            pads.isNotEmpty() -> Triple("כבוי", Ui.RED, "השלט מחובר – לחץ A להפעלה")
            else -> Triple("כבוי", Ui.OFF, "השלט הווירטואלי כבוי")
        }
        card.addView(Ui.statusOrb(this, active || pads.isNotEmpty(), label, glow))
        card.addView(TextView(this).apply {
            text = line
            textSize = 18f
            typeface = Ui.BOLD
            gravity = Gravity.CENTER
            setTextColor(Ui.INK)
            setPadding(0, dp(4), 0, dp(8))
        })
        val rows = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(Ui.WELL, Ui.dpf(this@MainActivity, 16f), Ui.SHELL_EDGE, 1)
            setPadding(dp(14), dp(6), dp(14), dp(6))
        }
        rows.addView(Ui.lightRow(this, active, if (active) "שלט וירטואלי: פעיל" else "שלט וירטואלי: כבוי"))
        rows.addView(
            Ui.lightRow(
                this, pads.isNotEmpty(),
                if (pads.isEmpty()) "שלט USB: לא מחובר" else "שלט USB: " + pads.joinToString(" · ")
            )
        )
        card.addView(rows, LinearLayout.LayoutParams(-1, -2))
        return card
    }

    private fun controlsCard(): View {
        val card = Ui.card(this, "שליטה")
        val size = 86
        val a = Ui.faceButton(this, "A", "הפעל", Ui.GREEN, Ui.GREEN_DEEP, size) {
            Starter.prefs(this).edit().putBoolean("manual_off", false).apply()
            startForegroundService(Intent(this, StartService::class.java))
            toast("מפעיל... התוצאה תופיע בהתראה")
        }
        val b = Ui.faceButton(this, "B", "כבה", Ui.RED, Ui.RED_DEEP, size) {
            Starter.prefs(this).edit().putBoolean("manual_off", true).apply()
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
        val popups = Starter.pairingPopupsAllowed(this)

        val card = Ui.card(this, "הגדרה חד-פעמית", Ui.BLUE)
        card.addView(Ui.lightRow(this, devOn, if (devOn) "אפשרויות מפתח פעילות" else "אפשרויות מפתח כבויות"))
        card.addView(Ui.lightRow(this, paired, if (paired) "צימוד בוצע" else "עוד לא בוצע צימוד"))
        card.addView(Ui.lightRow(this, wss, if (wss) "הרשאה להדלקת ניפוי באגים: התקבלה" else "הרשאה להדלקת ניפוי באגים: תתקבל בצימוד"))
        if (!paired) {
            card.addView(
                Ui.text(
                    this,
                    "לחץ 'התחל צימוד' – ההסבר יופיע על המסך, צעד אחר צעד. זה לוקח פחות מדקה, ורק פעם אחת."
                ).apply { setPadding(0, dp(6), 0, 0) }
            )
        }
        if (!devOn) {
            card.addView(Ui.bumper(this, "פתח 'מידע על הטלפון'") {
                open(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
                toast("לחץ 7 פעמים על 'גרסת MIUI/HyperOS' או 'מספר Build'")
            })
        }
        card.addView(Ui.bumper(this, if (popups) "התראות קופצות לצימוד: מאושרות" else "אשר התראות קופצות לצימוד", popups) {
            openPairingNotificationSettings()
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
        card.addView(Ui.text(this, "צריך להיות מחובר ל-WiFi בזמן ההפעלה. ברשת חדשה יופיע חלון 'לאפשר ניפוי באגים אלחוטי ברשת זו?' – סמן 'אפשר תמיד' ואשר.", 13f, Ui.INK_FAINT).apply {
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

    private fun openPairingNotificationSettings() {
        val i = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, Notif.CH_PAIR)
        if (!open(i)) open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        toast("הפעל התראות והצגה קופצת (Pop on screen / התראות צפות)")
    }

    private fun startPairing() {
        if (!Starter.isWifiConnected(this)) {
            toast("צריך להיות מחובר ל-WiFi")
            return
        }
        if (!Starter.notificationsAllowed(this)) {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            } else {
                openPairingNotificationSettings()
            }
            toast("את קוד הצימוד מקלידים בהתראה – צריך לאשר התראות")
            return
        }
        PairingGuide.show(this) { beginPairing() }
    }

    private fun beginPairing() {
        startActivity(Intent(this, PairingActivity::class.java))
    }

    private var leftForSettings = false

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
