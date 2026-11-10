package com.elad.codusb

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

/** Language switch drawn as a console D-pad: UP = EN, DOWN = HE. */
@SuppressLint("ViewConstructor")
class DpadLang(ctx: Context, private val onPick: (english: Boolean) -> Unit) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Ui.BLACK
    }
    private var pressed = 0 // -1 up, 1 down

    init {
        contentDescription = "Language: up English, down Hebrew"
        elevation = Ui.dpf(ctx, 6f)
        outlineProvider = null
    }

    private fun cross(cx: Float, cy: Float, arm: Float, half: Float, r: Float): Path {
        val a = Path()
        a.addRoundRect(RectF(cx - half, cy - arm, cx + half, cy + arm), r, r, Path.Direction.CW)
        val b = Path()
        b.addRoundRect(RectF(cx - arm, cy - half, cx + arm, cy + half), r, r, Path.Direction.CW)
        a.op(b, Path.Op.UNION)
        return a
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2; val cy = h / 2
        val arm = minOf(w, h) / 2 - Ui.dpf(context, 4f)
        val half = arm * 0.36f
        val r = half * 0.42f
        val d = Ui.dpf(context, 1f)

        // recessed socket in the white shell
        p.shader = null
        p.color = Ui.SHELL_EDGE
        c.drawPath(cross(cx, cy + d, arm + 3 * d, half + 3 * d, r + 3 * d), p)
        p.color = Ui.WELL
        c.drawPath(cross(cx, cy, arm + 2 * d, half + 2 * d, r + 2 * d), p)

        // base lip (gives the 3D thickness)
        p.color = Color.parseColor("#1C1F23")
        c.drawPath(cross(cx, cy + 2.5f * d, arm, half, r), p)
        // face
        p.shader = LinearGradient(0f, cy - arm, 0f, cy + arm,
            Color.parseColor("#4A5058"), Color.parseColor("#2A2E33"), Shader.TileMode.CLAMP)
        val face = cross(cx, cy, arm, half, r)
        c.drawPath(face, p)
        p.shader = null

        // lit arm for the current language
        val en = L.isEn
        for (up in listOf(true, false)) {
            val active = up == en
            val top = if (up) cy - arm else cy + half * 0.55f
            val bot = if (up) cy - half * 0.55f else cy + arm
            val arc = RectF(cx - half + 2 * d, top + 2 * d, cx + half - 2 * d, bot - 2 * d)
            val sink = if ((up && pressed == -1) || (!up && pressed == 1)) 1.5f * d else 0f
            arc.offset(0f, sink)
            if (active) {
                p.shader = LinearGradient(0f, arc.top, 0f, arc.bottom,
                    Ui.lighten(Ui.GREEN, 0.18f), Ui.GREEN_DEEP, Shader.TileMode.CLAMP)
                c.drawRoundRect(arc, r * 0.8f, r * 0.8f, p)
                p.shader = null
            }
            // arrow
            val ay = if (up) arc.top + arc.height() * 0.24f else arc.bottom - arc.height() * 0.24f
            val s = half * 0.32f
            val tri = Path().apply {
                if (up) { moveTo(cx, ay - s * 0.6f); lineTo(cx - s, ay + s * 0.5f); lineTo(cx + s, ay + s * 0.5f) }
                else { moveTo(cx, ay + s * 0.6f); lineTo(cx - s, ay - s * 0.5f); lineTo(cx + s, ay - s * 0.5f) }
                close()
            }
            p.color = if (active) Color.WHITE else Color.parseColor("#8A929B")
            c.drawPath(tri, p)
            txt.color = p.color
            txt.textSize = half * 0.62f
            val ty = if (up) arc.bottom - arc.height() * 0.2f else arc.top + arc.height() * 0.2f + txt.textSize * 0.8f
            c.drawText(if (up) "EN" else "HE", cx, ty, txt)
        }
        // centre dimple + gloss
        p.color = Color.parseColor("#24282C")
        c.drawCircle(cx, cy, half * 0.42f, p)
        p.shader = LinearGradient(0f, cy - arm, 0f, cy, Color.argb(70, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawPath(cross(cx, cy - d, arm - 2 * d, half - 3 * d, r), p)
        p.shader = null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val up = e.y < height / 2f
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> { pressed = if (up) -1 else 1; invalidate() }
            MotionEvent.ACTION_UP -> {
                pressed = 0; invalidate()
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                if (up != L.isEn) onPick(up)
            }
            MotionEvent.ACTION_CANCEL -> { pressed = 0; invalidate() }
        }
        return true
    }
}
