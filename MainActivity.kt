package com.vaibhav.jarvis
import android.Manifest.permission.*
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
class MainActivity : Activity() {
private val perms = mutableListOf(RECORD_AUDIO, CALL_PHONE, SEND_SMS, READ_CONTACTS, ANSWER_PHONE_CALLS, ACCESS_COARSE_LOCATION, READ_CALENDAR).apply {
if (Build.VERSION.SDK_INT >= 33) add(POST_NOTIFICATIONS)
}.toTypedArray()
private val DEF = "Mera naam Vaibhav hai (majin Vaibhav). Main games bana raha hoon: Ludo, chess, Majin runner, Yadavi Ji Dodh Nikale, torch game, aur Jarvis assistant."
private val CY = Color.parseColor("#00E5FF")
private val GR = Color.parseColor("#2BFF88")
private val RD = Color.parseColor("#FF4D6D")
private val DIM = Color.parseColor("#7A8FA3")
private var greet: JarvisVoice? = null
private var tester: JarvisVoice? = null
private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
private fun box(stroke: Int, fill: Int, r: Int) = GradientDrawable().apply {
setColor(fill); setStroke(dp(1), stroke); cornerRadius = dp(r).toFloat()
}
private fun mono(t: String, sz: Float, c: Int, bold: Boolean) = TextView(this).apply {
text = t; textSize = sz; setTextColor(c)
typeface = Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
}
private fun neon(t: String, c: Int, f: () -> Unit) = Button(this).apply {
text = t; setAllCaps(false); setTextColor(c)
typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
background = box(c, Color.TRANSPARENT, 24)
setOnClickListener { f() }
}
private fun field(h: String, v: String, multi: Boolean) = EditText(this).apply {
hint = h; setText(v); textSize = 13f
setTextColor(Color.WHITE); setHintTextColor(DIM)
typeface = Typeface.MONOSPACE
background = box(Color.parseColor("#12394A"), Color.parseColor("#04101A"), 10)
setPadding(dp(12), dp(10), dp(12), dp(10))
if (multi) { minLines = 4; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE } else setSingleLine(true)
}
private fun toggle() {
if (JarvisService.inst != null) stopService(Intent(this, JarvisService::class.java)) else ask()
}
override fun onCreate(b: Bundle?) {
super.onCreate(b)
val sp = getSharedPreferences("j", MODE_PRIVATE)
if (!sp.contains("prof")) sp.edit().putString("prof", DEF).apply()
val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#02070D")) }
val bgId = resources.getIdentifier("blueprint", "drawable", packageName)
if (bgId != 0) root.addView(ImageView(this).apply { setImageResource(bgId); scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0.22f }, FrameLayout.LayoutParams(-1, -1))
root.addView(GridBg(this), FrameLayout.LayoutParams(-1, -1))
val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(40), dp(18), dp(28)) }
col.addView(mono("J.A.R.V.I.S", 26f, CY, true).apply { gravity = Gravity.CENTER; letterSpacing = 0.25f })
col.addView(mono("JUST A RATHER VERY INTELLIGENT SYSTEM", 9f, Color.parseColor("#5FA8B8"), false).apply { gravity = Gravity.CENTER; letterSpacing = 0.1f })
val hudId = resources.getIdentifier("hud", "drawable", packageName)
val sheet = if (hudId != 0) BitmapFactory.decodeResource(resources, hudId) else null
if (sheet != null) {
val cuts = intArrayOf(0, 282, 296, 640, 656, 994, 1001, 1360)
val panels = ArrayList<Bitmap>()
for (i in 0 until 4) {
val y0 = cuts[i * 2].coerceAtMost(sheet.height - 2)
val y1 = cuts[i * 2 + 1].coerceAtMost(sheet.height)
if (y1 > y0) panels.add(Bitmap.createBitmap(sheet, 0, y0, sheet.width, y1 - y0))
}
if (panels.isNotEmpty()) {
var pi = 0
val iv = ImageView(this).apply {
scaleType = ImageView.ScaleType.CENTER_CROP
background = box(CY, Color.BLACK, 12)
clipToOutline = true
setImageBitmap(panels[0])
}
iv.setOnClickListener { pi = (pi + 1) % panels.size; iv.setImageBitmap(panels[pi]) }
col.addView(iv, LinearLayout.LayoutParams(-1, dp(170)).apply { topMargin = dp(14) })
iv.postDelayed(object : Runnable {
override fun run() {
if (!iv.isAttachedToWindow) return
pi = (pi + 1) % panels.size; iv.setImageBitmap(panels[pi])
iv.postDelayed(this, 4000)
}
}, 4000)
}
}
val reactor = ReactorView(this)
reactor.setOnClickListener { toggle() }
col.addView(reactor, LinearLayout.LayoutParams(dp(230), dp(230)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(18) })
val status = mono("OFFLINE", 14f, DIM, true).apply { gravity = Gravity.CENTER; letterSpacing = 0.3f; setPadding(0, dp(10), 0, dp(10)) }
col.addView(status)
val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
val cN = mono("NOTIF", 11f, Color.WHITE, true).apply { setPadding(dp(10), dp(6), dp(10), dp(6)) }
val cA = mono("ACCESS", 11f, Color.WHITE, true).apply { setPadding(dp(10), dp(6), dp(10), dp(6)) }
val cR = mono("AUTO-REPLY", 11f, Color.WHITE, true).apply { setPadding(dp(10), dp(6), dp(10), dp(6)) }
for (c in listOf(cN, cA, cR)) chips.addView(c, LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(4), 0, dp(4), 0) })
col.addView(chips)
val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(16), 0, dp(8)) }
btns.addView(neon("▶  ACTIVATE", CY) { ask() }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(6), 0) })
btns.addView(neon("■  SHUTDOWN", RD) { stopService(Intent(this, JarvisService::class.java)) }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(6), 0, 0, 0) })
col.addView(btns)
col.addView(mono("// SYSTEM LOG", 10f, Color.parseColor("#5FA8B8"), false).apply { setPadding(0, dp(10), 0, dp(4)) })
val logv = mono("", 11f, Color.parseColor("#9FE8FF"), false).apply {
setPadding(dp(12), dp(10), dp(12), dp(10)); background = box(Color.parseColor("#12394A"), Color.parseColor("#04101A"), 12)
}
col.addView(logv)
val st = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
val key = field("Gemini API key (zaroori nahi, bina key ke bhi chalega)", sp.getString("key", "") ?: "", false)
val pico = field("Picovoice AccessKey (ab zaroori nahi)", sp.getString("pico", "") ?: "", false)
val prof = field("Mere baare mein", sp.getString("prof", DEF) ?: DEF, true)
val igu = field("Tumhara Instagram username (bina @)", sp.getString("igu", "") ?: "", false)
for (f in listOf(key, pico, igu, prof)) st.addView(f, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
st.addView(neon("SAVE", CY) {
sp.edit().putString("key", key.text.toString().trim()).putString("pico", pico.text.toString().trim()).putString("prof", prof.text.toString()).putString("igu", igu.text.toString().trim().removePrefix("@")).apply()
Toast.makeText(this, "Save ho gaya", Toast.LENGTH_SHORT).show()
}, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
val tv = JarvisVoice(this)
tester = tv
st.addView(neon("HINDI AWAAZ BADLO (SUNO)", CY) {
sp.edit().putInt("hvi", sp.getInt("hvi", 0) + 1).apply()
tv.reload()
Toast.makeText(this, tv.hiName() + "  (" + tv.hiCount() + " mili)", Toast.LENGTH_LONG).show()
tv.speak("नमस्ते सर, मैं जार्विस हूँ। यह मेरी आवाज़ है।")
}, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
val pl = mono("", 11f, Color.parseColor("#9FE8FF"), false).apply { setPadding(0, dp(14), 0, 0) }
fun showPt(p: Float) { pl.text = "AWAAZ BHAARIPAN: " + String.format("%.2f", p) + "   (kam = bhaari, zyada = patli)" }
val p0 = sp.getFloat("pt", 0.65f)
showPt(p0)
st.addView(pl)
st.addView(SeekBar(this).apply {
max = 90
progress = Math.round((p0 - 0.3f) * 100f).coerceIn(0, 90)
setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
override fun onProgressChanged(s: SeekBar?, v: Int, u: Boolean) { showPt(0.3f + v / 100f) }
override fun onStartTrackingTouch(s: SeekBar?) {}
override fun onStopTrackingTouch(s: SeekBar?) {
val p = 0.3f + (s?.progress ?: 35) / 100f
sp.edit().putFloat("pt", p).apply()
tv.speak("नमस्ते सर, मैं जार्विस हूँ। यह मेरी आवाज़ है।")
}
})
}, LinearLayout.LayoutParams(-1, -2))
st.addView(mono("Male awaaz mile to ruk jao, phir SHUTDOWN aur ACTIVATE dabao.", 10f, DIM, false).apply { setPadding(0, dp(8), 0, 0) })
val head = mono("⚙  SETTINGS  ▾", 12f, CY, true).apply {
setPadding(0, dp(18), 0, dp(4))
setOnClickListener { st.visibility = if (st.visibility == View.GONE) View.VISIBLE else View.GONE }
}
col.addView(head)
col.addView(st)
root.addView(ScrollView(this).apply { addView(col) }, FrameLayout.LayoutParams(-1, -1))
if (b == null) {
val intro = IronManIntroView(this) { root.post { root.removeViewAt(root.childCount - 1) } }
intro.isClickable = true
root.addView(intro, FrameLayout.LayoutParams(-1, -1))
val gv = JarvisVoice(this)
greet = gv
gv.speak("नमस्ते सर, मैं जार्विस हूँ। All systems online.")
}
setContentView(root)
val hh = Handler(Looper.getMainLooper())
hh.post(object : Runnable {
override fun run() {
val en = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
val ac = Settings.Secure.getString(contentResolver, "enabled_accessibility_services") ?: ""
fun chip(c: TextView, label: String, ok: Boolean) {
val k = if (ok) GR else RD
c.text = (if (ok) "● " else "○ ") + label; c.setTextColor(k); c.background = box(k, Color.TRANSPARENT, 20)
}
chip(cN, "NOTIF", en.contains(packageName)); chip(cA, "ACCESS", ac.contains(packageName)); chip(cR, "AUTO-REPLY", sp.getBoolean("ar", false))
val on = JarvisService.inst != null
reactor.on = on
status.text = if (on) "ONLINE" else "OFFLINE"
status.setTextColor(if (on) CY else DIM)
logv.text = JarvisService.logs.joinToString("\n") { "› " + it }.ifEmpty { "› standby" }
hh.postDelayed(this, 1000)
}
})
}
override fun onDestroy() {
try { greet?.shutdown() } catch (e: Exception) { }
try { tester?.shutdown() } catch (e: Exception) { }
super.onDestroy()
}
private fun ask() {
if (perms.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) requestPermissions(perms, 1)
else proceed()
}
override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, g: IntArray) {
proceed()
}
private fun proceed() {
if (checkSelfPermission(RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
Toast.makeText(this, "Mic ki permission zaroori hai", Toast.LENGTH_LONG).show(); return
}
if (!Settings.canDrawOverlays(this)) {
Toast.makeText(this, "Jarvis ko 'Display over other apps' allow karo, phir wapas aakar ON dabao", Toast.LENGTH_LONG).show()
startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
return
}
startForegroundService(Intent(this, JarvisService::class.java))
}
}
