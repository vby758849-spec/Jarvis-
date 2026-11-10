package com.vaibhav.jarvis

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Voice: "<app> install karo" / "<app> uninstall karo".
 * Install  : opens the Play Store page and taps the green Install button (free apps only).
 * Uninstall: opens Android's uninstall dialog and taps OK (never system apps, never Jarvis).
 * The taps are done by SendAcc (accessibility), which calls AppMgr.onEvent().
 */
object AppMgr {
    private const val PLAY = "com.android.vending"
    private val main = Handler(Looper.getMainLooper())

    @Volatile var inName = ""     // app name we are installing ("" = idle)
    @Volatile var inPk = ""       // package id if we know it
    @Volatile var inAt = 0L
    @Volatile var inStage = 0     // 0 = search results, 1 = app page
    @Volatile var unPk = ""       // package we are uninstalling ("" = idle)
    @Volatile var unLabel = ""
    @Volatile var unAt = 0L
    private var lastAct = 0L

    // Popular apps: opened directly on their Play Store page (more reliable than search).
    private val KNOWN = mapOf(
        "whatsapp" to "com.whatsapp", "instagram" to "com.instagram.android", "youtube" to "com.google.android.youtube",
        "facebook" to "com.facebook.katana", "telegram" to "org.telegram.messenger", "snapchat" to "com.snapchat.android",
        "spotify" to "com.spotify.music", "netflix" to "com.netflix.mediaclient", "chrome" to "com.android.chrome",
        "gmail" to "com.google.android.gm", "google maps" to "com.google.android.apps.maps", "maps" to "com.google.android.apps.maps",
        "hotstar" to "in.startv.hotstar", "amazon" to "in.amazon.mShop.android.shopping", "flipkart" to "com.flipkart.android",
        "phonepe" to "com.phonepe.app", "google pay" to "com.google.android.apps.nbu.paisa.user", "paytm" to "net.one97.paytm",
        "twitter" to "com.twitter.android", "x" to "com.twitter.android", "sharechat" to "in.mohalla.sharechat", "moj" to "in.mohalla.video",
        "truecaller" to "com.truecaller", "zomato" to "com.application.zomato", "swiggy" to "in.swiggy.android",
        "free fire" to "com.dts.freefireth", "bgmi" to "com.pubg.imobile", "pubg" to "com.pubg.imobile", "ludo king" to "com.ludo.king",
        "mx player" to "com.mxtech.videoplayer.ad", "shareit" to "com.lenovo.anyshare.gps", "zoom" to "us.zoom.videomeetings"
    )
    private val ALIAS = mapOf(
        "यूट्यूब" to "youtube", "यू ट्यूब" to "youtube", "व्हाट्सऐप" to "whatsapp", "व्हाट्सएप" to "whatsapp", "वॉट्सऐप" to "whatsapp",
        "इंस्टाग्राम" to "instagram", "फेसबुक" to "facebook", "गूगल" to "google", "क्रोम" to "chrome", "टेलीग्राम" to "telegram",
        "जीमेल" to "gmail", "स्नैपचैट" to "snapchat", "नेटफ्लिक्स" to "netflix", "स्पॉटिफाई" to "spotify", "मैप्स" to "maps",
        "हॉटस्टार" to "hotstar", "अमेज़न" to "amazon", "फोनपे" to "phonepe", "गूगल पे" to "google pay", "ट्विटर" to "twitter",
        "शेयरचैट" to "sharechat", "मोज" to "moj", "ज़ूम" to "zoom", "जूम" to "zoom", "पेटीएम" to "paytm", "फ्लिपकार्ट" to "flipkart",
        "ज़ोमैटो" to "zomato", "स्विगी" to "swiggy", "ट्रूकॉलर" to "truecaller"
    )
    // Words that mean this is NOT an app name (so other Jarvis commands keep working).
    private val NOT_APP = Regex("jarvis|जार्विस|update|अपडेट|song|gaana|गाना|music|video|वीडियो|photo|फोटो|file|फाइल|pdf|message|मैसेज|chat|चैट|history|हिस्ट्री|contact|कॉन्टैक्ट|number|नंबर|^$")
    // Whole words only (so e.g. "सुपर" or "सेल्फी" are not damaged).
    private val FILLER = Regex("(?:^|\\s)(?:ko|app|apps|ye|yeh|is|isko|abhi|please|play ?store|se|pe|par|ek|the|mera|mere|karo|kro|kardo|kar do|को|ऐप्प|ऐप|इसको|अभी|प्ले ?स्टोर|से|पर|एक|करो|कर दो)(?=\\s|$)")
    private const val DO = "(?:karo|kro|kar do|kardo|kar de|kar|करो|कर दो|कर दे|कर|do|दो)?"
    private val UN = Regex("^(.+?)\\s*(uninstall|अनइंस्टॉल|अन इंस्टॉल|अनइन्स्टॉल|delete|डिलीट|remove|हटा(?:ओ|दो| दो)?)\\s*$DO\\s*$")
    private val IN = Regex("^(.+?)\\s*(install|इंस्टॉल|इन्स्टॉल|download|डाउनलोड)\\s*$DO\\s*$")
    private val IN2 = Regex("^(install|इंस्टॉल|download|डाउनलोड)\\s+(.+)$")
    private val STRONG_UN = Regex("uninstall|अनइंस्टॉल|अन इंस्टॉल|अनइन्स्टॉल")

