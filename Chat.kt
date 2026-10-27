package com.vaibhav.jarvis

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Grok (xAI) chat. OpenAI-compatible endpoint. */
object Grok {
    @Volatile var lastErr = ""

    @Volatile private var okSlow = ""
    @Volatile private var okFast = ""

    fun ask(key: String, model: String, sys: String, hist: List<Pair<String, String>>, q: String, fast: Boolean = false): String? {
        val ok = if (fast) okFast else okSlow
        val models = (listOf(ok, model.trim()) + (if (fast) listOf("grok-4-fast-non-reasoning", "grok-3-mini", "grok-4", "grok-3") else listOf("grok-4.3", "grok-4", "grok-3", "grok-3-mini"))).filter { it.isNotBlank() }.distinct()
        for (m in models) {
            try {
                val msgs = JSONArray()
                msgs.put(JSONObject().put("role", "system").put("content", sys))
                for ((u, a) in hist) {
                    msgs.put(JSONObject().put("role", "user").put("content", u))
                    msgs.put(JSONObject().put("role", "assistant").put("content", a))
                }
                msgs.put(JSONObject().put("role", "user").put("content", q))
                val body = JSONObject().put("model", m).put("messages", msgs).put("temperature", 0.7)
                if (fast) body.put("max_tokens", 400)
                val c = URL("https://api.x.ai/v1/chat/completions").openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.connectTimeout = 15000; c.readTimeout = 60000; c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("Authorization", "Bearer " + key)
                c.setRequestProperty("User-Agent", "Jarvis/1.0")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = c.responseCode
                val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
                if (code in 200..299) {
                    if (fast) okFast = m else okSlow = m
                    return JSONObject(txt).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                }
                lastErr = "HTTP " + code + " " + txt.take(160)
                if (code == 401 || code == 403 || code == 429) return null
            } catch (e: Exception) {
                lastErr = e.toString()
                return null
            }
        }
        return null
    }
}
