package com.omnicontrol.native_llama

import com.omnicontrol.core.common.OmniLog
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

object LlamaNative {
    private const val TAG = "OmniLLM"

    init {
        try {
            System.loadLibrary("omnillama")
            OmniLog.i(TAG, "libomnillama.so loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            OmniLog.e(TAG, "Failed to load libomnillama.so", e)
        }
    }

    // Llama contexts are not thread-safe; serialize all llama calls onto a single thread.
    val llamaDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "OmniLlamaWorker")
    }.asCoroutineDispatcher()

    external fun loadModel(path: String, nCtx: Int, nThreads: Int): Long
    external fun generate(handle: Long, prompt: String, grammar: String?, maxTokens: Int): String
    external fun tokenCount(handle: Long, text: String): Int
    external fun free(handle: Long)

    const val TEST_GRAMMAR = """root ::= "{" ws "\"type\":" ws type "," ws "\"target\":" ws num ws "}"
type ::= "\"click\"" | "\"scroll\"" | "\"back\""
num  ::= [0-9] [0-9]?
ws   ::= [ \t\n]*
"""

    suspend fun testGenerate(
        modelFile: File,
        prompt: String = "Elements:\n#1 Search button\n#2 Home\nTask: Tap the search button\nAction: ",
        grammar: String? = TEST_GRAMMAR
    ): Result<String> = withContext(llamaDispatcher) {
        if (!modelFile.exists()) {
            return@withContext Result.failure(IllegalStateException("Model file not found: ${modelFile.absolutePath}"))
        }

        val startTime = System.currentTimeMillis()
        val handle = loadModel(modelFile.absolutePath, 2048, 4)
        if (handle == 0L) {
            return@withContext Result.failure(IllegalStateException("loadModel returned 0"))
        }

        try {
            val loadTime = System.currentTimeMillis() - startTime
            OmniLog.i(TAG, "Model loaded in ${loadTime}ms")

            val genStart = System.currentTimeMillis()
            val output = generate(handle, prompt, grammar, 64)
            val genTime = System.currentTimeMillis() - genStart
            OmniLog.i(TAG, "Generated output in ${genTime}ms: $output")

            Result.success(output)
        } catch (e: Exception) {
            OmniLog.e(TAG, "Error in testGenerate", e)
            Result.failure(e)
        } finally {
            free(handle)
        }
    }
}
