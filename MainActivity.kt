package com.vaibhav.jarvis
import android.Manifest.permission.*
import android.app.Activity
import android.app.AlertDialog
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
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
class MainActivity : Activity() {
private val perms = mutableListOf(RECORD_AUDIO, CALL_PHONE, SEND_SMS, READ_CONTACTS, ANSWER_PHONE_CALLS, ACCESS_COARSE_LOCATION, READ_CALENDAR).apply {
if (Build.VERSION.SDK_INT >= 33) add(POST_NOTIFICATIONS)
}.toTypedArray()
private val CY = Color.parseColor("#00E5FF")
private val GR = Color.parseColor("#2BFF88")
private val RD = Color.parseColor("#FF4D6D")
private val DIM = Color.parseColor("#7A8FA3")
private val SAMPLE = "नमस्ते सर, मैं जार्विस हूँ। यह मेरी आवाज़ है।"
private var voice: JarvisVoice? = null
private var xikF: EditText? = null
private var cvLab: TextView? = null
private val hh = Handler(Looper.getMainLooper())
private var tick: Runnable? = null
private fun vc(): JarvisVoice { val v = voice ?: JarvisVoice(this); voice = v; return v }
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
private fun field(h: String, v: String, secret: Boolean) = EditText(this).apply {
hint = h; setText(v); textSize = 13f
setTextColor(Color.WHITE); setHintTextColor(DIM)
typeface = Typeface.MONOSPACE
background = box(Color.parseColor("#12394A"), Color.parseColor("#04101A"), 10)
setPadding(dp(12), dp(10), dp(12), dp(10))
setSingleLine(true)
if (secret) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
}
private fun toggle() {
if (JarvisService.inst != null) stopService(Intent(this, JarvisService::class.java)) else ask()
}
override fun onCreate(b: Bundle?) {
super.onCreate(b)
val sp = getSharedPreferences("j", MODE_PRIVATE)
val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#02070D")) }
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
sheet.recycle()
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
if (iv.hasWindowFocus()) { pi = (pi + 1) % panels.size; iv.setImageBitmap(panels[pi]) }
iv.postDelayed(this, 4000)
}
}, 4000)
}
}
val panel = col
val reactor = ReactorView(this)
reactor.setOnClickListener { toggle() }
val status = mono("OFFLINE", 14f, DIM, true).apply { gravity = Gravity.CENTER; letterSpacing = 0.3f; setPadding(0, dp(10), 0, dp(10)) }
panel.addView(status)
val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
val cN = mono("NOTIF", 11f, Color.WHITE, true).apply { setPadding(dp(10), dp(6), dp(10), dp(6)) }
val cA = mono("ACCESS", 11f, Color.WHITE, true).apply { setPadding(dp(10), dp(6), dp(10), dp(6)) }
val cR = mono("AUTO-REPLY", 11f, Color.WHITE, true).apply { setPadding(dp(10), dp(6), dp(10), dp(6)) }
for (c in listOf(cN, cA, cR)) chips.addView(c, LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(4), 0, dp(4), 0) })
panel.addView(chips)
val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(16), 0, dp(8)) }
btns.addView(neon("▶  ACTIVATE", CY) { ask() }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(6), 0) })
btns.addView(neon("■  SHUTDOWN", RD) { stopService(Intent(this, JarvisService::class.java)) }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(6), 0, 0, 0) })
panel.addView(btns)
val logHead = mono("// SYSTEM LOG", 10f, Color.parseColor("#5FA8B8"), false).apply { setPadding(0, dp(14), 0, dp(4)) }
val logv = mono("", 11f, Color.parseColor("#9FE8FF"), false).apply {
setPadding(dp(12), dp(10), dp(12), dp(10)); background = box(Color.parseColor("#12394A"), Color.parseColor("#04101A"), 12)
}
val showLog = { sp.getBoolean("showlog", false) }
logHead.visibility = if (showLog()) View.VISIBLE else View.GONE
logv.visibility = if (showLog()) View.VISIBLE else View.GONE
panel.addView(logHead)
panel.addView(logv)

