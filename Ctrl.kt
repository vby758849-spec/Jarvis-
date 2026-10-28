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

/** Bluetooth ("bt") or hotspot ("hs") on/off by tapping the quick-settings tile, without opening the Settings app. */
fun JarvisService.qsToggle(kind: String, on: Boolean) {
    val acc = SendAcc.inst
    if (acc == null) {
        say(if (isEn()) "Please turn on Accessibility for Jarvis." else "Jarvis की Accessibility चालू करो")
        if (kind == "bt") bluetooth(on)
        return
    }
    JarvisService.qsKind = kind; JarvisService.qsWant = on; JarvisService.qsTries = 0
    JarvisService.qsAt = System.currentTimeMillis()
    val nm = if (kind == "bt") (if (isEn()) "Bluetooth" else "ब्लूटूथ") else (if (isEn()) "hotspot" else "हॉटस्पॉट")
    say(if (isEn()) "Turning " + nm + (if (on) " on" else " off") else nm + (if (on) " चालू कर रहा हूँ" else " बंद कर रहा हूँ"))
    acc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
    Handler(Looper.getMainLooper()).postDelayed({
        if (JarvisService.qsAt != 0L) {
            JarvisService.qsAt = 0L
            acc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            JarvisService.lg("qs: tile nahi mila (" + kind + ")")
            if (kind == "bt") bluetooth(on)
            else {
                say(if (isEn()) "I could not find the hotspot tile. Opening hotspot settings." else "हॉटस्पॉट का बटन नहीं मिला, सेटिंग खोल रहा हूँ")
                go(Intent("android.settings.TETHER_SETTINGS"))
            }
        }
    }, 7000)
}
