package com.vaibhav.jarvis
import android.content.Context
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
object CloneVoice {
private val h = Handler(Looper.getMainLooper())
private var mp: MediaPlayer? = null
fun active(sp: SharedPreferences): Boolean = sp.getBoolean("cv", true) && !sp.getString("cvid", "").isNullOrEmpty() && !sp.getString("xik", "").isNullOrEmpty()
fun stop() {
try { mp?.stop() } catch (e: Exception) { }
try { mp?.release() } catch (e: Exception) { }
mp = null
}
fun create(key: String, name: String, data: ByteArray, fileName: String, cb: (String?, String) -> Unit) {
Thread {
try {
val b = "jv" + System.nanoTime()
val c = URL("https://api.elevenlabs.io/v1/voices/add").openConnection() as HttpURLConnection
c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 20000; c.readTimeout = 120000
c.setRequestProperty("xi-api-key", key)
c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$b")
c.outputStream.use { o ->
o.write(("--$b\r\nContent-Disposition: form-data; name=\"name\"\r\n\r\n$name\r\n").toByteArray())
o.write(("--$b\r\nContent-Disposition: form-data; name=\"files\"; filename=\"$fileName\"\r\nContent-Type: audio/mpeg\r\n\r\n").toByteArray())
o.write(data)
o.write("\r\n--$b--\r\n".toByteArray())
}
val code = c.responseCode
val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
if (code in 200..299) {
val id = JSONObject(body).optString("voice_id")
h.post { if (id.isEmpty()) cb(null, "voice id not returned") else cb(id, "ok") }
} else h.post { cb(null, "Error " + code + ": " + body.take(160)) }
} catch (e: Exception) { h.post { cb(null, "Net error: " + (e.message ?: "")) } }
}.start()
}
fun speak(ctx: Context, sp: SharedPreferences, text: String, done: () -> Unit, fail: () -> Unit) {
val vid = sp.getString("cvid", "") ?: ""
val key = sp.getString("xik", "") ?: ""
val dir = ctx.cacheDir
Thread {
try {
val c = URL("https://api.elevenlabs.io/v1/text-to-speech/" + vid + "?output_format=mp3_44100_64").openConnection() as HttpURLConnection
c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 15000; c.readTimeout = 60000
c.setRequestProperty("xi-api-key", key)
c.setRequestProperty("Content-Type", "application/json")
c.setRequestProperty("Accept", "audio/mpeg")
c.outputStream.use { it.write(JSONObject().put("text", text).put("model_id", "eleven_multilingual_v2").toString().toByteArray()) }
if (c.responseCode != 200) {
JarvisService.lg("Clone: error " + c.responseCode)
h.post { fail() }
return@Thread
}
val f = File(dir, "cv.mp3")
f.outputStream().use { o -> c.inputStream.copyTo(o) }
h.post {
try {
stop()
val m = MediaPlayer()
m.setDataSource(f.path)
m.setOnCompletionListener { stop(); done() }
m.setOnErrorListener { _, _, _ -> stop(); fail(); true }
m.prepare()
m.start()
mp = m
} catch (e: Exception) { stop(); fail() }
}
} catch (e: Exception) {
JarvisService.lg("Clone: " + (e.message ?: "error"))
h.post { fail() }
}
}.start()
}
}
