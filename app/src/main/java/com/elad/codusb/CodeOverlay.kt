package com.elad.codusb

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Big floating box for the pairing code, shown on top of the Settings screen.
 * It sits in the upper part of the screen so the code in the Settings dialog
 * (in the middle) stays visible, and the keyboard opens at the bottom.
 */
class CodeOverlay(private val ctx: Context, private val onCode: (String) -> Unit, private val onCancel: () -> Unit) {

    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var root: View? = null
    private var message: TextView? = null
    private var input: EditText? = null

    val isShowing get() = root != null

    fun show(msg: String) = main.post {
        if (root != null) {
            setMessage(msg, false)
            return@post
        }
        if (!Settings.canDrawOverlays(ctx)) return@post
        val c = ctx
        val panel = Retro.faceplate(c, null).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(Retro.stripes(c))
            addView(TextView(c).apply {
                this.text = "קוד צימוד"
                textSize = 20f
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                setTextColor(Retro.RED_TEXT)
                setPadding(0, Retro.dp(c, 12), 0, Retro.dp(c, 4))
            })
            message = TextView(c).apply {
                this.text = msg
                textSize = 15f
                setTextColor(Retro.TEXT_LIGHT)
            }
            addView(message)
            input = EditText(c).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                filters = arrayOf(InputFilter.LengthFilter(6))
                textSize = 30f
                typeface = Retro.pixel(c)
                letterSpacing = 0.15f
                gravity = Gravity.CENTER
                hint = "------"
                setTextColor(Retro.TEXT_LIGHT)
                setHintTextColor(Retro.STRIPE)
                textDirection = View.TEXT_DIRECTION_LTR
                background = Retro.rounded(
                    android.graphics.Color.BLACK, Retro.dpf(c, 14f), Retro.STRIPE, Retro.dp(c, 2)
                )
                setPadding(0, Retro.dp(c, 14), 0, Retro.dp(c, 14))
                imeOptions = EditorInfo.IME_ACTION_DONE
                setOnEditorActionListener { _, _, _ -> submit(); true }
            }
            addView(input, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Retro.dp(c, 12) })
            val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(
                Retro.pill(c, "צמד ✓", true) { submit() },
                LinearLayout.LayoutParams(0, Retro.dp(c, 52), 1f).apply {
                    topMargin = Retro.dp(c, 12); marginEnd = Retro.dp(c, 6)
                }
            )
            row.addView(
                Retro.pill(c, "ביטול") { hide(); onCancel() },
                LinearLayout.LayoutParams(0, Retro.dp(c, 52), 1f).apply {
                    topMargin = Retro.dp(c, 12); marginStart = Retro.dp(c, 6)
                }
            )
            addView(row)
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = Retro.dp(c, 40)
            horizontalMargin = 0.04f
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        }
        try {
            wm.addView(panel, lp)
            root = panel
            input?.requestFocus()
            main.postDelayed({
                input?.let {
                    it.requestFocus()
                    c.getSystemService(InputMethodManager::class.java)
                        .showSoftInput(it, InputMethodManager.SHOW_IMPLICIT)
                }
            }, 350)
        } catch (t: Throwable) {
            Starter.log(c, "חלון הקוד לא נפתח: ${t.message}")
            root = null
        }
    }

    private fun submit() {
        val code = input?.text?.toString()?.filter { it.isDigit() } ?: ""
        if (code.length != 6) {
            setMessage("הקוד צריך להיות 6 ספרות", true)
            return
        }
        onCode(code)
    }

    fun setMessage(text: String, error: Boolean) = main.post {
        message?.text = text
        message?.setTextColor(if (error) Retro.RED_TEXT else Retro.TEXT_LIGHT)
        if (error) input?.setText("")
    }

    fun hide() = main.post {
        root?.let {
            try {
                wm.removeView(it)
            } catch (_: Throwable) {
            }
        }
        root = null
        input = null
        message = null
    }
}
