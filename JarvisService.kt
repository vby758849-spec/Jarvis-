package com.vaibhav.jarvis
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telephony.SmsManager
import android.widget.Toast
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import java.text.SimpleDateFormat
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.util.Locale
class JarvisService : Service(), RecognitionListener {
private lateinit var sr: SpeechRecognizer
private lateinit var am: AudioManager
private var tts: TextToSpeech? = null
private var ttsReady = false
private val h = Handler(Looper.getMainLooper())
private var awake = false
private var porc: PorcupineManager? = null
@Volatile private var asking = false
@Volatile private var lastErr = ""
private var lastMsg = ""
private var lastAnn = ""
companion object { @Volatile var inst: JarvisService? = null }
private fun hi(t: String): String = try {
if (Build.VERSION.SDK_INT >= 29 && t.any { it in 'A'..'z' }) android.icu.text.Transliterator.getInstance("Latin-Devanagari").transliterate(t) else t
} catch (e: Exception) { t }
fun announce(who: String, app: String, text: String, group: String) {
val key = who + text
if (key == lastAnn) return
lastAnn = key
lastMsg = who + " ने कहा: " + text
h.postDelayed({ lastAnn = "" }, 10000)
val ap = when {
app.contains("whats", true) -> "व्हाट्सऐप"
app.contains("telegram", true) -> "टेलीग्राम"
app.contains("gmail", true) -> "जीमेल"
app.contains("messag", true) || app.contains("sms", true) -> "मैसेज"
else -> app
}
val g = if (group.isEmpty()) "" else hi(group) + " ग्रुप में "
h.post {
say("सर, " + hi(who) + " का " + ap + " पर " + g + "मैसेज आया है, पढ़ूँ?")
if (porc == null) { h.postDelayed({ awake = true }, 4000); h.postDelayed({ awake = false }, 20000) }
else { awake = true; listen() }
}
}
override fun onCreate() {
super.onCreate()
inst = this
getSystemService(NotificationManager::class.java)
.createNotificationChannel(NotificationChannel("jarvis", "Jarvis", NotificationManager.IMPORTANCE_LOW))
val n = Notification.Builder(this, "jarvis").setContentTitle("Jarvis sun raha hai")
.setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
else startForeground(1, n)
tts = TextToSpeech(this) {
if (it == TextToSpeech.SUCCESS) {
tts?.language = Locale("hi", "IN")
applyVoice()
tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
override fun onStart(id: String?) { speaking = true }
override fun onDone(id: String?) { speaking = false }
@Deprecated("x") override fun onError(id: String?) { speaking = false }
})
ttsReady = true
}
}
am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
for (st in intArrayOf(AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_SYSTEM)) try { am.adjustStreamVolume(st, AudioManager.ADJUST_MUTE, 0) } catch (e: Exception) {}
sr = SpeechRecognizer.createSpeechRecognizer(this)
sr.setRecognitionListener(this)
initWake()
}
override fun onDestroy() {
inst = null
h.removeCallbacksAndMessages(null)
try { porc?.stop(); porc?.delete() } catch (e: Exception) { }
sr.destroy(); tts?.shutdown()
beep(false)
for (st in intArrayOf(AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_SYSTEM)) try { am.adjustStreamVolume(st, AudioManager.ADJUST_UNMUTE, 0) } catch (e: Exception) {}
super.onDestroy()
}
private fun say(t: String) {
if (!ttsReady) return
speaking = true
tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "u")
}
private fun beep(m: Boolean) {
try { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (m) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, 0) } catch (e: Exception) {}
}
private fun listen() {
if (speaking) { h.postDelayed({ listen() }, 700); return }
val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
.putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANG)
.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 8000L)
.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L)
beep(true)
h.postDelayed({ beep(false) }, 900)
try { sr.startListening(i) } catch (e: Exception) { h.postDelayed({ listen() }, 1000) }
}
override fun onResults(b: Bundle?) {
val l = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
(l?.firstOrNull { wake.containsMatchIn(it.lowercase()) } ?: l?.firstOrNull())?.let { process(it.lowercase()) }
if (porc == null) h.postDelayed({ listen() }, 400) else if (!asking) resume()
}
override fun onError(e: Int) {
sr.cancel()
if (porc == null) h.postDelayed({ listen() }, if (e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1500 else 400) else resume()
}
override fun onReadyForSpeech(p: Bundle?) {}
override fun onBeginningOfSpeech() {}
override fun onRmsChanged(r: Float) {}
override fun onBufferReceived(b: ByteArray?) {}
override fun onEndOfSpeech() {}
override fun onPartialResults(p: Bundle?) {}
override fun onEvent(t: Int, p: Bundle?) {}
private fun process(s: String) {
val m = wake.find(s)
if (m != null) {
val rest = s.substring(m.range.last + 1).trim()
if (rest.isNotEmpty()) { awake = false; handle(rest) }
else {
say("जी, बोलिए")
h.postDelayed({ awake = true }, 1500)
h.postDelayed({ awake = false }, 20000)
}
} else if (awake) { awake = false; handle(s) }
}
private fun handle(s0: String) {
val s = s0.replace("whats app", "whatsapp").replace("you tube", "youtube")
when {
Regex("kis ?ne (banaya|bnaya|banaaya|create)|who (made|created|built) you").containsMatchIn(s) -> say("मुझे वैभव सर ने बनाया है")
lastMsg.isNotEmpty() && Regex("^(haan|han|ha|yes|yeah|ji|padho|bolo|sunao)\\b").containsMatchIn(s) -> say(lastMsg)
Regex("(message|msg|massage) ?(kya|padh|sunao)|kya (likha|message|msg)|padh ?do|padh ?ke sunao|read (the )?message").containsMatchIn(s) -> say(if (lastMsg.isEmpty()) "कोई नया मैसेज नहीं है" else lastMsg)
Regex("(awaaz|awaz|voice).*(bhari|bhaari|gehri|deep|heavy)|(bhari|bhaari|gehri|deep|heavy).*(awaaz|awaz|voice)").containsMatchIn(s) -> {
sp.edit().putFloat("pt", maxOf(0.4f, sp.getFloat("pt", 0.65f) - 0.1f)).apply(); applyVoice()
say("अब आवाज़ और भारी है")
}
Regex("(awaaz|awaz|voice).*(patli|halki|light)|(patli|halki|light).*(awaaz|awaz|voice)").containsMatchIn(s) -> {
sp.edit().putFloat("pt", minOf(1.2f, sp.getFloat("pt", 0.65f) + 0.1f)).apply(); applyVoice()
say("अब आवाज़ हल्की है")
}
Regex("awaaz badlo|awaz badlo|voice change|change voice").containsMatchIn(s) -> {
sp.edit().putInt("vk", sp.getInt("vk", 0) + 1).apply(); applyVoice()
say("ये मेरी नई आवाज़ है, पसंद आई?")
}
Regex("^(yaad rakh|yaad rakho|remember)").containsMatchIn(s) -> remember(s)
"torch" in s || "flashlight" in s -> torch(!Regex("\\b(off|band|bandh)\\b").containsMatchIn(s))
Regex("\\bcall\\b").containsMatchIn(s) -> call(s)
"alarm" in s -> alarm(s)
Regex("message|sms|msg|whatsapp").containsMatchIn(s) -> msg(s)
"youtube" in s -> youtube(s)
"maps" in s || "raasta" in s || "navigate" in s -> {
val q = s.replace(Regex("\\b(maps|raasta|navigate|to|kholo|dikhao)\\b"), "").trim()
say("$q का रास्ता"); go(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q))))
}
Regex("\\b(time|samay)\\b|baje kya").containsMatchIn(s) ->
say("अभी " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) + " हुए हैं")
Regex("\\b(open|kholo|khol)\\b").containsMatchIn(s) -> openApp(s)
else -> ask(s0)
}
}
private fun initWake() {
val k = sp.getString("pico", "")?.trim().orEmpty()
if (k.isNotEmpty()) {
try {
porc = PorcupineManager.Builder().setAccessKey(k).setKeyword(Porcupine.BuiltInKeyword.JARVIS)
.setSensitivity(0.7f).build(this, PorcupineManagerCallback { h.post { onWake() } })
porc?.start()
return
} catch (e: Exception) {
porc = null
say("Picovoice key चेक करो, पुराने तरीके से चला रहा हूँ")
}
}
listen()
}
private fun onWake() {
try { porc?.stop() } catch (e: Exception) { }
awake = true
say("जी भाई?")
listen()
}
private fun resume() {
awake = false
if (porc == null) { listen(); return }
if (speaking) { h.postDelayed({ resume() }, 700); return }
try { porc?.start() } catch (e: Exception) { h.postDelayed({ resume() }, 1500) }
}
private fun applyVoice() {
tts?.setPitch(sp.getFloat("pt", 0.65f)); tts?.setSpeechRate(0.95f)
val pref = Regex("hic|hid")
val v = (tts?.voices ?: emptySet<android.speech.tts.Voice>())
.filter { it.locale.language == "hi" }
.sortedWith(compareBy({ !pref.containsMatchIn(it.name) }, { it.name }))
if (v.isNotEmpty()) tts?.voice = v[sp.getInt("vk", 0) % v.size]
}
private fun remember(s: String) {
val fact = s.replace(Regex("^(yaad rakh|yaad rakho|remember)( ki| that)?"), "").trim()
sp.edit().putString("prof", sp.getString("prof", "") + " " + fact + ".").apply()
say("याद रख लिया")
}
private fun jm(r: String, t: String) = JSONObject().put("role", r).put("parts", JSONArray().put(JSONObject().put("text", t)))
private fun ask(q: String) {
val key = sp.getString("key", "")?.trim().orEmpty()
if (key.isEmpty()) { say("पहले ऐप में Gemini API key डालो"); return }
asking = true
Thread {
val ans = callGemini(key, q)
h.post {
asking = false
if (ans == null) { say("जवाब नहीं मिल पाया, स्क्रीन पर error देखो"); Toast.makeText(this, lastErr.take(300), Toast.LENGTH_LONG).show(); resume() }
else {
hist.add(q to ans); if (hist.size > 6) hist.removeAt(0)
say(ans.replace(Regex("[*#`_]"), ""))
if (porc == null) {
h.postDelayed({ awake = true }, 2500)
h.postDelayed({ awake = false }, 25000)
} else { awake = true; listen() }
}
}
}.start()
}
private var models: List<String>? = null
private fun listModels(key: String): List<String> {
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
if (ok && n.contains("flash") && !Regex("lite|image|tts|live|audio|thinking|exp|preview|8b|vision|robotics|computer|gemma").containsMatchIn(n)) out.add(n)
}
} else lastErr = "models list " + c.responseCode + " | "
} catch (e: Exception) { lastErr = "models list " + e + " | " }
val r = out.sortedDescending()
if (r.isNotEmpty()) models = r
return r
}
private fun callGemini(key: String, q: String): String? {
val sys = "Tum Jarvis ho, Vaibhav ke dost aur voice assistant. Vaibhav ne tumhe banaya hai. Koi pooche tumhe kisne banaya to bolo: मुझे वैभव सर ने बनाया है. Dost ki tarah casual aur friendly baat karo, kabhi halka mazaak, Vaibhav ko bhai bulao. Jawab hamesha Devanagari Hindi mein likho (English shabd English mein chalenge), 1-3 chhote vaakya, bina markdown aur emoji ke. Google ya Gemini ka naam mat lo. Vaibhav ke baare mein jaankari: " +
sp.getString("prof", "") + " Aaj ki tarikh aur time: " + Date()
val contents = JSONArray()
for ((u, a) in hist) { contents.put(jm("user", u)); contents.put(jm("model", a)) }
contents.put(jm("user", q))
val body = JSONObject().put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
.put("contents", contents).toString()
var err = ""
for (m in (listModels(key) + listOf("gemini-flash-latest", "gemini-3.8-flash", "gemini-2.5-flash")).distinct().take(5)) {
try {
val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent").openConnection() as HttpURLConnection
c.requestMethod = "POST"; c.connectTimeout = 15000; c.readTimeout = 30000; c.doOutput = true
c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("x-goog-api-key", key)
c.outputStream.use { it.write(body.toByteArray()) }
if (c.responseCode == 200) {
val ps = JSONObject(c.inputStream.bufferedReader().readText()).getJSONArray("candidates")
.getJSONObject(0).getJSONObject("content").getJSONArray("parts")
val sb = StringBuilder()
for (i in 0 until ps.length()) sb.append(ps.getJSONObject(i).optString("text"))
if (sb.isNotBlank()) return sb.toString().trim()
} else err += m + " " + c.responseCode + " " + (c.errorStream?.bufferedReader()?.readText() ?: "").take(60) + " | "
} catch (e: Exception) { err += m + " " + e + " | " }
}
lastErr = err
return null
}
private fun go(i: Intent): Boolean = try {
i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); true
} catch (e: Exception) { say("ये नहीं हो पाया"); false }
private fun digits(s: String) = Regex("\\+?\\d[\\d ]{6,}\\d").find(s)?.value?.replace(" ", "")
private fun findNumber(name: String): String? {
if (name.isBlank() || checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?", arrayOf("%$name%"), null)?.use {
if (it.moveToFirst()) return it.getString(0)
}
return null
}
private fun call(s: String) {
val name = s.replace(Regex("\\b(call|karo|kar|do|lagao|laga|ko|phone|please)\\b"), "").trim()
val num = digits(s) ?: findNumber(name)
if (num == null) { say("$name कॉन्टैक्ट में नहीं मिला"); return }
say("$name को कॉल लगा रहा हूँ")
go(Intent(Intent.ACTION_CALL, Uri.parse("tel:$num")))
}
@Suppress("DEPRECATION")
private fun msg(s: String) {
val t = s.replace(Regex("\\b(send|message|sms|msg|whatsapp|bhejo|bhej|bhejna|karo|likh|likho|text)\\b"), "")
.replace(Regex("\\s+"), " ").trim()
val d = digits(t)
val name: String; val body: String
if (d != null) { name = d; body = t.replace(Regex("\\+?\\d[\\d ]{6,}\\d"), "").replace(" ko ", " ").trim() }
else {
val k = t.indexOf(" ko ")
if (k > 0) { name = t.substring(0, k).trim(); body = t.substring(k + 4).trim() }
else { val p = t.split(" ", limit = 2); name = p[0]; body = p.getOrElse(1) { "" } }
}
val num = d ?: findNumber(name)
if (num == null) { say("$name कॉन्टैक्ट में नहीं मिला"); return }
if ("whatsapp" in s) {
val n = num.filter { it.isDigit() }
val full = if (n.length == 10) "91$n" else n
say("$name को WhatsApp मैसेज तैयार है")
go(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$full?text=" + Uri.encode(body))))
} else {
try {
val sm = if (Build.VERSION.SDK_INT >= 31) getSystemService(SmsManager::class.java) else SmsManager.getDefault()
sm.sendMultipartTextMessage(num, null, sm.divideMessage(body), null, null)
say("$name को मैसेज भेज दिया")
} catch (e: Exception) { say("मैसेज नहीं गया") }
}
}
private fun alarm(s: String) {
val m = Regex("(\\d{1,2})(?:[: ](\\d{2}))?\\s*(am|pm|baje)?").find(s)
if (m == null) { say("कितने बजे का अलार्म?"); return }
var hr = m.groupValues[1].toInt(); val mn = m.groupValues[2].toIntOrNull() ?: 0
val ap = m.groupValues[3]
if (ap == "pm" && hr < 12) hr += 12
if (ap == "am" && hr == 12) hr = 0
say("अलार्म लगा दिया")
go(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, hr)
.putExtra(AlarmClock.EXTRA_MINUTES, mn).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
}
private fun youtube(s: String) {
val q = s.replace(Regex("\\b(youtube|play|chalao|chala|kholo|khol|open|par|pe|on|karo|do|gaana|song)\\b"), "").trim()
if (q.isEmpty()) {
say("YouTube खोल रहा हूँ")
go(packageManager.getLaunchIntentForPackage("com.google.android.youtube") ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://youtube.com")))
} else {
say("$q YouTube पर")
go(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))))
}
}
private fun openApp(s: String) {
val name = s.replace(Regex("\\b(open|kholo|khol|karo|app|do|ko)\\b"), "").trim()
if (name.isEmpty()) return
val pm = packageManager
val li = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
val hit = li.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(name) }
val i = hit?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
if (i != null) { say("$name खोल रहा हूँ"); go(i) } else say("$name ऐप नहीं मिली")
}
private fun torch(on: Boolean) {
try {
val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
val id = cm.cameraIdList.first { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
cm.setTorchMode(id, on)
say(if (on) "टॉर्च ऑन" else "टॉर्च ऑफ")
} catch (e: Exception) { say("टॉर्च नहीं चली") }
}
}
class MsgListener : NotificationListenerService() {
override fun onNotificationPosted(sbn: StatusBarNotification) {
val j = JarvisService.inst
if (j == null) return
val n = sbn.notification
if (sbn.packageName == packageName) return
if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
if ((n.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
val known = sbn.packageName in setOf("com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "com.google.android.apps.messaging", "com.android.mms", "com.samsung.android.messaging", "com.instagram.android", "com.facebook.orca", "com.google.android.gm")
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
val app = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() } catch (e: Exception) { sbn.packageName }
j.announce(who, app, text, group)
}
}
