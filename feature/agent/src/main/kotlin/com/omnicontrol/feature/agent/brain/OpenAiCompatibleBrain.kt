package com.omnicontrol.feature.agent.brain

import com.omnicontrol.core.common.OmniLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/**
 * Talks to any server that implements the OpenAI `/chat/completions` API: Ollama, LM Studio,
 * llama.cpp server, Gemini (OpenAI endpoint), Groq, OpenRouter...
 * No SDK and no extra dependency: plain HttpURLConnection + kotlinx.serialization.
 */
class OpenAiCompatibleBrain(
    override val name: String,
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String,
    vision: Boolean,
    override val isPrivate: Boolean
) : Brain {

    @Volatile
    override var seesImages: Boolean = vision
        private set

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun decide(request: BrainRequest): Result<BrainDecision> {
        val image = if (seesImages) request.screenshotBase64 else null
        val first = complete(Prompts.SYSTEM, Prompts.userMessage(request.copy(screenshotBase64 = image)), image)
        val text = first.getOrElse { err ->
            // Some models reject images: switch vision off for this session and retry once.
            if (image != null && looksLikeImageError(err.message.orEmpty())) {
                OmniLog.w(TAG, "Model rejected images, continuing text-only: ${err.message}")
                seesImages = false
                complete(Prompts.SYSTEM, Prompts.userMessage(request.copy(screenshotBase64 = null)), null)
                    .getOrElse { return Result.failure(it) }
            } else return Result.failure(err)
        }
        val parsed = Prompts.parseDecision(text)
        if (parsed.isSuccess) return parsed
        // One repair attempt: tell the model exactly what went wrong.
        OmniLog.w(TAG, "Bad JSON from model (${text.length} chars), asking again")
        val retry = complete(
            Prompts.SYSTEM,
            Prompts.userMessage(request.copy(screenshotBase64 = null)) +
                "\n\nYour previous reply was not valid JSON. Reply with ONLY the JSON object.",
            null
        ).getOrElse { return Result.failure(it) }
        return Prompts.parseDecision(retry)
    }

    override suspend fun ping(): Result<String> =
        complete("You are a test endpoint.", "Reply with the single word OK.", null)

    private fun looksLikeImageError(msg: String): Boolean {
        val m = msg.lowercase()
        return listOf("image", "vision", "multimodal", "image_url", "content must be a string", "does not support")
            .any { m.contains(it) }
    }

    private suspend fun complete(system: String, user: String, imageBase64: String?): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                checkUrlAllowed(baseUrl)
                val body = buildJsonObject {
                    put("model", model)
                    put("temperature", 0.1)
                    put("max_tokens", 600)
                    put("stream", false)
                    putJsonArray("messages") {
                        addJsonObject { put("role", "system"); put("content", system) }
                        addJsonObject {
                            put("role", "user")
                            if (imageBase64 == null) {
                                put("content", user)
                            } else {
                                putJsonArray("content") {
                                    addJsonObject { put("type", "text"); put("text", user) }
                                    addJsonObject {
                                        put("type", "image_url")
                                        putJsonObject("image_url") { put("url", "data:image/jpeg;base64,$imageBase64") }
                                    }
                                }
                            }
                        }
                    }
                }.toString()

                val conn = (URL("$baseUrl/chat/completions").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 90_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("X-Title", "OmniControl")
                }
                val started = System.currentTimeMillis()
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                conn.disconnect()
                OmniLog.d(TAG, "$name HTTP $code in ${System.currentTimeMillis() - started}ms")

                if (code !in 200..299) throw BrainException(errorMessage(code, response))
                val root = json.parseToJsonElement(response).jsonObject
                val message = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
                    ?: throw BrainException("Unexpected reply from server: ${response.take(150)}")
                when (val content = message["content"]) {
                    is JsonPrimitive -> content.contentOrNull.orEmpty()
                    is JsonArray -> content.joinToString("") { part ->
                        (part as? JsonObject)?.get("text")?.let { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
                    }
                    else -> ""
                }.ifBlank { throw BrainException("The AI returned an empty reply") }
            }
        }

    private fun errorMessage(code: Int, body: String): String {
        val apiMsg = runCatching {
            val root = json.parseToJsonElement(body)
            val err = (root as? JsonObject)?.get("error")
            when (err) {
                is JsonObject -> (err["message"] as? JsonPrimitive)?.contentOrNull
                is JsonPrimitive -> err.contentOrNull
                is JsonArray -> err.firstOrNull()?.jsonObject?.get("error")?.jsonObject?.get("message")
                    ?.let { (it as? JsonPrimitive)?.contentOrNull }
                else -> null
            }
        }.getOrNull()
        val hint = when (code) {
            401, 403 -> "API key is wrong or missing."
            404 -> "Wrong URL or model name."
            429 -> "Free limit reached, wait a minute."
            else -> ""
        }
        return "AI server error $code. ${apiMsg ?: body.take(160)} $hint".trim()
    }

    /** Plain http is only allowed inside the home network (Ollama/LM Studio on your computer). */
    private fun checkUrlAllowed(url: String) {
        val u = URL(url)
        if (u.protocol == "https") return
        if (u.protocol != "http") throw BrainException("URL must start with http:// or https://")
        val host = u.host
        val local = host == "localhost" || host.endsWith(".local") ||
            runCatching { InetAddress.getByName(host).let { it.isSiteLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress } }
                .getOrDefault(false)
        if (!local) throw BrainException("Use https:// for internet servers (http is only allowed on your home network).")
    }

    companion object {
        private const val TAG = "OmniBrain"
    }
}
