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
@Volatile var ytAt = 0L
@Volatile var orbSpeak = false
@Volatile var callOn = false
@Volatile var orbLevel = 0f
@Volatile var orbLevelAt = 0L
@Volatile var btWant = 0
@Volatile var btAt = 0L
@Volatile var btName = ""
@Volatile var fsAt = 0L
@Volatile var fsStage = 0
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
if (calm()) return
val g = if (group.isEmpty()) "" else group + " ग्रुप में "
h.post {
if (isEn()) say("Boss, " + who + " messaged you on " + app + (if (group.isEmpty()) "" else " in " + group) + ". Shall I read it?")
else say("बॉस, " + who + " का " + ap + " पर " + g + "मैसेज आया है, पढ़ूँ?")
annWait = true
h.postDelayed({ wakeHit(true) }, 3500)
h.postDelayed({ annWait = false }, 9000)
}
}
private val sp by lazy { getSharedPreferences("j", Context.MODE_PRIVATE) }
private val hist = ArrayList<Pair<String, String>>()
@Volatile private var speaking = false
private val wake = Regex("jarvis|jarvish|jar vis|jarwis|जार्विस|जारविस|जर्विस|जार्वीस|जारवीस|जरविस|ज़ार्विस|जार्विश")
private var annWait = false
private val YES = Regex("^(haan|han|yes|yeah|ji|padho|bolo|sunao|हाँ|हां|जी|पढ़ो|पढ़ दो|सुनाओ|बोलो)")
private fun playing(): Boolean = try { (getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager).isMusicActive } catch (e: Exception) { false }
private fun calm(): Boolean = sp.getBoolean("calm", true) && playing()
private var LANG = "hi-IN"
private var off = false
override fun onBind(i: Intent?): IBinder? = null
override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
if (i?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
if (i?.action == "TAKE") { h.post { callTakeSay() }; return START_STICKY }
return START_STICKY
}
override fun onCreate() {
super.onCreate()
inst = this
if (!sp.getBoolean("pt2", false)) sp.edit().putFloat("pt", 0.85f).putFloat("rt", 1.0f).putBoolean("pt2", true).apply()
sp.registerOnSharedPreferenceChangeListener(prefL)
if (!sp.getBoolean("hi42", false)) sp.edit().putString("lang", "en").putInt("vk", 0).putBoolean("hi42", true).apply()
lg("Jarvis v53")
sp.getString("key", "")?.trim()?.takeIf { it.isNotEmpty() }?.let { k -> Thread { Gemini.warm(k) }.start() }
getSystemService(NotificationManager::class.java)
.createNotificationChannel(NotificationChannel("jarvis", "Jarvis", NotificationManager.IMPORTANCE_LOW))
val n = Notification.Builder(this, "jarvis").setContentTitle("Jarvis is listening").setContentText("Say: Jarvis shut down, to stop")
.setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
else startForeground(1, n)
if (!License.valid(this)) { stopSelf(); return }
tts = TextToSpeech(this) {
if (it == TextToSpeech.SUCCESS) {
tts?.language = Locale("hi", "IN")
applyVoice()
tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
override fun onStart(id: String?) { speaking = true; orbSpeak = true; vSvc?.setPause(true) }
override fun onDone(id: String?) { if (id == "u") { speaking = false; orbSpeak = false; vSvc?.setPause(false); h.post { unstyle(); afterSpeech() } } }
@Deprecated("x") override fun onError(id: String?) { speaking = false; orbSpeak = false; h.post { unstyle() } }
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
try { sp.unregisterOnSharedPreferenceChangeListener(prefL) } catch (e: Exception) { }
sr.destroy(); tts?.shutdown()
try { vSvc?.shutdown(); vModel?.close() } catch (e: Exception) { }
super.onDestroy()
}
fun isEn() = sp.getString("lang", "en") == "en"
private fun curPt() = sp.getFloat("pt", 0.85f)
private fun curRt() = sp.getFloat("rt", 1.0f)
fun say(t0: String) {
if (!ttsReady) return
val t = if (isEn()) Tr.en(t0) else Pron.fix(t0)
try {
val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
val mx = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
if (!sp.getBoolean("manvol", false) && am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) < mx * 0.7) am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (mx * 0.8).toInt(), 0)
} catch (e: Exception) { }
speaking = true; orbSpeak = true
if (CloneVoice.active(sp)) {
vSvc?.setPause(true)
CloneVoice.speak(this, sp, if (isEn()) t else t0, { speaking = false; orbSpeak = false; vSvc?.setPause(false); h.post { unstyle(); afterSpeech() } }, { speakMixed(t) })
return
}
speakMixed(t)
}
private val segRe = Regex("[\\u0900-\\u097F][\\u0900-\\u097F\\s,।?!.]*|[^\\u0900-\\u097F]+")
private fun speakMixed(t: String) {
val tt = tts ?: return
val hv = hiV
val ev = enV
if (isEn() || hv == null || ev == null || sp.getBoolean("onevoice", true)) {
if (!isEn() && hv != null) tt.setVoice(hv)
tt.speak(t, TextToSpeech.QUEUE_FLUSH, null, "u"); return
}
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
if (callOn) return
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
if (o.optString("word") == "jarvis" && o.optDouble("conf", 0.0) >= (if (playing()) 0.97 else 0.9)) {
if (calm()) { lg("shaant mode: media chal raha hai, nahi jaaga"); return }
h.post { wakeHit(false) }; return
}
}
} catch (e: Exception) { }
}
private var convFollow = false
private fun afterSpeech() {
if (callOn) { callAfterSpeech(); return }
if (!sp.getBoolean("conv", true) || playing() || annWait || stopping) return
convFollow = true
if (vSvc != null) wakeHit(true)
stayAwake(15000)
}
private var stopping = false
private fun wakeHit(quiet: Boolean) {
val sv = vSvc ?: return
vSvc = null
lg("wake: Jarvis suna")
try { sv.stop(); sv.shutdown() } catch (e: Exception) { }
stayAwake(if (quiet) 7000 else 10000)
if (!quiet) say("जी बॉस?")
h.postDelayed({ listen() }, 60)
}
private fun next() {
if (callOn) { h.postDelayed({ listen() }, 300); return }
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
if (callOn) { callHear(b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""); return }
val l = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
(l?.firstOrNull { wake.containsMatchIn(it.lowercase()) } ?: l?.firstOrNull())?.let { process(it.lowercase()) }
next()
}
override fun onError(e: Int) {
lg("SR error " + e)
sr.cancel()
if (callOn) { callQuiet(); return }
if (e == 12 || e == 13) LANG = "en-IN"
if (e == 1 || e == 2) off = true
awake = false; tk++
h.postDelayed({ next() }, 600)
}
override fun onReadyForSpeech(p: Bundle?) {}
override fun onBeginningOfSpeech() {}
override fun onRmsChanged(r: Float) { orbLevel = (r / 10f).coerceIn(0f, 1f); orbLevelAt = System.currentTimeMillis() }
override fun onBufferReceived(b: ByteArray?) {}
override fun onEndOfSpeech() {}
override fun onPartialResults(p: Bundle?) {}
override fun onEvent(t: Int, p: Bundle?) {}
private fun process(s: String) {
if (!awake && calm()) return
lg("सुना: " + s)
val m = wake.find(s)
if (m != null) {
val rest = s.substring(m.range.last + 1).trim()
if (rest.isNotEmpty()) handle(rest)
else { say("जी बॉस?"); h.postDelayed({ stayAwake(10000) }, 1500) }
} else if (awake) {
if (convFollow && !annWait && s.trim().split(Regex("\\s+")).size < 2) return
convFollow = false
awake = false; tk++
if (annWait) { annWait = false; if (YES.containsMatchIn(s)) say(lastMsg) }
else handle(s)
}
}
private val aliases = mapOf("यूट्यूब" to "youtube","यू ट्यूब" to "youtube","व्हाट्सऐप" to "whatsapp","व्हाट्सएप" to "whatsapp","वॉट्सऐप" to "whatsapp","इंस्टाग्राम" to "instagram","फेसबुक" to "facebook","गूगल" to "google","क्रोम" to "chrome","टेलीग्राम" to "telegram","जीमेल" to "gmail","स्नैपचैट" to "snapchat","नेटफ्लिक्स" to "netflix","स्पॉटिफाई" to "spotify","मैप्स" to "maps","कैमरा" to "camera","गैलरी" to "gallery","हॉटस्टार" to "hotstar","अमेज़न" to "amazon","फोनपे" to "phonepe","गूगल पे" to "google pay","ट्विटर" to "twitter","शेयरचैट" to "sharechat","मोज" to "moj")
private val notApp = Regex("jarvis|जार्विस|torch|टॉर्च|टोर्च|bluetooth|ब्लूटूथ|auto|ऑटो|message|मैसेज|\\bad\\b|ऐड|wifi|वाईफाई|awaaz|आवाज|mimic|मिमिक्री|volume|वॉल्यूम|gaana|गाना|music|song")
private fun closeApp(s: String): Boolean {
val m = Regex("^(.+?)\\s*(band (karo|kro|kar do|kardo|kar de)|बंद (करो|कर दो|कर दे)|close( karo| kar do)?|kill)\\s*$").find(s) ?: return false
val n0 = m.groupValues[1].replace(Regex("\\b(ko|app|ye|yeh|is|isko|abhi|please)\\b|को|ऐप्प|ऐप|इसको|अभी"), " ").replace(Regex("\\s+"), " ").trim()
if (n0.isEmpty() || notApp.containsMatchIn(n0)) return false
val n = aliases[n0] ?: n0
val pm = packageManager
val li = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).filter { it.activityInfo.packageName != packageName }
val hit = li.firstOrNull { it.loadLabel(pm).toString().lowercase() == n } ?: li.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(n) } ?: return false
val pk = hit.activityInfo.packageName
say(n0 + " बंद कर रहा हूँ")
lg("Band: " + pk)
if (checkSelfPermission("android.permission.KILL_BACKGROUND_PROCESSES") == PackageManager.PERMISSION_GRANTED) {
fsAt = 0L
go(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
for (d in longArrayOf(900, 2500, 5000)) h.postDelayed({ try { am.killBackgroundProcesses(pk) } catch (e: Exception) { } }, d)
return true
}
fsAt = System.currentTimeMillis(); fsStage = 0
go(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + pk)))
return true
}
private fun handle(s: String) {
when {
Regex("^.+\\s*(band (karo|kro|kar do|kardo|kar de)|बंद (करो|कर दो|कर दे)|close( karo| kar do)?|kill)\\s*$").containsMatchIn(s.trim()) && closeApp(s.trim()) -> {}
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
Regex("(कॉल|फोन|फ़ोन|call|phone).*(उठा|रिसीव|अटेंड|receive|utha|pick|attend|answer)|^(utha|uthao|utha lo|utha do|pick up|answer|उठाओ|उठा लो|उठा दो)\\b").containsMatchIn(s) -> callCtl(true)
Regex("(कॉल|फोन|फ़ोन|call|phone).*(काट|कट|रिजेक्ट|reject|cut|kaat|disconnect|hang|end)|^(kaat|kaato|kaat do|cut|reject|decline|काट दो|काटो|कट कर)\\b").containsMatchIn(s) -> callCtl(false)
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
Regex("band ho ?ja|बंद हो ?जा|apne aap ko band|अपने आप को बंद|shut ?down|शट ?डाउन|app band|ऐप बंद|exit").containsMatchIn(s) -> { stopping = true; say("ठीक है सर, बंद हो रहा हूँ"); h.postDelayed({ stopSelf() }, 2500) }
Regex("mimicry|mimic|मिमिक्री|nakal|नकल").containsMatchIn(s) -> mimic(s)
Regex("insta|इंस्टा").containsMatchIn(s) && !Regex("bhej|भेज|send|likh|लिख").containsMatchIn(s) -> insta(s)
Regex("schedule|शेड्यूल|कैलेंडर|calendar|agenda|एजेंडा|मीटिंग").containsMatchIn(s) -> agenda()
Regex("कितने बजे|टाइम क्या|samay kya|time kya|what time|बजे हैं").containsMatchIn(s) -> say("अभी " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) + " हुए हैं")
else -> ask(s)
}
}
private var styled = false
private fun sayStyled(t: String, pt: Float, rt: Float) {
styled = true
tts?.setPitch(pt.coerceIn(0.4f, 1.6f)); tts?.setSpeechRate(rt.coerceIn(0.7f, 1.4f))
say(t)
}
private fun unstyle() {
if (styled) { styled = false; tts?.setPitch(curPt()); tts?.setSpeechRate(curRt()) }
}
private val MIM = """Tum Jarvis ho. User kisi ki nakal (mimicry) karne ko keh raha hai. Sirf unke bolne ke andaaz, lahje aur mashhoor adaaon ki halki-fulki, harmless nakal karo. Asli awaaz ki copy nahi, sirf andaaz.
Niyam: koi gaali, beizzati, rajneeti, dharm ya jhoota bayan us vyakti ke naam se nahi. Kisi nijee (private) insaan ya bachche ki nakal nahi. Lambe mashhoor dialogue ya gaane ki line nahi dohrani. 2 se 3 chhote vakya, Devanagari Hindi mein, English shabd bhi Devanagari mein likho.
Agar nakal ke layak nahi to chhota sa shalin jawab do aur kisi mazedaar kirdaar (jaise purane zamane ka announcer) ki nakal karo.
Sirf JSON: {"say":"...","pitch":0.4 se 1.6,"rate":0.7 se 1.4}. Bhaari awaaz ke liye pitch kam, patli ya bachche ki awaaz ke liye zyada."""
private fun mimic(s: String) {
if (Regex("band|बंद|रोक|stop|off").containsMatchIn(s)) { unstyle(); say("ठीक है, अब अपनी आवाज़ में बोलूँगा"); return }
val pre: Triple<String, Float, Float>? = when {
Regex("robot|रोबोट").containsMatchIn(s) -> Triple("मैं रोबोट हूँ। बीप बूप। आदेश दीजिए।", 0.5f, 0.85f)
Regex("buddh|बूढ़|बुढ़|दादा|nana|नाना").containsMatchIn(s) -> Triple("अरे बेटा, हमारे ज़माने में तो सब पैदल चलते थे।", 0.55f, 0.8f)
Regex("bachch|बच्च").containsMatchIn(s) -> Triple("मम्मी मम्मी, मुझे टॉफी चाहिए!", 1.5f, 1.15f)
Regex("gussa|गुस्स").containsMatchIn(s) -> Triple("अरे! ये क्या बदतमीज़ी है! फिर से करो सब कुछ!", 0.6f, 1.25f)
else -> null
}
if (pre != null) { sayStyled(pre.first, pre.second, pre.third); return }
val key = sp.getString("key", "")?.trim().orEmpty()
if (key.isEmpty()) { say("बिना Gemini key के मैं सिर्फ़ रोबोट, बूढ़ा, बच्चा या गुस्सैल की नकल कर सकता हूँ"); return }
Thread {
val raw = Gemini.call(key, MIM, ArrayList<Pair<String, String>>(), "Request: " + s)
h.post {
val j = try { JSONObject(raw!!.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()) } catch (e: Exception) { null }
val tx = j?.optString("say")?.replace(Regex("[*#`_]"), "").orEmpty()
if (j == null || tx.isEmpty()) say("नकल नहीं हो पाई, फिर कोशिश करो")
else sayStyled(tx, j.optDouble("pitch", 0.65).toFloat(), j.optDouble("rate", 1.0).toFloat())
}
}.start()
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
if (checkSelfPermission(android.Manifest.permission.ANSWER_PHONE_CALLS) != android.content.pm.PackageManager.PERMISSION_GRANTED) { lg("call: ANSWER_PHONE_CALLS permission nahi"); say("कॉल की permission दो"); return }
val tm = getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
if (accept) { tm.acceptRingingCall(); lg("call utha liya"); say("कॉल उठा लिया") }
else if (Build.VERSION.SDK_INT >= 28) { tm.endCall(); lg("call kaata"); say("कॉल काट दिया") }
} catch (e: SecurityException) { lg("call permission: " + e.message); say("कॉल की permission दो") } catch (e: Exception) { lg("call error: " + e); say("ये नहीं हो पाया") }
}
private val prefL = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
if (k == "vk" || k == "hvi" || k == "pt" || k == "rt" || k == "lang" || k == "onevoice") h.post { if (ttsReady) applyVoice() }
}
private fun applyVoice() {
val en = isEn()
tts?.setPitch(curPt()); tts?.setSpeechRate(curRt())
var r = tts?.setLanguage(if (en) Locale.UK else Locale("hi", "IN")) ?: -2
if (en && r < 0) r = tts?.setLanguage(Locale("en", "IN")) ?: -2
if (en && r < 0) tts?.setLanguage(Locale.US)
val pref = Regex(if (en) "gbb|gbd|rjs" else "hic|hid|hib")
val lang = tts?.voice?.locale?.language ?: ""
var v = (tts?.voices ?: emptySet<android.speech.tts.Voice>()).filter { it.locale.language == lang && !(it.features?.contains("notInstalled") ?: false) }
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
private val infoRe = Regex("kitna|kitni|kitne|kaun|kaise|kya hai|kya hota|kya hoti|kab |kahan|kaha |kyun|kyu |kaunsa|kaunsi|distance|door|\\bdur\\b|how far|how many|how much|how long|how old|how tall|what is|what's|what are|who is|who was|when |where |why |capital|population|price|rate|score|news|khabar|tell me|batao|bata do|कितना|कितनी|कितने|कौन|क्या है|क्या होता|कब |कहाँ|कहां|क्यों|कैसे|दूर|राजधानी|कीमत|भाव|स्कोर|खबर|ख़बर|बताओ|बता दो")
private val cmdRe = Regex("call|msg|message|sms|whatsapp|youtube|alarm|torch|play|chalao|chala do|kholo|khol do|open|bhejo|bhej do|insta|weather|mausam|gaana|gana|song|volume|bluetooth|reminder|timer|कॉल|मैसेज|अलार्म|टॉर्च|चलाओ|खोलो|भेजो|भेज दो|मौसम|गाना|वॉल्यूम|ब्लूटूथ|यूट्यूब|इंस्टा|व्हाट्सऐप|टाइमर")
private fun whereAmI(): String {
return try {
val lm = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
val l = lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time } ?: return ""
var city = ""
try {
@Suppress("DEPRECATION") val a = android.location.Geocoder(this, Locale("en", "IN")).getFromLocation(l.latitude, l.longitude, 1)
if (!a.isNullOrEmpty()) city = listOfNotNull(a[0].subLocality, a[0].locality, a[0].adminArea).joinToString(", ")
} catch (e: Exception) { }
" User ke phone ki abhi ki jagah: " + l.latitude + "," + l.longitude + (if (city.isNotEmpty()) " (" + city + ")" else "") + ". 'Yahan se' ka matlab yehi jagah hai."
} catch (e: Exception) { "" }
}
private fun ask(q: String) {
val key = sp.getString("key", "")?.trim().orEmpty()
if (key.isNotEmpty() && infoRe.containsMatchIn(q.lowercase()) && !cmdRe.containsMatchIn(q.lowercase())) { askInfo(q, key); return }
if (key.isEmpty() && infoRe.containsMatchIn(q.lowercase()) && !cmdRe.containsMatchIn(q.lowercase())) { wikiOnly(q); return }
askJson(q)
}
private fun wikiOnly(q: String) {
Thread {
val en = isEn()
val w = Wiki.lookup(q, if (en) "en" else "hi") ?: (if (!en) Wiki.lookup(q, "en") else null)
h.post {
if (w == null) { if (!fallback(q)) say(if (isEn()) "I could not find that on Wikipedia." else "यह मुझे विकिपीडिया पर नहीं मिला।") }
else {
val sents = w.second.split(Regex("(?<=[.।])\\s+")).take(2).joinToString(" ")
say(dv((if (en) "According to Wikipedia, " else "विकिपीडिया के मुताबिक, ") + sents))
}
}
}.start()
}
private fun askInfo(q: String, key: String) {
val hh = ArrayList(hist)
Thread {
val en = isEn()
val wk = Wiki.lookup(q, if (en) "en" else "hi") ?: (if (!en) Wiki.lookup(q, "en") else null)
val wkTxt = if (wk != null) " Wikipedia ka lekh (" + wk.first + "): " + wk.second.take(1200) + " Agar sawal ka jawab isme hai to isi ke aadhaar par do aur jawab ki shuruaat " + (if (en) "\"According to Wikipedia,\" se karo." else "\"विकिपीडिया के मुताबिक,\" se karo.") + " Agar isme nahi hai to search se do." else ""
val sys = "Tum Jarvis ho, " + sp.getString("owner", "")?.trim().orEmpty().ifEmpty { "Boss" } + " ke phone assistant, Google jaise sahi aur kaam ke jawab dene wale. Jawab chhota rakho (2-4 vaakya) par poori kaam ki jaankari do: doori pooche to kilometre mein sadak ki doori, gaadi se lagbhag kitna samay lagega, aur ek-do chhoti kaam ki baatein (jaise raasta ya mashhoor cheez). Taza jaankari ke liye search use karo. Markdown, bullet, emoji, star, link bilkul nahi. Number saaf bolo. " + (if (en) "Jawab English mein do." else "Jawab Devanagari Hindi mein do, English shabd English mein.") + " Abhi: " + java.util.Date() + whereAmI() + wkTxt
val raw = Gemini.info(key, sys, hh, q)
h.post {
if (raw == null) { lg("info fail: " + Gemini.lastErr); askJson(q) }
else {
val sy = raw.replace(Regex("[*#`_\\[\\]]"), "").replace(Regex("\\(https?://[^)]*\\)"), "").trim()
hist.add(q to sy); if (hist.size > 6) hist.removeAt(0)
lg("Info: " + sy.take(120))
say(dv(sy))
}
}
}.start()
}
private fun askJson(q: String) {
val key = sp.getString("key", "")?.trim().orEmpty()
if (key.isEmpty()) { if (!fallback(q)) say(if (isEn()) "I need a Gemini key to understand that, but I can still handle calls, messages, YouTube, alarms, weather and music." else "यह समझने के लिए Gemini key चाहिए। बिना key के मैं कॉल, मैसेज, यूट्यूब, अलार्म, मौसम और म्यूज़िक जैसे आसान काम कर सकता हूँ।"); return }
val hh = ArrayList(hist)
Thread {
val sys = Tr.sys(sp.getString("prof", "") ?: "", isEn(), sp.getString("owner", "")?.trim().orEmpty().ifEmpty { "Boss" })
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
"youtube" -> youtube("youtube " + j.optString("query"))
"maps" -> { val q = j.optString("query"); say(q + " का रास्ता"); go(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q)))) }
"alarm" -> alarm("alarm " + j.optInt("hour") + ":" + String.format("%02d", j.optInt("minute")), tx)
"agenda" -> agenda()
"torch" -> torch(j.optBoolean("on", true))
"openapp" -> openApp("open " + j.optString("app").lowercase())
"weather" -> weather(j.optString("query"))
"media" -> media(j.optString("query").lowercase())
"bluetooth" -> bluetooth(j.optBoolean("on", true))
"btpair" -> btPair(j.optString("name"))
"wacall" -> waCall(j.optString("name"), j.optString("query").contains("video", true))
"volume" -> volume(j.optString("query").lowercase())
else -> say(j.optString("say"))
}
}
// ---------- CALL ASSISTANT ----------
private var callWho = ""
private var callT0 = 0L
private var callTurns = 0
private var callSmsN = 0
private var callSilent = 0
private var callLast = 0L
private var callPend = false
private var callEndAfter = false
private var callTakeAfter = false
private val callHist = ArrayList<Pair<String, String>>()
private val callLines = ArrayList<String>()
private fun callOwn() = sp.getString("owner", "")?.trim().orEmpty().ifEmpty { "the owner" }
private fun callBn() = sp.getString("bn", "")?.trim().orEmpty().ifEmpty { "our business" }
private fun callAm() = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
private fun inCallMode(): Boolean { val m = callAm().mode; return m == android.media.AudioManager.MODE_IN_CALL || m == android.media.AudioManager.MODE_IN_COMMUNICATION }

