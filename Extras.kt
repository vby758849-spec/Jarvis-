package com.vaibhav.jarvis
import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
object Gemini {
@Volatile var lastErr = ""
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
.put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("maxOutputTokens", 1000).put("temperature", 0.6)).toString()
var err = ""
for (md in (list(key) + listOf("gemini-flash-lite-latest", "gemini-flash-latest", "gemini-2.5-flash-lite")).distinct().take(4)) {
try {
val con = URL("https://generativelanguage.googleapis.com/v1beta/models/$md:generateContent").openConnection() as HttpURLConnection
con.requestMethod = "POST"; con.connectTimeout = 10000; con.readTimeout = 25000; con.doOutput = true
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
private val last = HashMap<String, Long>()
private var hrStart = 0L
private var hrCnt = 0
override fun onNotificationPosted(sbn: StatusBarNotification) {
val n = sbn.notification
val pk = sbn.packageName
if (pk == packageName) return
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
if (!sd.isNullOrEmpty() && sd != title) { who = sd; group = title }
}
if (n.extras.getBoolean("android.isGroupConversation") && group.isEmpty()) group = title
val app = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pk, 0)).toString() } catch (e: Exception) { pk }
JarvisService.inst?.announce(who, app, text, group)
if (pk.startsWith("com.whatsapp")) autoReply(sbn, who, text, group)
}
private fun autoReply(sbn: StatusBarNotification, who: String, text: String, group: String) {
val sp = getSharedPreferences("j", MODE_PRIVATE)
if (!sp.getBoolean("ar", false) || group.isNotEmpty() || text.isBlank()) return
if (who.firstOrNull()?.let { it == '+' || it.isDigit() } == true) return
val k = sp.getString("key", "")?.trim().orEmpty()
if (k.isEmpty()) return
val id = sbn.key ?: who
val now = System.currentTimeMillis()
if (now - (last[id] ?: 0L) < 120000) return
if (now - hrStart > 3600000) { hrStart = now; hrCnt = 0 }
if (hrCnt >= 15) return
last[id] = now; hrCnt++
val prof = sp.getString("prof", "") ?: ""
Thread {
val sys = """Tum Vaibhav ki taraf se WhatsApp par ek jaankar ko reply likh rahe ho jab Vaibhav busy hai. Reply chhota (1-2 vaakya), natural aur usi bhasha/style mein ho jisme message aaya hai (Hinglish ho to Roman letters mein). Koi vaada, paise ki baat, address, OTP, password ya koi serious/emergency baat mat karo; aisa message ho to reply mein SKIP likho. Sawal ka seedha jawab pata na ho to bolo ki Vaibhav abhi busy hai, baad mein baat karega. Sirf JSON do: {"reply":"..."} . Vaibhav ke baare mein: """ + prof
val raw = Gemini.call(k, sys, emptyList(), "Bhejne wala: " + who + ". Message: " + text)
if (raw == null) { JarvisService.lg("auto-reply: Gemini fail"); return@Thread }
val r = try { JSONObject(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()).optString("reply") } catch (e: Exception) { "" }
if (r.isBlank() || r.contains("SKIP", true)) { JarvisService.lg("auto-reply skip: " + who); return@Thread }
if (send(sbn, "(Jarvis, Vaibhav ka assistant) " + r)) JarvisService.lg("auto-reply -> " + who + ": " + r)
else JarvisService.lg("auto-reply fail: reply button nahi mila")
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
override fun onAccessibilityEvent(e: AccessibilityEvent?) {
val root = rootInActiveWindow ?: return
val now = System.currentTimeMillis()
if (JarvisService.igText.isNotEmpty() && now - JarvisService.igAt < 25000 && e?.packageName?.toString() == "com.instagram.android") { ig(root); return }
if (now - JarvisService.sendAt > 20000) return
var n: AccessibilityNodeInfo? = null
for (id in arrayOf("com.whatsapp:id/send", "com.whatsapp.w4b:id/send")) {
n = root.findAccessibilityNodeInfosByViewId(id).firstOrNull()
if (n != null) break
}
if (n == null) n = root.findAccessibilityNodeInfosByText("Send").firstOrNull { it.isClickable }
?: root.findAccessibilityNodeInfosByText("भेजें").firstOrNull { it.isClickable }
if (n != null) { n.performAction(AccessibilityNodeInfo.ACTION_CLICK); JarvisService.sendAt = 0L }
}
private fun ig(root: AccessibilityNodeInfo) {
val et = edit(root) ?: return
val t = JarvisService.igText
if (et.text?.toString() != t) {
val a = android.os.Bundle()
a.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, t)
et.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, a)
} else {
val n = root.findAccessibilityNodeInfosByText("Send").firstOrNull { it.isClickable }
if (n != null) { n.performAction(AccessibilityNodeInfo.ACTION_CLICK); JarvisService.igText = "" }
}
}
private fun edit(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
if (n == null) return null
if (n.className?.toString() == "android.widget.EditText") return n
for (i in 0 until n.childCount) { val r = edit(n.getChild(i)); if (r != null) return r }
return null
}
override fun onInterrupt() {}
}
