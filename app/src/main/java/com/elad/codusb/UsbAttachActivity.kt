package com.elad.codusb

import android.app.Activity
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Bundle

/**
 * Android opens this invisible screen the moment a USB controller is plugged in — even when the app
 * is closed. It starts the virtual controller in the background and closes right away.
 */
class UsbAttachActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
                val name = try {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<android.hardware.usb.UsbDevice>(UsbManager.EXTRA_DEVICE)?.productName
                } catch (_: Throwable) { null } ?: "USB"
                when {
                    !Starter.isPaired(this) -> Unit
                    Starter.isVirtualPresent(this) ->
                        Starter.log(this, L.t("שלט חובר ($name) – השלט הווירטואלי כבר פעיל", "Controller plugged in ($name) – the virtual controller is already on"))
                    else -> {
                        Starter.log(this, L.t("שלט חובר ($name) – מפעיל את השלט הווירטואלי", "Controller plugged in ($name) – starting the virtual controller"))
                        startForegroundService(Intent(this, StartService::class.java))
                    }
                }
            }
        } catch (t: Throwable) {
            Starter.log(this, L.t("הפעלה בחיבור שלט נכשלה: ${t.message}", "Start on plug-in failed: ${t.message}"))
        }
        finish()
        overridePendingTransition(0, 0)
    }
}
