package com.elad.codusb

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

/** Language switch drawn as a 3D console D-pad: UP = EN, DOWN = HE. The current language glows green. */
@SuppressLint("ViewConstructor")
class DpadLang(ctx: Context, private val onPick: (english: Boolean) -> Unit) : View(ctx) {
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Ui.BLACK
    }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pressed = 0 // -1 up, 1 down

    private val graphite = Color.parseColor("#5A626B")
    private val graphiteDeep = Color.parseColor("#2A2E33")
    private val led = Color.parseColor("#5BF07D")

    init {
        contentDescription = "Language: up English, down Hebrew"
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private fun cross(cx: Float, cy: Float, arm: Float, half: Float, r: Float, dy: Float): Path {
        val a = Path()
        a.addRoundRect(RectF(cx - half, cy - arm + dy, cx + half, cy + arm + dy), r, r, Path.Direction.CW)
        val b = Path()
        b.addRoundRect(RectF(cx - arm, cy - half + dy, cx + arm, cy + half + dy), r, r, Path.Direction.CW)
        a.op(b, Path.Op.UNION)
        return a
    }

    override fun onDraw(c: Canvas) {
        val d = Ui.dpf(context, 1f)
        val cx = width / 2f
        val cy = height / 2f - 2 * d
        val arm = minOf(width, height) / 2f - 8 * d
        val half = arm * 0.40f
        val r = half * 0.38f
        val down = pressed != 0
        val shape: (Float, Float) -> Path = { g, dy -> cross(cx, cy, arm + g, half + g, r + g, dy) }
        val box = RectF(cx - arm, cy - arm, cx + arm, cy + arm)
        Paint3D.key(c, context, shape, box, graphite, graphiteDeep, down)

        val faceDy = if (down) 1.2f * d else 0f
        // the pressed arm tilts into the shell: shade it
        if (down) {
            c.save()
            c.clipPath(shape(0f, faceDy))
            ink.shader = null
            ink.color = Color.argb(70, 0, 0, 0)
            if (pressed < 0) c.drawRect(cx - half, cy - arm, cx + half, cy - half * 0.4f + faceDy, ink)
            else c.drawRect(cx - half, cy + half * 0.4f + faceDy, cx + half, cy + arm + faceDy, ink)
            c.restore()
        }
        // centre dimple (concave)
        ink.shader = RadialGradient(cx, cy + faceDy - half * 0.15f, half * 0.55f,
            Color.parseColor("#1E2226"), Color.parseColor("#454C54"), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy + faceDy, half * 0.5f, ink)
        ink.shader = null

        val en = L.isEn
        for (up in listOf(true, false)) {
            val active = up == en
            val sign = if (up) -1f else 1f
            // engraved arrow near the tip
            val ay = cy + faceDy + sign * (arm - half * 0.55f)
            val s = half * 0.36f
            val tri = Path().apply {
                moveTo(cx, ay + sign * s * 0.65f)
                lineTo(cx - s, ay - sign * s * 0.45f)
                lineTo(cx + s, ay - sign * s * 0.45f)
                close()
            }
            // engraving: dark edge above, light edge below
            ink.color = Color.argb(110, 0, 0, 0)
            c.save(); c.translate(0f, -0.8f * d); c.drawPath(tri, ink); c.restore()
            ink.color = Color.argb(60, 255, 255, 255)
            c.save(); c.translate(0f, 0.8f * d); c.drawPath(tri, ink); c.restore()
            ink.color = if (active) led else Color.parseColor("#3A4047")
            if (active) ink.setShadowLayer(4 * d, 0f, 0f, led) else ink.clearShadowLayer()
            c.drawPath(tri, ink)
            ink.clearShadowLayer()

            // label between the arrow and the centre
            txt.textSize = half * 0.78f
            val ty = cy + faceDy + sign * (half * 0.95f) + txt.textSize * 0.36f
            val label = if (up) "EN" else "HE"
            if (active) {
                txt.color = led
                txt.setShadowLayer(4 * d, 0f, 0f, led)
            } else {
                txt.color = Color.parseColor("#9AA3AC")
                txt.setShadowLayer(0.8f * d, 0f, -0.8f * d, Color.argb(160, 0, 0, 0))
            }
            c.drawText(label, cx, ty, txt)
            txt.clearShadowLayer()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val up = e.y < height / 2f
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val p = if (up) -1 else 1
                if (p != pressed) { pressed = p; invalidate() }
            }
            MotionEvent.ACTION_UP -> {
                pressed = 0; invalidate()
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                if (up != L.isEn) postDelayed({ onPick(up) }, 90)
            }
            MotionEvent.ACTION_CANCEL -> { pressed = 0; invalidate() }
        }
        return true
    }
}
