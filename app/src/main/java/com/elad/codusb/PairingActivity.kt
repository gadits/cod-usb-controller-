package com.elad.codusb

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * The designed pairing screen.
 *
 * It opens Settings next to itself in split screen. In split screen both halves stay active,
 * so the pairing dialog in Settings stays open while the code is typed here.
 * (If the phone doesn't split, the pairing notification is still there as a fallback.)
 */
class PairingActivity : Activity() {

    companion object {
        private const val REQ_CAPTURE = 7
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var dot: View
    private lateinit var code: EditText
    private var submitted = false

    private val listener: (String, PairingUi.Kind) -> Unit = { text, kind ->
        handler.post { show(text, kind) }
    }

    private val codeListener: (String, Int) -> Unit = { c, port ->
        handler.post {
            readPort = port
            filling = true
            code.setText(c) // shown for checking — not sent until L.t("צמד", "Pair") is pressed
            filling = false
            show(L.t("זוהה הקוד $c – ודא שהוא זהה לקוד בחלון ולחץ 'צמד'", "Code $c detected – check it matches the window and tap 'Pair'"), PairingUi.Kind.OK)
        }
    }
    private var filling = false
    private var readPort = 0

    private fun dp(v: Int) = Ui.dp(this, v)

    private fun readFromScreen() {
        val mpm = getSystemService(android.media.projection.MediaProjectionManager::class.java)
        val i = if (android.os.Build.VERSION.SDK_INT >= 34)
            mpm.createScreenCaptureIntent(android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay())
        else mpm.createScreenCaptureIntent()
        show(L.t("אשר את צילום המסך...", "Approve the screen capture..."), PairingUi.Kind.BUSY)
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(i, REQ_CAPTURE)
        } catch (t: Throwable) {
            show(L.t("צילום מסך לא זמין במכשיר – הקלד את הקוד", "Screen capture isn't available on this device – type the code"), PairingUi.Kind.ERROR)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_CAPTURE) return
        if (resultCode != RESULT_OK || data == null) {
            show(L.t("צילום המסך בוטל – אפשר להקליד את הקוד", "Screen capture cancelled – you can type the code"), PairingUi.Kind.INFO)
            return
        }
        show(L.t("קורא את הקוד מהמסך...", "Reading the code from the screen..."), PairingUi.Kind.BUSY)
        startForegroundService(
            Intent(this, CaptureService::class.java)
                .putExtra(CaptureService.EXTRA_RESULT, resultCode)
                .putExtra(CaptureService.EXTRA_DATA, data)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = L.dir
        window.statusBarColor = Ui.BG_TOP

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
            clipChildren = false
            clipToPadding = false
        }
        val card = Ui.card(this, L.t("קוד צימוד", "Pairing code"), Ui.GREEN)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(10))
        }
        dot = View(this)
        row.addView(dot, LinearLayout.LayoutParams(dp(12), dp(12)).apply { marginEnd = dp(10) })
        status = Ui.text(this, "", 15f, Ui.INK, Ui.MEDIUM)
        row.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(row)

        code = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(6))
            textSize = 34f
            typeface = Ui.BLACK
            letterSpacing = 0.3f
            gravity = Gravity.CENTER
            hint = "••••••"
            isSingleLine = true
            setTextColor(Ui.INK)
            setHintTextColor(Ui.OFF)
            textDirection = View.TEXT_DIRECTION_LTR
            background = Ui.rounded(Ui.WELL, Ui.dpf(this@PairingActivity, 18f), Ui.SHELL_EDGE, dp(1))
            setPadding(0, dp(12), 0, dp(12))
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, _, _ -> submit(); true }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (s?.length == 6 && !filling) submit() // all 6 digits typed → pair right away
                }
            })
        }
        card.addView(code, LinearLayout.LayoutParams(-1, -2))
        card.addView(Ui.bumper(this, L.t("צמד", "Pair"), true) { submit() })
        card.addView(Ui.bumper(this, L.t("📷  קרא את הקוד מהמסך", "📷  Read the code from the screen")) { readFromScreen() })
        card.addView(Ui.text(
            this,
            L.t("אפשרות נוספת: פתח את חלון הצימוד בהגדרות ולחץ כאן. אחרי אישור צילום מסך, האפליקציה תזהה את הקוד ותכניס אותו לתיבה – ", "Another option: open the pairing window in Settings and tap here. After you approve a screen capture, the app finds the code and fills it in – ") +
                L.t("בדוק שהוא נכון ולחץ 'צמד'. התמונה לא נשמרת ולא נשלחת לשום מקום.", "check it and tap 'Pair'. The image is never saved or sent anywhere."),
            12.5f, Ui.INK_FAINT
        ).apply { setPadding(dp(4), dp(8), dp(4), 0) })
        card.addView(Ui.bumper(this, L.t("ביטול", "Cancel")) {
            startService(Intent(this, PairingService::class.java).setAction(PairingService.ACTION_STOP))
            finish()
        })
        root.addView(card, LinearLayout.LayoutParams(-1, -2))
        setContentView(ScrollView(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Ui.BG_TOP, Ui.BG_BOTTOM)
            )
            addView(root)
        })

        PairingUi.listener = listener
        PairingUi.codeListener = codeListener
        val last = PairingUi.last
        if (savedInstanceState != null && last != null) {
            // recreated (e.g. by the system): just show where we are, don't start over
            show(last.first, last.second)
        } else {
            show(L.t("פותח את ההגדרות לידי...", "Opening Settings next to me..."), PairingUi.Kind.INFO)
            startForegroundService(Intent(this, PairingService::class.java).setAction(PairingService.ACTION_START))
            handler.postDelayed({ openSettingsBeside() }, 250)
        }
    }

    override fun onDestroy() {
        if (PairingUi.codeListener === codeListener) PairingUi.codeListener = null
        if (PairingUi.listener === listener) {
            PairingUi.listener = null
            PairingUi.inSplit = false
        }
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /** Safety net: always show the latest status, even if a message was missed. */
    private var shown: Pair<String, PairingUi.Kind>? = null
    private val poll = object : Runnable {
        override fun run() {
            val l = PairingUi.last
            if (l != null && l != shown && submitted) show(l.first, l.second)
            handler.postDelayed(this, 700)
        }
    }

    override fun onResume() {
        super.onResume()
        PairingUi.inSplit = isInMultiWindowMode
        handler.removeCallbacks(poll)
        handler.post(poll)
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean) {
        super.onMultiWindowModeChanged(isInMultiWindowMode)
        PairingUi.inSplit = isInMultiWindowMode
        if (isInMultiWindowMode && !submitted) {
            show(L.t("בחר בהגדרות 'צימוד מכשיר באמצעות קוד צימוד' והקלד כאן את 6 הספרות", "In Settings choose 'Pair device with pairing code' and type the 6 digits here"), PairingUi.Kind.INFO)
        }
    }

    private fun wirelessIntent() = Intent("android.service.quicksettings.action.QS_TILE_PREFERENCES")
        .setPackage("com.android.settings")
        .putExtra(
            Intent.EXTRA_COMPONENT_NAME,
            ComponentName(
                "com.android.settings",
                "com.android.settings.development.qstile.DevelopmentTiles\$WirelessDebugging"
            )
        )

    private fun devOptionsIntent() = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        .putExtra(":settings:fragment_args_key", "toggle_adb_wireless")

    /**
     * Other ways to reach the "Wireless debugging" screen, for phones (e.g. Xiaomi) that ignore the
     * shortcut. Each one either opens the right screen, or fails / falls back to Developer options.
     */
    private fun alternativeWirelessIntents(): List<Intent> {
        val fragment = "com.android.settings.development.WirelessDebuggingFragment"
        return listOf(
            // a dedicated activity some Settings builds have
            Intent().setClassName("com.android.settings", "com.android.settings.Settings\$WirelessDebuggingActivity"),
            // the Settings sub-page host, asked to show the Wireless debugging page
            Intent().setClassName("com.android.settings", "com.android.settings.SubSettings")
                .putExtra(":settings:show_fragment", fragment),
            // the explicit Developer-options host, asked for the page (both extra spellings)
            Intent().setClassName("com.android.settings", "com.android.settings.Settings\$DevelopmentSettingsDashboardActivity")
                .putExtra(":settings:show_fragment", fragment)
                .putExtra(":android:show_fragment", fragment),
            // action names some vendor builds register
            Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS"),
            Intent("android.settings.ADB_WIRELESS_SETTINGS"),
            // Developer options, asked to show the Wireless debugging page (else scrolls to that row)
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                .putExtra(":android:show_fragment", fragment)
                .putExtra(":settings:show_fragment", fragment)
                .putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
        )
    }

    private fun launchBeside(i: Intent): Boolean = try {
        startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT))
        true
    } catch (_: Throwable) {
        false
    }

    private fun openSettingsBeside() {
        if (!launchBeside(wirelessIntent())) launchBeside(devOptionsIntent())
        // Xiaomi may accept the shortcut but show nothing: then open Developer options
        handler.postDelayed({
            if (!isFinishing && !isInMultiWindowMode && hasWindowFocus()) {
                // try the other ways, in order; the first one the phone accepts is used
                val ok = alternativeWirelessIntents().withIndex().firstOrNull { (_, i) ->
                    i.resolveActivity(packageManager) != null && launchBeside(i)
                }
                if (ok != null) {
                    Starter.log(this, L.t("הקיצור לא נפתח – נפתח בדרך חלופית ${ok.index + 1}", "Shortcut didn't open – opened with alternative ${ok.index + 1}"))
                } else {
                    Starter.log(this, L.t("הקיצור לא נפתח – פותח אפשרויות מפתח", "Shortcut didn't open – opening Developer options"))
                    launchBeside(devOptionsIntent())
                }
            }
        }, 1500)
        handler.postDelayed({
            if (!isFinishing && !isInMultiWindowMode) {
                Starter.log(this, L.t("המכשיר לא פיצל את המסך – ההקלדה תתבצע בהתראה", "The device didn't split the screen – type the code in the notification"))
            }
        }, 3000)
    }

    private fun submit() {
        val c = code.text.toString().filter { it.isDigit() }
        if (c.length != 6) {
            show(L.t("הקוד צריך להיות 6 ספרות", "The code must be 6 digits"), PairingUi.Kind.ERROR)
            return
        }
        submitted = true
        show(L.t("מצמד...", "Pairing..."), PairingUi.Kind.BUSY)
        code.windowInsetsController?.hide(WindowInsets.Type.ime())
        startService(
            Intent(this, PairingService::class.java)
                .setAction(PairingService.ACTION_CODE)
                .putExtra(PairingService.EXTRA_CODE, c)
                .putExtra(PairingService.EXTRA_PORT, readPort)
        )
        readPort = 0
    }

    private fun show(text: String, kind: PairingUi.Kind) {
        shown = text to kind
        status.text = text
        status.setTextColor(if (kind == PairingUi.Kind.ERROR) Ui.RED else Ui.INK)
        val color = when (kind) {
            PairingUi.Kind.INFO -> Ui.BLUE
            PairingUi.Kind.BUSY -> Ui.YELLOW
            PairingUi.Kind.OK -> Ui.GREEN
            PairingUi.Kind.ERROR -> Ui.RED
        }
        dot.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        when (kind) {
            PairingUi.Kind.ERROR -> {
                submitted = false
                code.setText("")
                code.requestFocus()
            }
            else -> Unit
        }
        if (text.startsWith("✓") && kind == PairingUi.Kind.OK && PairingUi.done) {
            handler.postDelayed({ finish() }, 2500)
        }
    }
}

/** Tiny bridge between the pairing service and the pairing screen (same process). */
object PairingUi {
    enum class Kind { INFO, BUSY, OK, ERROR }

    @Volatile
    var listener: ((String, Kind) -> Unit)? = null

    @Volatile
    var done = false

    /** The pairing screen is beside Settings: no need to pop the notification over it. */
    @Volatile
    var inSplit = false

    @Volatile
    var last: Pair<String, Kind>? = null

    fun update(text: String, kind: Kind) {
        last = text to kind
        listener?.invoke(text, kind)
    }

    /** The code was read from the screen. */
    @Volatile
    var codeListener: ((String, Int) -> Unit)? = null

    fun codeRead(code: String, port: Int) {
        codeListener?.invoke(code, port)
    }
}
