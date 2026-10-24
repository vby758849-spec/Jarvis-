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
private val SAMPLE = "Hello sir, I am Jarvis. This is my voice."
private val SAMPLE_HI = "नमस्ते सर, मैं जार्विस हूँ। यह मेरी आवाज़ है।"
private fun say(t: String) { if (getSharedPreferences("j", MODE_PRIVATE).getString("lang", "en") == "en") vc().speakEn(t) else vc().speak(SAMPLE_HI) }
private var voice: JarvisVoice? = null
private var xikF: EditText? = null
private var ownF: EditText? = null
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
override fun onCreate(b: Bundle?) {
super.onCreate(b)
val sp = getSharedPreferences("j", MODE_PRIVATE)
val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#02070D")) }
root.addView(GridBg(this), FrameLayout.LayoutParams(-1, -1))
val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(40), dp(18), dp(28)) }
col.addView(mono("J.A.R.V.I.S", 26f, CY, true).apply { gravity = Gravity.CENTER; letterSpacing = 0.25f })
col.addView(mono("JUST A RATHER VERY INTELLIGENT SYSTEM", 9f, Color.parseColor("#5FA8B8"), false).apply { gravity = Gravity.CENTER; letterSpacing = 0.1f })
val robo = RoboHead(this)
col.addView(robo, LinearLayout.LayoutParams(-1, dp(190)).apply { topMargin = dp(14) })
val panel = col
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
say(SAMPLE)
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

sec("ACCOUNT")
val key = field("Gemini API key", sp.getString("key", "") ?: "", false)
val igu = field("Instagram username (without @)", sp.getString("igu", "") ?: "", false)
add("gemini key api token chabi चाबी", key)
add("instagram insta username इंस्टा", igu)
val own = field("Your name (shown in auto-replies)", sp.getString("owner", "") ?: "", false)
ownF = own
add("naam name owner aapka नाम", own)
add("save सेव", neon("SAVE", CY) {
sp.edit().putString("owner", (ownF?.text?.toString() ?: "").trim()).putString("xik", (xikF?.text?.toString() ?: sp.getString("xik", "") ?: "").trim()).putString("key", key.text.toString().trim()).putString("igu", igu.text.toString().trim().removePrefix("@")).apply()
Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
})
add("jaankari details privacy mitao delete profile meri जानकारी", neon("DELETE MY INFO", RD) {
sp.edit().remove("prof").apply()
Toast.makeText(this, "Your saved info was deleted", Toast.LENGTH_SHORT).show()
})

