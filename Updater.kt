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
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
object Updater {
private const val API = "https://api.github.com/repos/vby758849-spec/Jarvis-/releases/tags/latest"
private const val APK = "https://github.com/vby758849-spec/Jarvis-/releases/download/latest/Jarvis.apk"
private const val ACT = "com.vaibhav.jarvis.INSTALL_DONE"
private val h = Handler(Looper.getMainLooper())
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
if (n > cur(a)) ask(a, n) else if (manual) Toast.makeText(a, "You are up to date (build " + cur(a) + ")", Toast.LENGTH_LONG).show()
}
}
} catch (e: Exception) { if (manual) toast(a, "Could not check for updates") }
}.start()
}
private fun ask(a: Activity, n: Long) {
AlertDialog.Builder(a).setTitle("Update available")
.setMessage("Build " + n + " is ready (you have build " + cur(a) + "). Update now?")
.setNegativeButton("Later", null)
.setPositiveButton("Update") { _, _ -> install(a) }
.show()
}
fun install(a: Activity) {
if (Build.VERSION.SDK_INT >= 26 && !a.packageManager.canRequestPackageInstalls()) {
Toast.makeText(a, "Allow Jarvis to install apps from this source, then come back and tap Update again", Toast.LENGTH_LONG).show()
a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.packageName)))
return
}
Toast.makeText(a, "Downloading update...", Toast.LENGTH_LONG).show()
val app = a.applicationContext
Thread {
try {
val c = URL(APK).openConnection() as HttpURLConnection
c.connectTimeout = 15000; c.readTimeout = 60000; c.instanceFollowRedirects = true
if (c.responseCode != 200) { toast(app, "Download failed (code " + c.responseCode + ")"); return@Thread }
val pi = app.packageManager.packageInstaller
val id = pi.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
val s = pi.openSession(id)
s.openWrite("jarvis", 0, -1).use { o -> c.inputStream.copyTo(o); s.fsync(o) }
val r = object : BroadcastReceiver() {
override fun onReceive(ctx: Context, i: Intent) {
val st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
@Suppress("DEPRECATION") val it = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
if (it != null) { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(it) }
return
}
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
} catch (e: Exception) { toast(app, "Update error: " + (e.message ?: "")) }
}.start()
}
}
