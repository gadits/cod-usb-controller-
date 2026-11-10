package com.elad.codusb

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** White game-controller look: soft white shell, glossy 3D face buttons, deep shadows. */
object Ui {
    // shell
    val BG_TOP = Color.parseColor("#F4F5F6")
    val BG_BOTTOM = Color.parseColor("#DCDFE2")
    val SHELL = Color.parseColor("#FFFFFF")
    val SHELL_SHADE = Color.parseColor("#E9EBED")
    val SHELL_EDGE = Color.parseColor("#D4D8DC")
    val WELL = Color.parseColor("#EEF0F2")
    val INK = Color.parseColor("#17191C")
    val INK_SOFT = Color.parseColor("#5B6168")
    val INK_FAINT = Color.parseColor("#8B9097")

    // face-button colours
    val GREEN = Color.parseColor("#2DB84B")
    val GREEN_DEEP = Color.parseColor("#0E7A1F")
    val RED = Color.parseColor("#E5383B")
    val RED_DEEP = Color.parseColor("#9E1518")
    val BLUE = Color.parseColor("#2F80ED")
    val BLUE_DEEP = Color.parseColor("#1449A8")
    val YELLOW = Color.parseColor("#F5C518")
    val YELLOW_DEEP = Color.parseColor("#B08600")
    val OFF = Color.parseColor("#B9BEC4")

