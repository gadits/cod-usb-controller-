package com.elad.codusb

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Shared 3D painting: recessed socket → cast shadow → side wall → lit face → gloss. */
object Paint3D {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Rounded polygon through [pts] (x0,y0,x1,y1,...) with corner radius [r]. */
    fun roundPoly(pts: FloatArray, r: Float): Path {
        val n = pts.size / 2
        val path = Path()
        for (i in 0 until n) {
            val px = pts[((i - 1 + n) % n) * 2]; val py = pts[((i - 1 + n) % n) * 2 + 1]
            val cx = pts[i * 2]; val cy = pts[i * 2 + 1]
            val nx = pts[((i + 1) % n) * 2]; val ny = pts[((i + 1) % n) * 2 + 1]
            val d1 = Math.hypot((px - cx).toDouble(), (py - cy).toDouble()).toFloat()
            val d2 = Math.hypot((nx - cx).toDouble(), (ny - cy).toDouble()).toFloat()
            val a = minOf(r, d1 / 2, d2 / 2)
            val sx = cx + (px - cx) / d1 * a; val sy = cy + (py - cy) / d1 * a
            val ex = cx + (nx - cx) / d2 * a; val ey = cy + (ny - cy) / d2 * a
            if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
            path.quadTo(cx, cy, ex, ey)
        }
        path.close()
        return path
    }

