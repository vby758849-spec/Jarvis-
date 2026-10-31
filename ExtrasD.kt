package com.vaibhav.jarvis
import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
object Gemini {
fun warm(key: String) { Thread { list(key) }.start() }
@Volatile var lastErr = ""
private val FAST = listOf("gemini-2.5-flash-lite", "gemini-2.5-flash")
private val PLAIN = listOf("gemini-flash-lite-latest", "gemini-flash-latest")

/** One request. Returns the text, or null (error text is added to errs). */
private fun post(key: String, md: String, body: String, errs: java.util.concurrent.ConcurrentLinkedQueue<String>): String? {
try {
val con = URL("https://generativelanguage.googleapis.com/v1beta/models/$md:generateContent").openConnection() as HttpURLConnection
con.requestMethod = "POST"; con.connectTimeout = 4000; con.readTimeout = 11000; con.doOutput = true
con.setRequestProperty("Content-Type", "application/json"); con.setRequestProperty("x-goog-api-key", key)
con.outputStream.use { it.write(body.toByteArray()) }
if (con.responseCode == 200) {
val ps = JSONObject(con.inputStream.bufferedReader().readText()).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
val sb = StringBuilder()
for (i in 0 until ps.length()) sb.append(ps.getJSONObject(i).optString("text"))
if (sb.isNotBlank()) return sb.toString().trim()
errs.add(md + " empty")
} else errs.add(md + " " + con.responseCode + " " + (try { con.errorStream?.bufferedReader()?.readText()?.replace(Regex("\\s+"), " ")?.take(70) } catch (x: Exception) { "" } ?: ""))
} catch (e: Exception) { errs.add(md + " " + e.toString().take(60)) }
return null
}

/** Sends the same request to several models at once and returns whichever answers first. */
private fun race(key: String, mds: List<String>, body: String): String? {
val errs = java.util.concurrent.ConcurrentLinkedQueue<String>()
val q = java.util.concurrent.LinkedBlockingQueue<String>()
for (md in mds) Thread { q.put(post(key, md, body, errs) ?: "") }.start()
var got = 0
while (got < mds.size) {
val r = q.poll(13, java.util.concurrent.TimeUnit.SECONDS) ?: break
got++
if (r.isNotEmpty()) return r
}
lastErr = errs.joinToString(" | ")
return null
}

private fun build(sys: String, hist: List<Pair<String, String>>, q: String, tokens: Int, temp: Double, json: Boolean, search: Boolean, fast: Boolean): String {
val c = JSONArray()
for ((u, a) in hist) { c.put(m("user", u)); c.put(m("model", a)) }
c.put(m("user", q))
val gc = JSONObject().put("maxOutputTokens", tokens).put("temperature", temp)
if (json) gc.put("responseMimeType", "application/json")
if (fast) gc.put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
val b = JSONObject().put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
.put("contents", c).put("generationConfig", gc)
if (search) b.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
return b.toString()
}

fun info(key: String, sys: String, hist: List<Pair<String, String>>, q: String): String? {
lastErr = ""
race(key, FAST, build(sys, hist, q, 600, 0.4, false, true, true))?.let { return it }
val e1 = lastErr
race(key, PLAIN, build(sys, hist, q, 600, 0.4, false, false, false))?.let { return it }
lastErr = e1 + " || " + lastErr
return null
}
private var models: List<String>? = null
private fun m(r: String, t: String) = JSONObject().put("role", r).put("parts", JSONArray().put(JSONObject().put("text", t)))
private fun list(key: String): List<String> {
models?.let { return it }
val out = ArrayList<String>()
try {
val c = URL("https://generativelanguage.googleapis.com/v1beta/models?pageSize=100").openConnection() as HttpURLConnection
c.setRequestProperty("x-goog-api-key", key); c.connectTimeout = 15000; c.readTimeout = 15000
if (c.responseCode == 200) {
val arr = JSONObject(c.inputStream.bufferedReader().readText()).getJSONArray("models")
for (i in 0 until arr.length()) {
val o = arr.getJSONObject(i)
val n = o.getString("name").removePrefix("models/")
val ok = o.optJSONArray("supportedGenerationMethods")?.toString()?.contains("generateContent") == true
if (ok && n.contains("flash") && !Regex("image|tts|live|audio|thinking|exp|preview|8b|vision|robotics|computer|gemma").containsMatchIn(n)) out.add(n)
}
}
} catch (e: Exception) { }
val r = out.sortedWith(compareByDescending<String> { it.contains("lite") }.thenByDescending { it })
if (r.isNotEmpty()) models = r
return r
}
fun call(key: String, sys: String, hist: List<Pair<String, String>>, q: String): String? {
lastErr = ""
race(key, FAST, build(sys, hist, q, 500, 0.6, true, false, true))?.let { return it }
val e1 = lastErr
race(key, PLAIN, build(sys, hist, q, 500, 0.6, true, false, false))?.let { return it }
lastErr = e1 + " || " + lastErr
return null
}
}
class MsgListener : NotificationListenerService() {
companion object { @Volatile var inst: MsgListener? = null }
override fun onListenerConnected() { inst = this }
override fun onListenerDisconnected() { inst = null }
fun callConnected(): Boolean {
for (sbn in activeNotifications ?: return false) {
if (sbn.packageName == packageName) continue
val n = sbn.notification
if (n.category != Notification.CATEGORY_CALL) continue
val acts = n.actions ?: continue
val ans = acts.any { Regex("answer|accept|pick|उठा|जवाब|रिसीव|उत्तर").containsMatchIn(it.title?.toString()?.lowercase() ?: "") }
if (!ans) return true
}
return false
}
fun callAct(accept: Boolean): Boolean {
for (sbn in activeNotifications ?: return false) {
if (sbn.packageName == packageName) continue
val n = sbn.notification
val acts = n.actions ?: continue
val pk = sbn.packageName
val callish = n.category == Notification.CATEGORY_CALL || pk.contains("dialer") || pk.contains("incallui") || pk.contains("telecom") || pk.startsWith("com.whatsapp") || pk.contains("phone")
if (!callish) continue
if (n.category != Notification.CATEGORY_CALL && acts.size < 2 && !pk.contains("dialer") && !pk.contains("phone")) continue
for (a in acts) {
val t = a.title?.toString()?.lowercase() ?: ""
val ok = if (accept) Regex("answer|accept|pick|उठा|जवाब|रिसीव|उत्तर").containsMatchIn(t) else Regex("decline|reject|end|hang|dismiss|काट|अस्वीकार|मना|समाप्त").containsMatchIn(t)
if (ok) {
try { a.actionIntent.send(); JarvisService.lg("Call: " + pk + " " + t); return true } catch (e: Exception) { JarvisService.lg("Call fail: " + e) }
}
}
}
return false
}
private val chat = java.util.concurrent.ConcurrentHashMap<String, ArrayList<Pair<String, String>>>()
private val lastIn = HashMap<String, String>()
private val lastAt = HashMap<String, Long>()
private val cnt = HashMap<String, Int>()
private var cntStart = 0L
private val muted = HashMap<String, Long>()
private val abuseN = HashMap<String, Int>()
override fun onNotificationPosted(sbn: StatusBarNotification) {
val n = sbn.notification
val pk = sbn.packageName
if (pk == packageName) return
if (n.category == Notification.CATEGORY_CALL && !JarvisService.callOn && getSharedPreferences("j", MODE_PRIVATE).getBoolean("autocall", false)) {
val canAns = n.actions?.any { Regex("answer|accept|pick|उठा|जवाब|रिसीव|उत्तर").containsMatchIn(it.title?.toString()?.lowercase() ?: "") } == true
if (canAns) {
val ex = listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT, Notification.EXTRA_BIG_TEXT).joinToString(" ") { n.extras.getCharSequence(it)?.toString() ?: "" }
JarvisService.inst?.callIncoming(n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Unknown", ex); return
}
}
if (pk.startsWith("com.whatsapp")) JarvisService.lg("WA aaya f=" + n.flags + " cat=" + n.category)
if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
if ((n.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
val known = pk in setOf("com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "com.google.android.apps.messaging", "com.android.mms", "com.samsung.android.messaging", "com.instagram.android", "com.facebook.orca", "com.google.android.gm")
if (!known && n.category != Notification.CATEGORY_MESSAGE && n.category != Notification.CATEGORY_EMAIL) return
val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
var text = (n.extras.getCharSequence(Notification.EXTRA_TEXT) ?: "").toString().take(200)
var who = title
var group = ""
val lb = n.extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.lastOrNull() as? android.os.Bundle
if (lb != null) {
val sd = lb.getCharSequence("sender")?.toString()
val tx = lb.getCharSequence("text")?.toString()
if (!tx.isNullOrEmpty()) text = tx.take(200)
if (!sd.isNullOrEmpty() && sd != title && !pk.startsWith("com.instagram")) { who = sd; group = title }
}
if ((n.extras.getBoolean("android.isGroupConversation") || (pk.startsWith("com.whatsapp") && !n.extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE).isNullOrEmpty())) && group.isEmpty()) group = title
val app = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pk, 0)).toString() } catch (e: Exception) { pk }
JarvisService.inst?.announce(who, app, text, group)
if (pk.startsWith("com.whatsapp") || pk == "com.instagram.android") { JarvisService.lg("DM: " + who + (if (group.isEmpty()) "" else " (group: " + group + ")")); autoReply(sbn, who, text, group) }
}
private fun autoReply(sbn: StatusBarNotification, who: String, text: String, group: String) {
val sp = getSharedPreferences("j", MODE_PRIVATE)
val own = sp.getString("owner", "")?.trim().orEmpty().ifEmpty { "Malik" }
fun sk(r: String) { JarvisService.lg("auto-reply skip: " + r) }
if (!sp.getBoolean("ar", false)) { sk("band hai"); return }
val biz = sp.getString("arm", "p") == "b"
val bn = sp.getString("bn", "")?.trim().orEmpty().ifEmpty { "our business" }
if (group.isNotEmpty()) { sk("group"); return }
if (text.isBlank()) { sk("khali"); return }
if (text.startsWith("(Jarvis") || text.startsWith("(" + bn + " Assistant)")) return
if (!biz && who.firstOrNull()?.let { it == '+' || it.isDigit() } == true) { sk("number"); return }
val k = sp.getString("key", "")?.trim().orEmpty()
if (k.isEmpty()) {
val id0 = sbn.key ?: who
val n0 = System.currentTimeMillis()
if (n0 - (lastAt[id0] ?: 0L) < 3600000) return
lastAt[id0] = n0
val fb = if (biz) "(" + bn + " Assistant) Hello! Thank you for contacting " + bn + ". We have received your message and will get back to you shortly." else "(Jarvis, " + own + " ka assistant) " + own + " abhi busy hain. Aapka message unhe mil gaya hai, wo jaldi jawab denge."
if (send(sbn, fb)) JarvisService.lg("auto-reply (bina key) -> " + who)
return
}
val id = sbn.key ?: who
val now = System.currentTimeMillis()
if (now < (muted[id] ?: 0L)) return
if (lastIn[id] == text && now - (lastAt[id] ?: 0L) < 15000) return
if (now - (lastAt[id] ?: 0L) < 4000) { sk("jaldi"); return }
if (now - cntStart > 3600000) { cntStart = now; cnt.clear() }
val c = cnt[id] ?: 0
if (c >= 12) { sk("is chat ki ghante ki limit"); return }
if (cnt.values.sum() >= (if (biz) 150 else 40)) { sk("kul limit"); return }
cnt[id] = c + 1; lastIn[id] = text; lastAt[id] = now
val hist = ArrayList(chat[id] ?: ArrayList())
val prof = sp.getString("prof", "") ?: ""
val bizInfo = "Business name: " + bn + ". What we do/sell: " + (sp.getString("bd", "") ?: "") + ". Prices: " + (sp.getString("bp", "") ?: "") + ". Working hours: " + (sp.getString("bh", "") ?: "") + ". Address/city: " + (sp.getString("ba", "") ?: "") + ". How to order/contact/payment: " + (sp.getString("bc", "") ?: "") + ". Extra rules/FAQ: " + (sp.getString("bx", "") ?: "")
val sysP = if (biz) Tr.bsys(bn, own, bizInfo) else Tr.rsys(prof, own)
Thread {
val raw = Gemini.call(k, sysP, hist, "Bhejne wala: " + who + ". Message: " + text)
if (raw == null) { JarvisService.lg("auto-reply: Gemini fail"); return@Thread }
val jo = try { JSONObject(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()) } catch (e: Exception) { null }
val r = jo?.optString("reply") ?: ""
if (jo?.optBoolean("abuse") == true) {
val c2 = (abuseN[id] ?: 0) + 1
abuseN[id] = c2
val pause = c2 >= 2
if (pause) { muted[id] = now + 600000; abuseN[id] = 0 }
if (!biz) JarvisService.inst?.alert(who, pause)
}
if (r.isBlank() || r.contains("SKIP", true)) { JarvisService.lg("auto-reply skip: " + who); return@Thread }
if (send(sbn, (if (biz) "(" + bn + " Assistant) " else "(Jarvis, " + own + " ka assistant) ") + r)) {
val l = chat.getOrPut(id) { ArrayList() }
l.add(text to r); while (l.size > 6) l.removeAt(0)
JarvisService.lg("auto-reply -> " + who + ": " + r)
} else JarvisService.lg("auto-reply fail: reply button nahi mila, actions=" + (sbn.notification.actions?.joinToString { it.title.toString() + (if (it.remoteInputs != null) "*" else "") } ?: "none"))
}.start()
}
private fun send(sbn: StatusBarNotification, text: String): Boolean {
for (a in sbn.notification.actions ?: return false) {
val ri = a.remoteInputs ?: continue
if (ri.isEmpty()) continue
val b = android.os.Bundle()
for (r in ri) b.putCharSequence(r.resultKey, text)
val i = android.content.Intent()
android.app.RemoteInput.addResultsToIntent(ri, i, b)
try { a.actionIntent.send(this, 0, i); return true } catch (e: Exception) { }
}
return false
}
}
class SendAcc : AccessibilityService() {
private var lastLog = 0L
companion object { @Volatile var inst: SendAcc? = null }
override fun onServiceConnected() { inst = this; JarvisService.lg("Accessibility chalu") }
override fun onDestroy() { inst = null; super.onDestroy() }
override fun onAccessibilityEvent(e: AccessibilityEvent?) {
val root = rootInActiveWindow ?: return
val now = System.currentTimeMillis()
if (JarvisService.qsAt != 0L) {
if (now - JarvisService.qsAt > 7000) JarvisService.qsAt = 0L
else if ((e?.packageName?.toString() ?: "").contains("systemui")) { if (now - lastQs > 500) { lastQs = now; qsClick(root) }; return }
}
if (JarvisService.callOn && JarvisService.spkWant) {
val pk0 = e?.packageName?.toString() ?: ""
if (pk0.contains("dialer") || pk0.contains("incallui")) { if (now - lastSpk > 1200) { lastSpk = now; speakerClick(root) }; return }
}
if (e?.packageName?.toString() == "com.android.settings") { if (JarvisService.fsAt != 0L) { force(root, now); return }; if (JarvisService.btWant != 0 && now - JarvisService.btAt < 30000) bt(root); return }
if (e?.packageName?.toString() == "com.google.android.youtube") {
if (JarvisService.ytAt != 0L) { if (now - JarvisService.ytAt > 20000) JarvisService.ytAt = 0L else if (playFirst(root, now)) return }
if (getSharedPreferences("j", MODE_PRIVATE).getBoolean("ys", true)) skipAd(root); return }
if (JarvisService.igText.isNotEmpty() && now - JarvisService.igAt < 25000 && e?.packageName?.toString() == "com.instagram.android") { ig(root); return }
if (now - JarvisService.sendAt > 20000) return
val n = findSend(root)
if (n != null) { n.performAction(AccessibilityNodeInfo.ACTION_CLICK); JarvisService.sendAt = 0L; JarvisService.lg("WA: send dabaya") }
else if (now - lastLog > 3000) { lastLog = now; JarvisService.lg("WA: send button nahi mila") }
}
private var lastFs = 0L
private fun force(root: AccessibilityNodeInfo, now: Long) {
if (now - JarvisService.fsAt > 15000) { JarvisService.fsAt = 0L; return }
if (JarvisService.fsStage == 1) {
val ok = root.findAccessibilityNodeInfosByViewId("android:id/button1").firstOrNull()
?: root.findAccessibilityNodeInfosByText("OK").firstOrNull() ?: root.findAccessibilityNodeInfosByText("ठीक है").firstOrNull()
if (ok != null) { ok.performAction(AccessibilityNodeInfo.ACTION_CLICK); JarvisService.lg("Band: confirm"); JarvisService.fsAt = 0L; JarvisService.fsStage = 0
Handler(Looper.getMainLooper()).postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 700) }
return
}
if (now - lastFs < 800) return
val re = Regex("force stop|जबरदस्ती रोकें|ज़बरदस्ती रोकें|जबरन रोकें|ज़बरन रोकें|बलपूर्वक रोकें|force close", RegexOption.IGNORE_CASE)
val btn = root.findAccessibilityNodeInfosByViewId("com.android.settings:id/right_button").firstOrNull()
?: root.findAccessibilityNodeInfosByText("Force stop").firstOrNull()
?: root.findAccessibilityNodeInfosByText("जबरन रोकें").firstOrNull()
if (btn == null || !re.containsMatchIn((btn.text?.toString() ?: "") + " " + (btn.contentDescription?.toString() ?: ""))) {
lastFs = now; return
}
lastFs = now
if (!btn.isEnabled) { JarvisService.lg("Band: app pehle se band"); JarvisService.fsAt = 0L; performGlobalAction(GLOBAL_ACTION_HOME); return }
up(btn)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
JarvisService.fsStage = 1
JarvisService.lg("Band: Force stop dabaya")
}
private var lastBt = 0L
private var lastSpk = 0L
private var lastQs = 0L
private fun findTile(n: AccessibilityNodeInfo?, re: Regex, d: Int): AccessibilityNodeInfo? {
if (n == null || d > 22) return null
val s = (n.contentDescription?.toString() ?: "") + " " + (n.text?.toString() ?: "") + " " + (n.viewIdResourceName ?: "").let { if (it.contains("tile") || it.contains("wifi") || it.contains("internet")) it else "" }
if (s.isNotBlank() && re.containsMatchIn(s) && up(n) != null) return n
for (i in 0 until n.childCount) { val r = findTile(n.getChild(i), re, d + 1); if (r != null) return r }
return null
}
private fun scrollQs(n: AccessibilityNodeInfo?, d: Int): Boolean {
if (n == null || d > 22) return false
if (n.isScrollable && n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true
for (i in 0 until n.childCount) { if (scrollQs(n.getChild(i), d + 1)) return true }
return false
}
private fun dumpTxt(n: AccessibilityNodeInfo?, sb: StringBuilder, d: Int) {
if (n == null || d > 22 || sb.length > 150) return
val s = (n.text?.toString() ?: n.contentDescription?.toString() ?: "").replace("\n", " ").take(18)
if (s.isNotBlank()) sb.append(s).append("|")
for (i in 0 until n.childCount) dumpTxt(n.getChild(i), sb, d + 1)
}
private fun findSwitch(n: AccessibilityNodeInfo?, d: Int): AccessibilityNodeInfo? {
if (n == null || d > 22) return null
if (n.isCheckable && (n.className?.toString() ?: "").contains("Switch", true)) return n
for (i in 0 until n.childCount) { val r = findSwitch(n.getChild(i), d + 1); if (r != null) return r }
return null
}
private fun findBtn(root: AccessibilityNodeInfo, re: Regex): AccessibilityNodeInfo? {
for (w in listOf("Done", "OK", "पूरा हुआ", "ठीक है")) { val l = root.findAccessibilityNodeInfosByText(w); val h = l.firstOrNull { re.containsMatchIn(it.text?.toString() ?: "") && up(it) != null }; if (h != null) return h }
return null
}
private fun qsFinish() {
Curtain.hideSoon()
val t = JarvisService.qsDone
JarvisService.qsDone = ""
if (t.isNotEmpty()) Handler(Looper.getMainLooper()).postDelayed({ JarvisService.inst?.say(t) }, 500)
}
private fun qsClick(root: AccessibilityNodeInfo) {
val kd = JarvisService.qsKind
if (JarvisService.qsStage == 1) {
val sw = findSwitch(root, 0)
if (sw != null) {
if (sw.isChecked != JarvisService.qsWant) { up(sw)?.performAction(AccessibilityNodeInfo.ACTION_CLICK); JarvisService.lg("qs: dialog switch dabaya") }
JarvisService.qsStage = 2
Handler(Looper.getMainLooper()).postDelayed({
val r = rootInActiveWindow
val done = if (r != null) findBtn(r, Regex("^(done|ok|पूरा हुआ|ठीक है)$", RegexOption.IGNORE_CASE)) else null
up(done)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
JarvisService.qsAt = 0L; JarvisService.qsStage = 0
Handler(Looper.getMainLooper()).postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK); qsFinish() }, 600)
}, 700)
}
return
}
if (JarvisService.qsStage == 2) return
val bt = kd == "bt"
val re = Regex(when (kd) {
"bt" -> "bluetooth|ब्लूटूथ"
"hs" -> "hotspot|tethering|हॉटस्पॉट|हॉट स्पॉट"
"wifi" -> "wi-?fi|wlan|internet|वाई-?फाई|इंटरनेट"
"data" -> "mobile data|cellular|data|मोबाइल डेटा|डेटा"
"bs" -> "battery saver|power saving|बैटरी सेवर|बैटरी बचत"
"dnd" -> "do not disturb|dnd|silent mode|परेशान न करें|डू नॉट डिस्टर्ब"
else -> "screen record|recorder|स्क्रीन रिकॉर्ड"
}, RegexOption.IGNORE_CASE)
val tile = findTile(root, re, 0)
if (tile == null) {
if (JarvisService.qsTries == 0) { val sb = StringBuilder(); dumpTxt(root, sb, 0); JarvisService.lg("qs: " + kd + " tile nahi dikha. Screen: " + sb.toString().take(150)) }
if (JarvisService.qsTries++ in 2..4) scrollQs(root, 0); return }
var d = (tile.contentDescription?.toString() ?: "") + " " + (tile.text?.toString() ?: "")
if (android.os.Build.VERSION.SDK_INT >= 30) d += " " + (tile.stateDescription?.toString() ?: "")
val isOn = when {
Regex("\\b(off|disabled)\\b|बंद|बन्द", RegexOption.IGNORE_CASE).containsMatchIn(d) -> false
Regex("\\b(on|enabled)\\b|चालू", RegexOption.IGNORE_CASE).containsMatchIn(d) -> true
else -> tile.isChecked || tile.isSelected
}
val want = JarvisService.qsWant
if (kd == "rec" || isOn != want) {
up(tile)?.performAction(AccessibilityNodeInfo.ACTION_CLICK); JarvisService.lg("qs: " + kd + " tile dabaya")
JarvisService.qsStage = 1
Handler(Looper.getMainLooper()).postDelayed({
if (JarvisService.qsStage == 1) { JarvisService.qsStage = 0; JarvisService.qsAt = 0L; performGlobalAction(GLOBAL_ACTION_BACK); qsFinish() }
}, 1600)
} else {
JarvisService.qsAt = 0L
JarvisService.lg("qs: pehle se " + (if (want) "on" else "off"))
JarvisService.qsDone = if (JarvisService.inst?.isEn() != false) "It is already " + (if (want) "on" else "off") else "यह पहले से " + (if (want) "चालू" else "बंद") + " है"
Handler(Looper.getMainLooper()).postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK); qsFinish() }, 500)
}
}
private fun speakerClick(root: AccessibilityNodeInfo) {
val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
if (am.isSpeakerphoneOn || JarvisService.spkTries >= 3) { JarvisService.spkWant = false; return }
val n = root.findAccessibilityNodeInfosByText("Speaker").firstOrNull() ?: root.findAccessibilityNodeInfosByText("स्पीकर").firstOrNull() ?: return
val c = up(n) ?: return
c.performAction(AccessibilityNodeInfo.ACTION_CLICK)
JarvisService.spkTries++
JarvisService.lg("call: Speaker button dabaya")
}
private var lastSk = 0L
private var lastYt = 0L
private fun findVid(n: AccessibilityNodeInfo?, re: Regex, d: Int): AccessibilityNodeInfo? {
if (n == null || d > 14) return null
val cd = n.contentDescription?.toString() ?: ""
if (cd.isNotEmpty() && re.containsMatchIn(cd) && !cd.contains("shorts", true)) return n
for (i in 0 until n.childCount) { val r = findVid(n.getChild(i), re, d + 1); if (r != null) return r }
return null
}
private fun playFirst(root: AccessibilityNodeInfo, now: Long): Boolean {
if (now - lastYt < 700) return false
lastYt = now
val re = Regex("play video|वीडियो चलाएं|वीडियो चलाएँ|views|बार देखा", RegexOption.IGNORE_CASE)
val v = up(findVid(root, re, 0)) ?: return false
if (v.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { JarvisService.ytAt = 0L; JarvisService.lg("YT: pehla video chalaya"); return true }
return false
}
private fun skipAd(root: AccessibilityNodeInfo) {
val now = System.currentTimeMillis()
if (now - lastSk < 500) return
var n: AccessibilityNodeInfo? = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/skip_ad_button").firstOrNull()
if (n == null) {
val re = Regex("^skip( ad)?s?$|skip ad|विज्ञापन छोड़ें", RegexOption.IGNORE_CASE)
n = root.findAccessibilityNodeInfosByText("Skip").firstOrNull { re.containsMatchIn((it.text?.toString() ?: "") + "") || re.containsMatchIn(it.contentDescription?.toString() ?: "") }
}
val c = up(n)
if (c != null) { lastSk = now; c.performAction(AccessibilityNodeInfo.ACTION_CLICK); JarvisService.lg("YT: ad skip") }
}
private fun up(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
var p = n
while (p != null && !p.isClickable) p = p.parent
return p
}
private fun sw(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
if (n == null) return null
if (n.isCheckable && (n.className?.toString() ?: "").contains("Switch", true)) return n
for (i in 0 until n.childCount) { val r = sw(n.getChild(i)); if (r != null) return r }
return null
}
private fun bt(root: AccessibilityNodeInfo) {
val want = JarvisService.btWant == 1
val s = sw(root)
if (s != null && s.isChecked != want) { up(s)?.performAction(AccessibilityNodeInfo.ACTION_CLICK); JarvisService.lg("BT: switch dabaya"); return }
val nm = JarvisService.btName
if (nm.isEmpty()) { JarvisService.btWant = 0; return }
val pb = root.findAccessibilityNodeInfosByText("Pair").firstOrNull { (it.className?.toString() ?: "").endsWith("Button") }
if (pb != null) { pb.performAction(AccessibilityNodeInfo.ACTION_CLICK); JarvisService.lg("BT: Pair dabaya"); JarvisService.btWant = 0; JarvisService.btName = ""; return }
val now = System.currentTimeMillis()
if (now - lastBt < 4000) return
val d = root.findAccessibilityNodeInfosByText(nm).firstOrNull()
if (d != null) { lastBt = now; up(d)?.performAction(AccessibilityNodeInfo.ACTION_CLICK); JarvisService.lg("BT: " + nm + " dabaya") }
}
private fun ig(root: AccessibilityNodeInfo) {
val et = edit(root) ?: return
val t = JarvisService.igText
if (et.text?.toString() != t) {
val a = android.os.Bundle()
a.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, t)
et.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
et.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, a)
JarvisService.lg("IG: text likha")
} else {
val n = findSend(root)
if (n != null) { n.performAction(AccessibilityNodeInfo.ACTION_CLICK); JarvisService.igText = ""; JarvisService.lg("IG: send dabaya") }
}
}
private fun findSend(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
if (n == null) return null
val id = n.viewIdResourceName ?: ""
val t = n.text?.toString() ?: ""
val d = n.contentDescription?.toString() ?: ""
if (id.contains("send", true) || t.equals("Send", true) || d.startsWith("Send", true) || d.startsWith("भेज")) {
var p: AccessibilityNodeInfo? = n
while (p != null && !p.isClickable) p = p.parent
if (p != null) return p
}
for (i in 0 until n.childCount) { val r = findSend(n.getChild(i)); if (r != null) return r }
return null
}
private fun edit(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
if (n == null) return null
if (n.className?.toString() == "android.widget.EditText") return n
for (i in 0 until n.childCount) { val r = edit(n.getChild(i)); if (r != null) return r }
return null
}
override fun onInterrupt() {}
}