    val BOLD: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
    val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val BLACK: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)

    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    fun dpf(ctx: Context, v: Float) = v * ctx.resources.displayMetrics.density

    private fun rgba(color: Int, alpha: Int) = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    fun rounded(color: Int, radius: Float, stroke: Int? = null, strokeW: Int = 0) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        if (stroke != null) setStroke(strokeW, stroke)
    }

    private fun vertical(top: Int, bottom: Int, radius: Float, stroke: Int? = null, strokeW: Int = 0) =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)).apply {
            cornerRadius = radius
            if (stroke != null) setStroke(strokeW, stroke)
        }

    private fun pressable(normal: Drawable, pressed: Drawable) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), pressed)
        addState(intArrayOf(), normal)
    }

    /** Push in a little while pressed, with the shadow shrinking — feels like a real button. */
    @SuppressLint("ClickableViewAccessibility")
    fun pushEffect(v: View) {
        val base = v.elevation
        v.setOnTouchListener { view, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.96f).scaleY(0.96f)
                    .translationZ(-base * 0.7f).setDuration(80).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(140).start()
            }
            false
        }
    }

    /** White shell card with soft depth. */
    fun card(ctx: Context, title: String?, accent: Int = GREEN): LinearLayout {
        val c = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = LayerDrawable(
                arrayOf(
                    vertical(SHELL, SHELL_SHADE, dpf(ctx, 28f), SHELL_EDGE, 1),
                    // soft gloss on the upper part of the shell
                    GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(Color.argb(170, 255, 255, 255), Color.argb(0, 255, 255, 255))
                    ).apply { cornerRadius = dpf(ctx, 26f) }
                )
            ).apply { setLayerInset(1, dp(ctx, 2), dp(ctx, 2), dp(ctx, 2), dp(ctx, 60)) }
            elevation = dpf(ctx, 12f)
            outlineProvider = ViewOutlineProvider.BACKGROUND
            outlineSpotShadowColor = Color.BLACK
            setPadding(dp(ctx, 20), dp(ctx, 18), dp(ctx, 20), dp(ctx, 20))
        }
        if (title != null) {
            c.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(ctx, 8))
                addView(View(ctx).apply {
                    background = rounded(accent, dpf(ctx, 3f))
                }, LinearLayout.LayoutParams(dp(ctx, 5), dp(ctx, 20)).apply { marginEnd = dp(ctx, 10) })
                addView(TextView(ctx).apply {
                    text = title
                    textSize = 18f
                    typeface = BOLD
                    setTextColor(INK)
                })
            })
        }
        return c
    }

    fun text(ctx: Context, s: String, size: Float = 14f, color: Int = INK_SOFT, face: Typeface = Typeface.DEFAULT) =
        TextView(ctx).apply {
            text = s
            textSize = size
            typeface = face
            setTextColor(color)
            setLineSpacing(0f, 1.15f)
        }

    /**
     * Raised 3D button: a darker base ("thickness") under a lit face with a gloss.
     * Pressing pushes the face down onto the base. Turns green only when [done] is true.
     */
    fun bumper(ctx: Context, label: String, done: Boolean = false, onClick: () -> Unit): TextView =
        TextView(ctx).apply {
            text = if (done) "✓  $label" else label
            textSize = 15.5f
            typeface = BOLD
            gravity = Gravity.CENTER
            val lip = dp(ctx, 6)
            setPadding(dp(ctx, 18), 0, dp(ctx, 18), lip)
            isClickable = true
            isFocusable = true
            val r = dpf(ctx, 20f)
            val baseColor: Int
            val faceTop: Int
            val faceBottom: Int
            val edge: Int
            if (done) {
                baseColor = Color.parseColor("#075314"); faceTop = Color.parseColor("#4CD86A")
                faceBottom = Color.parseColor("#129330"); edge = Color.parseColor("#0B6A1A")
            } else {
                baseColor = Color.parseColor("#B4BAC1"); faceTop = Color.WHITE
                faceBottom = Color.parseColor("#E2E6EA"); edge = Color.parseColor("#CDD2D7")
            }
            val normal = LayerDrawable(
                arrayOf(
                    rounded(baseColor, r),
                    vertical(faceTop, faceBottom, r, edge, dp(ctx, 1)),
                    vertical(Color.argb(if (done) 110 else 230, 255, 255, 255), Color.argb(0, 255, 255, 255), r)
                )
            ).apply {
                setLayerInset(1, 0, 0, 0, lip)
                setLayerInset(2, dp(ctx, 3), dp(ctx, 2), dp(ctx, 3), lip + dp(ctx, 22))
            }
            val pressed = LayerDrawable(
                arrayOf(
                    rounded(baseColor, r),
                    vertical(faceBottom, faceTop, r, edge, dp(ctx, 1))
                )
            ).apply { setLayerInset(1, 0, lip - dp(ctx, 2), 0, dp(ctx, 1)) }
            background = pressable(normal, pressed)
            setTextColor(if (done) Color.WHITE else INK)
            if (done) setShadowLayer(dpf(ctx, 2f), 0f, dpf(ctx, 1f), Color.argb(110, 0, 0, 0))
            elevation = dpf(ctx, 9f)
            outlineProvider = ViewOutlineProvider.BACKGROUND
            outlineSpotShadowColor = Color.BLACK
            outlineAmbientShadowColor = Color.BLACK
            layoutParams = LinearLayout.LayoutParams(-1, dp(ctx, 60)).apply { topMargin = dp(ctx, 12) }
            setOnClickListener { onClick() }
            pushEffect(this)
        }

    /** Glossy 3D face button (A / B / X / Y) with a caption inside. */
    fun faceButton(ctx: Context, letter: String, caption: String, color: Int, deep: Int, sizeDp: Int, onClick: () -> Unit): View {
        val size = dp(ctx, sizeDp)
        fun oval() = GradientDrawable().apply { shape = GradientDrawable.OVAL }
        fun radial(cx: Float, cy: Float, r: Float, vararg c: Int) = oval().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            setGradientCenter(cx, cy)
            gradientRadius = r
            colors = c
        }

        // Recessed socket in the white shell: shaded at the top inside, lit at the bottom
        val socket = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#BFC5CC"), Color.parseColor("#DCE0E4"), Color.parseColor("#F7F8FA"))
        ).apply { shape = GradientDrawable.OVAL }
        // soft shadow the cap casts into the socket
        val castShadow = radial(0.5f, 0.56f, size * 0.5f, Color.argb(120, 0, 0, 0), Color.argb(55, 0, 0, 0), Color.argb(0, 0, 0, 0))
        // the cap's side wall (same colour, darker) — gives the button its thickness, no black ring
        val wall = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(deep, darken(deep))
        ).apply { shape = GradientDrawable.OVAL }
        // the dome, lit from the top-left
        val dome = radial(0.40f, 0.28f, size * 0.62f, lighten(color, 0.42f), color, deep)
        // glossy reflection across the top of the dome
        val gloss = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(175, 255, 255, 255), Color.argb(20, 255, 255, 255))
        ).apply { shape = GradientDrawable.OVAL }
        // faint rim light along the bottom edge
        val rimLight = radial(0.5f, 0.95f, size * 0.35f, Color.argb(70, 255, 255, 255), Color.argb(0, 255, 255, 255))

        val s4 = dp(ctx, 4)
        val cap = LayerDrawable(arrayOf(socket, castShadow, wall, dome, rimLight, gloss)).apply {
            setLayerInset(1, s4, dp(ctx, 7), s4, 0)                       // shadow, shifted down
            setLayerInset(2, dp(ctx, 7), dp(ctx, 6), dp(ctx, 7), dp(ctx, 5))   // side wall
            setLayerInset(3, dp(ctx, 7), dp(ctx, 5), dp(ctx, 7), dp(ctx, 11))  // dome sits on top of the wall
            setLayerInset(4, dp(ctx, 12), size / 2, dp(ctx, 12), dp(ctx, 12))
            setLayerInset(5, size / 4, dp(ctx, 8), size / 4, size / 2 + dp(ctx, 6))
        }
        val pressedCap = LayerDrawable(
            arrayOf(
                socket,
                radial(0.5f, 0.5f, size * 0.5f, Color.argb(70, 0, 0, 0), Color.argb(0, 0, 0, 0)),
                wall,
                radial(0.5f, 0.45f, size * 0.6f, lighten(color, 0.15f), color, deep)
            )
        ).apply {
            setLayerInset(1, s4, s4, s4, s4)
            setLayerInset(2, dp(ctx, 7), dp(ctx, 8), dp(ctx, 7), dp(ctx, 6))
            setLayerInset(3, dp(ctx, 7), dp(ctx, 8), dp(ctx, 7), dp(ctx, 8))   // pushed down: almost no wall
        }

        val btn = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = pressable(cap, pressedCap)
            isClickable = true
            elevation = 0f
            setPadding(0, 0, 0, dp(ctx, 6))
            addView(TextView(ctx).apply {
                text = letter
                textSize = 26f
                typeface = BLACK
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                // engraved look: dark shadow below, letter slightly translucent
                setShadowLayer(dpf(ctx, 1.5f), 0f, dpf(ctx, 1.5f), Color.argb(150, 0, 0, 0))
                alpha = 0.96f
                includeFontPadding = false
            })
            addView(TextView(ctx).apply {
                text = caption
                textSize = 11.5f
                typeface = BOLD
                gravity = Gravity.CENTER
                setTextColor(Color.argb(235, 255, 255, 255))
                setShadowLayer(dpf(ctx, 2f), 0f, dpf(ctx, 1f), Color.argb(150, 0, 0, 0))
                includeFontPadding = false
                setPadding(0, dp(ctx, 2), 0, 0)
            })
            setOnClickListener { onClick() }
            pushEffect(this)
        }
        return btn.also { it.layoutParams = FrameLayout.LayoutParams(size, size) }
    }

    /** The four face buttons in the classic diamond: Y top, X left, B right, A bottom. */
    fun diamond(ctx: Context, y: View, x: View, b: View, a: View, buttonDp: Int, gapDp: Int): FrameLayout {
        val s = dp(ctx, buttonDp)
        val g = dp(ctx, gapDp)
        val step = s - g
        val total = step * 2 + s
        val frame = FrameLayout(ctx).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            clipChildren = false
            clipToPadding = false
            setPadding(0, dp(ctx, 6), 0, dp(ctx, 14))
        }
        // dark recessed well behind the buttons
        frame.addView(View(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = total * 0.55f
                colors = intArrayOf(Color.parseColor("#E4E7EA"), Color.parseColor("#D5D9DD"), Color.argb(0, 213, 217, 221))
            }
        }, FrameLayout.LayoutParams(total + dp(ctx, 30), total + dp(ctx, 30), Gravity.CENTER))
        fun place(v: View, left: Int, top: Int) {
            frame.addView(v, FrameLayout.LayoutParams(s, s).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                leftMargin = left
                topMargin = top + dp(ctx, 15)
            })
        }
        place(y, 0, 0)
        place(x, -step, step)
        place(b, step, step)
        place(a, 0, step * 2)
        frame.minimumHeight = total + dp(ctx, 50)
        return frame
    }

    /** Glowing round "guide" button showing whether the virtual controller is on. */
    fun statusOrb(ctx: Context, on: Boolean, label: String, glowColor: Int? = null): View {
        val size = dp(ctx, 150)
        val glow = glowColor ?: if (on) GREEN else OFF
        val orb = FrameLayout(ctx)
        orb.addView(View(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = size * 0.5f
                colors = intArrayOf(rgba(glow, if (on) 150 else 60), rgba(glow, if (on) 70 else 25), rgba(glow, 0))
            }
        }, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        val inner = dp(ctx, 104)
        orb.addView(TextView(ctx).apply {
            text = label
            textSize = 17f
            typeface = BLACK
            gravity = Gravity.CENTER
            setTextColor(if (on) darken(glow) else INK_FAINT)
            background = LayerDrawable(
                arrayOf(
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (on) lighten(glow, 0.35f) else Color.parseColor("#C9CDD2"))
                    },
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        gradientType = GradientDrawable.RADIAL_GRADIENT
                        setGradientCenter(0.4f, 0.32f)
                        gradientRadius = inner * 0.75f
                        colors = intArrayOf(Color.WHITE, Color.parseColor("#F1F3F5"), Color.parseColor("#D3D7DC"))
                    }
                )
            ).apply { val r = dp(ctx, 5); setLayerInset(1, r, r, r, r) }
            elevation = dpf(ctx, 14f)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) = outline.setOval(0, 0, view.width, view.height)
            }
        }, FrameLayout.LayoutParams(inner, inner, Gravity.CENTER))
        orb.layoutParams = LinearLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER_HORIZONTAL }
        return orb
    }

    /** Status line with a small glowing light. */
    fun lightRow(ctx: Context, on: Boolean, label: String): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(ctx, 6), 0, dp(ctx, 6))
        val c = if (on) GREEN else RED
        addView(View(ctx).apply {
            background = LayerDrawable(
                arrayOf(
                    GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(rgba(c, 50)) },
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        gradientType = GradientDrawable.RADIAL_GRADIENT
                        setGradientCenter(0.4f, 0.35f)
                        gradientRadius = dpf(ctx, 7f)
                        colors = intArrayOf(lighten(c, 0.5f), c)
                    }
                )
            ).apply { val r = dp(ctx, 4); setLayerInset(1, r, r, r, r) }
        }, LinearLayout.LayoutParams(dp(ctx, 20), dp(ctx, 20)).apply { marginEnd = dp(ctx, 10) })
        addView(text(ctx, label, 15f, INK, MEDIUM))
    }

    fun darken(color: Int): Int = Color.rgb(
        (Color.red(color) * 0.55f).toInt(), (Color.green(color) * 0.55f).toInt(), (Color.blue(color) * 0.55f).toInt()
    )

    fun lighten(color: Int, f: Float): Int = Color.rgb(
        (Color.red(color) + (255 - Color.red(color)) * f).toInt(),
        (Color.green(color) + (255 - Color.green(color)) * f).toInt(),
        (Color.blue(color) + (255 - Color.blue(color)) * f).toInt()
    )
}
