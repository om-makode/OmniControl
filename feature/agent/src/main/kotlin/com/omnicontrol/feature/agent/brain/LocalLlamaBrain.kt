package com.omnicontrol.feature.agent.brain

import android.content.Context
import com.omnicontrol.core.common.OmniLog
import com.omnicontrol.native_llama.LlamaNative
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Runs a GGUF model on the phone through llama.cpp. Output is forced into our JSON shape with a
 * grammar, so even small models always produce a parseable action. No images.
 */
class LocalLlamaBrain(private val modelFile: File) : Brain {

    override val name: String = "On-device · ${modelFile.nameWithoutExtension.take(28)}"
    override val seesImages: Boolean = false

    private var handle = 0L

    private suspend fun ensureLoaded(): Long = withContext(LlamaNative.llamaDispatcher) {
        if (handle != 0L) return@withContext handle
        if (!LlamaNative.available) throw BrainException("On-device AI library is not available on this phone.")
        if (!modelFile.exists()) throw BrainException("Model file missing: ${modelFile.name}")
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
        handle = LlamaNative.loadModel(modelFile.absolutePath, 4096, threads)
        if (handle == 0L) throw BrainException("Could not load ${modelFile.name} (not enough memory?)")
        OmniLog.i(TAG, "Loaded ${modelFile.name}")
        handle
    }

    override suspend fun decide(request: BrainRequest): Result<BrainDecision> = runCatching {
        val h = ensureLoaded()
        val raw = withContext(LlamaNative.llamaDispatcher) {
            val prompt = LlamaNative.applyChatTemplate(
                h, Prompts.SYSTEM, Prompts.userMessage(request.copy(screenshotBase64 = null), maxElements = 45)
            )
            LlamaNative.generate(h, prompt, GRAMMAR, 220)
        }
        OmniLog.d(TAG, "local output: $raw")
        Prompts.parseDecision(raw).getOrThrow()
    }

    override suspend fun ping(): Result<String> = runCatching {
        val h = ensureLoaded()
        withContext(LlamaNative.llamaDispatcher) {
            LlamaNative.generate(h, LlamaNative.applyChatTemplate(h, "You are a test.", "Say OK."), null, 8)
        }
    }

    override fun close() {
        val h = handle
        handle = 0L
        if (h != 0L) LlamaNative.free(h)
    }

    companion object {
        private const val TAG = "OmniLocalBrain"

        fun findModel(context: Context): File? =
            File(context.filesDir, "models/llm").listFiles { f -> f.extension.equals("gguf", true) }
                ?.maxByOrNull { it.lastModified() }

        private val GRAMMAR = """
root ::= "{" ws "\"thought\":" ws str "," ws "\"action\":" ws act "," ws "\"target\":" ws (num | "null") "," ws "\"text\":" ws (str | "null") "," ws "\"direction\":" ws (dir | "null") "," ws "\"app\":" ws (str | "null") "," ws "\"submit\":" ws ("true" | "false") "," ws "\"note\":" ws (str | "null") "," ws "\"message\":" ws (str | "null") ws "}"
act ::= "\"open_app\"" | "\"click\"" | "\"long_press\"" | "\"type\"" | "\"scroll\"" | "\"swipe\"" | "\"back\"" | "\"home\"" | "\"open_url\"" | "\"wait\"" | "\"done\"" | "\"answer\"" | "\"ask\""
dir ::= "\"up\"" | "\"down\"" | "\"left\"" | "\"right\""
num ::= [0-9] [0-9]? [0-9]?
str ::= "\"" ( [^"\\\n] | "\\" ["\\/nt] )* "\""
ws ::= [ ]?
""".trimIndent()
    }
}
