package com.elad.codusb

import android.content.Context

/** "Auto COD USB": pairs by itself (accessibility helper) and keeps everything running hands-free. */
object Flavor {
    const val AUTO = true
    const val TITLE = "AUTO COD USB"
    fun a11yEnabled(ctx: Context) = PairingAccessibility.isEnabled(ctx)
}
