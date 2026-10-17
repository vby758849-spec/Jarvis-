package com.vaibhav.jarvis
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.provider.AlarmClock
import android.telephony.SmsManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.view.KeyEvent
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
private fun http(u: String): String {
val c = URL(u).openConnection() as HttpURLConnection
c.connectTimeout = 10000; c.readTimeout = 15000
return c.inputStream.bufferedReader().readText()
}
private fun wdesc(c: Int, en: Boolean): String = when (c) {
0 -> if (en) "clear sky" else "साफ़ आसमान"
1, 2, 3 -> if (en) "partly cloudy" else "बादल छाए हैं"
45, 48 -> if (en) "foggy" else "कोहरा है"
in 51..57 -> if (en) "light drizzle" else "हल्की बूंदाबांदी"
in 61..67, in 80..82 -> if (en) "rain" else "बारिश हो रही है"
in 71..77 -> if (en) "snow" else "बर्फ़बारी"
in 95..99 -> if (en) "thunderstorm" else "आंधी-तूफ़ान"
else -> if (en) "mixed weather" else "मिला-जुला मौसम"
}
private fun JarvisService.lastLoc(): android.location.Location? = try {
val lm = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
} catch (e: SecurityException) { null }
fun JarvisService.weather(city: String) {
val en = isEn()
val loc = if (city.isBlank()) lastLoc() else null
if (city.isBlank() && loc == null) { say("कौन सा शहर?"); return }
Thread {
var msg = ""
try {
var lat = loc?.latitude ?: 0.0
var lon = loc?.longitude ?: 0.0
var name = ""
if (city.isNotBlank()) {
val r = JSONObject(http("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + URLEncoder.encode(city, "UTF-8"))).getJSONArray("results").getJSONObject(0)
lat = r.getDouble("latitude"); lon = r.getDouble("longitude"); name = r.getString("name")
}
val w = JSONObject(http("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon + "&current=temperature_2m,relative_humidity_2m,weather_code&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=1"))
val c = w.getJSONObject("current")
val d = w.getJSONObject("daily")
val t = Math.round(c.getDouble("temperature_2m"))
val hu = c.getInt("relative_humidity_2m")
val mx = Math.round(d.getJSONArray("temperature_2m_max").getDouble(0))
val mn = Math.round(d.getJSONArray("temperature_2m_min").getDouble(0))
val p = d.getJSONArray("precipitation_probability_max").optInt(0)
val ds = wdesc(c.getInt("weather_code"), en)
msg = if (en) "It is " + t + " degrees " + (if (name.isEmpty()) "here" else "in " + name) + ", " + ds + ". Humidity " + hu + " percent. Today's high " + mx + ", low " + mn + ", with a " + p + " percent chance of rain."
else (if (name.isEmpty()) "यहाँ" else name + " में") + " अभी " + t + " डिग्री है, " + ds + "। नमी " + hu + " प्रतिशत। आज अधिकतम " + mx + ", न्यूनतम " + mn + ", बारिश की संभावना " + p + " प्रतिशत।"
} catch (e: Exception) { JarvisService.lg("weather error: " + e); msg = "मौसम की जानकारी नहीं मिली" }
Handler(Looper.getMainLooper()).post { say(msg) }
}.start()
}
fun JarvisService.media(q: String) {
val k = when {
q.contains("prev") || q.contains("back") -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
q.contains("pause") || q.contains("stop") -> KeyEvent.KEYCODE_MEDIA_PAUSE
q.contains("play") -> KeyEvent.KEYCODE_MEDIA_PLAY
else -> KeyEvent.KEYCODE_MEDIA_NEXT
}
val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, k))
am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, k))
say("ठीक है")
}
fun JarvisService.volume(q: String) {
val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
val st = android.media.AudioManager.STREAM_MUSIC
val ui = android.media.AudioManager.FLAG_SHOW_UI
val mx = am.getStreamMaxVolume(st)
val num = Regex("\\d{1,3}").find(q)?.value?.toIntOrNull()
when {
num != null -> am.setStreamVolume(st, mx * num.coerceIn(0, 100) / 100, ui)
Regex("max|full|फुल|पूरा").containsMatchIn(q) -> am.setStreamVolume(st, mx, ui)
Regex("min|mute|म्यूट|ज़ीरो").containsMatchIn(q) -> am.setStreamVolume(st, 0, ui)
Regex("down|kam|कम|घटा|ghata|dheema|धीमा|low").containsMatchIn(q) -> repeat(3) { am.adjustStreamVolume(st, android.media.AudioManager.ADJUST_LOWER, ui) }
else -> repeat(3) { am.adjustStreamVolume(st, android.media.AudioManager.ADJUST_RAISE, ui) }
}
getSharedPreferences("j", Context.MODE_PRIVATE).edit().putBoolean("manvol", true).apply()
say("ठीक है")
}
fun JarvisService.bluetooth(on: Boolean) {
JarvisService.btWant = if (on) 1 else 2
JarvisService.btName = ""
JarvisService.btAt = System.currentTimeMillis()
say(if (on) "ब्लूटूथ चालू कर रहा हूँ" else "ब्लूटूथ बंद कर रहा हूँ")
go(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
}
fun JarvisService.btPair(name: String) {
JarvisService.btWant = 1
JarvisService.btName = name.lowercase()
JarvisService.btAt = System.currentTimeMillis()
say(name + " से जोड़ रहा हूँ")
go(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
}
fun JarvisService.waCall(name: String, video: Boolean) {
val mt = "vnd.android.cursor.item/vnd.com.whatsapp." + (if (video) "video" else "voip") + ".call"
var id = -1L
try {
contentResolver.query(ContactsContract.Data.CONTENT_URI, arrayOf(ContactsContract.Data._ID), ContactsContract.Data.MIMETYPE + "=? AND " + ContactsContract.Data.DISPLAY_NAME + " LIKE ?", arrayOf(mt, "%" + name + "%"), null)?.use { if (it.moveToFirst()) id = it.getLong(0) }
} catch (e: Exception) { JarvisService.lg("wacall error: " + e) }
if (id < 0) { say(name + " का WhatsApp कॉन्टैक्ट नहीं मिला"); return }
say(name + " को WhatsApp कॉल लगा रहा हूँ")
go(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://com.android.contacts/data/" + id), mt).setPackage("com.whatsapp"))
}
fun JarvisService.digits(s: String) = Regex("\\+?\\d[\\d ]{6,}\\d").find(s)?.value?.replace(" ", "")
fun JarvisService.findNumber(name: String): String? {
if (name.isBlank() || checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?", arrayOf("%$name%"), null)?.use {
if (it.moveToFirst()) return it.getString(0)
}
return null
}
fun JarvisService.call(s: String) {
val name = s.replace(Regex("\\b(call|karo|kar|do|lagao|laga|ko|phone|please)\\b"), "").trim()
val num = digits(s) ?: findNumber(name)
if (num == null) { say("$name कॉन्टैक्ट में नहीं मिला"); return }
say("$name को कॉल लगा रहा हूँ")
go(Intent(Intent.ACTION_CALL, Uri.parse("tel:$num")))
}
@Suppress("DEPRECATION")
fun JarvisService.msg(s: String) {
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
say("$name को भेज रहा हूँ: $body")
if (body.isNotEmpty()) { JarvisService.sendAt = System.currentTimeMillis(); JarvisService.lg("WA: " + name + " ko likh raha, send ka intezaar") }
go(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$full?text=" + Uri.encode(body))))
} else {
try {
val sm = if (Build.VERSION.SDK_INT >= 31) getSystemService(SmsManager::class.java) else SmsManager.getDefault()
sm.sendMultipartTextMessage(num, null, sm.divideMessage(body), null, null)
say("$name को मैसेज भेज दिया")
} catch (e: Exception) { say("मैसेज नहीं गया") }
}
}
fun JarvisService.alarm(s: String, label: String = "") {
val m = Regex("(\\d{1,2})(?:[: ](\\d{2}))?\\s*(am|pm|baje)?").find(s)
if (m == null) { say("कितने बजे का अलार्म?"); return }
var hr = m.groupValues[1].toInt(); val mn = m.groupValues[2].toIntOrNull() ?: 0
val ap = m.groupValues[3]
if (ap == "pm" && hr < 12) hr += 12
if (ap == "am" && hr == 12) hr = 0
say("अलार्म लगा दिया")
go(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, hr)
.putExtra(AlarmClock.EXTRA_MINUTES, mn).putExtra(AlarmClock.EXTRA_SKIP_UI, true).putExtra(AlarmClock.EXTRA_MESSAGE, label))
}
fun JarvisService.youtube(s: String) {
val q = s.replace(Regex("\\b(youtube|play|chalao|chala|kholo|khol|open|par|pe|on|karo|do|gaana|song)\\b"), "").trim()
if (q.isEmpty()) {
say("YouTube खोल रहा हूँ")
go(packageManager.getLaunchIntentForPackage("com.google.android.youtube") ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://youtube.com")))
} else {
say("$q YouTube पर")
go(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))))
}
}
fun JarvisService.openApp(s: String) {
val name = s.replace(Regex("\\b(open|kholo|khol|karo|app|do|ko)\\b"), "").trim()
if (name.isEmpty()) return
val pm = packageManager
val li = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
val hit = li.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(name) }
val i = hit?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
if (i != null) { say("$name खोल रहा हूँ"); go(i) } else say("$name ऐप नहीं मिली")
}
fun JarvisService.torch(on: Boolean) {
try {
val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
val id = cm.cameraIdList.first { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
cm.setTorchMode(id, on)
say(if (on) "टॉर्च ऑन" else "टॉर्च ऑफ")
} catch (e: Exception) { say("टॉर्च नहीं चली") }
}
fun JarvisService.agenda() {
val en = isEn()
val cal = java.util.Calendar.getInstance()
cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0); cal.set(java.util.Calendar.SECOND, 0)
val b = cal.timeInMillis
val ub = android.provider.CalendarContract.Instances.CONTENT_URI.buildUpon()
android.content.ContentUris.appendId(ub, b); android.content.ContentUris.appendId(ub, b + 86400000L)
val f = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
val items = ArrayList<String>()
try {
contentResolver.query(ub.build(), arrayOf(android.provider.CalendarContract.Instances.TITLE, android.provider.CalendarContract.Instances.BEGIN), null, null, android.provider.CalendarContract.Instances.BEGIN + " ASC")?.use { c ->
while (c.moveToNext()) items.add(f.format(java.util.Date(c.getLong(1))) + (if (en) ", " else " बजे, ") + c.getString(0))
}
} catch (e: SecurityException) { say("कैलेंडर की permission दो"); return }
if (items.isEmpty()) say("आज कैलेंडर में कुछ नहीं है")
else say((if (en) "Today, boss. " else "आज का शेड्यूल। ") + items.joinToString(". "))
}
fun JarvisService.offline(q0: String): Boolean {
val q = q0.lowercase()
val en = isEn()
fun r(e: String, h: String) = say(if (en) e else h)
val jokes = listOf(
"I would tell you a UDP joke, but you might not get it." to "मैं तुम्हें एक UDP जोक सुनाता, पर शायद वो तुम तक पहुँचता ही नहीं।",
"Why do programmers prefer dark mode? Because light attracts bugs." to "प्रोग्रामर डार्क मोड क्यों पसंद करते हैं? क्योंकि रोशनी बग को बुलाती है।",
"I am fluent in sarcasm, boss. It is the only language that needs no internet." to "मुझे व्यंग्य बहुत अच्छा आता है बॉस, और उसके लिए इंटरनेट भी नहीं चाहिए।")
when {
Regex("hello|hi\\b|hey|namaste|नमस्ते|हेलो|हाय").containsMatchIn(q) -> r("Hello, boss. At your service, even offline.", "नमस्ते बॉस। मैं ऑफ़लाइन भी हाज़िर हूँ।")
Regex("kaise ho|कैसे हो|how are you|क्या हाल").containsMatchIn(q) -> r("All systems nominal, boss. Just slightly offline.", "सब कुछ बढ़िया है बॉस, बस इंटरनेट नहीं है।")
Regex("thank|धन्यवाद|शुक्रिया|थैंक").containsMatchIn(q) -> r("Always, boss.", "हमेशा, बॉस।")
Regex("joke|जोक|मज़ाक|चुटकुला").containsMatchIn(q) -> { val j = jokes.random(); r(j.first, j.second) }
Regex("tum kaun|who are you|तुम कौन|आप कौन").containsMatchIn(q) -> r("I am Jarvis, built by Vaibhav sir.", "मैं जार्विस हूँ, वैभव सर का बनाया हुआ।")
Regex("date|तारीख|कौन सा दिन|आज कौन").containsMatchIn(q) -> r("Today is " + java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.ENGLISH).format(java.util.Date()), "आज " + java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale("hi")).format(java.util.Date()) + " है।")
Regex("alarm|अलार्म").containsMatchIn(q) -> alarm(q)
else -> return false
}
return true
}
