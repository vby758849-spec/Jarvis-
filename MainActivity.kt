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
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
class MainActivity : Activity() {
private val perms = mutableListOf(RECORD_AUDIO, CALL_PHONE, SEND_SMS, READ_CONTACTS, ANSWER_PHONE_CALLS, ACCESS_COARSE_LOCATION, READ_CALENDAR).apply {
if (Build.VERSION.SDK_INT >= 33) add(POST_NOTIFICATIONS)
}.toTypedArray()
private val CY = Color.parseColor("#A9B8FF")
private val GR = Color.parseColor("#FFC27A")
private val RD = Color.parseColor("#FF6B8A")
private val DIM = Color.parseColor("#8A8FB5")
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
background = box(Color.parseColor("#2B2F66"), Color.parseColor("#0B0C22"), 10)
setPadding(dp(12), dp(10), dp(12), dp(10))
setSingleLine(true)
if (secret) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
}
override fun onCreate(b: Bundle?) {
super.onCreate(b)
val sp = getSharedPreferences("j", MODE_PRIVATE)
val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#05060F")) }
root.addView(PremiumBg(this), FrameLayout.LayoutParams(-1, -1))
val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(28), dp(18), dp(28)) }
col.addView(mono("J.A.R.V.I.S", 26f, CY, true).apply { gravity = Gravity.CENTER; letterSpacing = 0.25f })
col.addView(mono("JUST A RATHER VERY INTELLIGENT SYSTEM", 9f, Color.parseColor("#8C86C9"), false).apply { gravity = Gravity.CENTER; letterSpacing = 0.1f })
val robo = BotView(this)
col.addView(robo, LinearLayout.LayoutParams(-1, dp(200)).apply { topMargin = dp(6) })
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
panel.addView(neon("\uD83D\uDCAC  CHAT WITH JARVIS", GR) { openChat(root) }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
val logHead = mono("// SYSTEM LOG", 10f, Color.parseColor("#8C86C9"), false).apply { setPadding(0, dp(14), 0, dp(4)) }
val logv = mono("", 11f, Color.parseColor("#D6DCFF"), false).apply {
setPadding(dp(12), dp(10), dp(12), dp(10)); background = box(Color.parseColor("#2B2F66"), Color.parseColor("#0B0C22"), 12)
}
val showLog = { sp.getBoolean("showlog", false) }
logHead.visibility = if (showLog()) View.VISIBLE else View.GONE
logv.visibility = if (showLog()) View.VISIBLE else View.GONE
panel.addView(logHead)
panel.addView(logv)

val st = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
val items = ArrayList<Pair<String, View>>()
var applyVis: () -> Unit = {}
var secN = 0
var curSec = -1
val open = HashMap<Int, Boolean>()
val secOf = HashMap<View, Int>()
fun add(keys: String, v: View, top: Int = 10) {
st.addView(v, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
items.add(keys.lowercase() to v)
secOf[v] = curSec
}
fun sec(title: String) {
val idx = secN++
curSec = idx
val hv = mono("▸  " + title, 11f, CY, true).apply { setPadding(0, dp(12), 0, dp(6)); letterSpacing = 0.12f }
hv.setOnClickListener { open[idx] = !(open[idx] ?: false); hv.text = (if (open[idx] == true) "▾  " else "▸  ") + title; applyVis() }
add("\u0000", hv, 6)
}
fun slider(keys: String, title: String, pk: String, def: Float, mn: Float, mx: Float, hint: String): () -> Unit {
val lab = mono("", 11f, Color.parseColor("#D6DCFF"), false).apply { setPadding(0, dp(4), 0, 0) }
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
sp.edit().remove("prof").remove("chatlog").apply()
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
add("install more voices download voice data text to speech tts engine", neon("INSTALL MORE VOICES", CY) {
try { startActivity(Intent("com.android.settings.TTS_SETTINGS")) } catch (e: Exception) { try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (e2: Exception) { } }
})
add("hindi awaaz badlo voice picker हिंदी", neon("CHANGE HINDI VOICE (TEST)", CY) {
sp.edit().putInt("hvi", sp.getInt("hvi", 0) + 1).apply()
vc().reload()
Toast.makeText(this, vc().hiName() + "  (" + vc().hiCount() + " found)", Toast.LENGTH_LONG).show()
vc().speak(SAMPLE_HI)
})
add("english awaaz badlo voice picker अंग्रेज़ी", neon("CHANGE ENGLISH VOICE (TEST)", CY) {
sp.edit().putInt("vk", sp.getInt("vk", 0) + 1).apply()
vc().reload()
val n = vc().enCount()
Toast.makeText(this, if (n <= 1) "Only " + n + " English voice installed on this phone. Tap INSTALL MORE VOICES below." else "Voice " + ((sp.getInt("vk", 0) % n) + 1) + " of " + n + ": " + vc().enName(), Toast.LENGTH_LONG).show()
vc().speakEn("Hello boss, all systems are online.")
})
tgl("ek hi awaaz one voice alag hindi english एक", "onevoice", true, "ONE VOICE FOR BOTH LANGUAGES") { vc().reload(); say(SAMPLE) }
val refP = slider("bhaaripan bhaari patli pitch awaaz भारी", "VOICE DEPTH", "pt", 0.85f, 0.3f, 1.2f, "(lower = deeper, higher = lighter)")
val refR = slider("raftar speed tez dheere rate रफ़्तार", "VOICE SPEED", "rt", 1.0f, 0.6f, 1.6f, "(lower = slower, higher = faster)")
val pre = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
pre.addView(neon("DEEP", CY) { sp.edit().putFloat("pt", 0.7f).putFloat("rt", 0.95f).apply(); refP(); refR(); say(SAMPLE) }, LinearLayout.LayoutParams(0, -2, 1f))
pre.addView(neon("NORMAL", CY) { sp.edit().putFloat("pt", 0.85f).putFloat("rt", 1.0f).apply(); refP(); refR(); say(SAMPLE) }, LinearLayout.LayoutParams(0, -2, 1f))
pre.addView(neon("LIGHT", CY) { sp.edit().putFloat("pt", 1.0f).putFloat("rt", 1.05f).apply(); refP(); refR(); say(SAMPLE) }, LinearLayout.LayoutParams(0, -2, 1f))
add("preset gambhir normal patli bhaari awaaz style", pre)
tgl("auto volume badhana awaaz volume वॉल्यूम", "manvol", false, "AUTO VOLUME BOOST", true)

sec("VOICE CLONE (from an audio clip)")
val xik = field("ElevenLabs API key (clone ke liye)", sp.getString("xik", "") ?: "", false)
xikF = xik
add("clone clip voice nakal awaaz elevenlabs key kisi ki awaaz क्लोन", xik)
val cl = mono("", 11f, Color.parseColor("#D6DCFF"), false).apply { setPadding(0, dp(4), 0, 0) }
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

sec("AUTO-REPLY")
tgl("auto reply on off chalu band ऑटो रिप्लाई", "ar", false, "AUTO-REPLY")
val md = neon("", CY) { }
fun shM() { md.text = "MODE: " + (if (sp.getString("arm", "p") == "b") "BUSINESS (talk to my customers)" else "PERSONAL (casual use)") }
shM()
md.setOnClickListener { sp.edit().putString("arm", if (sp.getString("arm", "p") == "b") "p" else "b").apply(); shM() }
add("auto reply mode personal business professional vyapar dukaan ऑटो रिप्लाई मोड", md)
add("business details customer vyapar dukaan professional", mono("BUSINESS MODE: Jarvis replies to your customers using the details below. Fill them once and press SAVE BUSINESS.", 10f, Color.parseColor("#8C86C9"), false))
fun mf(h: String, k: String) = field(h, sp.getString(k, "") ?: "", false).apply { setSingleLine(false); minLines = 2; maxLines = 5; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE; gravity = android.view.Gravity.TOP or android.view.Gravity.START }
val fbn = field("Business name", sp.getString("bn", "") ?: "", false)
val fbd = mf("What you sell / your services", "bd")
val fbp = mf("Prices / rate list", "bp")
val fbh = mf("Working hours / days", "bh")
val fba = mf("Address / city / delivery area", "ba")
val fbc = mf("How to order, contact number, payment info", "bc")
val fbx = mf("Extra rules / FAQ (refund, offers, what NOT to say)", "bx")
add("business name vyapar dukaan naam", fbn)
add("business sell services products kya bechte", fbd)
add("business price rate list daam", fbp)
add("business hours timing samay", fbh)
add("business address city delivery pata", fba)
add("business order contact payment upi", fbc)
add("business faq rules refund offer", fbx)
add("save business vyapar details सेव", neon("SAVE BUSINESS", CY) {
sp.edit().putString("bn", fbn.text.toString().trim()).putString("bd", fbd.text.toString().trim()).putString("bp", fbp.text.toString().trim()).putString("bh", fbh.text.toString().trim()).putString("ba", fba.text.toString().trim()).putString("bc", fbc.text.toString().trim()).putString("bx", fbx.text.toString().trim()).apply()
Toast.makeText(this, "Business details saved", Toast.LENGTH_SHORT).show()
})

sec("CHAT AI (GROK / GEMINI)")
val pv = neon("", CY) { }
fun shPV() { pv.text = "CHAT AI: " + (if (sp.getString("cprov", "gemini") == "grok") "GROK (xAI)" else "GEMINI") }
shPV()
pv.setOnClickListener { sp.edit().putString("cprov", if (sp.getString("cprov", "gemini") == "grok") "gemini" else "grok").apply(); shPV() }
add("chat ai provider grok gemini xai chatgpt", pv)
val gk = field("Grok API key (xAI, starts with xai-)", sp.getString("gk", "") ?: "", false)
add("grok api key xai gork chabi", gk)
val gm = field("Grok model (leave empty for default)", sp.getString("gmodel", "") ?: "", false)
add("grok model name", gm)
add("save grok", neon("SAVE GROK", CY) {
sp.edit().putString("gk", gk.text.toString().trim()).putString("gmodel", gm.text.toString().trim()).apply()
Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
})
val vp = neon("", CY) { }
fun shVP() { vp.text = "VOICE AI: " + (if (sp.getString("vprov", "gemini") == "grok") "GROK (faster)" else "GEMINI") }
shVP()
vp.setOnClickListener { sp.edit().putString("vprov", if (sp.getString("vprov", "gemini") == "grok") "gemini" else "grok").apply(); shVP() }
add("voice ai grok gemini tez fast awaaz jawab", vp)
tgl("chat speak replies awaaz bolna", "chatspeak", true, "SPEAK CHAT REPLIES")

sec("CALL ASSISTANT")
add("call assistant auto answer phone uthana", mono("AUTO-ANSWER: Jarvis picks up calls, talks on speaker, SMSes you the conversation. Say 'Jarvis, give me the call' or tap TAKE OVER in the notification to take over. Needs: notification access, phone permission, SMS permission, speaker audible.", 10f, Color.parseColor("#8C86C9"), false))
tgl("auto answer calls call utha automatic phone", "autocall", false, "AUTO-ANSWER CALLS")
tgl("only unsaved numbers anjaan number", "acu", false, "ONLY UNSAVED NUMBERS")
val dl = neon("", CY) { }
fun shD() { dl.text = "ANSWER AFTER: " + sp.getInt("acd", 4) + " SEC" }
shD()
dl.setOnClickListener { val n = sp.getInt("acd", 4); sp.edit().putInt("acd", if (n >= 10) 2 else n + 2).apply(); shD() }
add("answer delay seconds kitni der baad", dl)
val cm = neon("", CY) { }
fun shCM() { cm.text = "WHEN A CALL COMES: " + (if (sp.getInt("acm", 1) == 1) "DECLINE + SMS THE CALLER" else "ANSWER & TALK (needs loudspeaker)") }
shCM()
cm.setOnClickListener { sp.edit().putInt("acm", if (sp.getInt("acm", 1) == 1) 0 else 1).apply(); shCM() }
add("call mode decline sms answer talk call aaye to kya kare", cm)
val cp = neon("", CY) { }
fun shC() { cp.text = "CALL VOICE PATH: " + when (sp.getString("cpath2", "alarm")) { "media" -> "MEDIA"; "voice" -> "CALL"; else -> "ALARM (recommended)" } }
shC()
cp.setOnClickListener { sp.edit().putString("cpath2", when (sp.getString("cpath2", "alarm")) { "alarm" -> "media"; "media" -> "voice"; else -> "alarm" }).apply(); shC() }
add("call voice path audio route awaaz speaker nahi sunai", cp)
val myn = field("Your number for call updates (SMS), e.g. +91XXXXXXXXXX", sp.getString("mynum", "") ?: "", false)
add("my number sms update mobile", myn)
add("save call number", neon("SAVE CALL SETTINGS", CY) {
sp.edit().putString("mynum", myn.text.toString().trim()).apply()
Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
})

sec("WAKE & LISTEN")
tgl("conversation continuous listening keep talking follow up hands free lagatar baat", "conv", true, "CONVERSATION MODE (keeps listening after a reply)")
tgl("shaant mode reels movie video media apne aap bolna chup शांत", "calm", true, "QUIET MODE (stay silent during reels/movies)")
tgl("message batana notification announce बताना", "an", true, "ANNOUNCE INCOMING MESSAGES")

sec("APP UPDATE")
add("update naya version check download install अपडेट", neon("CHECK FOR UPDATE", CY) { Updater.check(this, true) })
add("version build update", mono("Current version: " + Updater.ver(Updater.cur(this)), 11f, Color.parseColor("#D6DCFF"), false))

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
if ((e?.toString() ?: "").trim().isNotEmpty()) st.visibility = View.VISIBLE
applyVis()
}
})
applyVis = {
val s = q.text.toString().lowercase().trim()
var n = 0
for ((k, v) in items) {
val hdr = k == "\u0000"
val ok = if (s.isEmpty()) (hdr || open[secOf[v] ?: -1] == true) else (!hdr && k.contains(s))
v.visibility = if (ok) View.VISIBLE else View.GONE
if (ok && !hdr) n++
}
none.visibility = if (s.isNotEmpty() && n == 0) View.VISIBLE else View.GONE
}
applyVis()
panel.addView(head)
panel.addView(q, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
panel.addView(none)
panel.addView(st)
root.addView(ScrollView(this).apply { addView(col) }, FrameLayout.LayoutParams(-1, -1))

if (b == null) {
val splash = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#02030A")); isClickable = true; alpha = 0f }
splash.addView(MajinIntro(this), FrameLayout.LayoutParams(-1, -1))
root.addView(splash, FrameLayout.LayoutParams(-1, -1))
splash.animate().alpha(1f).setDuration(300).start()
var gone = false
val close: () -> Unit = {
if (!gone) {
gone = true
splash.animate().cancel()
splash.animate().alpha(0f).setDuration(500).withEndAction { root.removeView(splash) }.start()
}
}
splash.setOnClickListener { close() }
splash.postDelayed({ close() }, 8300)
if (sp.getString("lang", "en") == "en") vc().speakEn("Hello sir, I am Jarvis. All systems are online.") else vc().speak("नमस्ते सर, मैं जार्विस हूँ। सभी सिस्टम ऑनलाइन हैं।")
}
setContentView(root)
if (!License.valid(this)) lock(root)
if ((sp.getString("key", "") ?: "").isEmpty()) Toast.makeText(this, "First enter your Gemini API key in Settings (it is free)", Toast.LENGTH_LONG).show()
if (b == null) hh.postDelayed({ Updater.check(this, false) }, 9500)
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
private var chatV: View? = null
private val chatHist = ArrayList<Pair<String, String>>()
@Deprecated("x") override fun onBackPressed() { if (chatV != null) closeChat() else super.onBackPressed() }
private fun closeChat() {
val v = chatV ?: return
chatV = null
try { (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0) } catch (e: Exception) { }
(v.parent as? ViewGroup)?.removeView(v)
}
private fun openChat(root: FrameLayout) {
if (chatV != null) return
val sp = getSharedPreferences("j", MODE_PRIVATE)
window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
val ov = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#05060F")); isClickable = true }
ov.addView(PremiumBg(this), FrameLayout.LayoutParams(-1, -1))
val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(26), dp(12), dp(0)) }
val back = mono("\u2039", 30f, CY, true).apply { setPadding(dp(12), 0, dp(12), 0); setOnClickListener { closeChat() } }
val title = mono("JARVIS CHAT", 14f, Color.WHITE, true).apply { letterSpacing = 0.2f; gravity = Gravity.CENTER }
val prov = mono("", 10f, GR, true).apply { setPadding(dp(8), dp(6), dp(8), dp(6)) }
fun shP() { prov.text = if (sp.getString("cprov", "gemini") == "grok") "GROK" else "GEMINI" }
shP()
prov.setOnClickListener { sp.edit().putString("cprov", if (sp.getString("cprov", "gemini") == "grok") "gemini" else "grok").apply(); shP() }
val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(4), dp(12), dp(12)) }
val sv = ScrollView(this).apply { addView(list) }
val clr = mono("CLEAR", 10f, DIM, true).apply { setPadding(dp(8), dp(6), dp(8), dp(6)) }
clr.setOnClickListener { list.removeAllViews(); chatHist.clear(); sp.edit().remove("chatlog").apply() }
bar.addView(back); bar.addView(title, LinearLayout.LayoutParams(0, -2, 1f)); bar.addView(clr); bar.addView(prov)
col.addView(bar)
col.addView(BotView(this), LinearLayout.LayoutParams(-1, dp(150)))
col.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))

