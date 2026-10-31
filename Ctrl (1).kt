package com.vaibhav.jarvis

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/** Screen brightness, up / down / max / min / 0-100 (needs the "modify system settings" permission once). */
fun JarvisService.brightness(q: String) {
    if (!Settings.System.canWrite(this)) {
        say(if (isEn()) "I need permission to change brightness. Please allow it." else "ब्राइटनेस बदलने की अनुमति दो")
        go(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + packageName)))
        return
    }
    try {
        val cr = contentResolver
        val cur = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
        val num = Regex("\\d{1,3}").find(q)?.value?.toIntOrNull()
        val v = when {
            num != null -> 255 * num.coerceIn(1, 100) / 100
            Regex("sabse kam|सबसे कम|min").containsMatchIn(q) -> 8
            Regex("max|full|फुल|पूरा|sabse zyada|सबसे ज्यादा").containsMatchIn(q) -> 255
            Regex("down|kam|कम|घटा|ghata|dim|low|halka|हल्का").containsMatchIn(q) -> cur - 64
            else -> cur + 64
        }.coerceIn(5, 255)
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, v)
        val pc = v * 100 / 255
        say(if (isEn()) "Brightness set to $pc percent" else "ब्राइटनेस $pc प्रतिशत कर दी")
    } catch (e: Exception) {
        JarvisService.lg("brightness: " + e)
        say(if (isEn()) "I could not change the brightness" else "ये नहीं हो पाया")
    }
}

/** Quick-settings tile on/off without opening Settings. kind: bt, hs, wifi, data, bs (battery saver), dnd, rec (screen recorder) */
fun JarvisService.qsToggle(kind: String, on: Boolean) {
    val acc = SendAcc.inst
    if (acc == null) {
        say(if (isEn()) "Please turn on Accessibility for Jarvis." else "Jarvis की Accessibility चालू करो")
        if (kind == "bt") bluetooth(on)
        return
    }
    JarvisService.qsKind = kind; JarvisService.qsWant = on; JarvisService.qsTries = 0; JarvisService.qsStage = 0
    JarvisService.qsAt = System.currentTimeMillis()
    val nm = when (kind) {
        "bt" -> "Bluetooth"; "hs" -> "hotspot"; "wifi" -> "Wi-Fi"; "data" -> "mobile data"
        "bs" -> "battery saver"; "dnd" -> "do not disturb"; else -> "screen recorder"
    }
    val onT = if (kind == "rec") "" else if (on) " on" else " off"
    JarvisService.qsDone = if (isEn()) (if (kind == "rec") "Screen recorder started" else nm.replaceFirstChar { it.uppercase() } + " turned" + onT)
        else nm + (if (kind == "rec") " शुरू कर दिया" else if (on) " चालू कर दिया" else " बंद कर दिया")
    Curtain.show(acc, if (isEn()) "Turning " + nm + (if (kind == "rec") " on" else onT) + "\u2026" else nm + (if (kind == "rec") " शुरू हो रहा है" else if (on) " चालू हो रहा है" else " बंद हो रहा है") + "\u2026")
    Handler(Looper.getMainLooper()).postDelayed({ Curtain.hide() }, 8000)
    acc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
    Handler(Looper.getMainLooper()).postDelayed({
        if (JarvisService.qsAt != 0L) {
            JarvisService.qsAt = 0L; JarvisService.qsDone = ""
            acc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            Curtain.hideSoon()
            JarvisService.lg("qs: tile nahi mila (" + kind + ")")
            when (kind) {
                "bt" -> bluetooth(on)
                "hs" -> { say(if (isEn()) "I could not find that tile. Opening settings." else "बटन नहीं मिला, सेटिंग खोल रहा हूँ"); go(Intent("android.settings.TETHER_SETTINGS")) }
                else -> say(if (isEn()) "That button is not in your quick settings panel. Add it there first." else "वो बटन तुम्हारे क्विक सेटिंग पैनल में नहीं है, पहले उसे जोड़ो")
            }
        }
    }, 7000)
}

/** Screenshot via accessibility */
fun JarvisService.screenshot() {
    val acc = SendAcc.inst
    if (acc == null) { say(if (isEn()) "Please turn on Accessibility for Jarvis." else "Jarvis की Accessibility चालू करो"); return }
    if (android.os.Build.VERSION.SDK_INT < 28) { say(if (isEn()) "Screenshot needs Android 9 or newer" else "स्क्रीनशॉट के लिए Android 9 चाहिए"); return }
    Handler(Looper.getMainLooper()).postDelayed({ acc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) }, 700)
}