    private fun log(t: String) = JarvisService.lg("App: $t")
    private fun say(t: String) { JarvisService.inst?.say(t) }
    private fun clean(s: String): String {
        val n0 = s.replace(FILLER, " ").replace(Regex("\\s+"), " ").trim()
        return ALIAS[n0] ?: n0
    }
    private fun launchers(svc: JarvisService): List<Pair<String, String>> {
        val pm = svc.packageManager
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != svc.packageName }
            .map { it.loadLabel(pm).toString().lowercase() to it.activityInfo.packageName }
    }
    private fun installed(svc: JarvisService, pk: String) = try { svc.packageManager.getPackageInfo(pk, 0); true } catch (e: Exception) { false }

    /** Called from JarvisService.handle(). Returns true if the sentence was an install/uninstall command. */
    fun voice(svc: JarvisService, s0: String): Boolean {
        val s = s0.trim().lowercase()
        UN.find(s)?.let { m ->
            val name = clean(m.groupValues[1])
            return uninstall(svc, name, STRONG_UN.containsMatchIn(m.groupValues[2]))
        }
        val name = IN.find(s)?.groupValues?.get(1) ?: IN2.find(s)?.groupValues?.get(2) ?: return false
        return install(svc, clean(name))
    }

    // ---------------------------------------------------------------- uninstall
    private fun uninstall(svc: JarvisService, n: String, strong: Boolean): Boolean {
        if (n.isEmpty() || NOT_APP.containsMatchIn(n)) return false
        val apps = launchers(svc)
        val exact = apps.filter { it.first == n }
        val part = if (exact.isEmpty()) apps.filter { it.first.contains(n) } else exact
        if (part.isEmpty()) {
            if (!strong) return false // "delete ..." might be some other command
            say("$n नाम का ऐप नहीं मिला"); return true
        }
        if (part.map { it.second }.distinct().size > 1) {
            say("$n नाम के कई ऐप हैं, पूरा नाम बोलो"); return true
        }
        val (label, pk) = part.first()
        val ai = try { svc.packageManager.getApplicationInfo(pk, 0) } catch (e: Exception) { null }
        val isSystem = ai != null && (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 && (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
        if (isSystem) { say("$label सिस्टम ऐप है, इसे हटा नहीं सकता"); return true }
        unPk = pk; unLabel = label; unAt = System.currentTimeMillis()
        log("uninstall $pk")
        say("$label हटा रहा हूँ")
        val ok = svc.go(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pk")))
        if (!ok) { unPk = ""; log("uninstall dialog khul nahi paya (REQUEST_DELETE_PACKAGES permission?)") }
        return true
    }

    // ---------------------------------------------------------------- install
    private fun install(svc: JarvisService, n: String): Boolean {
        if (n.isEmpty() || NOT_APP.containsMatchIn(n) || n.length > 40) return false
        val have = launchers(svc).firstOrNull { it.first == n }
        if (have != null) { say("$n पहले से इंस्टॉल है"); return true }
        val pk = KNOWN[n] ?: ""
        inName = n; inPk = pk; inAt = System.currentTimeMillis(); inStage = if (pk.isNotEmpty()) 1 else 0
        log("install $n ${pk.ifEmpty { "(search)" }}")
        say("$n इंस्टॉल कर रहा हूँ")
        val uri = if (pk.isNotEmpty()) Uri.parse("market://details?id=$pk") else Uri.parse("market://search?q=" + Uri.encode(n) + "&c=apps")
        svc.go(Intent(Intent.ACTION_VIEW, uri).setPackage(PLAY))
        return true
    }

    // ---------------------------------------------------------------- accessibility side
    private fun up(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var p = n
        while (p != null && !p.isClickable) p = p.parent
        return p
    }
    private fun dump(n: AccessibilityNodeInfo?, sb: StringBuilder, d: Int) {
        if (n == null || d > 25 || sb.length > 1500) return
        val s = (n.text?.toString() ?: "") + " " + (n.contentDescription?.toString() ?: "")
        if (s.isNotBlank()) sb.append(s.lowercase()).append(' ')
        for (i in 0 until n.childCount) dump(n.getChild(i), sb, d + 1)
    }
    private fun txt(n: AccessibilityNodeInfo) = ((n.text?.toString() ?: "") + " " + (n.contentDescription?.toString() ?: "")).trim().lowercase()

    /** Find the first node (top to bottom) whose text/description satisfies [ok]. */
    private fun find(n: AccessibilityNodeInfo?, d: Int, ok: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (n == null || d > 25) return null
        if (ok(n)) return n
        for (i in 0 until n.childCount) { val r = find(n.getChild(i), d + 1, ok); if (r != null) return r }
        return null
    }

    /** Returns true while an install/uninstall job is using this window (so other handlers stay out of the way). */
    fun onEvent(acc: android.accessibilityservice.AccessibilityService, root: AccessibilityNodeInfo, pkg: String, now: Long): Boolean {
        if (unPk.isNotEmpty()) {
            if (now - unAt > 30000) { unPk = ""; log("uninstall timeout") }
            else if (pkg.contains("packageinstaller") || pkg == "android" || pkg.contains("permissioncontroller")) { uninstallClick(acc, root, now); return true }
        }
        if (inName.isNotEmpty()) {
            if (now - inAt > 45000) { log("install timeout (stage $inStage)"); inName = ""; return false }
            if (pkg == PLAY) { installClick(acc, root, now); return true }
        }
        return false
    }

    private fun uninstallClick(acc: android.accessibilityservice.AccessibilityService, root: AccessibilityNodeInfo, now: Long) {
        if (now - lastAct < 600) return
        lastAct = now
        // Safety: only press OK if this dialog really names the app we meant to remove.
        val sb = StringBuilder(); dump(root, sb, 0)
        val all = sb.toString()
        if (!all.contains(unLabel) && !all.contains(unPk)) { log("dialog me app ka naam nahi, click nahi kiya"); return }
        val btn = root.findAccessibilityNodeInfosByViewId("android:id/button1").firstOrNull()
            ?: find(root, 0) { n -> n.isClickable && Regex("^(ok|uninstall|ठीक है|अनइंस्टॉल करें|अनइंस्टॉल|हटाएं|हटाएँ)$", RegexOption.IGNORE_CASE).matches(txt(n)) }
        val b = up(btn) ?: return
        val pk = unPk; val label = unLabel
        b.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        log("uninstall OK dabaya: $pk")
        unPk = ""
        main.postDelayed({
            if (installed(JarvisService.inst ?: return@postDelayed, pk)) say("$label नहीं हट पाया") else say("$label हटा दिया")
        }, 3500)
    }

    private fun installClick(acc: android.accessibilityservice.AccessibilityService, root: AccessibilityNodeInfo, now: Long) {
        if (now - lastAct < 900) return
        lastAct = now
        val n = inName
        if (inStage == 0) {
            // Search results: tap the first result that mentions the app name (skip the search bar at the top).
            val h = acc.resources.displayMetrics.heightPixels
            val hit = find(root, 0) { x ->
                val r = Rect(); x.getBoundsInScreen(r)
                x.className?.toString() != "android.widget.EditText" && !x.isEditable && r.top > h * 0.15 && txt(x).contains(n) && up(x) != null
            }
            val c = up(hit)
            if (c != null) { c.performAction(AccessibilityNodeInfo.ACTION_CLICK); inStage = 1; log("search: pehla result dabaya"); }
            else log("search: result nahi mila")
            return
        }
        // App page
        val btn = find(root, 0) { x -> Regex("^(install|इंस्टॉल करें|इंस्टॉल|इन्स्टॉल करें)$", RegexOption.IGNORE_CASE).matches(txt(x)) && up(x) != null }
        if (btn != null) {
            up(btn)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            log("Install dabaya: $n")
            finishInstall(n, inPk)
            return
        }
        val open = find(root, 0) { x -> Regex("^(open|play|खोलें|खोलिए)$", RegexOption.IGNORE_CASE).matches(txt(x)) }
        if (open != null) { say("$n पहले से इंस्टॉल है"); inName = ""; return }
        val paid = find(root, 0) { x -> x.isClickable && Regex("^[₹$€£]\\s?\\d").containsMatchIn(txt(x)) }
        if (paid != null) { say("$n पेड ऐप है, मैं इसे नहीं खरीदूँगा"); inName = ""; log("paid app, skip"); return }
        val upd = find(root, 0) { x -> Regex("^(update|अपडेट करें|अपडेट)$", RegexOption.IGNORE_CASE).matches(txt(x)) }
        if (upd != null) { say("$n पहले से इंस्टॉल है"); inName = ""; return }
    }

    private fun finishInstall(n: String, pk0: String) {
        inName = ""
        say("$n इंस्टॉल हो रहा है")
        // Tell the user when it is done (check every 5s for up to 4 minutes).
        val svc = JarvisService.inst ?: return
        val label = n
        var tries = 0
        val chk = object : Runnable {
            override fun run() {
                tries++
                val done = if (pk0.isNotEmpty()) installed(svc, pk0) else launchers(svc).any { it.first.contains(label) }
                if (done) { say("$label इंस्टॉल हो गया"); log("install done: $label") }
                else if (tries < 48) main.postDelayed(this, 5000)
            }
        }
        main.postDelayed(chk, 6000)
    }
}
