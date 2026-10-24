package com.vaibhav.jarvis
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
object Wiki {
private val noise = Regex("(?i)\\b(jarvis|kya|hai|hain|kaun|kon|kitna|kitni|kitne|batao|bata do|bataiye|tell me|tell|what is|what's|what are|who is|who was|who|about|ke baare mein|baare mein|ki|ka|ke|ko|me|mein|please|explain|define|meaning of|the|is|are|was|a|an|of)\\b|क्या|है|हैं|कौन|कितना|कितनी|कितने|बताओ|बता दो|के बारे में|की|का|के|को|में|मतलब|ज़रा|जरा")
fun clean(q: String): String = q.replace(Regex("[?।!,.]"), " ").replace(noise, " ").replace(Regex("\\s+"), " ").trim()
private fun get(u: String): String {
val c = URL(u).openConnection() as HttpURLConnection
c.connectTimeout = 8000; c.readTimeout = 8000
c.setRequestProperty("User-Agent", "JarvisAssistant/1.0 (Android voice assistant)")
if (c.responseCode != 200) throw Exception("http " + c.responseCode)
return c.inputStream.bufferedReader().readText()
}
fun lookup(q: String, lang: String): Pair<String, String>? {
val query = clean(q)
if (query.length < 2) return null
return try {
val u = "https://" + lang + ".wikipedia.org/w/api.php?action=query&list=search&srsearch=" + URLEncoder.encode(query, "UTF-8") + "&srlimit=1&format=json&utf8=1"
val arr = JSONObject(get(u)).getJSONObject("query").getJSONArray("search")
if (arr.length() == 0) return null
val title = arr.getJSONObject(0).getString("title")
val s = JSONObject(get("https://" + lang + ".wikipedia.org/api/rest_v1/page/summary/" + URLEncoder.encode(title, "UTF-8").replace("+", "%20")))
val ex = s.optString("extract")
if (ex.length < 40) null else title to ex
} catch (e: Exception) {
JarvisService.lg("Wiki: " + (e.message ?: "error"))
null
}
}
}
