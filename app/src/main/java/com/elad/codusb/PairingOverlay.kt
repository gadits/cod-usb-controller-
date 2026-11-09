package com.elad.codusb

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Floating pairing box, drawn over the Settings app.
 *
 * - Shown at once (from the activity, while the app is in the foreground) as a small chip at
 *   the bottom, so it never blocks the way to "Wireless debugging".
 * - When the "pair with code" dialog opens (detected via mDNS), it expands by itself into a card
 *   at the top with the keyboard ready.
 * - The user can also tap the chip to expand it right away.
 *
 * All methods are safe to call from any thread.
 */
object PairingOverlay {

    enum class Kind { INFO, BUSY, OK, ERROR }

    const val WINDOW_TITLE = "CodUsbPairing"

    private val main = Handler(Looper.getMainLooper())
    private var ctx: Context? = null
    private var wm: WindowManager? = null
    private var root: FrameLayout? = null
    private var lp: WindowManager.LayoutParams? = null

    private var chip: View? = null
    private var chipLabel: TextView? = null
    private var card: View? = null
    private var statusDot: View? = null
    private var status: TextView? = null
    private var codeInput: EditText? = null
    private var portRow: View? = null
    private var portInput: EditText? = null

    @Volatile
    var isShowing = false
        private set

    @Volatile
    var isExpanded = false
        private set