fun callIncoming(who: String) {
if (callOn || callPend || !sp.getBoolean("autocall", false)) return
val unsaved = who.firstOrNull()?.let { it == '+' || it.isDigit() } == true
if (sp.getBoolean("acu", false) && !unsaved) return
callPend = true
lg("call aaya: " + who)
h.postDelayed({
if (inCallMode() || callOn) { callPend = false; return@postDelayed }
val ok = MsgListener.inst?.callAct(true) == true || callAccept()
if (!ok) { callPend = false; lg("call: utha nahi paya"); return@postDelayed }
callWait(who, 0)
}, sp.getInt("acd", 4) * 1000L)
}
private fun callAccept(): Boolean = try {
if (checkSelfPermission(android.Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
(getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager).acceptRingingCall(); true
} else false
} catch (e: Exception) { false }
private fun callWait(who: String, n: Int) {
if (inCallMode()) { callPend = false; callBegin(who); return }
if (n > 16) { callPend = false; lg("call: connect nahi hua"); return }
h.postDelayed({ callWait(who, n + 1) }, 400)
}
private fun callAudio(on: Boolean) {
try {
val am = callAm()
am.isSpeakerphoneOn = on
if (on) {
am.setStreamVolume(android.media.AudioManager.STREAM_VOICE_CALL, am.getStreamMaxVolume(android.media.AudioManager.STREAM_VOICE_CALL), 0)
am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) * 0.9).toInt(), 0)
}
} catch (e: Exception) { lg("speaker: " + e) }
try {
tts?.setAudioAttributes(android.media.AudioAttributes.Builder()
.setUsage(if (on) android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION else android.media.AudioAttributes.USAGE_ASSISTANT)
.setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
} catch (e: Exception) { }
}
private fun callBegin(who: String) {
callOn = true
callWho = who; callT0 = System.currentTimeMillis(); callTurns = 0; callSmsN = 0; callSilent = 0; callLast = callT0
callEndAfter = false; callTakeAfter = false; callHist.clear(); callLines.clear()
val sv = vSvc; vSvc = null
try { sv?.stop(); sv?.shutdown() } catch (e: Exception) { }
try { sr.cancel() } catch (e: Exception) { }
awake = false; tk++
for (d in longArrayOf(300, 1200, 2500)) h.postDelayed({ if (callOn) callAudio(true) }, d)
smsOwner("Jarvis ne " + who + " ka call utha liya. Baat-cheet ke update aate rahenge. Khud baat karni ho to notification mein TAKE OVER dabao ya bolo: Jarvis, call mujhe do.")
callNote("Call: " + who, "Answered by Jarvis")
val g = if (sp.getString("arm", "p") == "b") "Hello, this is the AI assistant of " + callBn() + ". How can I help you?"
else "Hello, this is Jarvis, the AI assistant of " + callOwn() + ". " + callOwn() + " is not available right now. How may I help you?"
h.postDelayed({ if (callOn) say(g) }, 1500)
callWatch()
}
private fun callWatch() {
h.postDelayed({
if (!callOn) return@postDelayed
val now = System.currentTimeMillis()
if (now - callT0 > 5000 && !inCallMode()) { callEnd(false); return@postDelayed }
if (now - callT0 > 300000) { say("I have to end the call now. " + callOwn() + " will get back to you."); callEndAfter = true }
else if (!speaking && now - callLast > 15000) { callLast = now; try { sr.cancel() } catch (e: Exception) { }; listen() }
callWatch()
}, 1500)
}
private fun callAfterSpeech() {
callLast = System.currentTimeMillis()
if (callTakeAfter) { callTakeAfter = false; callTake(); return }
if (callEndAfter) { callEnd(true); return }
h.postDelayed({ if (callOn) listen() }, 350)
}
private fun callQuiet() {
if (!callOn) return
callSilent++
if (callSilent == 3 && !speaking) { say("Hello? Are you there?"); return }
if (callSilent >= 7 && !speaking) { callEndAfter = true; say("I cannot hear you clearly. I will let " + callOwn() + " know that you called. Goodbye."); return }
h.postDelayed({ if (callOn && !speaking) listen() }, 500)
}
private fun callHear(t: String) {
callLast = System.currentTimeMillis()
if (t.isBlank()) { callQuiet(); return }
callSilent = 0
val s = t.lowercase()
if (Regex("(jarvis|जार्विस).*(mujhe|मुझे|me\\b|take|hand)|(call|कॉल|फोन).*(mujhe|मुझे) (do|de|दो|दे)|मैं बात करता|give me the (call|phone)").containsMatchIn(s)) { callTakeSay(); return }
callTurns++
val k = sp.getString("key", "")?.trim().orEmpty()
if (k.isEmpty()) {
callLines.add("Caller: " + t)
callReply(t, "Noted. I will pass your message to " + callOwn() + ".", callTurns >= 2, false)
return
}
val biz = sp.getString("arm", "p") == "b"
val bizInfo = "Business name: " + callBn() + ". What we do/sell: " + (sp.getString("bd", "") ?: "") + ". Prices: " + (sp.getString("bp", "") ?: "") + ". Working hours: " + (sp.getString("bh", "") ?: "") + ". Address/city: " + (sp.getString("ba", "") ?: "") + ". How to order/contact: " + (sp.getString("bc", "") ?: "") + ". Extra rules/FAQ: " + (sp.getString("bx", "") ?: "")
val sys = Tr.csys(callOwn(), biz, callBn(), bizInfo, sp.getString("prof", "") ?: "")
val hist = ArrayList(callHist)
callLines.add("Caller: " + t)
Thread {
val raw = Gemini.call(k, sys, hist, "Caller (" + callWho + ") said: " + t)
val jo = try { if (raw == null) null else JSONObject(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()) } catch (e: Exception) { null }
val r = jo?.optString("reply").orEmpty().ifBlank { "Sorry, could you please repeat that?" }
h.post { callReply(t, r, jo?.optBoolean("end") == true, jo?.optBoolean("urgent") == true) }
}.start()
}
private fun callReply(t: String, r: String, end: Boolean, urgent: Boolean) {
if (!callOn) return
callHist.add(t to r); while (callHist.size > 8) callHist.removeAt(0)
callLines.add("Jarvis: " + r)
if (callSmsN < 8 || urgent) { callSmsN++; smsOwner((if (urgent) "URGENT! " else "") + callWho + " | Caller: " + t + "\nJarvis: " + r) }
callNote("Call: " + callWho, "Caller: " + t)
callEndAfter = end
say(r)
}
private fun callTakeSay() {
if (!callOn) return
callTakeAfter = true
say("One moment please, " + callOwn() + " is joining the call.")
}
private fun callTake() {
if (!callOn) return
callOn = false
try { tts?.stop() } catch (e: Exception) { }
speaking = false; orbSpeak = false
callAudio(false)
smsOwner("Call aapko de diya. Ab aap baat kijiye.")
callNote("Call is yours", "Put the phone to your ear", true)
h.post { next() }
}
private fun callEnd(hangup: Boolean) {
if (!callOn) return
callOn = false; callTakeAfter = false
if (hangup) {
try { if (Build.VERSION.SDK_INT >= 28) (getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager).endCall() } catch (e: Exception) { MsgListener.inst?.callAct(false) }
}
try { tts?.stop() } catch (e: Exception) { }
speaking = false; orbSpeak = false
callAudio(false)
try { getSystemService(NotificationManager::class.java).cancel(78) } catch (e: Exception) { }
val sec = (System.currentTimeMillis() - callT0) / 1000
val dur = (sec / 60).toString() + ":" + String.format("%02d", sec % 60)
val lines = ArrayList(callLines); val who = callWho
val k = sp.getString("key", "")?.trim().orEmpty()
Thread {
var sum: String? = null
if (k.isNotEmpty() && lines.isNotEmpty()) sum = try { Gemini.info(k, "You summarise phone call transcripts in 2 short sentences in simple Hinglish (Roman letters). Mention who called, what they wanted and any action needed. No markdown.", emptyList(), lines.joinToString("\n")) } catch (e: Exception) { null }
val body = if (!sum.isNullOrBlank()) sum.trim() else if (lines.isEmpty()) "Caller ne kuch nahi bola." else lines.takeLast(2).joinToString(" / ")
smsOwner("Call khatam (" + who + ", " + dur + "). Summary: " + body)
}.start()
h.post { next() }
}
private fun smsOwner(text: String) {
val num = sp.getString("mynum", "")?.trim().orEmpty()
if (num.isEmpty()) return
try {
val sm = if (Build.VERSION.SDK_INT >= 31) getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
sm.sendMultipartTextMessage(num, null, sm.divideMessage(text), null, null)
} catch (e: Exception) { lg("sms fail: " + e) }
}
private fun callNote(title: String, text: String, alert: Boolean = false) {
try {
val nm = getSystemService(NotificationManager::class.java)
nm.createNotificationChannel(NotificationChannel("jcall", "Jarvis calls", NotificationManager.IMPORTANCE_HIGH))
val pi = PendingIntent.getService(this, 78, Intent(this, JarvisService::class.java).setAction("TAKE"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
val b = Notification.Builder(this, "jcall").setContentTitle(title).setContentText(text).setSmallIcon(android.R.drawable.ic_menu_call).setOnlyAlertOnce(!alert)
if (!alert) b.addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_call), "TAKE OVER", pi).build()).setOngoing(true)
nm.notify(78, b.build())
} catch (e: Exception) { lg("note: " + e) }
}
fun go(i: Intent): Boolean = try {
if (i.dataString?.contains("youtube.com/results") == true) ytAt = System.currentTimeMillis()
i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); true
} catch (e: Exception) { say("ये नहीं हो पाया"); false }
}