sec("VOICE")
add("awaaz voice test suno sunao आवाज़", neon("TEST VOICE", CY) { say(SAMPLE) })
val lb = neon("", CY) { }
fun shL() { lb.text = "LANGUAGE: " + (if (sp.getString("lang", "en") == "en") "ENGLISH" else "HINDI") }
shL()
lb.setOnClickListener {
sp.edit().putString("lang", if (sp.getString("lang", "en") == "en") "hi" else "en").apply()
shL()
Toast.makeText(this, "Press SHUTDOWN, then ACTIVATE", Toast.LENGTH_SHORT).show()
}
add("bhasha language hindi english भाषा अंग्रेज़ी हिंदी", lb)
add("hindi awaaz badlo voice picker हिंदी", neon("CHANGE HINDI VOICE (TEST)", CY) {
sp.edit().putInt("hvi", sp.getInt("hvi", 0) + 1).apply()
vc().reload()
Toast.makeText(this, vc().hiName() + "  (" + vc().hiCount() + " found)", Toast.LENGTH_LONG).show()
vc().speak(SAMPLE_HI)
})
add("english awaaz badlo voice picker अंग्रेज़ी", neon("CHANGE ENGLISH VOICE (TEST)", CY) {
sp.edit().putInt("vk", sp.getInt("vk", 0) + 1).apply()
vc().reload()
Toast.makeText(this, vc().enName(), Toast.LENGTH_LONG).show()
vc().speakEn("Hello boss, all systems are online.")
})
tgl("ek hi awaaz one voice alag hindi english एक", "onevoice", true, "ONE VOICE FOR BOTH LANGUAGES") { vc().reload(); say(SAMPLE) }
val refP = slider("bhaaripan bhaari patli pitch awaaz भारी", "VOICE DEPTH", "pt", 0.65f, 0.3f, 1.2f, "(lower = deeper, higher = lighter)")
val refR = slider("raftar speed tez dheere rate रफ़्तार", "VOICE SPEED", "rt", 1.05f, 0.6f, 1.6f, "(lower = slower, higher = faster)")
val pre = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
pre.addView(neon("DEEP", CY) { sp.edit().putFloat("pt", 0.5f).putFloat("rt", 0.95f).apply(); refP(); refR(); say(SAMPLE) }, LinearLayout.LayoutParams(0, -2, 1f))
pre.addView(neon("NORMAL", CY) { sp.edit().putFloat("pt", 0.65f).putFloat("rt", 1.05f).apply(); refP(); refR(); say(SAMPLE) }, LinearLayout.LayoutParams(0, -2, 1f))
pre.addView(neon("LIGHT", CY) { sp.edit().putFloat("pt", 0.95f).putFloat("rt", 1.1f).apply(); refP(); refR(); say(SAMPLE) }, LinearLayout.LayoutParams(0, -2, 1f))
add("preset gambhir normal patli bhaari awaaz style", pre)
tgl("auto volume badhana awaaz volume वॉल्यूम", "manvol", false, "AUTO VOLUME BOOST", true)

sec("VOICE CLONE (from an audio clip)")
val xik = field("ElevenLabs API key (clone ke liye)", sp.getString("xik", "") ?: "", false)
xikF = xik
add("clone clip voice nakal awaaz elevenlabs key kisi ki awaaz क्लोन", xik)
val cl = mono("", 11f, Color.parseColor("#9FE8FF"), false).apply { setPadding(0, dp(4), 0, 0) }
cvLab = cl
cl.text = if (sp.getString("cvid", "").isNullOrEmpty()) "Clone voice: none" else "Clone voice: " + (sp.getString("cvn", "") ?: "") + " (ready)"
add("clone clip voice nakal awaaz status", cl)
add("clip chuno upload audio awaaz clone voice sample क्लोन", neon("CHOOSE VOICE CLIP (UPLOAD)", CY) {
sp.edit().putString("xik", xik.text.toString().trim()).apply()
if (xik.text.toString().trim().isEmpty()) { Toast.makeText(this, "Enter your ElevenLabs key first", Toast.LENGTH_LONG).show() }
else startActivityForResult(Intent.createChooser(Intent(Intent.ACTION_GET_CONTENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE), "Choose a voice clip"), 77)
})
tgl("clone awaaz on off chalu band", "cv", true, "CLONE VOICE")
add("clone hatao delete awaaz voice", neon("REMOVE CLONE VOICE", RD) {
sp.edit().remove("cvid").remove("cvn").apply()
cl.text = "Clone voice: none"
Toast.makeText(this, "Clone removed, using the normal voice", Toast.LENGTH_SHORT).show()
})

sec("WAKE & LISTEN")
tgl("shaant mode reels movie video media apne aap bolna chup शांत", "calm", true, "QUIET MODE (stay silent during reels/movies)")
tgl("message batana notification announce बताना", "an", true, "ANNOUNCE INCOMING MESSAGES")

sec("APP UPDATE")
add("update naya version check download install अपडेट", neon("CHECK FOR UPDATE", CY) { Updater.check(this, true) })
add("version build update", mono("Current build: " + Updater.cur(this), 11f, Color.parseColor("#9FE8FF"), false))

sec("DISPLAY")
tgl("system log dikhao chhupao लॉग", "showlog", false, "SHOW SYSTEM LOG") {
val on = sp.getBoolean("showlog", false)
logHead.visibility = if (on) View.VISIBLE else View.GONE
logv.visibility = if (on) View.VISIBLE else View.GONE
}

