package com.vaibhav.jarvis
import android.content.Intent
import android.net.Uri
import android.os.Build
private val STOP = setOf("whatsapp", "call", "message", "par", "पर", "ko", "को", "karo", "करो", "kar", "कर", "do", "दो", "video", "लगाओ", "लगा", "लगाना", "भेजो", "भेज", "bhejo", "bhej", "send", "से", "का", "की", "के", "एक", "ek", "please", "प्लीज", "जरा", "ज़रा", "भेजना", "बोलो")
private val NAMES = mapOf(
"मम्मी" to listOf("mummy", "mom", "mama", "maa"), "मम्मा" to listOf("mummy", "mom", "mama"), "मां" to listOf("maa", "mummy", "mom"), "माँ" to listOf("maa", "mummy", "mom"),
"पापा" to listOf("papa", "dad", "daddy"), "भैया" to listOf("bhaiya", "bhai"), "दीदी" to listOf("didi", "sister"), "भाई" to listOf("bhai", "brother")
)
private val APPS = mapOf(
"कैलकुलेटर" to "calculator", "कैमरा" to "camera", "गैलरी" to "gallery", "सेटिंग" to "settings", "सेटिंग्स" to "settings", "क्रोम" to "chrome",
"घड़ी" to "clock", "क्लॉक" to "clock", "कैलेंडर" to "calendar", "इंस्टाग्राम" to "instagram", "instagram" to "instagram", "फेसबुक" to "facebook",
"टेलीग्राम" to "telegram", "स्पॉटिफाई" to "spotify", "जीमेल" to "gmail", "गूगल" to "google", "फोटो" to "photos", "नोट्स" to "notes", "म्यूजिक" to "music", "whatsapp" to "whatsapp"
)
private fun norm(s: String): String = s.lowercase()
.replace(Regex("व्हाट्स ?(ऐप|एप|अप)|वॉट्स ?(ऐप|एप|अप)|वाट्स ?(ऐप|एप|अप)|whats app"), "whatsapp")
.replace("यू ट्यूब", "youtube").replace("यूट्यूब", "youtube")
.replace(Regex("कॉल|फोन|फ़ोन"), "call").replace(Regex("मैसेज|मेसेज|संदेश"), "message")
.replace("अलार्म", "alarm").replace("मौसम", "weather").replace("वीडियो", "video")
private fun toks(s: String, extra: Set<String> = emptySet()): String =
s.split(Regex("\\s+")).filter { it.isNotBlank() && it !in STOP && it !in extra }.joinToString(" ")
fun JarvisService.numByName(n0: String): String? {
val n = n0.trim()
if (n.isEmpty()) return null
findNumber(n)?.let { return it }
NAMES[n]?.forEach { a -> findNumber(a)?.let { return it } }
if (Build.VERSION.SDK_INT >= 29) {
val lat = try { android.icu.text.Transliterator.getInstance("Devanagari-Latin; NFD; [:Nonspacing Mark:] Remove; NFC").transliterate(n).lowercase() } catch (e: Exception) { "" }
var c = lat
while (c.length >= 3) { findNumber(c)?.let { return it }; c = c.dropLast(1) }
}
return null
}
fun JarvisService.fallback(q: String): Boolean = local(q) || offline(q)
fun JarvisService.local(q0: String): Boolean {
val s = norm(q0)
val wa = s.contains("whatsapp")
val hasKo = Regex("\\s(ko|को)\\s").containsMatchIn(" $s ")
when {
wa && s.contains("call") -> waCall(toks(s), s.contains("video"))
s.contains("call") -> call("call " + toks(s))
(s.contains("message") || wa) && hasKo -> {
val m = Regex("^(.+?)\\s(ko|को)\\s(.*)$").find(s)
val name = toks(m?.groupValues?.get(1) ?: "")
val body = toks(m?.groupValues?.get(3) ?: "")
if (body.isEmpty() && !wa) say("क्या मैसेज भेजना है?") else msg((if (wa) "whatsapp " else "sms ") + name + " ko " + body)
}
s.contains("youtube") -> {
val q = toks(s, setOf("youtube", "खोलो", "खोल", "चलाओ", "चला", "चलाइए", "play", "chalao", "chala", "kholo", "khol", "open", "सुनाओ", "गाना", "गाने", "song", "gaana", "gana"))
youtube(if (q.isEmpty()) "youtube" else "youtube $q")
}
Regex("maps|\\bmap\\b|नक्शा|रास्ता|raasta|navigate|मैप").containsMatchIn(s) -> {
val q = toks(s, setOf("maps", "map", "नक्शा", "रास्ता", "दिखाओ", "बताओ", "raasta", "navigate", "dikhao", "batao", "ka", "ki", "मैप", "मैप्स"))
say(q + " का रास्ता")
go(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q))))
}
s.contains("alarm") -> alarm(s)
s.contains("weather") -> {
val g = Regex("(.+?)\\s+(का|की|ka|ki|में|me|mein|in)\\s+weather").find(s)?.groupValues?.get(1) ?: ""
weather(toks(g, setOf("आज", "aaj", "today", "अभी", "abhi", "यहाँ", "yahan")))
}
Regex("खोलो|खोल|kholo|khol|open|ओपन|चालू करो").containsMatchIn(s) -> {
val n = toks(s, setOf("खोलो", "खोल", "kholo", "khol", "open", "ओपन", "चालू", "app", "ऐप"))
val a = APPS[n] ?: try { if (Build.VERSION.SDK_INT >= 29) android.icu.text.Transliterator.getInstance("Devanagari-Latin; NFD; [:Nonspacing Mark:] Remove; NFC").transliterate(n).lowercase() else n } catch (e: Exception) { n }
if (a.isBlank()) return false
openApp("open $a")
}
else -> return false
}
return true
}
