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
 private val sp by lazy { getSharedPreferences("j", Context.MODE_PRIVATE) }
 private val hist = ArrayList<Pair<String, String>>()
 @Volatile private var speaking = false
 private val wake = Regex("jarvis|jarvish|travis|जार्विस|जारविस|जर्विस")
 private val LANG = "en-IN" // Hindi ke liye "hi-IN" kar sakte ho
 override fun onBind(i: Intent?): IBinder? = null
 override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY
 override fun onCreate() {
  super.onCreate()
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
  if (speaking) { h.postDelayed({ listen() }, 700); return }
  val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
   .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
   .putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANG)
   .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 8000L)
   .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L)
  beep(true)
  h.postDelayed({ beep(false) }, 900)
  try { sr.startListening(i) } catch (e: Exception) { h.postDelayed({ listen() }, 1000) }
 }
 override fun onResults(b: Bundle?) {
  b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { process(it.lowercase()) }
  h.postDelayed({ listen() }, 400)
 }
 override fun onError(e: Int) {
  sr.cancel()
  h.postDelayed({ listen() }, if (e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1500 else 400)
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
    say("Ji, boliye")
    h.postDelayed({ awake = true }, 1500)
    h.postDelayed({ awake = false }, 20000)
   }
  } else if (awake) { awake = false; handle(s) }
 }
 private fun handle(s0: String) {
  val s = s0.replace("whats app", "whatsapp").replace("you tube", "youtube")
  when {
   Regex("awaaz badlo|awaz badlo|voice change|change voice").containsMatchIn(s) -> {
    sp.edit().putInt("vi", sp.getInt("vi", 0) + 1).apply(); applyVoice()
    say("Ye meri nayi awaaz hai, pasand aayi?")
   }
   Regex("^(yaad rakh|yaad rakho|remember)").containsMatchIn(s) -> remember(s)
   "torch" in s || "flashlight" in s -> torch(!Regex("\\b(off|band|bandh)\\b").containsMatchIn(s))
   Regex("\\bcall\\b").containsMatchIn(s) -> call(s)
   "alarm" in s -> alarm(s)
   Regex("message|sms|msg|whatsapp").containsMatchIn(s) -> msg(s)
   "youtube" in s -> youtube(s)
   "maps" in s || "raasta" in s || "navigate" in s -> {
    val q = s.replace(Regex("\\b(maps|raasta|navigate|to|kholo|dikhao)\\b"), "").trim()
    say("$q ka rasta"); go(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q))))
   }
   Regex("\\b(time|samay)\\b|baje kya").containsMatchIn(s) ->
    say("Abhi " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) + " hue hain")
   Regex("\\b(open|kholo|khol)\\b").containsMatchIn(s) -> openApp(s)
   else -> ask(s0)
  }
 }
 private fun applyVoice() {
  tts?.setPitch(0.7f)
  val v = (tts?.voices ?: emptySet<android.speech.tts.Voice>())
   .filter { it.locale.language == "hi" || it.locale == Locale("en", "IN") }.sortedBy { it.name }
  if (v.isNotEmpty()) tts?.voice = v[sp.getInt("vi", 0) % v.size]
 }
 private fun remember(s: String) {
  val fact = s.replace(Regex("^(yaad rakh|yaad rakho|remember)( ki| that)?"), "").trim()
  sp.edit().putString("prof", sp.getString("prof", "") + " " + fact + ".").apply()
  say("Yaad rakh liya")
 }
 private fun jm(r: String, t: String) = JSONObject().put("role", r).put("parts", JSONArray().put(JSONObject().put("text", t)))
 private fun ask(q: String) {
  val key = sp.getString("key", "")?.trim().orEmpty()
  if (key.isEmpty()) { say("Pehle app mein Gemini API key daalo"); return }
  Thread {
   val ans = callGemini(key, q)
   h.post {
    if (ans == null) say("Jawab nahi mil paya, internet ya key check karo")
    else {
     hist.add(q to ans); if (hist.size > 6) hist.removeAt(0)
     say(ans.replace(Regex("[*#`_]"), ""))
     h.postDelayed({ awake = true }, 2500)
     h.postDelayed({ awake = false }, 25000)
    }
   }
  }.start()
 }
 private fun callGemini(key: String, q: String): String? {
  val sys = "Tum Jarvis ho, Vaibhav ke personal voice assistant. Hinglish mein, chhota (1-3 vaakya) aur seedha jawab do, bina markdown aur emoji ke. Vaibhav ke baare mein jaankari: " +
   sp.getString("prof", "") + " Aaj ki tarikh aur time: " + Date()
  val contents = JSONArray()
  for ((u, a) in hist) { contents.put(jm("user", u)); contents.put(jm("model", a)) }
  contents.put(jm("user", q))
  val body = JSONObject().put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
   .put("contents", contents).toString()
  for (m in arrayOf("gemini-flash-latest", "gemini-3.8-flash", "gemini-2.5-flash")) {
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
    }
   } catch (e: Exception) { }
  }
  return null
 }
 private fun go(i: Intent): Boolean = try {
  i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); true
 } catch (e: Exception) { say("Ye nahi ho paya"); false }
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
  if (num == null) { say("$name contact mein nahi mila"); return }
  say("$name ko call laga raha hoon")
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
  if (num == null) { say("$name contact mein nahi mila"); return }
  if ("whatsapp" in s) {
   val n = num.filter { it.isDigit() }
   val full = if (n.length == 10) "91$n" else n
   say("$name ko WhatsApp message taiyar hai")
   go(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$full?text=" + Uri.encode(body))))
  } else {
   try {
    val sm = if (Build.VERSION.SDK_INT >= 31) getSystemService(SmsManager::class.java) else SmsManager.getDefault()
    sm.sendMultipartTextMessage(num, null, sm.divideMessage(body), null, null)
    say("$name ko message bhej diya")
   } catch (e: Exception) { say("Message nahi gaya") }
  }
 }
 private fun alarm(s: String) {
  val m = Regex("(\\d{1,2})(?:[: ](\\d{2}))?\\s*(am|pm|baje)?").find(s)
  if (m == null) { say("Kitne baje ka alarm?"); return }
  var hr = m.groupValues[1].toInt(); val mn = m.groupValues[2].toIntOrNull() ?: 0
  val ap = m.groupValues[3]
  if (ap == "pm" && hr < 12) hr += 12
  if (ap == "am" && hr == 12) hr = 0
  say("Alarm laga diya")
  go(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, hr)
   .putExtra(AlarmClock.EXTRA_MINUTES, mn).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
 }
 private fun youtube(s: String) {
  val q = s.replace(Regex("\\b(youtube|play|chalao|chala|kholo|khol|open|par|pe|on|karo|do|gaana|song)\\b"), "").trim()
  if (q.isEmpty()) {
   say("YouTube khol raha hoon")
   go(packageManager.getLaunchIntentForPackage("com.google.android.youtube") ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://youtube.com")))
  } else {
   say("$q YouTube par")
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
  if (i != null) { say("$name khol raha hoon"); go(i) } else say("$name app nahi mili")
 }
 private fun torch(on: Boolean) {
  try {
   val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
   val id = cm.cameraIdList.first { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
   cm.setTorchMode(id, on)
   say(if (on) "Torch on" else "Torch off")
  } catch (e: Exception) { say("Torch nahi chali") }
 }
}