    /**
     * Draws a raised key. [shape] (grow, dy) returns the key outline grown by [grow] px and moved down by [dy] px.
     * [bounds] is the face's bounding box (for gradients).
     */
    fun key(
        c: Canvas, ctx: Context, shape: (Float, Float) -> Path, bounds: RectF,
        color: Int, deep: Int, pressed: Boolean
    ) {
        val d = Ui.dpf(ctx, 1f)
        p.shader = null; p.maskFilter = null
        // recessed socket in the white shell: dark at the top inside, lit at the bottom
        p.shader = LinearGradient(0f, bounds.top - 5 * d, 0f, bounds.bottom + 7 * d,
            intArrayOf(Color.parseColor("#B9C0C7"), Color.parseColor("#DCE0E4"), Color.parseColor("#F8F9FA")),
            null, Shader.TileMode.CLAMP)
        c.drawPath(shape(5 * d, 2 * d), p)
        p.shader = null
        // soft shadow the key casts into the socket
        p.color = Color.argb(if (pressed) 60 else 110, 0, 0, 0)
        p.maskFilter = BlurMaskFilter(3.5f * d, BlurMaskFilter.Blur.NORMAL)
        c.drawPath(shape(0f, if (pressed) 2 * d else 5 * d), p)
        p.maskFilter = null
        // side wall (same hue, darker) — the key's thickness
        val lift = if (pressed) 1.2f * d else 4f * d
        p.shader = LinearGradient(0f, bounds.top, 0f, bounds.bottom + lift,
            deep, Ui.darken(deep), Shader.TileMode.CLAMP)
        c.drawPath(shape(0f, lift), p)
        // the face, lit from the top-left
        val faceDy = if (pressed) 1.2f * d else 0f
        val face = shape(0f, faceDy)
        val w = bounds.width(); val h = bounds.height()
        p.shader = RadialGradient(bounds.left + w * 0.38f, bounds.top + h * 0.25f + faceDy, maxOf(w, h) * 0.85f,
            intArrayOf(Ui.lighten(color, if (pressed) 0.18f else 0.42f), color, deep),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(face, p)
        // gloss across the top, clipped to the face
        if (!pressed) {
            c.save()
            c.clipPath(face)
            p.shader = LinearGradient(0f, bounds.top, 0f, bounds.top + h * 0.5f,
                Color.argb(170, 255, 255, 255), Color.argb(10, 255, 255, 255), Shader.TileMode.CLAMP)
            c.drawOval(RectF(bounds.left + w * 0.12f, bounds.top + 1.5f * d, bounds.right - w * 0.12f, bounds.top + h * 0.52f), p)
            // faint rim light along the bottom
            p.shader = RadialGradient(bounds.centerX(), bounds.bottom, w * 0.45f,
                Color.argb(60, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
            c.drawRect(bounds.left, bounds.bottom - h * 0.3f, bounds.right, bounds.bottom, p)
            c.restore()
        }
        p.shader = null
    }
}

/** The key itself: START = play triangle, SELECT = rectangle. Follows the parent's pressed state. */
@SuppressLint("ViewConstructor")
class PadKeyView(ctx: Context, private val start: Boolean, private val color: Int, private val deep: Int) : View(ctx) {
    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // blurred shadow
        isDuplicateParentStateEnabled = true
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val d = Ui.dpf(context, 1f)
        val cx = width / 2f
        val box = if (start) RectF(cx - 22 * d, 8 * d, cx + 26 * d, 58 * d)
        else RectF(cx - 30 * d, 17 * d, cx + 30 * d, 49 * d)
        val shape: (Float, Float) -> Path = { g, dy ->
            if (start) {
                // play triangle; grown outward by g
                Paint3D.roundPoly(floatArrayOf(
                    box.left - g, box.top - g * 1.4f + dy,
                    box.right + g * 1.6f, box.centerY() + dy,
                    box.left - g, box.bottom + g * 1.4f + dy
                ), 9 * d + g)
            } else {
                Path().apply {
                    addRoundRect(RectF(box.left - g, box.top - g + dy, box.right + g, box.bottom + g + dy),
                        11 * d + g, 11 * d + g, Path.Direction.CW)
                }
            }
        }
        Paint3D.key(c, context, shape, box, color, deep, isPressed)
    }
}

object Pad {
    private val GRAPHITE = Color.parseColor("#5E666F")
    private val GRAPHITE_DEEP = Color.parseColor("#2C3136")

    /** A START (confirm) or SELECT (back) key with its caption, Sony-style. */
    fun key(ctx: Context, start: Boolean, caption: String, onClick: () -> Unit): View =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true
            isFocusable = true
            contentDescription = caption
            addView(
                if (start) PadKeyView(ctx, true, Ui.GREEN, Ui.GREEN_DEEP)
                else PadKeyView(ctx, false, GRAPHITE, GRAPHITE_DEEP),
                LinearLayout.LayoutParams(Ui.dp(ctx, 96), Ui.dp(ctx, 68))
            )
            addView(TextView(ctx).apply {
                text = caption
                textSize = 14.5f
                typeface = Ui.BOLD
                gravity = Gravity.CENTER
                setTextColor(Ui.INK)
                setPadding(0, Ui.dp(ctx, 6), 0, 0)
            }, LinearLayout.LayoutParams(-2, -2))
            addView(TextView(ctx).apply {
                text = if (start) "START" else "SELECT"
                textSize = 10f
                typeface = Ui.BLACK
                letterSpacing = 0.25f
                gravity = Gravity.CENTER
                setTextColor(Ui.INK_FAINT)
            }, LinearLayout.LayoutParams(-2, -2))
            setOnClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
        }

    /** SELECT (back) on the left, START (confirm) on the right — as on the controller — in a recessed panel. */
    fun row(ctx: Context, back: String, onBack: () -> Unit, confirm: String, onConfirm: () -> Unit): View =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
            setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 10), Ui.dp(ctx, 8), Ui.dp(ctx, 10))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#E3E6E9"), Color.parseColor("#F3F4F6"))
            ).apply { cornerRadius = Ui.dpf(ctx, 26f); setStroke(Ui.dp(ctx, 1), Ui.SHELL_EDGE) }
            addView(key(ctx, false, back, onBack), LinearLayout.LayoutParams(0, -2, 1f))
            addView(key(ctx, true, confirm, onConfirm), LinearLayout.LayoutParams(0, -2, 1f))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(ctx, 16) }
        }

    /** A designed dialog: card with a title, a body, and SELECT/START keys. */
    fun dialog(
        a: Activity, title: String, accent: Int, build: (LinearLayout) -> Unit,
        back: String, confirm: String?, onConfirm: () -> Unit = {}
    ): Dialog {
        val d = Dialog(a)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val card = Ui.card(a, title, accent).apply { layoutDirection = L.dir }
        build(card)
        if (confirm != null) {
            card.addView(row(a, back, { d.dismiss() }, confirm, { d.dismiss(); onConfirm() }))
        } else {
            card.addView(LinearLayout(a).apply {
                gravity = Gravity.CENTER
                setPadding(0, Ui.dp(a, 14), 0, 0)
                addView(key(a, false, back) { d.dismiss() })
            })
        }
        val holder = FrameLayout(a).apply {
            setPadding(Ui.dp(a, 16), Ui.dp(a, 24), Ui.dp(a, 16), Ui.dp(a, 24))
            clipToPadding = false
            addView(card)
        }
        d.setContentView(ScrollView(a).apply {
            isVerticalScrollBarEnabled = false
            addView(holder)
        })
        d.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        d.show()
        return d
    }
}
