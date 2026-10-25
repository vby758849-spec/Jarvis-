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
fun warm(key: String) { list(key) }
@Volatile var lastErr = ""
fun info(key: String, sys: String, hist: List<Pair<String, String>>, q: String): String? {
val c = JSONArray()
for ((u, a) in hist) { c.put(m("user", u)); c.put(m("model", a)) }
c.put(m("user", q))
val body = JSONObject().put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
.put("contents", c)
.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
.put("generationConfig", JSONObject().put("maxOutputTokens", 900).put("temperature", 0.4)).toString()
for (md in listOf("gemini-2.5-flash", "gemini-flash-latest", "gemini-2.5-flash-lite")) {
try {
val con = URL("https://generativelanguage.googleapis.com/v1beta/models/$md:generateContent").openConnection() as HttpURLConnection
con.requestMethod = "POST"; con.connectTimeout = 8000; con.readTimeout = 25000; con.doOutput = true
con.setRequestProperty("Content-Type", "application/json"); con.setRequestProperty("x-goog-api-key", key)
con.outputStream.use { it.write(body.toByteArray()) }
if (con.responseCode == 200) {
val ps = JSONObject(con.inputStream.bufferedReader().readText()).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
val sb = StringBuilder()
for (i in 0 until ps.length()) sb.append(ps.getJSONObject(i).optString("text"))
if (sb.isNotBlank()) return sb.toString().trim()
} else lastErr = md + " " + con.responseCode
} catch (e: Exception) { lastErr = md + " " + e }
}
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
val c = JSONArray()
for ((u, a) in hist) { c.put(m("user", u)); c.put(m("model", a)) }
c.put(m("user", q))
val body = JSONObject().put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
.put("contents", c)
.put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("maxOutputTokens", 700).put("temperature", 0.6)).toString()
var err = ""
for (md in (list(key) + listOf("gemini-flash-lite-latest", "gemini-flash-latest", "gemini-2.5-flash-lite")).distinct().take(3)) {
try {
val con = URL("https://generativelanguage.googleapis.com/v1beta/models/$md:generateContent").openConnection() as HttpURLConnection
con.requestMethod = "POST"; con.connectTimeout = 6000; con.readTimeout = 12000; con.doOutput = true
con.setRequestProperty("Content-Type", "application/json"); con.setRequestProperty("x-goog-api-key", key)
con.outputStream.use { it.write(body.toByteArray()) }
if (con.responseCode == 200) {
val ps = JSONObject(con.inputStream.bufferedReader().readText()).getJSONArray("candidates")
.getJSONObject(0).getJSONObject("content").getJSONArray("parts")
val sb = StringBuilder()
for (i in 0 until ps.length()) sb.append(ps.getJSONObject(i).optString("text"))
if (sb.isNotBlank()) return sb.toString().trim()
} else err += md + " " + con.responseCode + " " + (con.errorStream?.bufferedReader()?.readText() ?: "").take(60) + " | "
} catch (e: Exception) { err += md + " " + e + " | " }
}
lastErr = err
return null
}
}
class MsgListener : NotificationListenerService() {
companion object { @Volatile var inst: MsgListener? = null }
override fun onListenerConnected() { inst = this }
override fun onListenerDisconnected() { inst = null }
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
override fun onNotificationPosted(sbn: StatusBarNotification) {
val n = sbn.notification
val pk = sbn.packageName
if (pk == packageName) return
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
if (group.isNotEmpty()) { sk("group"); return }
if (text.isBlank()) { sk("khali"); return }
if (text.startsWith("(Jarvis")) return
if (who.firstOrNull()?.let { it == '+' || it.isDigit() } == true) { sk("number"); return }
val k = sp.getString("key", "")?.trim().orEmpty()
if (k.isEmpty()) {
val id0 = sbn.key ?: who
val n0 = System.currentTimeMillis()
if (n0 - (lastAt[id0] ?: 0L) < 3600000) return
lastAt[id0] = n0
if (send(sbn, "(Jarvis, " + own + " ka assistant) " + own + " abhi busy hain. Aapka message unhe mil gaya hai, wo jaldi jawab denge.")) JarvisService.lg("auto-reply (bina key) -> " + who)
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
if (cnt.values.sum() >= 40) { sk("kul limit"); return }
cnt[id] = c + 1; lastIn[id] = text; lastAt[id] = now
val hist = ArrayList(chat[id] ?: ArrayList())
val prof = sp.getString("prof", "") ?: ""
Thread {
val raw = Gemini.call(k, Tr.rsys(prof, own), hist, "Bhejne wala: " + who + ". Message: " + text)
if (raw == null) { JarvisService.lg("auto-reply: Gemini fail"); return@Thread }
val jo = try { JSONObject(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()) } catch (e: Exception) { null }
val r = jo?.optString("reply") ?: ""
if (jo?.optBoolean("abuse") == true) { muted[id] = now + 600000; JarvisService.inst?.alert(who) }
if (r.isBlank() || r.contains("SKIP", true)) { JarvisService.lg("auto-reply skip: " + who); return@Thread }
if (send(sbn, "(Jarvis, " + own + " ka assistant) " + r)) {
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
override fun onServiceConnected() { JarvisService.lg("Accessibility chalu") }
override fun onAccessibilityEvent(e: AccessibilityEvent?) {
val root = rootInActiveWindow ?: return
val now = System.currentTimeMillis()
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
