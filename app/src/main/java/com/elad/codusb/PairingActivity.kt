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

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var dot: View
    private lateinit var code: EditText
    private var submitted = false

    private val listener: (String, PairingUi.Kind) -> Unit = { text, kind ->
        handler.post { show(text, kind) }
    }

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        window.statusBarColor = Ui.BG_TOP

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
            clipChildren = false
            clipToPadding = false
        }
        val card = Ui.card(this, "קוד צימוד", Ui.GREEN)

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
                    if (s?.length == 6) submit() // all 6 digits typed → pair right away
                }
            })
        }
        card.addView(code, LinearLayout.LayoutParams(-1, -2))
        card.addView(Ui.bumper(this, "צמד", true) { submit() })
        card.addView(Ui.bumper(this, "ביטול") {
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

        show("פותח את ההגדרות לידי...", PairingUi.Kind.INFO)
        PairingUi.listener = listener
        startForegroundService(Intent(this, PairingService::class.java).setAction(PairingService.ACTION_START))
        handler.postDelayed({ openSettingsBeside() }, 250)
    }

    override fun onDestroy() {
        if (PairingUi.listener === listener) PairingUi.listener = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean) {
        super.onMultiWindowModeChanged(isInMultiWindowMode)
        if (isInMultiWindowMode && !submitted) {
            show("בחר בהגדרות 'צימוד מכשיר באמצעות קוד צימוד' והקלד כאן את 6 הספרות", PairingUi.Kind.INFO)
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
                Starter.log(this, "הקיצור לניפוי באגים אלחוטי לא נפתח – פותח אפשרויות מפתח")
                launchBeside(devOptionsIntent())
            }
        }, 1500)
        handler.postDelayed({
            if (!isFinishing && !isInMultiWindowMode) {
                Starter.log(this, "המכשיר לא פיצל את המסך – ההקלדה תתבצע בהתראה")
            }
        }, 3000)
    }

    private fun submit() {
        val c = code.text.toString().filter { it.isDigit() }
        if (c.length != 6) {
            show("הקוד צריך להיות 6 ספרות", PairingUi.Kind.ERROR)
            return
        }
        submitted = true
        show("מצמד...", PairingUi.Kind.BUSY)
        code.windowInsetsController?.hide(WindowInsets.Type.ime())
        startService(
            Intent(this, PairingService::class.java)
                .setAction(PairingService.ACTION_CODE)
                .putExtra(PairingService.EXTRA_CODE, c)
        )
    }

    private fun show(text: String, kind: PairingUi.Kind) {
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

    fun update(text: String, kind: Kind) {
        listener?.invoke(text, kind)
    }
}
