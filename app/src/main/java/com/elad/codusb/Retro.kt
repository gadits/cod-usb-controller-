package com.elad.codusb

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Look of a classic 8-bit console controller: grey body, black faceplate, red A/B buttons. */
object Retro {
    val BODY = Color.parseColor("#C9C9C9")
    val BODY_LIGHT = Color.parseColor("#DEDEDE")
    val BODY_EDGE = Color.parseColor("#9E9E9E")
    val FACE = Color.parseColor("#1E1E1E")
    val FACE_EDGE = Color.parseColor("#0C0C0C")
    val STRIPE = Color.parseColor("#7A7A7A")
    val STRIPE_LIGHT = Color.parseColor("#A8A8A8")
    val RED = Color.parseColor("#D0142C")
    val RED_DARK = Color.parseColor("#8A0A1B")
    val RED_TEXT = Color.parseColor("#E8283F")
    val PILL = Color.parseColor("#3B3B3B")
    val PILL_PRESSED = Color.parseColor("#262626")
    val GREEN = Color.parseColor("#2E9E4F")
    val GREEN_DARK = Color.parseColor("#1E6E36")
    val LED_GREEN = Color.parseColor("#39FF6A")
    val LED_RED = Color.parseColor("#FF3B30")
    val TEXT_LIGHT = Color.parseColor("#ECECEC")
    val TEXT_DIM = Color.parseColor("#A8A8A8")

    private var pixelFont: Typeface? = null

    fun pixel(ctx: Context): Typeface =
        pixelFont ?: try {
            ctx.resources.getFont(R.font.press_start).also { pixelFont = it }
        } catch (_: Throwable) {
            Typeface.MONOSPACE
        }

    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    fun dpf(ctx: Context, v: Float) = v * ctx.resources.displayMetrics.density

    fun rounded(color: Int, radius: Float, strokeColor: Int? = null, strokeW: Int = 0) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
            if (strokeColor != null) setStroke(strokeW, strokeColor)
        }

    /** Black faceplate panel with the two grey stripes at the top. */
    fun faceplate(ctx: Context, title: String?): LinearLayout {
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(FACE, dpf(ctx, 18f), FACE_EDGE, dp(ctx, 2))
            setPadding(dp(ctx, 18), dp(ctx, 14), dp(ctx, 18), dp(ctx, 18))
            elevation = dpf(ctx, 3f)
        }
        if (title != null) {
            panel.addView(stripes(ctx))
            panel.addView(TextView(ctx).apply {
                text = title
                textSize = 17f
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                setTextColor(RED_TEXT)
                setPadding(0, dp(ctx, 10), 0, dp(ctx, 6))
            })
        }
        return panel
    }

    fun stripes(ctx: Context): View {
        val wrap = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        repeat(2) { i ->
            wrap.addView(View(ctx).apply {
                background = rounded(if (i == 0) STRIPE_LIGHT else STRIPE, dpf(ctx, 3f))
            }, LinearLayout.LayoutParams(-1, dp(ctx, 5)).apply { topMargin = if (i == 0) 0 else dp(ctx, 4) })
        }
        return wrap
    }

    private fun pressable(normal: Drawable, pressed: Drawable) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), pressed)
        addState(intArrayOf(), normal)
    }

    /** Squash slightly while pressed, like a rubber button. */
    @SuppressLint("ClickableViewAccessibility")
    fun squish(v: View) {
        v.setOnTouchListener { view, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(70).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
            }
            false
        }
    }

    /** Rounded "SELECT / START" style pill button. */
    fun pill(ctx: Context, label: String, done: Boolean = false, onClick: () -> Unit): TextView =
        TextView(ctx).apply {
            text = label
            textSize = 15f
            gravity = Gravity.CENTER
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(dp(ctx, 16), 0, dp(ctx, 16), 0)
            isClickable = true
            isFocusable = true
            setPillState(ctx, this, done)
            layoutParams = LinearLayout.LayoutParams(-1, dp(ctx, 52)).apply { topMargin = dp(ctx, 10) }
            setOnClickListener { onClick() }
            squish(this)
        }

    fun setPillState(ctx: Context, v: TextView, done: Boolean) {
        val r = dpf(ctx, 26f)
        v.background = if (done) pressable(
            rounded(GREEN, r, GREEN_DARK, dp(ctx, 2)),
            rounded(GREEN_DARK, r, GREEN_DARK, dp(ctx, 2))
        ) else pressable(
            rounded(PILL, r, STRIPE, dp(ctx, 2)),
            rounded(PILL_PRESSED, r, STRIPE, dp(ctx, 2))
        )
        v.setTextColor(TEXT_LIGHT)
    }

    /** Big round red A/B button with its letter and a caption under it. */
    fun roundButton(ctx: Context, letter: String, caption: String, onClick: () -> Unit): LinearLayout {
        val size = dp(ctx, 86)
        val circle = TextView(ctx).apply {
            text = letter
            textSize = 20f
            typeface = pixel(ctx)
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#FFD6DB"))
            isClickable = true
            val normal = LayerDrawable(
                arrayOf(
                    GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(RED_DARK) },
                    GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        intArrayOf(Color.parseColor("#F0364C"), RED, RED_DARK)
                    ).apply { shape = GradientDrawable.OVAL }
                )
            ).apply { setLayerInset(1, dp(ctx, 3), dp(ctx, 2), dp(ctx, 3), dp(ctx, 6)) }
            val pressed = LayerDrawable(
                arrayOf(
                    GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(RED_DARK) },
                    GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#A50E22")) }
                )
            ).apply { setLayerInset(1, dp(ctx, 3), dp(ctx, 5), dp(ctx, 3), dp(ctx, 3)) }
            background = pressable(normal, pressed)
            setOnClickListener { onClick() }
            squish(this)
        }
        // the light grey square "well" around the button, like on the controller
        val well = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER
            background = rounded(BODY_LIGHT, dpf(ctx, 16f), BODY_EDGE, dp(ctx, 2))
            setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8))
            addView(circle, LinearLayout.LayoutParams(size, size))
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(well)
            addView(TextView(ctx).apply {
                text = caption
                textSize = 15f
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                setTextColor(RED_TEXT)
                gravity = Gravity.CENTER
                setPadding(0, dp(ctx, 8), 0, 0)
            })
        }
    }

    fun led(ctx: Context, on: Boolean): View = View(ctx).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (on) LED_GREEN else LED_RED)
            setStroke(dp(ctx, 2), Color.parseColor("#000000"))
        }
        layoutParams = LinearLayout.LayoutParams(dp(ctx, 14), dp(ctx, 14)).apply {
            marginEnd = dp(ctx, 10)
            gravity = Gravity.CENTER_VERTICAL
        }
    }

    /** One status line: LED + text. */
    fun ledRow(ctx: Context, on: Boolean, label: String): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(ctx, 5), 0, dp(ctx, 5))
        addView(led(ctx, on))
        addView(TextView(ctx).apply {
            text = label
            textSize = 15f
            setTextColor(TEXT_LIGHT)
        })
    }
}