fun bubble(text: String, mine: Boolean): TextView {
val tv = TextView(this).apply {
this.text = text; textSize = 14f; setTextColor(Color.WHITE); setTextIsSelectable(true)
setPadding(dp(14), dp(10), dp(14), dp(10))
background = box(if (mine) Color.parseColor("#4A5BFF") else Color.parseColor("#2B2F66"), if (mine) Color.parseColor("#2B3CC8") else Color.parseColor("#101230"), 16)
maxWidth = (resources.displayMetrics.widthPixels * 0.78f).toInt()
}
list.addView(tv, LinearLayout.LayoutParams(-2, -2).apply { gravity = if (mine) Gravity.END else Gravity.START; topMargin = dp(8) })
sv.post { sv.fullScroll(View.FOCUS_DOWN) }
return tv
}
fun saveLog() {
val a = org.json.JSONArray()
for ((u, r) in chatHist.takeLast(30)) a.put(org.json.JSONArray().put(u).put(r))
sp.edit().putString("chatlog", a.toString()).apply()
}
try {
val a = org.json.JSONArray(sp.getString("chatlog", "[]"))
chatHist.clear()
for (i in 0 until a.length()) { val x = a.getJSONArray(i); chatHist.add(x.getString(0) to x.getString(1)); bubble(x.getString(0), true); bubble(x.getString(1), false) }
} catch (e: Exception) { }
if (chatHist.isEmpty()) bubble("Hello! I am Jarvis. Ask me anything.", false)