val st = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
val items = ArrayList<Pair<String, View>>()
fun add(keys: String, v: View, top: Int = 10) {
st.addView(v, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
items.add(keys.lowercase() to v)
}
fun sec(title: String) {
add("\u0000", mono("// " + title, 10f, Color.parseColor("#5FA8B8"), false).apply { setPadding(0, dp(14), 0, 0) }, 4)
}
fun slider(keys: String, title: String, pk: String, def: Float, mn: Float, mx: Float, hint: String): () -> Unit {
val lab = mono("", 11f, Color.parseColor("#9FE8FF"), false).apply { setPadding(0, dp(4), 0, 0) }
val sb = SeekBar(this).apply { max = 100 }
fun show(v: Float) { lab.text = title + ": " + String.format("%.2f", v) + "   " + hint }
fun refresh() {
val v = sp.getFloat(pk, def)
show(v)
sb.progress = Math.round((v - mn) / (mx - mn) * 100f).coerceIn(0, 100)
}
sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
override fun onProgressChanged(s: SeekBar?, v: Int, u: Boolean) { if (u) show(mn + (mx - mn) * v / 100f) }
override fun onStartTrackingTouch(s: SeekBar?) {}
override fun onStopTrackingTouch(s: SeekBar?) {
sp.edit().putFloat(pk, mn + (mx - mn) * (s?.progress ?: 0) / 100f).apply()
vc().speak(SAMPLE)
}
})
refresh()
add(keys, LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(lab); addView(sb) })
return { refresh() }
}
fun tgl(keys: String, pk: String, def: Boolean, label: String, inv: Boolean = false, after: () -> Unit = {}) {
val bt = neon("", CY) { }
fun sh() { bt.text = label + ": " + (if (sp.getBoolean(pk, def) != inv) "ON" else "OFF") }
sh()
bt.setOnClickListener { sp.edit().putBoolean(pk, !sp.getBoolean(pk, def)).apply(); sh(); after() }
add(keys, bt)
}

sec("KHAATA")
val key = field("Gemini API key", sp.getString("key", "") ?: "", false)
val igu = field("Instagram username (bina @)", sp.getString("igu", "") ?: "", false)
add("gemini key api jemini chabi चाबी", key)
add("instagram insta username इंस्टा", igu)
add("save सेव", neon("SAVE", CY) {
sp.edit().putString("xik", (xikF?.text?.toString() ?: sp.getString("xik", "") ?: "").trim()).putString("key", key.text.toString().trim()).putString("igu", igu.text.toString().trim().removePrefix("@")).apply()
Toast.makeText(this, "Save ho gaya", Toast.LENGTH_SHORT).show()
})
add("jaankari details privacy mitao delete profile meri जानकारी", neon("MERI JAANKARI MITAO", RD) {
sp.edit().remove("prof").apply()
Toast.makeText(this, "Meri jaankari mita di", Toast.LENGTH_SHORT).show()
})

sec("AWAAZ")
add("awaaz voice test suno sunao आवाज़", neon("AWAAZ SUNO", CY) { vc().speak(SAMPLE) })
val lb = neon("", CY) { }
fun shL() { lb.text = "BHASHA: " + (if (sp.getString("lang", "hi") == "en") "ENGLISH" else "HINDI") }
shL()
lb.setOnClickListener {
sp.edit().putString("lang", if (sp.getString("lang", "hi") == "en") "hi" else "en").apply()
shL()
Toast.makeText(this, "SHUTDOWN phir ACTIVATE dabao", Toast.LENGTH_SHORT).show()
}
add("bhasha language hindi english भाषा अंग्रेज़ी हिंदी", lb)
add("hindi awaaz badlo voice picker हिंदी", neon("HINDI AWAAZ BADLO (SUNO)", CY) {
sp.edit().putInt("hvi", sp.getInt("hvi", 0) + 1).apply()
vc().reload()
Toast.makeText(this, vc().hiName() + "  (" + vc().hiCount() + " mili)", Toast.LENGTH_LONG).show()
vc().speak(SAMPLE)
})
add("english awaaz badlo voice picker अंग्रेज़ी", neon("ENGLISH AWAAZ BADLO (SUNO)", CY) {
sp.edit().putInt("vk", sp.getInt("vk", 0) + 1).apply()
vc().reload()
Toast.makeText(this, vc().enName(), Toast.LENGTH_LONG).show()
vc().speakEn("Hello boss, all systems are online.")
})
tgl("ek hi awaaz one voice alag hindi english एक", "onevoice", true, "EK HI AWAAZ (Hindi+English)") { vc().reload(); vc().speak(SAMPLE) }
val refP = slider("bhaaripan bhaari patli pitch awaaz भारी", "AWAAZ BHAARIPAN", "pt", 0.65f, 0.3f, 1.2f, "(kam = bhaari, zyada = patli)")
val refR = slider("raftar speed tez dheere rate रफ़्तार", "AWAAZ RAFTAR", "rt", 1.05f, 0.6f, 1.6f, "(kam = dheere, zyada = tez)")
val pre = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
pre.addView(neon("GAMBHIR", CY) { sp.edit().putFloat("pt", 0.5f).putFloat("rt", 0.95f).apply(); refP(); refR(); vc().speak(SAMPLE) }, LinearLayout.LayoutParams(0, -2, 1f))
pre.addView(neon("NORMAL", CY) { sp.edit().putFloat("pt", 0.65f).putFloat("rt", 1.05f).apply(); refP(); refR(); vc().speak(SAMPLE) }, LinearLayout.LayoutParams(0, -2, 1f))
pre.addView(neon("PATLI", CY) { sp.edit().putFloat("pt", 0.95f).putFloat("rt", 1.1f).apply(); refP(); refR(); vc().speak(SAMPLE) }, LinearLayout.LayoutParams(0, -2, 1f))
add("preset gambhir normal patli bhaari awaaz style", pre)
tgl("auto volume badhana awaaz volume वॉल्यूम", "manvol", false, "AUTO VOLUME BADHANA", true)