    /** Must be called on the main thread while the app is in the foreground. Returns false if it can't be shown. */
    fun show(context: Context): Boolean {
        val app = context.applicationContext
        if (!Settings.canDrawOverlays(app)) return false
        if (isShowing) {
            collapse()
            setStatus("פתח 'צימוד מכשיר באמצעות קוד צימוד'", Kind.INFO)
            return true
        }
        ctx = app
        wm = app.getSystemService(WindowManager::class.java)
        val frame = FrameLayout(app).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            clipToPadding = false
            clipChildren = false
        }
        chip = buildChip(app).also { frame.addView(it) }
        card = buildCard(app).also { it.visibility = View.GONE; frame.addView(it) }
        root = frame

        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            title = WINDOW_TITLE
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = Ui.dp(app, 96)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }
        return try {
            wm!!.addView(frame, lp)
            isShowing = true
            isExpanded = false
            setStatus("פתח 'צימוד מכשיר באמצעות קוד צימוד'", Kind.INFO)
            true
        } catch (t: Throwable) {
            Starter.log(app, "תיבת הקוד לא הוצגה: ${t.message}")
            root = null
            false
        }
    }

    // ---------- building ----------

    private fun buildChip(c: Context): View = TextView(c).apply {
        chipLabel = this
        text = "⌨  הקלד קוד צימוד"
        textSize = 15f
        typeface = Ui.BOLD
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(Ui.dp(c, 22), 0, Ui.dp(c, 22), 0)
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#3FCF5E"), Ui.GREEN_DEEP)
        ).apply {
            cornerRadius = Ui.dpf(c, 26f)
            setStroke(Ui.dp(c, 1), Color.parseColor("#0B6A1A"))
        }
        elevation = Ui.dpf(c, 10f)
        layoutParams = FrameLayout.LayoutParams(-2, Ui.dp(c, 52)).apply {
            setMargins(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12))
        }
        setOnClickListener { expand(true) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildCard(c: Context): View {
        val panel = Ui.card(c, null).apply { setPadding(Ui.dp(c, 18), Ui.dp(c, 14), Ui.dp(c, 18), Ui.dp(c, 16)) }

        // title row: dot + status text + close
        val top = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        statusDot = View(c).also {
            top.addView(it, LinearLayout.LayoutParams(Ui.dp(c, 12), Ui.dp(c, 12)).apply { marginEnd = Ui.dp(c, 10) })
        }
        status = Ui.text(c, "", 15f, Ui.INK, Ui.MEDIUM).also {
            top.addView(it, LinearLayout.LayoutParams(0, -2, 1f))
        }
        top.addView(TextView(c).apply {
            text = "✕"
            textSize = 18f
            setTextColor(Ui.INK_FAINT)
            gravity = Gravity.CENTER
            setOnClickListener { cancel() }
        }, LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40)))
        panel.addView(top)

        // code input + pair button on one row
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Ui.dp(c, 10), 0, 0)
        }
        codeInput = digitsField(c, 6, "קוד", 30f).apply {
            setOnEditorActionListener { _, _, _ -> submit(); true }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    // all 6 digits typed and no port needed → pair straight away
                    if (s?.length == 6 && portRow?.visibility != View.VISIBLE) submit()
                }
            })
        }
        row.addView(codeInput, LinearLayout.LayoutParams(0, Ui.dp(c, 62), 1f))
        val pair = Ui.bumper(c, "צמד", true) { submit() }
        row.addView(pair, LinearLayout.LayoutParams(Ui.dp(c, 96), Ui.dp(c, 58)).apply { marginStart = Ui.dp(c, 10) })
        panel.addView(row)

        // optional port row (only if the dialog could not be found automatically)
        val pRow = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, Ui.dp(c, 10), 0, 0)
            addView(Ui.text(c, "הקלד את המספר שאחרי הנקודתיים ':' בשורה 'כתובת IP ויציאה' בחלון:", 13f, Ui.INK_SOFT))
            portInput = digitsField(c, 5, "יציאה (פורט)", 22f).apply {
                setOnEditorActionListener { _, _, _ -> submit(); true }
            }
            addView(portInput, LinearLayout.LayoutParams(-1, Ui.dp(c, 54)).apply { topMargin = Ui.dp(c, 6) })
        }
        portRow = pRow
        panel.addView(pRow)

        return FrameLayout(c).apply {
            setPadding(Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 18))
            clipToPadding = false
            addView(panel)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun digitsField(c: Context, max: Int, hintText: String, size: Float) = EditText(c).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        filters = arrayOf(InputFilter.LengthFilter(max))
        textSize = size
        typeface = Ui.BLACK
        letterSpacing = if (max == 6) 0.25f else 0.1f
        gravity = Gravity.CENTER
        hint = hintText
        isSingleLine = true
        setTextColor(Ui.INK)
        setHintTextColor(Ui.OFF)
        textDirection = View.TEXT_DIRECTION_LTR
        background = Ui.rounded(Ui.WELL, Ui.dpf(c, 16f), Ui.SHELL_EDGE, Ui.dp(c, 1))
        setPadding(Ui.dp(c, 6), 0, Ui.dp(c, 6), 0)
        imeOptions = EditorInfo.IME_ACTION_DONE
        // a tap while the window can't take focus → make it focusable first
        setOnTouchListener { v, e ->
            if (e.action == MotionEvent.ACTION_UP) focusField(v as EditText)
            false
        }
    }

    // ---------- state changes ----------

    /** Turn the chip into the full card at the top; optionally open the keyboard. */
    fun expand(focus: Boolean) = main.post {
        val r = root ?: return@post
        val p = lp ?: return@post
        val c = ctx ?: return@post
        if (!isExpanded) {
            chip?.visibility = View.GONE
            card?.visibility = View.VISIBLE
            p.width = WindowManager.LayoutParams.MATCH_PARENT
            p.gravity = Gravity.TOP
            p.y = Ui.dp(c, 28)
            isExpanded = true
            safeUpdate(r, p)
        }
        if (focus) codeInput?.let { focusField(it) }
    }

    private fun collapse() = main.post {
        val r = root ?: return@post
        val p = lp ?: return@post
        val c = ctx ?: return@post
        hideKeyboard()
        card?.visibility = View.GONE
        chip?.visibility = View.VISIBLE
        portRow?.visibility = View.GONE
        codeInput?.setText("")
        p.width = WindowManager.LayoutParams.WRAP_CONTENT
        p.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        p.y = Ui.dp(c, 96)
        p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        isExpanded = false
        safeUpdate(r, p)
    }

    private fun focusField(field: EditText) {
        val r = root ?: return
        val p = lp ?: return
        if (p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0) {
            p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            safeUpdate(r, p)
        }
        main.postDelayed({
            field.requestFocus()
            field.setSelection(field.text.length)
            field.windowInsetsController?.show(WindowInsets.Type.ime())
            ctx?.getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
        }, 120)
    }

    private fun hideKeyboard() {
        codeInput?.let { f ->
            f.windowInsetsController?.hide(WindowInsets.Type.ime())
            ctx?.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(f.windowToken, 0)
        }
    }

    private fun safeUpdate(v: View, p: WindowManager.LayoutParams) {
        try {
            wm?.updateViewLayout(v, p)
        } catch (_: Throwable) {
        }
    }

    fun setStatus(text: String, kind: Kind) = main.post {
        status?.text = text
        status?.setTextColor(if (kind == Kind.ERROR) Ui.RED else Ui.INK)
        val color = when (kind) {
            Kind.INFO -> Ui.BLUE
            Kind.BUSY -> Ui.YELLOW
            Kind.OK -> Ui.GREEN
            Kind.ERROR -> Ui.RED
        }
        statusDot?.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
    }

    /** Called by the service when the pairing dialog has been found. */
    fun dialogDetected() {
        setStatus("חלון הצימוד זוהה – הקלד את 6 הספרות", Kind.OK)
        expand(true)
    }

    /** Called by the service when it could not find the dialog by itself. */
    fun askPort() = main.post {
        portRow?.visibility = View.VISIBLE
        setStatus("לא זיהיתי את חלון הצימוד אוטומטית", Kind.ERROR)
        expand(false)
        portInput?.let { focusField(it) }
    }

    fun codeRejected(text: String) = main.post {
        codeInput?.setText("")
        setStatus(text, Kind.ERROR)
        codeInput?.let { focusField(it) }
    }

    /** Show the final message, then close by itself. */
    fun finishWith(text: String, ok: Boolean) = main.post {
        hideKeyboard()
        setStatus(text, if (ok) Kind.OK else Kind.ERROR)
        main.postDelayed({ hide() }, if (ok) 3500L else 6000L)
    }

    private fun submit() {
        val c = ctx ?: return
        val code = codeInput?.text?.toString()?.filter { it.isDigit() } ?: ""
        if (code.length != 6) {
            setStatus("הקוד צריך להיות 6 ספרות", Kind.ERROR)
            return
        }
        val port = if (portRow?.visibility == View.VISIBLE) {
            val p = portInput?.text?.toString()?.toIntOrNull() ?: 0
            if (p !in 1..65535) {
                setStatus("הקלד את מספר היציאה מהחלון", Kind.ERROR)
                return
            }
            p
        } else 0
        setStatus("מצמד...", Kind.BUSY)
        hideKeyboard()
        try {
            c.startService(
                Intent(c, PairingService::class.java)
                    .setAction(PairingService.ACTION_CODE)
                    .putExtra(PairingService.EXTRA_CODE, code)
                    .putExtra(PairingService.EXTRA_PORT, port)
            )
        } catch (t: Throwable) {
            setStatus("שגיאה: ${t.message}", Kind.ERROR)
        }
    }

    private fun cancel() {
        val c = ctx
        hide()
        if (c != null) {
            try {
                c.startService(Intent(c, PairingService::class.java).setAction(PairingService.ACTION_STOP))
            } catch (_: Throwable) {
            }
        }
    }

    fun hide() = main.post {
        hideKeyboard()
        root?.let {
            try {
                wm?.removeView(it)
            } catch (_: Throwable) {
            }
        }
        root = null
        lp = null
        chip = null
        card = null
        status = null
        statusDot = null
        codeInput = null
        portInput = null
        portRow = null
        isShowing = false
        isExpanded = false
    }
}
