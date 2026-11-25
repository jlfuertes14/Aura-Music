package com.auramusic.app.core.lyrics

import com.auramusic.app.core.extractor.SimpMusicExtractorEngine
import com.auramusic.app.domain.model.LyricsData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Translates synced or plain [LyricsData] line-by-line via Gemini or OpenAI-compatible
 * chat completion APIs while preserving exact line indices and timestamps.
 */
object AiLyricsTranslator {

    enum class AiProvider(val label: String) {
        GEMINI("Gemini"),
        OPENAI("OpenAI"),
        CUSTOM("Custom OpenAI-Compatible")
    }

    data class AiTranslationConfig(
        val provider: AiProvider = AiProvider.GEMINI,
        val apiKey: String = "",
        val modelId: String = "",
        val customBaseUrl: String = "",
        val targetLanguage: String = "English"
    )

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    suspend fun translateLyrics(
        lyrics: LyricsData,
        config: AiTranslationConfig
    ): Result<Map<Int, String>> = withContext(Dispatchers.IO) {
        try {
            val apiKey = config.apiKey.trim()
            val targetLanguage = config.targetLanguage.trim().ifBlank { "English" }

            if (config.provider != AiProvider.CUSTOM && apiKey.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("API key is required for ${config.provider.label}."))
            }
            if (config.provider == AiProvider.CUSTOM && config.customBaseUrl.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Base URL is required for Custom OpenAI-Compatible provider."))
            }

            val indexedLines: List<Pair<Int, String>> = if (lyrics.syncedLines.isNotEmpty()) {
                lyrics.syncedLines.mapIndexedNotNull { idx, line ->
                    line.text.trim().takeIf { it.isNotEmpty() }?.let { idx to it }
                }
            } else {
                lyrics.plainLyrics.lines().mapIndexedNotNull { idx, line ->
                    line.trim().takeIf { it.isNotEmpty() }?.let { idx to it }
                }
            }

            if (indexedLines.isEmpty()) {
                return@withContext Result.success(emptyMap())
            }

            val inputJson = JSONObject().apply {
                indexedLines.forEach { (idx, text) -> put(idx.toString(), text) }
            }
            val systemPrompt = buildString {
                append("You are an expert lyric translator. Translate every numbered lyric line into ")
                append(targetLanguage)
                append(" naturally and poetically. Preserve line meaning, keep every numeric key index exact, ")
                append("and respond ONLY with valid JSON matching {\"translations\": {\"0\": \"...\", \"1\": \"...\"}}.")
            }
            val userPrompt = "Translate these lyrics to $targetLanguage:\n$inputJson"

            val rawModelText = when (config.provider) {
                AiProvider.GEMINI -> callGemini(
                    apiKey = apiKey,
                    modelId = config.modelId.trim().ifBlank { "gemini-2.5-flash" },
                    prompt = "$systemPrompt\n\n$userPrompt"
                )
                AiProvider.OPENAI -> callOpenAiCompatible(
                    baseUrl = "https://api.openai.com/v1",
                    apiKey = apiKey,
                    modelId = config.modelId.trim().ifBlank { "gpt-4o-mini" },
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt
                )
                AiProvider.CUSTOM -> callOpenAiCompatible(
                    baseUrl = config.customBaseUrl.trim(),
                    apiKey = apiKey,
                    modelId = config.modelId.trim().ifBlank { "gpt-4o-mini" },
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt
                )
            }

            val parsed = parseTranslationResponse(rawModelText)
            if (parsed.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Model returned no translated lyric lines."))
            }
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun callGemini(
        apiKey: String,
        modelId: String,
        prompt: String
    ): String {
        val safeModel = modelId.removePrefix("models/").trim()
        val url = "https://generativelanguage.googleapis.com/v1beta/models/" +
            "${URLEncoder.encode(safeModel, "UTF-8")}:generateContent?key=${URLEncoder.encode(apiKey, "UTF-8")}"

        val body = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.2)
            })
        }

        val req = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        SimpMusicExtractorEngine.httpClient.newCall(req).execute().use { resp ->
            val rawBody = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("Gemini API error (${resp.code}): ${extractApiErrorMessage(rawBody)}")
            }
            val root = JSONObject(rawBody)
            val candidates = root.optJSONArray("candidates")
                ?: throw IllegalStateException("Gemini response missing candidates.")
            val parts = candidates.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?: throw IllegalStateException("Gemini response missing content parts.")
            val combined = buildString {
                for (i in 0 until parts.length()) {
                    append(parts.optJSONObject(i)?.optString("text").orEmpty())
                }
            }
            return combined.ifBlank { throw IllegalStateException("Gemini returned an empty translation.") }
        }
    }

    private fun callOpenAiCompatible(
        baseUrl: String,
        apiKey: String,
        modelId: String,
        systemPrompt: String,
        userPrompt: String
    ): String {
        val cleanBase = baseUrl.trimEnd('/')
        val endpoint = if (cleanBase.endsWith("/chat/completions")) {
            cleanBase
        } else {
            "$cleanBase/chat/completions"
        }

        val body = JSONObject().apply {
            put("model", modelId)
            put("temperature", 0.2)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            })
        }

        val reqBuilder = Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/json")
        if (apiKey.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer $apiKey")
        }
        val req = reqBuilder
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        SimpMusicExtractorEngine.httpClient.newCall(req).execute().use { resp ->
            val rawBody = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("AI API error (${resp.code}): ${extractApiErrorMessage(rawBody)}")
            }
            val root = JSONObject(rawBody)
            val content = root.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
            return content.ifBlank { throw IllegalStateException("AI response returned empty content.") }
        }
    }

    private fun extractApiErrorMessage(rawBody: String): String {
        return try {
            val obj = JSONObject(rawBody)
            obj.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?: obj.optString("message").takeIf { it.isNotBlank() }
                ?: rawBody.take(180)
        } catch (_: Exception) {
            rawBody.take(180)
        }
    }

    /**
     * Parses plain JSON or markdown-fenced JSON (`{"translations": {"0": "..."}}` or flat `{"0": "..."}`)
     * into an index-ordered map of translated lyric lines.
     */
    internal fun parseTranslationResponse(rawJson: String): Map<Int, String> {
        val cleaned = stripMarkdownCodeFence(rawJson)
        if (cleaned.isBlank()) return emptyMap()

        val jsonCandidate = extractJsonObjectSlice(cleaned) ?: return emptyMap()
        return try {
            val root = JSONObject(jsonCandidate)
            val targetObj = root.optJSONObject("translations") ?: root
            val result = sortedMapOf<Int, String>()
            val keys = targetObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val idx = key.trim().toIntOrNull() ?: continue
                val value = targetObj.optString(key).trim()
                if (value.isNotEmpty() && value != "null") {
                    result[idx] = value
                }
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun serializeTranslations(map: Map<Int, String>): String {
        val translationsObj = JSONObject()
        map.toSortedMap().forEach { (idx, text) ->
            translationsObj.put(idx.toString(), text)
        }
        return JSONObject().put("translations", translationsObj).toString()
    }

    fun deserializeTranslations(json: String): Map<Int, String> {
        if (json.isBlank()) return emptyMap()
        return parseTranslationResponse(json)
    }

    private fun stripMarkdownCodeFence(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```")) {
            val firstNewline = text.indexOf('\n')
            if (firstNewline != -1) {
                text = text.substring(firstNewline + 1)
            } else {
                text = text.removePrefix("```json").removePrefix("```")
            }
            val closingFence = text.lastIndexOf("```")
            if (closingFence != -1) {
                text = text.substring(0, closingFence)
            }
        }
        return text.trim()
    }

    private fun extractJsonObjectSlice(text: String): String? {
        if (text.startsWith("{") && text.endsWith("}")) return text
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start == -1 || end <= start) return null
        return text.substring(start, end + 1)
    }
}
