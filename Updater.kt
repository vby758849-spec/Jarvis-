package com.vaibhav.jarvis
import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.net.URL
object Updater {
    fun ver(n: Long): String = "1." + maxOf(1L, n - 153L)

private const val API = "https://api.github.com/repos/vby758849-spec/Jarvis-/releases/tags/latest"
private const val APK = "https://github.com/vby758849-spec/Jarvis-/releases/download/latest/Jarvis.apk"
private const val ACT = "com.vaibhav.jarvis.INSTALL_DONE"
private val h = Handler(Looper.getMainLooper())
private var dlg: AlertDialog? = null
private var bar: ProgressBar? = null
private var pct: TextView? = null
private var head: TextView? = null
private fun showProgress(a: Activity) {
sT = 0L; sD = 0L; mbps = 0.0; shown = 0L
val lay = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(60, 50, 60, 40); setBackgroundColor(Color.parseColor("#0B0C22")) }
val hd = TextView(a).apply { text = "DOWNLOADING UPDATE"; setTextColor(Color.parseColor("#A9B8FF")); textSize = 14f; typeface = android.graphics.Typeface.MONOSPACE; letterSpacing = 0.15f }
val pb = ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = 0; isIndeterminate = true; progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#A9B8FF")); indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#A9B8FF")) }
val pc = TextView(a).apply { text = "Connecting..."; setTextColor(Color.parseColor("#D6DCFF")); textSize = 13f; typeface = android.graphics.Typeface.MONOSPACE; gravity = Gravity.CENTER }
lay.addView(hd)
lay.addView(pb, LinearLayout.LayoutParams(-1, 60).apply { topMargin = 30 })
lay.addView(pc, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 20 })
head = hd; bar = pb; pct = pc
dlg = AlertDialog.Builder(a).setView(lay).setCancelable(false).create()
dlg?.show()
}
private var sT = 0L
private var shown = 0L
private var sD = 0L
private var mbps = 0.0
private fun upd(done0: Long, total: Long) {
val done = maxOf(done0, shown); shown = done
val now = System.currentTimeMillis()
if (sT == 0L) { sT = now; sD = done }
if (now - sT >= 700) {
val inst = (done - sD) * 8.0 / ((now - sT) / 1000.0) / 1000000.0
mbps = if (mbps == 0.0) inst else mbps * 0.6 + inst * 0.4
sT = now; sD = done
}
val mb = String.format("%.1f", done / 1048576.0)
val sp = String.format("%.1f", mbps) + " Mbps"
if (total > 0) {
val p = (done * 100 / total).toInt()
bar?.isIndeterminate = false
bar?.max = 1000
bar?.progress = (done * 1000 / total).toInt()
val left = if (mbps > 0.05) ((total - done) * 8.0 / (mbps * 1000000.0)).toInt() else -1
pct?.text = "" + p + "%   " + mb + " / " + String.format("%.1f", total / 1048576.0) + " MB\n" + sp + (if (left >= 0) "   ~" + left + " s left" else "")
} else pct?.text = mb + " MB\n" + sp
}
private fun installing() {
head?.text = "INSTALLING..."
bar?.isIndeterminate = true
pct?.text = "Please confirm on the next screen"
}
private fun closeProgress() { try { dlg?.dismiss() } catch (e: Exception) { }; dlg = null }
fun cur(a: Context): Long {
return try {
val pi = a.packageManager.getPackageInfo(a.packageName, 0)
if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
} catch (e: Exception) { 0L }
}
private fun toast(c: Context, t: String) { h.post { Toast.makeText(c, t, Toast.LENGTH_LONG).show() } }
fun check(a: Activity, manual: Boolean) {
Thread {
try {
val c = URL(API).openConnection() as HttpURLConnection
c.connectTimeout = 10000; c.readTimeout = 10000
c.setRequestProperty("Accept", "application/vnd.github+json")
if (c.responseCode != 200) { if (manual) toast(a, "Could not check for updates (check internet)"); return@Thread }
val j = JSONObject(c.inputStream.bufferedReader().readText())
val n = Regex("\\d+").find(j.optString("body"))?.value?.toLongOrNull() ?: 0L
h.post {
if (!a.isFinishing) {
if (n > cur(a)) ask(a, n) else if (manual) Toast.makeText(a, "You are up to date (" + ver(cur(a)) + ")", Toast.LENGTH_LONG).show()
}
}
} catch (e: Exception) { if (manual) toast(a, "Could not check for updates") }
}.start()
}
private fun ask(a: Activity, n: Long) {
AlertDialog.Builder(a).setTitle("Update available")
.setMessage("Version " + ver(n) + " is ready (you have " + ver(cur(a)) + "). Update now?")
.setNegativeButton("Later", null)
.setPositiveButton("Update") { _, _ -> install(a) }
.show()
}
private fun finalUrl(u0: String): String {
var u = u0
for (i in 0 until 5) {
val c = URL(u).openConnection() as HttpURLConnection
c.instanceFollowRedirects = false
c.connectTimeout = 15000; c.readTimeout = 15000
c.setRequestProperty("Range", "bytes=0-0")
val code = c.responseCode
val loc = c.getHeaderField("Location")
c.disconnect()
if (code in 300..399 && loc != null) { u = loc; continue }
return u
}
return u
}
private fun probe(u: String): Long {
return try {
val c = URL(u).openConnection() as HttpURLConnection
c.connectTimeout = 15000; c.readTimeout = 15000
c.setRequestProperty("Range", "bytes=0-0")
val r = if (c.responseCode == 206) c.getHeaderField("Content-Range") else null
c.disconnect()
r?.substringAfter("/", "")?.trim()?.toLongOrNull() ?: -1L
} catch (e: Exception) { -1L }
}
private fun fetch(u: String, total: Long, f: File, prog: (Long) -> Unit) {
RandomAccessFile(f, "rw").use { it.setLength(total) }
val chunk = 2L * 1024 * 1024
val cnt = ((total + chunk - 1) / chunk).toInt()
val next = java.util.concurrent.atomic.AtomicInteger(0)
val done = AtomicLong(0)
val err = AtomicReference<Exception?>(null)
val ths = (0 until 8).map {
Thread {
try {
RandomAccessFile(f, "rw").use { raf ->
val buf = ByteArray(131072)
while (err.get() == null) {
val i = next.getAndIncrement()
if (i >= cnt) break
val s = i * chunk
val e = minOf(total - 1, s + chunk - 1)
var tries = 0
while (true) {
var got = 0L
try {
val c = URL(u).openConnection() as HttpURLConnection
c.setRequestProperty("Range", "bytes=" + s + "-" + e)
c.connectTimeout = 15000; c.readTimeout = 20000
if (c.responseCode != 206) throw Exception("range " + c.responseCode)
raf.seek(s)
val inp = c.inputStream
while (true) {
val r = inp.read(buf)
if (r < 0) break
raf.write(buf, 0, r)
got += r
prog(done.addAndGet(r.toLong()))
}
inp.close()
if (got != e - s + 1) throw Exception("short chunk")
break
} catch (ex: Exception) {
done.addAndGet(-got)
if (++tries >= 3) throw ex
}
}
}
}
} catch (ex: Exception) { err.set(ex) }
}
}
ths.forEach { it.start() }
ths.forEach { it.join() }
err.get()?.let { throw it }
if (done.get() != total) throw Exception("incomplete download")
}
fun install(a: Activity) {
if (Build.VERSION.SDK_INT >= 26 && !a.packageManager.canRequestPackageInstalls()) {
Toast.makeText(a, "Allow Jarvis to install apps from this source, then come back and tap Update again", Toast.LENGTH_LONG).show()
a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.packageName)))
return
}
showProgress(a)
val app = a.applicationContext
Thread {
try {
val fu = finalUrl(APK)
val total = probe(fu)
val f = File(app.cacheDir, "update.apk")
var last = 0L
val prog: (Long) -> Unit = { d ->
val now = System.currentTimeMillis()
if (now - last > 100) { last = now; h.post { upd(d, total) } }
}
if (total > 0) fetch(fu, total, f, prog)
else {
val c = URL(fu).openConnection() as HttpURLConnection
c.connectTimeout = 15000; c.readTimeout = 60000
if (c.responseCode != 200) { h.post { closeProgress() }; toast(app, "Download failed (code " + c.responseCode + ")"); return@Thread }
var d = 0L
f.outputStream().use { o ->
val inp = c.inputStream
val buf = ByteArray(262144)
while (true) { val n = inp.read(buf); if (n < 0) break; o.write(buf, 0, n); d += n; prog(d) }
}
}
val len = f.length()
val pi = app.packageManager.packageInstaller
val id = pi.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
val s = pi.openSession(id)
s.openWrite("jarvis", 0, len).use { o ->
f.inputStream().use { it.copyTo(o, 262144) }
s.fsync(o)
}
f.delete()
h.post { upd(len, len); installing() }
val r = object : BroadcastReceiver() {
override fun onReceive(ctx: Context, i: Intent) {
val st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
@Suppress("DEPRECATION") val it = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
closeProgress()
if (it != null) { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(it) }
return
}
closeProgress()
try { ctx.unregisterReceiver(this) } catch (e: Exception) { }
if (st != PackageInstaller.STATUS_SUCCESS) {
Toast.makeText(ctx, "Update failed: " + (i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""), Toast.LENGTH_LONG).show()
}
}
}
h.post {
if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(r, IntentFilter(ACT), Context.RECEIVER_NOT_EXPORTED) else app.registerReceiver(r, IntentFilter(ACT))
val pend = PendingIntent.getBroadcast(app, id, Intent(ACT).setPackage(app.packageName), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
s.commit(pend.intentSender)
}
} catch (e: Exception) { h.post { closeProgress() }; toast(app, "Update error: " + (e.message ?: "")) }
}.start()
}
}
