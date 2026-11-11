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

        val card = Ui.card(a, L.t("צימוד ראשוני", "First-time pairing"), Ui.BLUE).apply {
            layoutDirection = L.dir
        }
        card.addView(Ui.text(
            a,
            L.t("כדי שהאפליקציה תוכל להפעיל את השלט, יש לבצע צימוד חד-פעמי עם ניפוי הבאגים האלחוטי של המכשיר. התהליך אורך פחות מדקה.", "To let the app start the controller, pair it once with the device's wireless debugging. It takes less than a minute."),
            15f, Ui.INK_SOFT
        ).apply { setPadding(0, 0, 0, Ui.dp(a, 6)) })
        card.addView(step(a, "1", L.t("במסך שייפתח, ודא ש'ניפוי באגים אלחוטי' מופעל. אם תתבקש לאשר את הרשת – סמן 'אפשר תמיד ברשת זו' ואשר.", "On the screen that opens, make sure 'Wireless debugging' is on. If asked about the network – tick 'Always allow on this network' and confirm.")))
        card.addView(step(a, "2", L.t("בחר 'צימוד מכשיר באמצעות קוד צימוד'. יוצג חלון ובו קוד בן 6 ספרות.", "Choose 'Pair device with pairing code'. A window with a 6-digit code appears.")))
        card.addView(step(a, "3", L.t("בראש המסך תופיע התראה כמו זו. לחץ על 'הקלד קוד צימוד', הזן את הקוד ושלח:", "Type the code in the app's box (or in a notification like this one, at the top of the screen):")))
        card.addView(mockNotification(a), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = Ui.dp(a, 10)
        })
        card.addView(Ui.text(a, L.t("יש להישאר במסך ההגדרות עד לסיום הצימוד – יציאה ממנו מבטלת את הקוד.", "Stay on the Settings screen until pairing finishes – leaving it cancels the code."), 13.5f, Ui.RED, Ui.MEDIUM).apply {
            setPadding(0, Ui.dp(a, 14), 0, 0)
        })
        card.addView(Pad.row(a, L.t("לא עכשיו", "Not now"), { d.dismiss() }, L.t("התחל צימוד", "Start pairing"), {
            d.dismiss()
            onStart()
        }))

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
        box.addView(Ui.text(a, L.t("הקלד כאן את קוד הצימוד", "Type the pairing code here"), 18f, Color.WHITE, Ui.BOLD))
        box.addView(Ui.text(a, L.t("הזן את הקוד בן 6 הספרות המוצג בחלון הצימוד", "Enter the 6-digit code shown in the pairing window"), 13.5f, Color.argb(235, 255, 255, 255)))
        val button = TextView(a).apply {
            text = L.t("✍  הקלד קוד צימוד", "✍  Type pairing code")
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
