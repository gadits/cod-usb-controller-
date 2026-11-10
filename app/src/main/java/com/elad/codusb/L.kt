package com.elad.codusb

import android.content.Context
import android.view.View

/** Tiny two-language helper: every UI string is written as L.t(hebrew, english). */
object L {
    @Volatile private var en: Boolean? = null
    private var appCtx: Context? = null

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        en = prefs(ctx).getString("lang", null)?.let { it == "en" }
            ?: !java.util.Locale.getDefault().language.let { it == "iw" || it == "he" }
    }

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("lang", Context.MODE_PRIVATE)

    val isEn: Boolean get() = en ?: false

    fun set(ctx: Context, english: Boolean) {
        en = english
        prefs(ctx).edit().putString("lang", if (english) "en" else "he").apply()
    }

    fun t(he: String, en: String): String = if (isEn) en else he

    val dir: Int get() = if (isEn) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
}
