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
class JarvisVoice(context: Context, private val onReady: () -> Unit = {}) : TextToSpeech.OnInitListener {

    private val sp = context.applicationContext.getSharedPreferences("j", Context.MODE_PRIVATE)
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var hiVoice: Voice? = null
    private var enVoice: Voice? = null
    private val pending = ArrayList<String>()

    // Hindi (Devanagari) tukde vs baaki sab (English)
    private val part = Regex("[\\u0900-\\u097F][\\u0900-\\u097F\\s,।?!.]*|[^\\u0900-\\u097F]+")

    private fun pitch() = sp.getFloat("pt", 0.65f)

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        hiVoice = pickMale("hi", Regex("hic|hid|hib"), null, sp.getInt("hvi", 0))
        enVoice = pickMale("en", Regex("gbb|gbd|rjs|enb|end"), "IN", 0)
        tts.setPitch(pitch())
        tts.setSpeechRate(1.0f)
        ready = true
        pending.forEach { speak(it) }
        pending.clear()
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
    }

    fun hiName(): String = hiVoice?.name ?: "koi Hindi voice nahi mili"

    fun hiCount(): Int = try {
        tts.voices.count { it.locale.language == "hi" && !(it.features?.contains("notInstalled") ?: false) }
    } catch (e: Exception) { 0 }

    fun speak(text: String) {
        if (!ready) { pending.add(text); return }
        var first = true
        for (m in part.findAll(text)) {
            val seg = m.value.trim()
            if (seg.none { it.isLetterOrDigit() }) continue
            val isHindi = seg[0] in 'ऀ'..'ॿ'
            val v = if (isHindi) hiVoice else enVoice
            if (v != null) tts.setVoice(v)
            else tts.setLanguage(if (isHindi) Locale("hi", "IN") else Locale("en", "IN"))
            tts.setPitch(pitch())
            tts.speak(seg, if (first) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "j" + System.nanoTime())
            first = false
        }
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
