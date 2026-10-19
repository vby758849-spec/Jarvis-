package com.vaibhav.jarvis

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/**
 * Male voice + sahi pronunciation.
 * Devanagari (हिंदी) hissa -> Hindi male voice
 * English (Latin) hissa   -> English male voice
 * Isse English ke words ki spelling/pronunciation sahi padhi jaati hai.
 */
class JarvisVoice(context: Context, private val onReady: () -> Unit = {}) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var hiVoice: Voice? = null
    private var enVoice: Voice? = null
    private val pending = ArrayList<String>()

    // Hindi (Devanagari) tukde vs baaki sab (English)
    private val part = Regex("[\\u0900-\\u097F][\\u0900-\\u097F\\s,।?!.]*|[^\\u0900-\\u097F]+")

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        hiVoice = pickMale("hi", listOf("hic", "hie"))
        enVoice = pickMale("en", listOf("enb", "end"), preferCountry = "IN")
        tts.setPitch(0.8f)        // thodi bhaari awaaz
        tts.setSpeechRate(0.95f)
        ready = true
        pending.forEach { speak(it) }
        pending.clear()
        onReady()
    }

    private fun pickMale(lang: String, hints: List<String>, preferCountry: String? = null): Voice? {
        val all = try { tts.voices } catch (e: Exception) { null } ?: return null
        var c = all.filter { it.locale.language == lang && !it.features.orEmpty().contains("notInstalled") }
        if (preferCountry != null) {
            val sameCountry = c.filter { it.locale.country == preferCountry }
            if (sameCountry.isNotEmpty()) c = sameCountry
        }
        return c.firstOrNull { it.name.contains("male", true) && !it.name.contains("female", true) }
            ?: c.firstOrNull { v -> hints.any { v.name.contains("-x-$it", true) } }
            ?: c.firstOrNull()
    }

    fun speak(text: String) {
        if (!ready) { pending.add(text); return }
        var first = true
        for (m in part.findAll(text)) {
            val seg = m.value.trim()
            if (seg.none { it.isLetterOrDigit() }) continue
            val isHindi = seg[0] in '\u0900'..'\u097F'
            val v = if (isHindi) hiVoice else enVoice
            if (v != null) tts.setVoice(v)
            else tts.setLanguage(if (isHindi) Locale("hi", "IN") else Locale("en", "IN"))
            tts.setPitch(0.8f)
            tts.speak(seg, if (first) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "j" + System.nanoTime())
            first = false
        }
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
