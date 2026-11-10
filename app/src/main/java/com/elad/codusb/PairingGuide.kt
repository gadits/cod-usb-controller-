package com.elad.codusb

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Short visual guide shown before pairing: what will happen, and a picture of the exact
 * notification and button to tap, so nobody misses it.
 */
object PairingGuide {

    fun show(a: Activity, onStart: () -> Unit) {
        val d = Dialog(a)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val card = Ui.card(a, "צימוד – פעם אחת בלבד", Ui.BLUE).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        card.addView(step(a, "1", "ייפתח מסך 'ניפוי באגים אלחוטי'. אם הוא כבוי – הדלק אותו.\nאם שואלים על הרשת: סמן 'אפשר תמיד ברשת זו' ואשר."))
        card.addView(step(a, "2", "לחץ על 'צימוד מכשיר באמצעות קוד צימוד'. יופיע חלון עם 6 ספרות."))
        card.addView(step(a, "3", "למעלה תקפוץ התראה ירוקה כזו. לחץ בה על הכפתור, הקלד את 6 הספרות ושלח:"))
        card.addView(mockNotification(a), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = Ui.dp(a, 10)
        })
        card.addView(Ui.text(a, "חשוב: אל תצא ממסך ההגדרות עד שהצימוד מסתיים – יציאה ממנו מבטלת את הקוד.", 13.5f, Ui.RED, Ui.MEDIUM).apply {
            setPadding(0, Ui.dp(a, 14), 0, 0)
        })
        card.addView(Ui.bumper(a, "הבנתי – התחל", true) {
            d.dismiss()
            onStart()
        })
        card.addView(Ui.bumper(a, "ביטול") { d.dismiss() })

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
    }

    private fun step(a: Activity, n: String, text: String) = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 4))
        addView(TextView(a).apply {
            this.text = n
            textSize = 15f
            typeface = Ui.BLACK
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Ui.BLUE) }
        }, LinearLayout.LayoutParams(Ui.dp(a, 28), Ui.dp(a, 28)).apply { marginEnd = Ui.dp(a, 12) })
        addView(Ui.text(a, text, 15f, Ui.INK), LinearLayout.LayoutParams(0, -2, 1f))
    }

    /** A drawing of the real notification, with the button gently pulsing. */
    private fun mockNotification(a: Activity): View {
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 12))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#2DB84B"), Ui.GREEN_DEEP)
            ).apply { cornerRadius = Ui.dpf(a, 22f) }
            elevation = Ui.dpf(a, 6f)
        }
        box.addView(Ui.text(a, "הקלד כאן את קוד הצימוד", 18f, Color.WHITE, Ui.BOLD))
        box.addView(Ui.text(a, "את 6 הספרות שמופיעות בחלון שמאחורי ההתראה", 13.5f, Color.argb(235, 255, 255, 255)))
        val button = TextView(a).apply {
            text = "✍  הקלד קוד צימוד"
            textSize = 15f
            typeface = Ui.BOLD
            gravity = Gravity.CENTER
            setTextColor(Ui.GREEN_DEEP)
            background = Ui.rounded(Color.WHITE, Ui.dpf(a, 18f))
            setPadding(Ui.dp(a, 18), Ui.dp(a, 8), Ui.dp(a, 18), Ui.dp(a, 8))
        }
        box.addView(button, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Ui.dp(a, 12) })
        // pulse to draw the eye to the button
        listOf("scaleX", "scaleY").forEach { prop ->
            ObjectAnimator.ofFloat(button, prop, 1f, 1.08f).apply {
                duration = 650
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                start()
            }
        }
        return box
    }
}
