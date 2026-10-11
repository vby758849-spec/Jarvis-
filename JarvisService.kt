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
private var tk = 0
private fun stayAwake(ms: Long) { awake = true; val t = ++tk; h.postDelayed({ if (t == tk) awake = false }, ms) }
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
h.postDelayed({ stayAwake(20000) }, 4000)
}
}
private val sp by lazy { getSharedPreferences("j", Context.MODE_PRIVATE) }
private val hist = ArrayList<Pair<String, String>>()
@Volatile private var speaking = false
private val wake = Regex("jarvis|jarvish|travis|jar vis|jarwis|darvis|charvis|harvis|service|जार्विस|जारविस|जर्विस|जार्वीस|जारवीस|जरविस|ज़ार्विस|डार्विस|चार्विस|जार्विश")
private val LANG = "hi-IN"
override fun onBind(i: Intent?): IBinder? = null
override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY
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
listen()
}
override fun onDestroy() {
inst = null
h.removeCallbacksAndMessages(null)
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
if (speaking) { h.postDelayed({ listen() }, 300); return }
val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
.putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANG)
.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
beep(true)
h.postDelayed({ beep(false) }, 600)
try { sr.startListening(i) } catch (e: Exception) { h.postDelayed({ listen() }, 500) }
}
override fun onResults(b: Bundle?) {
val l = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
(l?.firstOrNull { wake.containsMatchIn(it.lowercase()) } ?: l?.firstOrNull())?.let { process(it.lowercase()) }
h.postDelayed({ listen() }, 150)
}
override fun onError(e: Int) {
sr.cancel()
h.postDelayed({ listen() }, if (e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1500 else 150)
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
if (rest.isNotEmpty()) { stayAwake(20000); handle(rest) }
else { say("जी भाई?"); h.postDelayed({ stayAwake(20000) }, 1500) }
} else if (awake) { stayAwake(20000); handle(s) }
}
private fun handle(s: String) {
when {
Regex("kis ?ne (banaya|bnaya|banaaya|create)|किसने (तुम्हें |तुम्हे |तुमको )?बनाया|who (made|created|built) you").containsMatchIn(s) -> say("मुझे वैभव सर ने बनाया है")
lastMsg.isNotEmpty() && (Regex("^(haan|han|yes|yeah|ji|padho|bolo|sunao)\\b").containsMatchIn(s) || Regex("^(हाँ|हां|पढ़ो|पढ़ दो|सुनाओ|बोलो)").containsMatchIn(s)) -> say(lastMsg)
Regex("(message|msg|massage) ?(kya|padh|sunao)|kya (likha|message|msg)|padh ?do|padh ?ke sunao|read (the )?message|मैसेज (क्या|पढ़|सुना)|क्या (लिखा|मैसेज)|पढ़ के सुनाओ").containsMatchIn(s) -> say(if (lastMsg.isEmpty()) "कोई नया मैसेज नहीं है" else lastMsg)
Regex("(awaaz|awaz|voice|आवाज़|आवाज).*(bhari|bhaari|gehri|deep|heavy|भारी|गहरी)|(bhari|bhaari|gehri|deep|heavy|भारी|गहरी).*(awaaz|awaz|voice|आवाज़|आवाज)").containsMatchIn(s) -> {
sp.edit().putFloat("pt", maxOf(0.4f, sp.getFloat("pt", 0.65f) - 0.1f)).apply(); applyVoice()
say("अब आवाज़ और भारी है")
}
Regex("(awaaz|awaz|voice|आवाज़|आवाज).*(patli|halki|light|पतली|हल्की)|(patli|halki|light|पतली|हल्की).*(awaaz|awaz|voice|आवाज़|आवाज)").containsMatchIn(s) -> {
sp.edit().putFloat("pt", minOf(1.2f, sp.getFloat("pt", 0.65f) + 0.1f)).apply(); applyVoice()
say("अब आवाज़ हल्की है")
}
Regex("awaaz badlo|awaz badlo|voice change|change voice|आवाज़ बदल|आवाज बदल").containsMatchIn(s) -> {
sp.edit().putInt("vk", sp.getInt("vk", 0) + 1).apply(); applyVoice()
say("ये मेरी नई आवाज़ है, पसंद आई?")
}
Regex("^(yaad rakh|yaad rakho|remember)|^याद रख").containsMatchIn(s) -> remember(s)
Regex("torch|flashlight|टॉर्च|टोर्च|फ्लैशलाइट").containsMatchIn(s) -> torch(!Regex("\\b(off|band|bandh)\\b|बंद|बन्द|ऑफ").containsMatchIn(s))
else -> ask(s)
}
}
private fun applyVoice() {
tts?.setPitch(sp.getFloat("pt", 0.65f)); tts?.setSpeechRate(1.05f)
val pref = Regex("hic|hid")
val v = (tts?.voices ?: emptySet<android.speech.tts.Voice>())
.filter { it.locale.language == "hi" }
.sortedWith(compareBy({ !pref.containsMatchIn(it.name) }, { it.name }))
if (v.isNotEmpty()) tts?.voice = v[sp.getInt("vk", 0) % v.size]
}
private fun remember(s: String) {
val fact = s.replace(Regex("^(yaad rakh|yaad rakho|remember|याद रख|याद रखो)( ki| that| कि)?"), "").trim()
sp.edit().putString("prof", sp.getString("prof", "") + " " + fact + ".").apply()
say("याद रख लिया")
}
private fun ask(q: String) {
val key = sp.getString("key", "")?.trim().orEmpty()
if (key.isEmpty()) { say("पहले ऐप में Gemini API key डालो"); return }
val hh = ArrayList(hist)
Thread {
val sys = """Tum Jarvis ho, Vaibhav ke dost aur phone assistant. Vaibhav ne tumhe banaya hai. Koi pooche kisne banaya to say mein bolo: मुझे वैभव सर ने बनाया है. Dost ki tarah casual aur friendly baat karo, kabhi halka mazaak, Vaibhav ko bhai bulao. say hamesha Devanagari Hindi mein (English shabd English mein), 1-3 chhote vaakya, bina emoji ke. User ki baat speech recognition se aayi hai, galtiyan ho sakti hain, sahi matlab samjho. Sirf ek JSON object do: {"say":"jo bolna hai","action":"none|call|sms|whatsapp|youtube|maps|alarm|torch|openapp","name":"contact ka naam English letters mein","number":"digits agar bole ho","text":"message ka text","hour":0,"minute":0,"on":true,"query":"youtube ya maps ka search","app":"app ka naam English mein"}. Jo field kaam ka na ho use khali chhodo. Vaibhav ke baare mein jaankari: """ + sp.getString("prof", "") + " Abhi: " + Date()
val raw = Gemini.call(key, sys, hh, q)
h.post {
if (raw == null) { say("जवाब नहीं मिल पाया, स्क्रीन पर error देखो"); Toast.makeText(this, Gemini.lastErr.take(300), Toast.LENGTH_LONG).show() }
else {
var jj: JSONObject? = null
try { jj = JSONObject(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()) } catch (e: Exception) { }
val j = jj
val sy = (j?.optString("say") ?: raw).replace(Regex("[*#`_]"), "")
hist.add(q to sy); if (hist.size > 6) hist.removeAt(0)
val a = j?.optString("action") ?: "none"
if (j == null || a == "none" || a.isEmpty()) say(sy) else act(a, j)
}
stayAwake(20000)
}
}.start()
}
private fun act(a: String, j: JSONObject) {
val who = j.optString("number").ifEmpty { j.optString("name") }
val tx = j.optString("text")
when (a) {
"call" -> call("call $who")
"sms" -> if (tx.isEmpty()) say("क्या मैसेज भेजना है?") else msg("sms $who ko $tx")
"whatsapp" -> msg("whatsapp $who ko $tx")
"youtube" -> youtube("youtube " + j.optString("query"))
"maps" -> { val q = j.optString("query"); say(q + " का रास्ता"); go(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q)))) }
"alarm" -> alarm("alarm " + j.optInt("hour") + ":" + String.format("%02d", j.optInt("minute")))
"torch" -> torch(j.optBoolean("on", true))
"openapp" -> openApp("open " + j.optString("app").lowercase())
else -> say(j.optString("say"))
}
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
else if (t.endsWith(" ko")) { name = t.removeSuffix(" ko"); body = "" }
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
