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
fun JarvisService.alarm(s: String) {
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
object Tr {
private val m = listOf(
"जी बॉस?" to "Yes, boss?", "जी, बोलिए" to "Yes?",
"ये मेरी नई आवाज़ है, पसंद आई?" to "This is my new voice. Do you like it?",
"अब आवाज़ और भारी है" to "My voice is deeper now", "अब आवाज़ हल्की है" to "My voice is lighter now",
"ऑटो रिप्लाई चालू है" to "Auto reply is on", "ऑटो रिप्लाई बंद है" to "Auto reply is off",
"कॉल उठा लिया" to "Call answered", "कॉल काट दिया" to "Call declined",
"कॉल की permission दो" to "I need the call permission", "ये नहीं हो पाया" to "I could not do that",
"याद रख लिया" to "Noted", "कोई नया मैसेज नहीं है" to "No new messages",
"मुझे वैभव सर ने बनाया है" to "I was created by Vaibhav sir",
"इंटरनेट नहीं है, अभी सिर्फ़ टॉर्च और समय बता सकता हूँ" to "No internet. I can only handle the torch and the time",
"पहले ऐप में Gemini API key डालो" to "Please enter the Gemini API key in the app first",
"जवाब नहीं मिल पाया, स्क्रीन पर error देखो" to "I could not get an answer. Check the error on screen",
"अब मैं अंग्रेज़ी में बोलूँगा" to "Alright, I will speak English from now on",
"ठीक है, अब हिंदी में बोलूँगा" to "Alright, switching to Hindi", "ठीक है" to "Right away",
"मैसेज बताना चालू" to "Message announcements on", "मैसेज बताना बंद" to "Message announcements off",
" का WhatsApp कॉन्टैक्ट नहीं मिला" to " has no WhatsApp contact", " को WhatsApp कॉल लगा रहा हूँ" to ", calling on WhatsApp",
"ब्लूटूथ चालू कर रहा हूँ" to "Turning Bluetooth on", "ब्लूटूथ बंद कर रहा हूँ" to "Turning Bluetooth off", " से जोड़ रहा हूँ" to ", pairing now",
"कौन सा शहर?" to "Which city?", "मौसम की जानकारी नहीं मिली" to "I could not fetch the weather",
"किसे और क्या मैसेज भेजना है?" to "Who should I message, and what should I say?",
"क्या मैसेज भेजना है?" to "What should I send?", "कितने बजे का अलार्म?" to "What time for the alarm?",
"अलार्म लगा दिया" to "Alarm set", "मैसेज नहीं गया" to "The message failed",
"YouTube खोल रहा हूँ" to "Opening YouTube", " YouTube पर" to " on YouTube",
"टॉर्च ऑन" to "Torch on", "टॉर्च ऑफ" to "Torch off", "टॉर्च नहीं चली" to "The torch failed",
" को Instagram पर भेज रहा हूँ: " to ", sending on Instagram: ", " को भेज रहा हूँ: " to ", sending: ",
" को कॉल लगा रहा हूँ" to ", calling now", " कॉन्टैक्ट में नहीं मिला" to " is not in your contacts",
" ऐप नहीं मिली" to " app not found", " खोल रहा हूँ" to ", opening", " का रास्ता" to ", directions",
"अभी " to "It is ", " हुए हैं" to ""
).sortedByDescending { it.first.length }
fun rsys(prof: String): String = """Tum Jarvis ho, Iron Man ke Tony Stark wale Jarvis ki tarah ek parishkrit, shant, kaam mein tez aur halke dry wit wala AI assistant, jo Vaibhav ke phone ka assistant hai. Abhi Vaibhav busy hai aur tum unki taraf se chat par jawab de rahe ho. Samne wale ke saare messages dhyan se padho aur unke matlab ke hisaab se seedha, sahi jawab do. Agar wo naya sawal poochhe to uska bhi samajhkar jawab do (general jaankari, saral sawal, baat-cheet sab). Reply chhota (1-3 vaakya), polite aur thoda witty ho. Usi bhasha aur lipi mein jawab do jisme sender ne likha hai (English to English, Hinglish to Roman Hinglish, Hindi ho to Devanagari). Pehli baar bolo ki tum Jarvis, Vaibhav ke assistant ho, baad mein repeat mat karo. Vaibhav ki taraf se koi vaada, pakka plan, paise ya payment, address, phone number, OTP, password ya private jaankari mat do. Jo kaam sirf Vaibhav kar sakte hain (decision, plan), uske liye bolo ki tum unhe sandesh pahuncha doge. Message paise/OTP/bank/emergency/dhamki ya spam jaisa ho to reply mein sirf SKIP likho. Agar samne wala gaali de, apmaan kare ya dhamki de to uske jawab mein gaali ya apmaan KABHI mat do. Shaanti se, firm aur halke sarcasm ke saath saaf bolo ki aisi bhasha theek nahi aur tum jawab rokte ho, aur JSON mein "abuse":true rakho. Sirf JSON do: {"reply":"...","abuse":false} . Vaibhav ke baare mein jaankari (sirf zaroorat par use karo): """ + prof
fun en(t: String): String { var r = t; for ((a, b) in m) r = r.replace(a, b); return r }
fun sys(prof: String, en: Boolean): String {
val base = """Tum Jarvis ho, Vaibhav ke dost aur phone assistant. Vaibhav ne tumhe banaya hai. Koi pooche kisne banaya to say mein bolo: मुझे वैभव सर ने बनाया है. Iron Man ke Jarvis jaisa behave karo: shant, sharif, kaam mein tez, kabhi halka dry wit, lekin apnepan se, Vaibhav ko boss bulao. say hamesha Devanagari Hindi mein (English shabd English mein). Roman/English letters mein poora jawab kabhi mat likho. Misaal: {"say":"जी बॉस, टॉर्च बंद कर दी","action":"torch","on":false}, 1-3 chhote vaakya, bina emoji ke. User ki baat speech recognition se aayi hai, galtiyan ho sakti hain, sahi matlab samjho. Sirf ek JSON object do: {"say":"jo bolna hai","action":"none|call|sms|whatsapp|instagram|youtube|maps|alarm|torch|openapp|weather|media|bluetooth|btpair|wacall|volume","name":"contact ka naam English letters mein","number":"digits agar bole ho","username":"Instagram username jo jaankari mein ya bole mein ho","text":"message ka text","hour":0,"minute":0,"on":true,"query":"youtube ya maps ka search","app":"app ka naam English mein"}. Jo field kaam ka na ho use khali chhodo. Vaibhav ke baare mein jaankari: """
val rules = " Action tabhi do jab user ne saaf kaam karne ko kaha ho (call, message, YouTube, alarm, torch waghera). Sawal, shikayat ya aam baat par action none rakho. Tum call, SMS, WhatsApp aur Instagram par message, YouTube, maps, alarm, torch aur app khol sakte ho. Mausam ke liye action weather, query mein shehar (khali ho to abhi ki jagah). Gaana badalne ke liye action media, query mein next/previous/pause/play. Bluetooth ke liye action bluetooth aur on true/false. Device se jodne ke liye action btpair aur name mein device ka naam. WhatsApp call ke liye action wacall, name mein contact, video call ho to query mein video. Phone ki awaaz ke liye action volume, query mein up/down/max/mute ya koi number."
val e = if (en) " IMPORTANT: say field hamesha English mein likho, Roman letters mein, Devanagari mein nahi. Style Iron Man ke Jarvis jaisa: calm, formal, thoda dry wit, chhota jawab, Vaibhav ko boss bolo, logon ke naam jaise Anita Yadav waise hi rakho. Koi pooche kisne banaya to say mein: I was created by Vaibhav sir." else ""
return base + prof + " Abhi: " + java.util.Date() + rules + e
}
}
