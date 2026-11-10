package com.elad.codusb

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Reads the pairing code for the user, so nothing has to be typed.
 *
 * Works only inside the Settings app (see res/xml/pairing_accessibility.xml) and only while a
 * pairing is in progress. While pairing it:
 *   1. opens "Wireless debugging" in Developer options,
 *   2. opens "Pair device with pairing code",
 *   3. reads the 6-digit code and the port from the dialog and hands them to [PairingService].
 * It never taps system security prompts (like "Allow wireless debugging on this network?").
 */
class PairingAccessibility : AccessibilityService() {

    private var lastCode: String? = null
    private var lastWirelessClick = 0L
    private var lastPairClick = 0L
    private var lastToggleClick = 0L
    private var lastScroll = 0L
    private var scrolls = 0

    private val handler = Handler(Looper.getMainLooper())

    /** Also act on a timer: if the screen stops changing there are no events to react to. */
    private val tick = object : Runnable {
        override fun run() {
            if (PairingService.running) {
                try {
                    step()
                } catch (t: Throwable) {
                    Log.w(TAG, "step failed", t)
                }
            }
            handler.postDelayed(this, 700)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "connected")
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            step()
        } catch (t: Throwable) {
            Log.w(TAG, "step failed", t)
        }
    }

    private fun step() {
        if (!PairingService.running) {
            lastCode = null
            scrolls = 0
            return
        }
        val roots = try {
            (windows.mapNotNull { it.root } + listOfNotNull(rootInActiveWindow))
                .filter { it.packageName?.toString() == SETTINGS }
        } catch (_: Throwable) {
            listOfNotNull(rootInActiveWindow)
        }
        if (roots.isEmpty()) return

        // 1. the pairing dialog: 6-digit code + "IP:port"
        for (root in roots) {
            val texts = ArrayList<String>()
            collectTexts(root, texts, 0)
            val code = texts.map { it.replace(" ", "").trim() }.firstOrNull { CODE.matches(it) }
            val port = texts.firstNotNullOfOrNull { ADDRESS.find(it)?.groupValues?.get(1)?.toIntOrNull() }
            if (code != null && port != null) {
                if (code != lastCode) {
                    lastCode = code
                    Log.i(TAG, "code read, port=$port")
                    Starter.log(this, "הקוד נקרא אוטומטית מחלון הצימוד")
                    try {
                        startService(
                            Intent(this, PairingService::class.java)
                                .setAction(PairingService.ACTION_AUTO)
                                .putExtra(PairingService.EXTRA_CODE, code)
                                .putExtra(PairingService.EXTRA_PORT, port)
                        )
                    } catch (t: Throwable) {
                        Starter.log(this, "העברת הקוד נכשלה: ${t.message}")
                    }
                }
                return
            }
        }

        val now = System.currentTimeMillis()
        // 2. on the Wireless debugging screen: open "Pair device with pairing code"
        for (root in roots) {
            val pair = findByText(root, PAIR_WORDS)
            if (pair != null) {
                if (now - lastPairClick > 4_000 && clickRow(pair)) {
                    lastPairClick = now
                    Log.i(TAG, "clicked pair-with-code")
                }
                return
            }
        }
        // 2b. Wireless debugging screen but switched off: turn the main switch on
        //     (the system then asks to allow this network — that tap stays with the user)
        for (root in roots) {
            if (findByText(root, WIRELESS_SCREEN_SWITCH) != null) {
                val sw = findUncheckedSwitch(root)
                if (sw != null && now - lastToggleClick > 6_000 && clickRow(sw)) {
                    lastToggleClick = now
                    Log.i(TAG, "turned wireless debugging on")
                }
                return
            }
        }
        // 3. in Developer options: open "Wireless debugging"
        for (root in roots) {
            val wireless = findByText(root, WIRELESS_WORDS)
            if (wireless != null) {
                if (now - lastWirelessClick > 4_000 && clickRow(wireless)) {
                    lastWirelessClick = now
                    Log.i(TAG, "clicked wireless debugging")
                }
                return
            }
        }
        // 4. not visible yet: scroll the settings list (not the top bar) down to it
        if (now - lastScroll > 600 && scrolls < 40) {
            val lists = ArrayList<AccessibilityNodeInfo>()
            for (root in roots) collectScrollables(root, lists, 0)
            val ordered = lists.sortedWith(
                compareByDescending<AccessibilityNodeInfo> {
                    val c = it.className?.toString() ?: ""
                    c.contains("RecyclerView") || c.contains("ListView")
                }.thenByDescending { area(it) }
            )
            for (list in ordered) {
                if (list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                    scrolls++
                    lastScroll = now
                    Log.i(TAG, "scrolled ${list.className} ($scrolls)")
                    return
                }
            }
            lastScroll = now
        }
    }

    private fun area(n: AccessibilityNodeInfo): Int {
        val r = Rect()
        n.getBoundsInScreen(r)
        return r.width() * r.height()
    }

    private fun collectScrollables(n: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>, depth: Int) {
        if (n == null || depth > 40) return
        if (n.isScrollable) out.add(n)
        for (i in 0 until n.childCount) collectScrollables(n.getChild(i), out, depth + 1)
    }

    private fun findScrollable(n: AccessibilityNodeInfo?, depth: Int = 0): AccessibilityNodeInfo? {
        if (n == null || depth > 40) return null
        if (n.isScrollable) return n
        for (i in 0 until n.childCount) findScrollable(n.getChild(i), depth + 1)?.let { return it }
        return null
    }

    override fun onInterrupt() {}

    // ---------- node helpers ----------

    private fun collectTexts(n: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        if (n == null || depth > 40) return
        n.text?.toString()?.let { if (it.isNotBlank()) out.add(it) }
        for (i in 0 until n.childCount) collectTexts(n.getChild(i), out, depth + 1)
    }

    private fun findByText(n: AccessibilityNodeInfo?, words: List<String>, depth: Int = 0): AccessibilityNodeInfo? {
        if (n == null || depth > 40) return null
        val t = n.text?.toString()?.trim()
        if (t != null && words.any { t.equals(it, ignoreCase = true) }) return n
        for (i in 0 until n.childCount) findByText(n.getChild(i), words, depth + 1)?.let { return it }
        return null
    }

    private fun findUncheckedSwitch(n: AccessibilityNodeInfo?, depth: Int = 0): AccessibilityNodeInfo? {
        if (n == null || depth > 40) return null
        if (n.isCheckable && !n.isChecked && n.className?.toString()?.contains("Switch") == true) return n
        for (i in 0 until n.childCount) findUncheckedSwitch(n.getChild(i), depth + 1)?.let { return it }
        return null
    }

    /** Click the node or its nearest clickable parent (the whole settings row). */
    private fun clickRow(n: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = n
        var depth = 0
        while (cur != null && depth < 8) {
            if (cur.isClickable) return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            cur = cur.parent
            depth++
        }
        return false
    }

    companion object {
        private const val TAG = "CodUsbA11y"
        private const val SETTINGS = "com.android.settings"
        private val CODE = Regex("^\\d{6}$")
        private val ADDRESS = Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:(\\d{2,5})")

        private val WIRELESS_WORDS = listOf(
            "Wireless debugging", "ניפוי באגים אלחוטי", "ניפוי באגים באופן אלחוטי",
            "ניפוי באגים באלחוט", "ניפוי באגים אלחוטי (Wi-Fi)"
        )
        private val WIRELESS_SCREEN_SWITCH = listOf(
            "Use wireless debugging", "שימוש בניפוי באגים אלחוטי", "השתמש בניפוי באגים אלחוטי"
        )
        private val PAIR_WORDS = listOf(
            "Pair device with pairing code", "צימוד מכשיר באמצעות קוד צימוד",
            "התאמת מכשיר באמצעות קוד התאמה", "צימוד מכשיר עם קוד צימוד", "התאמת מכשיר עם קוד התאמה"
        )

        fun isEnabled(ctx: Context): Boolean {
            val me = ComponentName(ctx, PairingAccessibility::class.java)
            val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            return list.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
