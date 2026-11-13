package com.elad.codusb

import android.content.Context

/** The regular app: pairing code is typed by the user. */
object Flavor {
    const val AUTO = false
    const val TITLE = "COD USB"
    fun a11yEnabled(ctx: Context) = false
}