sec("NAKAL AWAAZ (kisi ki clip se)")
val xik = field("ElevenLabs API key (clone ke liye)", sp.getString("xik", "") ?: "", false)
xikF = xik
add("clone clip voice nakal awaaz elevenlabs key kisi ki awaaz क्लोन", xik)
val cl = mono("", 11f, Color.parseColor("#9FE8FF"), false).apply { setPadding(0, dp(4), 0, 0) }
cvLab = cl
cl.text = if (sp.getString("cvid", "").isNullOrEmpty()) "Clone awaaz: koi nahi" else "Clone awaaz: " + (sp.getString("cvn", "") ?: "") + " (taiyaar)"
add("clone clip voice nakal awaaz status", cl)
add("clip chuno upload audio awaaz clone voice sample क्लोन", neon("AWAAZ CLIP CHUNO (UPLOAD)", CY) {
sp.edit().putString("xik", xik.text.toString().trim()).apply()
if (xik.text.toString().trim().isEmpty()) { Toast.makeText(this, "Pehle ElevenLabs key daalo", Toast.LENGTH_LONG).show() }
else startActivityForResult(Intent.createChooser(Intent(Intent.ACTION_GET_CONTENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE), "Awaaz ki clip chuno"), 77)
})
tgl("clone awaaz on off chalu band", "cv", true, "CLONE AWAAZ")
add("clone hatao delete awaaz voice", neon("CLONE AWAAZ HATAO", RD) {
sp.edit().remove("cvid").remove("cvn").apply()
cl.text = "Clone awaaz: koi nahi"
Toast.makeText(this, "Clone hata diya, ab normal awaaz", Toast.LENGTH_SHORT).show()
})

sec("JAAGNA AUR SUNNA")
tgl("shaant mode reels movie video media apne aap bolna chup शांत", "calm", true, "SHAANT MODE (reels/movie mein na jaage)")
tgl("message batana notification announce बताना", "an", true, "MESSAGE AANE PAR BATANA")

sec("DIKHAVA")
tgl("system log dikhao chhupao लॉग", "showlog", false, "SYSTEM LOG DIKHAO") {
val on = sp.getBoolean("showlog", false)
logHead.visibility = if (on) View.VISIBLE else View.GONE
logv.visibility = if (on) View.VISIBLE else View.GONE
}

