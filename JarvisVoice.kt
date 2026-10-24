package com.vaibhav.jarvis

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/**
 * Male voice + sahi pronunciation (service jaisi hi awaaz).
 * Devanagari (हिंदी) hissa -> Hindi male voice
 * English (Latin) hissa   -> English male voice
 */
/** English words ko Devanagari mein badalta hai, taaki Hindi awaaz unhe sahi bole. */
object Pron {
    private val map = mapOf(
        "jarvis" to "जार्विस", "instagram" to "इंस्टाग्राम", "whatsapp" to "व्हाट्सऐप",
        "youtube" to "यूट्यूब", "gemini" to "जेमिनी", "bluetooth" to "ब्लूटूथ",
        "gmail" to "जीमेल", "telegram" to "टेलीग्राम", "google" to "गूगल",
        "facebook" to "फेसबुक", "wifi" to "वाईफाई", "internet" to "इंटरनेट",
        "online" to "ऑनलाइन", "offline" to "ऑफलाइन", "boss" to "बॉस", "torch" to "टॉर्च",
        "ok" to "ओके", "okay" to "ओके", "volume" to "वॉल्यूम", "alarm" to "अलार्म",
        "message" to "मैसेज", "call" to "कॉल", "system" to "सिस्टम", "systems" to "सिस्टम्स",
        "all" to "ऑल", "hello" to "हेलो", "sir" to "सर", "notification" to "नोटिफिकेशन"
    )
    private val re = Regex("\\b(" + map.keys.joinToString("|") + ")\\b", RegexOption.IGNORE_CASE)
    fun fix(t: String): String = re.replace(t) { map[it.value.lowercase()] ?: it.value }
}

class JarvisVoice(context: Context, private val onReady: () -> Unit = {}) : TextToSpeech.OnInitListener {

    private val sp = context.applicationContext.getSharedPreferences("j", Context.MODE_PRIVATE)
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var hiVoice: Voice? = null
    private var enVoice: Voice? = null
    private val pending = ArrayList<String>()
    private var pendingEn: String? = null

    // Hindi (Devanagari) tukde vs baaki sab (English)
    private val part = Regex("[\\u0900-\\u097F][\\u0900-\\u097F\\s,।?!.]*|[^\\u0900-\\u097F]+")

    private fun pitch() = sp.getFloat("pt", 0.85f)
    private fun rate() = sp.getFloat("rt", 1.0f)

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        hiVoice = pickMale("hi", Regex("hic|hid|hib"), null, sp.getInt("hvi", 0))
        enVoice = pickMale("en", Regex("gbb|gbd|rjs"), null, sp.getInt("vk", 0))
        tts.setPitch(pitch())
        tts.setSpeechRate(rate())
        ready = true
        pending.forEach { speak(it) }
        pending.clear()
        pendingEn?.let { pendingEn = null; speakEn(it) }
        onReady()
    }

    private fun pickMale(lang: String, pref: Regex, country: String?, idx: Int): Voice? {
        val all = try { tts.voices } catch (e: Exception) { null } ?: return null
        var c = all.filter { it.locale.language == lang && !(it.features?.contains("notInstalled") ?: false) }
        if (country != null) {
            val same = c.filter { it.locale.country == country || it.locale.country == "GB" }
            if (same.isNotEmpty()) c = same
        }
        val sorted = c.sortedWith(compareBy(
            { !(it.name.contains("male", true) && !it.name.contains("female", true)) },
            { !pref.containsMatchIn(it.name) },
            { it.isNetworkConnectionRequired },
            { it.name }
        ))
        return if (sorted.isEmpty()) null else sorted[idx % sorted.size]
    }

    /** Settings se "Hindi awaaz badlo" dabane par nayi voice chunta hai. */
    fun reload() {
        if (!ready) return
        hiVoice = pickMale("hi", Regex("hic|hid|hib"), null, sp.getInt("hvi", 0))
        enVoice = pickMale("en", Regex("gbb|gbd|rjs"), null, sp.getInt("vk", 0))
    }

    fun enCount(): Int = try {
        tts.voices.count { it.locale.language == "en" && !(it.features?.contains("notInstalled") ?: false) }
    } catch (e: Exception) { 0 }

    fun enName(): String = enVoice?.name ?: "koi English voice nahi mili"

    fun hiName(): String = hiVoice?.name ?: "koi Hindi voice nahi mili"

    fun hiCount(): Int = try {
        tts.voices.count { it.locale.language == "hi" && !(it.features?.contains("notInstalled") ?: false) }
    } catch (e: Exception) { 0 }

    fun speak(text: String) {
        if (!ready) { pending.add(text); return }
        if (sp.getBoolean("onevoice", true) && hiVoice != null) {
            tts.setVoice(hiVoice)
            tts.setPitch(pitch())
            tts.setSpeechRate(rate())
            tts.speak(Pron.fix(text), TextToSpeech.QUEUE_FLUSH, null, "j" + System.nanoTime())
            return
        }
        var first = true
        for (m in part.findAll(text)) {
            val seg = m.value.trim()
            if (seg.none { it.isLetterOrDigit() }) continue
            val isHindi = seg[0] in 'ऀ'..'ॿ'
            val v = if (isHindi) hiVoice else enVoice
            if (v != null) tts.setVoice(v)
            else tts.setLanguage(if (isHindi) Locale("hi", "IN") else Locale("en", "IN"))
            tts.setPitch(pitch())
            tts.setSpeechRate(rate())
            tts.speak(seg, if (first) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "j" + System.nanoTime())
            first = false
        }
    }

    /** English awaaz ko alag se sunne ke liye (settings ka test). */
    fun speakEn(text: String) {
        if (!ready) { pendingEn = text; return }
        val v = enVoice
        if (v != null) tts.setVoice(v) else tts.setLanguage(Locale("en", "IN"))
        tts.setPitch(pitch())
        tts.setSpeechRate(rate())
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "e" + System.nanoTime())
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
