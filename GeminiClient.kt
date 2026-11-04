package com.vaibhav.jarvis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-healing Gemini client.
 *
 * - Asks Google which models are actually available (models.list) and picks the newest Flash models.
 * - If a model returns 404 / 429 / 5xx, it automatically tries the next one.
 * - Remembers the last model that worked, so later messages are fast.
 * - Never dumps raw JSON into the chat: returns a short, readable error instead.
 * - Works even if Google retires more models in the future, with no code change.
 */
object GeminiClient {

    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"

    // Used only if models.list itself fails (offline / quota). Newest first.
    private val FALLBACK_MODELS = listOf(
        "gemini-3.8-flash",
        "gemini-3.6-flash",
        "gemini-3.5-flash-lite",
        "gemini-3.1-flash-lite",
        "gemini-2.5-flash",
        "gemini-2.5-flash-lite"
    )

    private const val CACHE_MS = 6L * 60 * 60 * 1000 // refresh model list every 6 hours
    private const val MAX_TRIES = 5

    @Volatile private var cachedModels: List<String> = emptyList()
    @Volatile private var cachedAt = 0L
    @Volatile private var lastGood: String? = null

    sealed class Result {
        data class Ok(val text: String, val model: String) : Result()
        data class Fail(val message: String) : Result()
    }

    /**
     * @param history list of (role, text); role is "user" or "model". Last item should be the new user message.
     * @param systemPrompt optional personality/instructions for Jarvis.
     */
    suspend fun ask(
        apiKey: String,
        history: List<Pair<String, String>>,
        systemPrompt: String? = null
    ): Result = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.Fail("Gemini key missing. Settings > ACCOUNT me key daalo.")
        }

        val candidates = candidateModels(apiKey)
        val body = buildBody(history, systemPrompt)
        var lastError = "Koi model available nahi mila."

        for (model in candidates.take(MAX_TRIES)) {
            when (val r = call(apiKey, model, body)) {
                is Call.Success -> {
                    lastGood = model
                    return@withContext Result.Ok(r.text, model)
                }
                is Call.AuthError -> {
                    // Wrong/expired key: trying other models is pointless.
                    return@withContext Result.Fail("Gemini key galat/expire hai. Settings > ACCOUNT me check karo.")
                }
                is Call.Retry -> {
                    lastError = "${model}: ${r.reason}"
                    if (model == lastGood) lastGood = null
                }
            }
        }
        Result.Fail("Gemini abhi jawab nahi de raha ($lastError). Thodi der baad try karo ya GROK use karo.")
    }

    // ---------- model discovery ----------

    private fun candidateModels(apiKey: String): List<String> {
        val discovered = discoverModels(apiKey)
        val base = if (discovered.isNotEmpty()) discovered else FALLBACK_MODELS
        val good = lastGood
        return if (good != null && good in base) listOf(good) + base.filter { it != good } else base
    }

    private fun discoverModels(apiKey: String): List<String> {
        val now = System.currentTimeMillis()
        if (cachedModels.isNotEmpty() && now - cachedAt < CACHE_MS) return cachedModels
        return try {
            val conn = open("$BASE/models?pageSize=200", apiKey, "GET")
            if (conn.responseCode != 200) return cachedModels
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val arr = json.optJSONArray("models") ?: JSONArray()
            val found = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val name = m.optString("name").removePrefix("models/")
                val methods = m.optJSONArray("supportedGenerationMethods")
                val canGenerate = methods != null && (0 until methods.length()).any { methods.getString(it) == "generateContent" }
                if (canGenerate && isUsableChatModel(name)) found.add(name)
            }
            val ranked = found.sortedWith(
                compareByDescending<String> { versionOf(it) }
                    .thenBy { if (it.contains("lite")) 1 else 0 } // full flash before flash-lite
            )
            if (ranked.isNotEmpty()) {
                cachedModels = ranked
                cachedAt = now
            }
            ranked
        } catch (_: Exception) {
            cachedModels
        }
    }

    // Only plain text "flash" chat models; skip audio/image/live/embedding/experimental ones.
    private fun isUsableChatModel(name: String): Boolean {
        if (!name.startsWith("gemini-") || !name.contains("flash")) return false
        val banned = listOf("tts", "image", "live", "audio", "embedding", "exp", "preview", "thinking", "robotics", "computer", "native")
        return banned.none { name.contains(it) }
    }

    private fun versionOf(name: String): Double =
        Regex("""gemini-(\d+(?:\.\d+)?)""").find(name)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

    // ---------- request ----------

    private sealed class Call {
        data class Success(val text: String) : Call()
        data class Retry(val reason: String) : Call()
        object AuthError : Call()
    }

    private fun buildBody(history: List<Pair<String, String>>, systemPrompt: String?): String {
        val contents = JSONArray()
        history.forEach { (role, text) ->
            contents.put(
                JSONObject()
                    .put("role", if (role == "model" || role == "assistant") "model" else "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", text)))
            )
        }
        val root = JSONObject().put("contents", contents)
        if (!systemPrompt.isNullOrBlank()) {
            root.put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
            )
        }
        return root.toString()
    }

    private fun call(apiKey: String, model: String, body: String): Call {
        return try {
            val conn = open("$BASE/models/$model:generateContent", apiKey, "POST")
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            if (code == 200) {
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val text = json.optJSONArray("candidates")
                    ?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                    ?.let { parts -> (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("text") } }
                    ?.trim().orEmpty()
                if (text.isNotEmpty()) Call.Success(text) else Call.Retry("empty reply")
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                when (code) {
                    401, 403 -> Call.AuthError
                    400 -> if (err.contains("API key", ignoreCase = true)) Call.AuthError
                           else Call.Retry("HTTP 400 ${shortMsg(err)}")
                    else -> Call.Retry("HTTP $code ${shortMsg(err)}") // 404, 429, 5xx -> try next model
                }
            }
        } catch (e: Exception) {
            Call.Retry(e.javaClass.simpleName + " (internet check karo)")
        }
    }

    private fun open(url: String, apiKey: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 45_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey) // header, so the key never appears in URLs/logs
        }

    private fun shortMsg(raw: String): String =
        try {
            JSONObject(raw).getJSONObject("error").optString("message")
        } catch (_: Exception) {
            raw
        }.replace("\n", " ").take(80)
}
