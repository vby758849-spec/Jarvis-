package com.vaibhav.jarvis
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
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
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.text.SimpleDateFormat
import org.json.JSONObject
import java.util.Date
import java.util.Locale
class JarvisService : Service(), RecognitionListener {
private lateinit var sr: SpeechRecognizer
private var tts: TextToSpeech? = null
private var ttsReady = false
private var hiV: android.speech.tts.Voice? = null
private var enV: android.speech.tts.Voice? = null
private val h = Handler(Looper.getMainLooper())
private var awake = false
private var tk = 0
private fun stayAwake(ms: Long) { awake = true; val t = ++tk; h.postDelayed({ if (t == tk) awake = false }, ms) }
private var lastMsg = ""
private var lastAnn = ""
companion object {
@Volatile var inst: JarvisService? = null
@Volatile var sendAt = 0L
@Volatile var igText = ""
@Volatile var igAt = 0L
@Volatile var btWant = 0
@Volatile var btAt = 0L
@Volatile var btName = ""
val logs = java.util.concurrent.ConcurrentLinkedDeque<String>()
fun lg(t: String) { logs.addLast(t.take(170)); while (logs.size > 22) logs.pollFirst() }
}
private fun dv(t: String) = t
private fun hi(t: String): String = try {
if (Build.VERSION.SDK_INT >= 29 && t.any { it in 'A'..'z' }) android.icu.text.Transliterator.getInstance("Latin-Devanagari").transliterate(t) else t
} catch (e: Exception) { t }
fun announce(who: String, app: String, text: String, group: String) {
val key = who + text
if (key == lastAnn) return
lastAnn = key
lastMsg = if (isEn()) who + " said: " + text else who + " ने कहा: " + text
h.postDelayed({ lastAnn = "" }, 10000)
if (!sp.getBoolean("an", true)) return
val ap = when {
app.contains("whats", true) -> "व्हाट्सऐप"
app.contains("telegram", true) -> "टेलीग्राम"
app.contains("gmail", true) -> "जीमेल"
app.contains("messag", true) || app.contains("sms", true) -> "मैसेज"
else -> app
}
val g = if (group.isEmpty()) "" else group + " ग्रुप में "
h.post {
if (isEn()) say("Boss, " + who + " messaged you on " + app + (if (group.isEmpty()) "" else " in " + group) + ". Shall I read it?")
else say("बॉस, " + who + " का " + ap + " पर " + g + "मैसेज आया है, पढ़ूँ?")
h.postDelayed({ wakeHit(true) }, 3500)
}
}
private val sp by lazy { getSharedPreferences("j", Context.MODE_PRIVATE) }
private val hist = ArrayList<Pair<String, String>>()
@Volatile private var speaking = false
private val wake = Regex("jarvis|jarvish|travis|jar vis|jarwis|darvis|charvis|harvis|service|जार्विस|जारविस|जर्विस|जार्वीस|जारवीस|जरविस|ज़ार्विस|डार्विस|चार्विस|जार्विश")
private var LANG = "hi-IN"
private var off = false
override fun onBind(i: Intent?): IBinder? = null
override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY
override fun onCreate() {
super.onCreate()
inst = this
if (!sp.getBoolean("hi42", false)) sp.edit().putString("lang", "hi").putInt("vk", 0).putBoolean("hi42", true).apply()
lg("Jarvis v43")
sp.getString("key", "")?.trim()?.takeIf { it.isNotEmpty() }?.let { k -> Thread { Gemini.warm(k) }.start() }
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
override fun onStart(id: String?) { speaking = true; vSvc?.setPause(true) }
override fun onDone(id: String?) { if (id == "u") { speaking = false; vSvc?.setPause(false) } }
@Deprecated("x") override fun onError(id: String?) { speaking = false }
})
ttsReady = true
}
}
sr = SpeechRecognizer.createSpeechRecognizer(this)
sr.setRecognitionListener(this)
initWake()
}
override fun onDestroy() {
inst = null
h.removeCallbacksAndMessages(null)
sr.destroy(); tts?.shutdown()
try { vSvc?.shutdown(); vModel?.close() } catch (e: Exception) { }
super.onDestroy()
}
fun isEn() = sp.getString("lang", "en") == "en"
private fun curPt() = sp.getFloat("pt", if (isEn()) 0.85f else 0.65f)
fun say(t0: String) {
if (!ttsReady) return
val t = if (isEn()) Tr.en(t0) else t0
try {
val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
val mx = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
if (!sp.getBoolean("manvol", false) && am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) < mx * 0.7) am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (mx * 0.8).toInt(), 0)
} catch (e: Exception) { }
speaking = true
speakMixed(t)
}
private val segRe = Regex("[\\u0900-\\u097F][\\u0900-\\u097F\\s,।?!.]*|[^\\u0900-\\u097F]+")
private fun speakMixed(t: String) {
val tt = tts ?: return
val hv = hiV
val ev = enV
if (isEn() || hv == null || ev == null) { tt.speak(t, TextToSpeech.QUEUE_FLUSH, null, "u"); return }
val segs = segRe.findAll(t).map { it.value.trim() }.filter { s -> s.any { it.isLetterOrDigit() } }.toList()
if (segs.isEmpty()) { tt.speak(t, TextToSpeech.QUEUE_FLUSH, null, "u"); return }
segs.forEachIndexed { i, s ->
val hin = s[0] in 'ऀ'..'ॿ'
tt.setVoice(if (hin) hv else ev)
tt.speak(s, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, if (i == segs.lastIndex) "u" else "p" + i)
}
}
private fun pickEn(): android.speech.tts.Voice? {
val all = try { tts?.voices } catch (e: Exception) { null } ?: return null
val c = all.filter { it.locale.language == "en" && !(it.features?.contains("notInstalled") ?: false) }
val pref = Regex("gbb|gbd|rjs|enb|end")
return c.sortedWith(compareBy(
{ !(it.name.contains("male", true) && !it.name.contains("female", true)) },
{ !pref.containsMatchIn(it.name) },
{ !(it.locale.country == "IN" || it.locale.country == "GB") },
{ it.isNetworkConnectionRequired },
{ it.name }
)).firstOrNull()
}
private fun setLang(l: String) {
sp.edit().putString("lang", l).putInt("vk", 0).apply(); applyVoice()
say(if (l == "en") "अब मैं अंग्रेज़ी में बोलूँगा" else "ठीक है, अब हिंदी में बोलूँगा")
}
private var vModel: Model? = null
private var vSvc: SpeechService? = null
private var vErr = 0
private fun initWake() {
StorageService.unpack(this, "model-en", "model",
StorageService.Callback<Model> { m -> h.post { lg("Vosk model taiyar"); vModel = m; startWake() } },
StorageService.Callback<java.io.IOException> { e -> h.post { Toast.makeText(this, "Offline wake fail: " + e.message, Toast.LENGTH_LONG).show(); listen() } })
}
private fun startWake() {
val m = vModel ?: run { listen(); return }
try {
vSvc?.shutdown()
val r = Recognizer(m, 16000.0f, "[\"jarvis\", \"travis\", \"[unk]\"]")
r.setWords(true)
val sv = SpeechService(r, 16000.0f)
vSvc = sv
lg("Vosk sun raha hai")
sv.startListening(object : org.vosk.android.RecognitionListener {
override fun onPartialResult(p: String?) { }
override fun onResult(p: String?) { chk(p) }
override fun onFinalResult(p: String?) { }
override fun onError(e: Exception?) { h.post { lg("Vosk error: " + e?.message); vSvc = null; vErr++; Toast.makeText(this@JarvisService, "Vosk error: " + e?.message, Toast.LENGTH_LONG).show(); if (vErr >= 3) vModel = null; h.postDelayed({ next() }, 1500) } }
override fun onTimeout() { }
})
} catch (e: Exception) { Toast.makeText(this, "Wake start fail: " + e, Toast.LENGTH_LONG).show(); vModel = null; listen() }
}
private fun chk(j: String?) {
if (j == null) return
try {
val a = JSONObject(j).optJSONArray("result") ?: return
for (i in 0 until a.length()) {
val o = a.getJSONObject(i)
if (o.optString("word") in listOf("jarvis", "travis") && o.optDouble("conf", 0.0) >= 0.85) { h.post { wakeHit(false) }; return }
}
} catch (e: Exception) { }
}
private fun wakeHit(quiet: Boolean) {
val sv = vSvc ?: return
vSvc = null
lg("wake: Jarvis suna")
try { sv.stop(); sv.shutdown() } catch (e: Exception) { }
stayAwake(20000)
if (!quiet) say("जी बॉस?")
h.postDelayed({ listen() }, 60)
}
private fun next() {
if (vModel != null && !awake) startWake() else h.postDelayed({ listen() }, 150)
}
private fun listen() {
if (speaking) { h.postDelayed({ listen() }, 60); return }
val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
.putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANG)
.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, off)
.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
try { sr.startListening(i) } catch (e: Exception) { h.postDelayed({ listen() }, 500) }
}
override fun onResults(b: Bundle?) {
off = false
val l = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
(l?.firstOrNull { wake.containsMatchIn(it.lowercase()) } ?: l?.firstOrNull())?.let { process(it.lowercase()) }
next()
}
override fun onError(e: Int) {
lg("SR error " + e)
sr.cancel()
if (e == 12 || e == 13) LANG = "en-IN"
if (e == 1 || e == 2) off = true
awake = false; tk++
h.postDelayed({ next() }, 600)
}
override fun onReadyForSpeech(p: Bundle?) {}
override fun onBeginningOfSpeech() {}
override fun onRmsChanged(r: Float) {}
override fun onBufferReceived(b: ByteArray?) {}
override fun onEndOfSpeech() {}
override fun onPartialResults(p: Bundle?) {}
override fun onEvent(t: Int, p: Bundle?) {}
private fun process(s: String) {
lg("सुना: " + s)
val m = wake.find(s)
if (m != null) {
val rest = s.substring(m.range.last + 1).trim()
if (rest.isNotEmpty()) handle(rest)
else { say("जी बॉस?"); h.postDelayed({ stayAwake(20000) }, 1500) }
} else if (awake) { awake = false; tk++; handle(s) }
}
private fun handle(s: String) {
when {
Regex("kis ?ne (banaya|bnaya|banaaya|create)|किसने (तुम्हें |तुम्हे |तुमको )?बनाया|who (made|created|built) you").containsMatchIn(s) -> say("मुझे वैभव सर ने बनाया है")
lastMsg.isNotEmpty() && (Regex("^(haan|han|yes|yeah|ji|padho|bolo|sunao)\\b").containsMatchIn(s) || Regex("^(हाँ|हां|पढ़ो|पढ़ दो|सुनाओ|बोलो)").containsMatchIn(s)) -> say(lastMsg)
Regex("(message|msg|massage) ?(kya|padh|sunao)|kya (likha|message|msg)|padh ?do|padh ?ke sunao|read (the )?message|मैसेज (क्या|पढ़|सुना)|क्या (लिखा|मैसेज)|पढ़ के सुनाओ").containsMatchIn(s) -> say(if (lastMsg.isEmpty()) "कोई नया मैसेज नहीं है" else lastMsg)
Regex("(awaaz|awaz|voice|आवाज़|आवाज).*(bhari|bhaari|gehri|deep|heavy|भारी|गहरी)|(bhari|bhaari|gehri|deep|heavy|भारी|गहरी).*(awaaz|awaz|voice|आवाज़|आवाज)").containsMatchIn(s) -> {
sp.edit().putFloat("pt", maxOf(0.4f, curPt() - 0.1f)).apply(); applyVoice()
say("अब आवाज़ और भारी है")
}
Regex("(awaaz|awaz|voice|आवाज़|आवाज).*(patli|halki|light|पतली|हल्की)|(patli|halki|light|पतली|हल्की).*(awaaz|awaz|voice|आवाज़|आवाज)").containsMatchIn(s) -> {
sp.edit().putFloat("pt", minOf(1.2f, curPt() + 0.1f)).apply(); applyVoice()
say("अब आवाज़ हल्की है")
}
Regex("awaaz badlo|awaz badlo|voice change|change voice|आवाज़ बदल|आवाज बदल").containsMatchIn(s) -> {
if (isEn()) sp.edit().putInt("vk", sp.getInt("vk", 0) + 1).apply() else sp.edit().putInt("hvi", sp.getInt("hvi", 0) + 1).apply()
applyVoice()
say("ये मेरी नई आवाज़ है, पसंद आई?")
}
Regex("(कॉल|फोन|फ़ोन|call|phone).*(उठा|रिसीव|अटेंड|receive|utha|pick|attend)").containsMatchIn(s) -> callCtl(true)
Regex("(कॉल|फोन|फ़ोन|call|phone).*(काट|कट|रिजेक्ट|reject|cut|kaat|disconnect)").containsMatchIn(s) -> callCtl(false)
Regex("english|इंग्लिश|अंग्रेज").containsMatchIn(s) && Regex("bol|बोल|speak").containsMatchIn(s) -> setLang("en")
Regex("hindi|हिंदी|हिन्दी").containsMatchIn(s) && Regex("bol|बोल|speak").containsMatchIn(s) -> setLang("hi")
Regex("auto ?reply|ऑटो ?रिप्लाई").containsMatchIn(s) -> {
val on = !Regex("band|off|बंद|बन्द|ऑफ|हटा").containsMatchIn(s)
sp.edit().putBoolean("ar", on).apply()
say(if (on) "ऑटो रिप्लाई चालू है" else "ऑटो रिप्लाई बंद है")
}
Regex("^(yaad rakh|yaad rakho|remember)|^याद रख").containsMatchIn(s) -> remember(s)
Regex("torch|flashlight|टॉर्च|टोर्च|फ्लैशलाइट").containsMatchIn(s) -> torch(!Regex("\\b(off|band|bandh)\\b|बंद|बन्द|ऑफ").containsMatchIn(s))
Regex("(message|मैसेज|मेसेज|notification|नोटिफिकेशन).*(batana|बताना|बताओ)|(batana|बताना|बताओ).*(message|मैसेज|मेसेज|notification|नोटिफिकेशन)").containsMatchIn(s) -> {
val on = !Regex("band|बंद|बन्द|off|ऑफ|रोक").containsMatchIn(s)
sp.edit().putBoolean("an", on).apply()
say(if (on) "मैसेज बताना चालू" else "मैसेज बताना बंद")
}
Regex("(volume|वॉल्यूम|आवाज़|आवाज)").containsMatchIn(s) && Regex("badha|बढ़ा|ज़्यादा|zyada|तेज़|tez|full|फुल|kam|कम|घटा|ghata|धीमा|dheema|mute|म्यूट|max|min").containsMatchIn(s) -> volume(s)
Regex("(gaana|gana|song|गाना|गाने)").containsMatchIn(s) && Regex("badlo|बदलो|बदल|next|नेक्स्ट|agla|अगला").containsMatchIn(s) -> media("next")
Regex("(pichla|पिछला|previous|पिछले)").containsMatchIn(s) && Regex("(gaana|gana|song|गाना|गाने)").containsMatchIn(s) -> media("previous")
Regex("(bluetooth|ब्लूटूथ|ब्लुटूथ)").containsMatchIn(s) && !Regex("जोड़|pair|connect|कनेक्ट").containsMatchIn(s) -> bluetooth(!Regex("off|band|बंद|बन्द|ऑफ").containsMatchIn(s))
Regex("(\\bad\\b|ads|विज्ञापन|एड).*(skip|स्किप|छोड़)|(skip|स्किप).*(\\bad\\b|ads|विज्ञापन|एड)").containsMatchIn(s) -> {
val on = !Regex("band|बंद|बन्द|off|ऑफ|रोक").containsMatchIn(s)
sp.edit().putBoolean("ys", on).apply()
say(if (on) "ऐड स्किप चालू" else "ऐड स्किप बंद")
}
Regex("insta|इंस्टा").containsMatchIn(s) && !Regex("bhej|भेज|send|likh|लिख").containsMatchIn(s) -> insta(s)
Regex("schedule|शेड्यूल|कैलेंडर|calendar|agenda|एजेंडा|मीटिंग").containsMatchIn(s) -> agenda()
Regex("कितने बजे|टाइम क्या|samay kya|time kya|what time|बजे हैं").containsMatchIn(s) -> say("अभी " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) + " हुए हैं")
else -> ask(s)
}
}
private fun insta(s: String) {
val pk = "com.instagram.android"
fun open(u: String?): Boolean {
val i = (if (u != null) Intent(Intent.ACTION_VIEW, Uri.parse(u)).setPackage(pk) else packageManager.getLaunchIntentForPackage(pk)) ?: return false
return try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); true } catch (e: Exception) { false }
}
val me = sp.getString("igu", "")?.trim().orEmpty().removePrefix("@")
val ok = when {
Regex("inbox|इनबॉक्स|message|मैसेज|dm|डीएम").containsMatchIn(s) -> { say("इंस्टाग्राम इनबॉक्स खोल रहा हूँ"); open("instagram://direct_inbox") || open(null) }
Regex("profile|प्रोफाइल|प्रोफ़ाइल").containsMatchIn(s) -> {
if (me.isEmpty()) { say("सेटिंग्स में अपना इंस्टाग्राम यूज़रनेम डालो"); true }
else { say("तुम्हारी प्रोफाइल खोल रहा हूँ"); open("instagram://user?username=" + me) || open(null) }
}
Regex("camera|कैमरा|story|स्टोरी|post|पोस्ट").containsMatchIn(s) -> { say("इंस्टाग्राम कैमरा खोल रहा हूँ"); open("instagram://camera") || open(null) }
else -> { say("इंस्टाग्राम खोल रहा हूँ"); open(null) }
}
if (!ok) say("इंस्टाग्राम नहीं मिला")
}
fun alert(who: String) {
h.post { say(if (isEn()) "Boss, " + who + " insulted you. I have replied and paused for ten minutes." else "बॉस, " + who + " ने आपकी बेइज़्ज़ती की। मैंने जवाब दे दिया और दस मिनट के लिए रोक दिया।") }
}
private fun callCtl(accept: Boolean) {
if (MsgListener.inst?.callAct(accept) == true) { say(if (accept) "कॉल उठा लिया" else "कॉल काट दिया"); return }
try {
val tm = getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
if (accept) { tm.acceptRingingCall(); lg("call utha liya"); say("कॉल उठा लिया") }
else if (Build.VERSION.SDK_INT >= 28) { tm.endCall(); lg("call kaata"); say("कॉल काट दिया") }
} catch (e: SecurityException) { lg("call permission: " + e.message); say("कॉल की permission दो") } catch (e: Exception) { lg("call error: " + e); say("ये नहीं हो पाया") }
}
private fun applyVoice() {
val en = isEn()
tts?.setPitch(curPt()); tts?.setSpeechRate(if (en) 0.95f else 1.05f)
var r = tts?.setLanguage(if (en) Locale.UK else Locale("hi", "IN")) ?: -2
if (en && r < 0) r = tts?.setLanguage(Locale("en", "IN")) ?: -2
if (en && r < 0) tts?.setLanguage(Locale.US)
val pref = Regex(if (en) "gbb|gbd|rjs" else "hic|hid|hib")
val lang = tts?.voice?.locale?.language ?: ""
var v = (tts?.voices ?: emptySet<android.speech.tts.Voice>()).filter { it.locale.language == lang && !(it.features?.contains("notInstalled") ?: false) }
val uk = v.filter { it.locale == Locale.UK }
if (en && uk.isNotEmpty()) v = uk
v = v.sortedWith(compareBy({ !(it.name.contains("male", true) && !it.name.contains("female", true)) }, { !pref.containsMatchIn(it.name) }, { it.isNetworkConnectionRequired }, { it.name }))
if (v.isNotEmpty()) tts?.voice = v[(if (en) sp.getInt("vk", 0) else sp.getInt("hvi", 0)) % v.size]
if (!en) hiV = tts?.voice
if (enV == null) enV = pickEn()
lg("awaaz: " + tts?.voice?.name + " (" + v.size + " mili) en=" + enV?.name)
}
private fun remember(s: String) {
val fact = s.replace(Regex("^(yaad rakh|yaad rakho|remember|याद रख|याद रखो)( ki| that| कि)?"), "").trim()
sp.edit().putString("prof", sp.getString("prof", "") + " " + fact + ".").apply()
say("याद रख लिया")
}
private fun ask(q: String) {
val key = sp.getString("key", "")?.trim().orEmpty()
if (key.isEmpty()) { if (!fallback(q)) say(if (isEn()) "I need a Gemini key to understand that, but I can still handle calls, messages, YouTube, alarms, weather and music." else "यह समझने के लिए Gemini key चाहिए। बिना key के मैं कॉल, मैसेज, यूट्यूब, अलार्म, मौसम और म्यूज़िक जैसे आसान काम कर सकता हूँ।"); return }
val hh = ArrayList(hist)
Thread {
val sys = Tr.sys(sp.getString("prof", "") ?: "", isEn())
val raw = Gemini.call(key, sys, hh, q)
h.post {
if (raw == null) { lg("Gemini fail: " + Gemini.lastErr); if (!fallback(q)) { say(if (Gemini.lastErr.contains("resolve") || Gemini.lastErr.contains("Unknown")) "इंटरनेट नहीं है, अभी सिर्फ़ टॉर्च और समय बता सकता हूँ" else "जवाब नहीं मिल पाया, थोड़ी देर बाद फिर कोशिश करो"); Toast.makeText(this, Gemini.lastErr.take(300), Toast.LENGTH_LONG).show() } }
else {
lg("Gemini: " + raw)
var jj: JSONObject? = null
try { jj = JSONObject(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()) } catch (e: Exception) { }
val j = jj
val sy = (j?.optString("say") ?: raw).replace(Regex("[*#`_]"), "")
hist.add(q to sy); if (hist.size > 6) hist.removeAt(0)
val a = j?.optString("action") ?: "none"
if (j == null || a == "none" || a.isEmpty()) say(dv(sy)) else act(a, j)
}
}
}.start()
}
private fun act(a: String, j: JSONObject) {
lg("action: " + a + " " + j.optString("name") + j.optString("number") + j.optString("query") + j.optString("app"))
val who = j.optString("number").ifEmpty { j.optString("name") }
val tx = j.optString("text")
when (a) {
"call" -> call("call $who")
"sms" -> if (tx.isEmpty()) say("क्या मैसेज भेजना है?") else msg("sms $who ko $tx")
"whatsapp" -> msg("whatsapp $who ko $tx")
"instagram" -> {
val u = j.optString("username").trim().removePrefix("@")
if (u.isEmpty() || tx.isEmpty()) say("किसे और क्या मैसेज भेजना है?")
else {
igText = tx; igAt = System.currentTimeMillis()
say("$u को Instagram पर भेज रहा हूँ: $tx")
val url = "https://ig.me/m/" + Uri.encode(u)
try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.instagram.android").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
catch (e: Exception) { go(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
}
"youtube" -> you