val head = mono("⚙  SETTINGS  ▾", 12f, CY, true).apply {
setPadding(0, dp(18), 0, dp(4))
setOnClickListener { st.visibility = if (st.visibility == View.GONE) View.VISIBLE else View.GONE }
}
val none = mono("Koi setting nahi mili", 11f, DIM, false).apply { setPadding(0, dp(8), 0, 0); visibility = View.GONE }
val q = field("Setting khojo: awaaz, key, shaant, volume...", "", false)
q.addTextChangedListener(object : TextWatcher {
override fun beforeTextChanged(s: CharSequence?, a: Int, b2: Int, c: Int) {}
override fun onTextChanged(s: CharSequence?, a: Int, b2: Int, c: Int) {}
override fun afterTextChanged(e: Editable?) {
val s = (e?.toString() ?: "").lowercase().trim()
if (s.isNotEmpty()) st.visibility = View.VISIBLE
var n = 0
for ((k, v) in items) {
val ok = s.isEmpty() || k.contains(s)
v.visibility = if (ok) View.VISIBLE else View.GONE
if (ok && k != "\u0000") n++
}
none.visibility = if (s.isNotEmpty() && n == 0) View.VISIBLE else View.GONE
}
})
panel.addView(head)
panel.addView(q, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
panel.addView(none)
panel.addView(st)
root.addView(ScrollView(this).apply { addView(col) }, FrameLayout.LayoutParams(-1, -1))

if (b == null) {
val sid = resources.getIdentifier("blueprint", "drawable", packageName)
if (sid != 0) {
window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
if (Build.VERSION.SDK_INT >= 28) {
val wl = window.attributes
wl.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
window.attributes = wl
}
val splash = FrameLayout(this).apply { setBackgroundColor(Color.BLACK); isClickable = true; alpha = 0f }
val img = ImageView(this).apply { setImageResource(sid); scaleType = ImageView.ScaleType.CENTER_CROP }
splash.addView(img, FrameLayout.LayoutParams(-1, -1))
val rc = ReactorView(this).apply { on = true; alpha = 0f; scaleX = 0.4f; scaleY = 0.4f }
splash.addView(rc, FrameLayout.LayoutParams(1, 1))
root.addView(splash, FrameLayout.LayoutParams(-1, -1))
splash.post {
val w = splash.width.toFloat()
val h = splash.height.toFloat()
val dw = (img.drawable?.intrinsicWidth ?: 675).toFloat()
val dh = (img.drawable?.intrinsicHeight ?: 1200).toFloat()
val sc = maxOf(w / dw, h / dh)
val cx = 0.489f * dw * sc - (dw * sc - w) / 2f
val cy = 0.291f * dh * sc - (dh * sc - h) / 2f
val d = (0.30f * dw * sc).toInt()
rc.layoutParams = FrameLayout.LayoutParams(d, d).apply { leftMargin = (cx - d / 2f).toInt(); topMargin = (cy - d / 2f).toInt() }
}
splash.animate().alpha(1f).setDuration(500).start()
rc.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(700).setDuration(800).start()
var gone = false
val close: () -> Unit = {
if (!gone) {
gone = true
splash.animate().cancel()
splash.animate().alpha(0f).setDuration(600).withEndAction {
root.removeView(splash)
window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
}.start()
}
}
splash.setOnClickListener { close() }
splash.postDelayed({ close() }, 4300)
}
vc().speak("नमस्ते सर, मैं जार्विस हूँ। सभी सिस्टम ऑनलाइन हैं।")
}
setContentView(root)
tick = object : Runnable {
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
if (logv.visibility == View.VISIBLE) logv.text = JarvisService.logs.joinToString("\n") { "› " + it }.ifEmpty { "› standby" }
hh.postDelayed(this, 1000)
}
}
}
override fun onResume() {
super.onResume()
tick?.let { hh.post(it) }
}
override fun onPause() {
super.onPause()
tick?.let { hh.removeCallbacks(it) }
}
override fun onDestroy() {
tick?.let { hh.removeCallbacks(it) }
try { voice?.shutdown() } catch (e: Exception) { }
super.onDestroy()
}
override fun onActivityResult(rq: Int, rs: Int, d: Intent?) {
super.onActivityResult(rq, rs, d)
val u = d?.data
if (rq != 77 || rs != RESULT_OK || u == null) return
val sp = getSharedPreferences("j", MODE_PRIVATE)
AlertDialog.Builder(this).setTitle("Ijazat zaroori hai")
.setMessage("Ye clip sirf tumhari apni awaaz ki ho, ya us insaan ki jisne ijazat di ho. Kya aisa hi hai?")
.setNegativeButton("Nahi", null)
.setPositiveButton("Haan, ijazat hai") { _, _ ->
val key = sp.getString("xik", "") ?: ""
val bytes = try { contentResolver.openInputStream(u)?.use { it.readBytes() } } catch (e: Exception) { null }
if (bytes == null || bytes.isEmpty()) { Toast.makeText(this, "Clip padh nahi payi", Toast.LENGTH_LONG).show() }
else if (bytes.size > 10 * 1024 * 1024) { Toast.makeText(this, "Clip 10MB se choti chuno (1-2 minute kaafi hai)", Toast.LENGTH_LONG).show() }
else {
cvLab?.text = "Clone ban raha hai..."
val nm = "Jarvis-" + (System.currentTimeMillis() / 1000)
CloneVoice.create(key, nm, bytes, "clip.mp3") { id, msg ->
if (id != null) {
sp.edit().putString("cvid", id).putString("cvn", nm).putBoolean("cv", true).apply()
cvLab?.text = "Clone awaaz: " + nm + " (taiyaar)"
Toast.makeText(this, "Clone ban gaya. SHUTDOWN phir ACTIVATE dabao", Toast.LENGTH_LONG).show()
} else {
cvLab?.text = "Clone nahi bana: " + msg
}
}
}
}.show()
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