val input = EditText(this).apply {
hint = "Message Jarvis..."; setTextColor(Color.WHITE); setHintTextColor(DIM); textSize = 14f
background = box(Color.parseColor("#2B2F66"), Color.parseColor("#0B0C22"), 22)
setPadding(dp(16), dp(10), dp(16), dp(10)); maxLines = 4
inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
}
val doSend = {
val q = input.text.toString().trim()
if (q.isNotEmpty()) {
input.setText("")
bubble(q, true)
val wait = bubble("\u2026", false)
val hist = ArrayList(chatHist)
val grok = sp.getString("cprov", "gemini") == "grok"
val owner = sp.getString("owner", "")?.trim().orEmpty()
val sys = "You are Jarvis, a smart, friendly AI assistant" + (if (owner.isNotEmpty()) " of " + owner else "") + ". Answer clearly and concisely in the same language the user writes in (English, Hindi or Hinglish). Use plain text, no markdown."
Thread {
val r: String = if (grok) {
val k = sp.getString("gk", "")?.trim().orEmpty()
if (k.isEmpty()) "Add your Grok API key in Settings > CHAT AI, or switch the top-right button to GEMINI." else (Grok.ask(k, sp.getString("gmodel", "") ?: "", sys, hist, q) ?: ("Grok error: " + Grok.lastErr))
} else {
val k = sp.getString("key", "")?.trim().orEmpty()
if (k.isEmpty()) "Add your Gemini API key in Settings > ACCOUNT, or use Grok." else (Gemini.info(k, sys, hist, q) ?: "Gemini did not answer. Check your key or try again.")
}
runOnUiThread {
wait.text = r
if (!r.startsWith("Grok error") && !r.startsWith("Add your") && !r.startsWith("Gemini did")) { chatHist.add(q to r); while (chatHist.size > 30) chatHist.removeAt(0); saveLog() }
sv.post { sv.fullScroll(View.FOCUS_DOWN) }
if (sp.getBoolean("chatspeak", true) && chatV != null) {
val sayT = r.replace(Regex("[*#`_>]"), "").take(500)
if (sayT.any { it in '\u0900'..'\u097F' }) vc().speak(sayT) else vc().speakEn(sayT)
}
}
}.start()
}
}
val send = neon("\u27A4", CY) { doSend() }
val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(6), dp(12), dp(14)) }
row.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
row.addView(send, LinearLayout.LayoutParams(dp(56), -2).apply { leftMargin = dp(8) })
col.addView(row)
ov.addView(col, FrameLayout.LayoutParams(-1, -1))
root.addView(ov, FrameLayout.LayoutParams(-1, -1))
chatV = ov
}
private fun lock(root: FrameLayout) {
val sp = getSharedPreferences("j", MODE_PRIVATE)
val ov = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#05060F")); isClickable = true }
val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(24), dp(48), dp(24), dp(24)) }
c.addView(BotView(this), LinearLayout.LayoutParams(-1, dp(190)))
c.addView(mono("ACTIVATION REQUIRED", 17f, CY, true).apply { gravity = Gravity.CENTER; letterSpacing = 0.12f; setPadding(0, dp(16), 0, dp(8)) })
c.addView(mono("Send this Device ID to the seller to get your licence code:", 12f, Color.WHITE, false).apply { gravity = Gravity.CENTER })
val id = License.deviceId(this)
c.addView(mono(id, 22f, GR, true).apply { gravity = Gravity.CENTER; setTextIsSelectable(true); setPadding(0, dp(12), 0, dp(12)) })
val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
row.addView(neon("COPY ID", CY) {
(getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("id", id))
Toast.makeText(this, "Device ID copied", Toast.LENGTH_SHORT).show()
}, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(6), 0) })
row.addView(neon("SHARE ID", CY) {
startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "My Jarvis Device ID: " + id), "Share Device ID"))
}, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(6), 0, 0, 0) })
c.addView(row, LinearLayout.LayoutParams(-1, -2))
val code = field("Paste licence code here", "", false)
c.addView(code, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
c.addView(neon("ACTIVATE LICENCE", GR) {
val t = code.text.toString().trim()
if (License.check(id, t)) {
sp.edit().putString("lic", t).apply()
root.removeView(ov)
Toast.makeText(this, "Activated. Welcome!", Toast.LENGTH_LONG).show()
} else Toast.makeText(this, "Invalid code for this device", Toast.LENGTH_LONG).show()
}, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
ov.addView(ScrollView(this).apply { addView(c) }, FrameLayout.LayoutParams(-1, -1))
root.addView(ov, FrameLayout.LayoutParams(-1, -1))
}
private fun ask() {
if (perms.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) requestPermissions(perms, 1)
else proceed()
}
override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, g: IntArray) {
proceed()
}
private fun proceed() {
if (!License.valid(this)) { Toast.makeText(this, "Licence not activated", Toast.LENGTH_LONG).show(); return }
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