val head = mono("⚙  SETTINGS  ▾", 12f, CY, true).apply {
setPadding(0, dp(18), 0, dp(4))
setOnClickListener { st.visibility = if (st.visibility == View.GONE) View.VISIBLE else View.GONE }
}
val none = mono("No settings found", 11f, DIM, false).apply { setPadding(0, dp(8), 0, 0); visibility = View.GONE }
val q = field("Search settings: voice, key, quiet, volume...", "", false)
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
RoboSound.boot()
val splash = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#02070D")); isClickable = true; alpha = 0f }
splash.addView(GridBg(this), FrameLayout.LayoutParams(-1, -1))
val big = RoboHead(this)
splash.addView(big, FrameLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * 0.55f).toInt(), Gravity.CENTER_VERTICAL).apply { bottomMargin = dp(60) })
val bt = mono("INITIALIZING...", 13f, CY, true).apply { gravity = Gravity.CENTER; letterSpacing = 0.3f }
splash.addView(bt, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = dp(90) })
root.addView(splash, FrameLayout.LayoutParams(-1, -1))
splash.animate().alpha(1f).setDuration(400).start()
splash.postDelayed({ big.on = true; bt.text = "SYSTEMS ONLINE" }, 1500)
var gone = false
val close: () -> Unit = {
if (!gone) {
gone = true
splash.animate().cancel()
splash.animate().alpha(0f).setDuration(500).withEndAction { root.removeView(splash) }.start()
}
}
splash.setOnClickListener { close() }
splash.postDelayed({ close() }, 3200)
if (sp.getString("lang", "en") == "en") vc().speakEn("Hello sir, I am Jarvis. All systems are online.") else vc().speak("नमस्ते सर, मैं जार्विस हूँ। सभी सिस्टम ऑनलाइन हैं।")
}
setContentView(root)
if ((sp.getString("key", "") ?: "").isEmpty()) Toast.makeText(this, "First enter your Gemini API key in Settings (it is free)", Toast.LENGTH_LONG).show()
if (b == null) hh.postDelayed({ Updater.check(this, false) }, 4500)
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
robo.on = on
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
AlertDialog.Builder(this).setTitle("Permission required")
.setMessage("This clip must be your own voice, or the voice of a person who has given you permission. Is that the case?")
.setNegativeButton("No", null)
.setPositiveButton("Yes, I have permission") { _, _ ->
val key = sp.getString("xik", "") ?: ""
val bytes = try { contentResolver.openInputStream(u)?.use { it.readBytes() } } catch (e: Exception) { null }
if (bytes == null || bytes.isEmpty()) { Toast.makeText(this, "Could not read the clip", Toast.LENGTH_LONG).show() }
else if (bytes.size > 10 * 1024 * 1024) { Toast.makeText(this, "Choose a clip under 10 MB (1-2 minutes is enough)", Toast.LENGTH_LONG).show() }
else {
cvLab?.text = "Creating clone..."
val nm = "Jarvis-" + (System.currentTimeMillis() / 1000)
CloneVoice.create(key, nm, bytes, "clip.mp3") { id, msg ->
if (id != null) {
sp.edit().putString("cvid", id).putString("cvn", nm).putBoolean("cv", true).apply()
cvLab?.text = "Clone voice: " + nm + " (ready)"
Toast.makeText(this, "Clone created. Press SHUTDOWN, then ACTIVATE", Toast.LENGTH_LONG).show()
} else {
cvLab?.text = "Clone failed: " + msg
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
Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_LONG).show(); return
}
if (!Settings.canDrawOverlays(this)) {
Toast.makeText(this, "Allow Jarvis to Display over other apps, then come back and press ACTIVATE", Toast.LENGTH_LONG).show()
startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
return
}
startForegroundService(Intent(this, JarvisService::class.java))
}
}
