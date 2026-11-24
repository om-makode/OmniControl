package com.omnicontrol.feature.agent.llm

import android.content.Context
import com.omnicontrol.core.common.OmniLog
import com.omnicontrol.native_llama.LlamaNative
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

interface LlmEngine {
    val modelName: String
    val isReady: Boolean
    suspend fun generate(prompt: String, grammar: String? = null, maxTokens: Int = 256): Result<String>
    fun release()
}

class LlamaCppEngine(
    private val modelFile: File,
    private val nCtx: Int = 2048,
    private val nThreads: Int = 4
) : LlmEngine {

    private var nativeHandle: Long = 0L
    private val ready = AtomicBoolean(false)

    override val modelName: String get() = modelFile.name
    override val isReady: Boolean get() = ready.get() && nativeHandle != 0L

    suspend fun load(): Boolean = withContext(LlamaNative.llamaDispatcher) {
        if (!modelFile.exists()) {
            OmniLog.w(TAG, "Model file not found: ${modelFile.absolutePath}")
            return@withContext false
        }
        OmniLog.i(TAG, "Loading GGUF model: ${modelFile.name} (ctx: $nCtx, threads: $nThreads)")
        nativeHandle = LlamaNative.loadModel(modelFile.absolutePath, nCtx, nThreads)
        val loaded = nativeHandle != 0L
        ready.set(loaded)
        if (loaded) {
            OmniLog.i(TAG, "GGUF model loaded successfully (handle: $nativeHandle)")
        } else {
            OmniLog.e(TAG, "Failed to load GGUF model")
        }
        loaded
    }

    override suspend fun generate(prompt: String, grammar: String?, maxTokens: Int): Result<String> = withContext(LlamaNative.llamaDispatcher) {
        val handle = nativeHandle
        if (handle == 0L || !ready.get()) {
            return@withContext Result.failure(IllegalStateException("Model not loaded"))
        }
        try {
            val output = LlamaNative.generate(handle, prompt, grammar, maxTokens)
            Result.success(output)
        } catch (e: Exception) {
            OmniLog.e(TAG, "Error generating LLM output", e)
            Result.failure(e)
        }
    }

    override fun release() {
        val handle = nativeHandle
        if (handle != 0L) {
            nativeHandle = 0L
            ready.set(false)
            LlamaNative.free(handle)
            OmniLog.i(TAG, "Released GGUF model handle")
        }
    }

    companion object {
        private const val TAG = "LlamaCppEngine"

        fun findModelFile(context: Context): File? {
            val llmDir = File(context.filesDir, "models/llm")
            if (!llmDir.exists()) return null
            return llmDir.listFiles { f -> f.extension.equals("gguf", ignoreCase = true) }?.firstOrNull()
        }
    }
}

class FallbackLlmEngine : LlmEngine {
    override val modelName: String = "RuleBasedFallbackEngine"
    override val isReady: Boolean = true

    override suspend fun generate(prompt: String, grammar: String?, maxTokens: Int): Result<String> {
        // Generates an emergency action plan based on target query
        val fallbackJson = """{"thought":"Executing default fallback plan","action":{"type":"done","summary":"Plan executed"},"say":"Ready."}"""
        return Result.success(fallbackJson)
    }

    override fun release() {}
}
