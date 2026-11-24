#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <sstream>
#include <algorithm>

#include "llama.h"
#include "common.h"

#define TAG "OmniLLM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct OmniLlamaInstance {
    llama_model * model = nullptr;
    const struct llama_vocab * vocab = nullptr;
    llama_context * ctx = nullptr;
    int n_threads = 4;
};

static void omni_log_callback(ggml_log_level level, const char * text, void * user_data) {
    (void) user_data;
    if (level == GGML_LOG_LEVEL_ERROR) {
        LOGE("%s", text);
    } else if (level == GGML_LOG_LEVEL_WARN) {
        LOGW("%s", text);
    } else if (level == GGML_LOG_LEVEL_INFO) {
        LOGI("%s", text);
    }
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_omnicontrol_native_1llama_LlamaNative_loadModel(
        JNIEnv * env,
        jobject /* this */,
        jstring jpath,
        jint nCtx,
        jint nThreads
) {
    static bool backend_initialized = false;
    if (!backend_initialized) {
        llama_backend_init();
        llama_log_set(omni_log_callback, nullptr);
        backend_initialized = true;
    }

    const char * path = env->GetStringUTFChars(jpath, nullptr);
    if (!path) {
        LOGE("Failed to get model path string");
        return 0;
    }

    LOGI("Loading GGUF model from: %s", path);
    llama_model_params model_params = llama_model_default_params();
    llama_model * model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(jpath, path);

    if (!model) {
        LOGE("Failed to load model from file");
        return 0;
    }

    const struct llama_vocab * vocab = llama_model_get_vocab(model);

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = (nCtx > 0) ? (uint32_t) nCtx : 2048;
    int threads = (nThreads > 0) ? nThreads : 4;
    ctx_params.n_threads = threads;
    ctx_params.n_threads_batch = threads;

    llama_context * ctx = llama_new_context_with_model(model, ctx_params);
    if (!ctx) {
        LOGE("Failed to create llama context");
        llama_model_free(model);
        return 0;
    }

    auto * instance = new OmniLlamaInstance();
    instance->model = model;
    instance->vocab = vocab;
    instance->ctx = ctx;
    instance->n_threads = threads;

    LOGI("Model loaded successfully. Context size: %u, threads: %d", ctx_params.n_ctx, threads);
    return reinterpret_cast<jlong>(instance);
}

JNIEXPORT jint JNICALL
Java_com_omnicontrol_native_1llama_LlamaNative_tokenCount(
        JNIEnv * env,
        jobject /* this */,
        jlong handle,
        jstring jtext
) {
    auto * instance = reinterpret_cast<OmniLlamaInstance *>(handle);
    if (!instance || !instance->ctx) {
        return 0;
    }

    const char * text = env->GetStringUTFChars(jtext, nullptr);
    if (!text) {
        return 0;
    }

    std::vector<llama_token> tokens = common_tokenize(instance->ctx, std::string(text), true, true);
    env->ReleaseStringUTFChars(jtext, text);
    return static_cast<jint>(tokens.size());
}

JNIEXPORT jstring JNICALL
Java_com_omnicontrol_native_1llama_LlamaNative_generate(
        JNIEnv * env,
        jobject /* this */,
        jlong handle,
        jstring jprompt,
        jstring jgrammar,
        jint maxTokens
) {
    auto * instance = reinterpret_cast<OmniLlamaInstance *>(handle);
    if (!instance || !instance->ctx || !instance->model) {
        LOGE("Invalid llama instance handle");
        return env->NewStringUTF("{}");
    }

    const char * prompt_cstr = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt = prompt_cstr ? prompt_cstr : "";
    if (prompt_cstr) env->ReleaseStringUTFChars(jprompt, prompt_cstr);

    const char * grammar_cstr = jgrammar ? env->GetStringUTFChars(jgrammar, nullptr) : nullptr;
    std::string grammar = grammar_cstr ? grammar_cstr : "";
    if (grammar_cstr) env->ReleaseStringUTFChars(jgrammar, grammar_cstr);

    LOGI("Generating with prompt length %zu chars, grammar length %zu", prompt.length(), grammar.length());

    // Tokenize prompt
    std::vector<llama_token> prompt_tokens = common_tokenize(instance->ctx, prompt, true, true);
    if (prompt_tokens.empty()) {
        LOGE("Prompt tokenization returned 0 tokens");
        return env->NewStringUTF("{}");
    }

    uint32_t n_ctx = llama_n_ctx(instance->ctx);
    if (prompt_tokens.size() + (size_t) maxTokens > n_ctx) {
        LOGW("Prompt (%zu) + maxTokens (%d) exceeds context (%u)", prompt_tokens.size(), maxTokens, n_ctx);
    }

    // Clear KV cache
    llama_kv_self_clear(instance->ctx);

    // Build batch for prompt
    llama_batch batch = llama_batch_init(n_ctx, 0, 1);
    for (size_t i = 0; i < prompt_tokens.size(); ++i) {
        common_batch_add(batch, prompt_tokens[i], (llama_pos) i, { 0 }, false);
    }
    batch.logits[batch.n_tokens - 1] = true;

    if (llama_decode(instance->ctx, batch) != 0) {
        LOGE("llama_decode failed on prompt");
        llama_batch_free(batch);
        return env->NewStringUTF("{}");
    }

    // Initialize sampler chain
    auto sparams = llama_sampler_chain_default_params();
    sparams.no_perf = true;
    llama_sampler * smpl = llama_sampler_chain_init(sparams);

    if (!grammar.empty()) {
        llama_sampler * g_smpl = llama_sampler_init_grammar(instance->vocab, grammar.c_str(), "root");
        if (g_smpl) {
            llama_sampler_chain_add(smpl, g_smpl);
            LOGI("Grammar sampler attached");
        } else {
            LOGW("Failed to compile grammar");
        }
    }

    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.2f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string output;
    int max_gen = (maxTokens > 0) ? maxTokens : 256;
    llama_pos n_cur = (llama_pos) prompt_tokens.size();

    for (int step = 0; step < max_gen; ++step) {
        llama_token new_token = llama_sampler_sample(smpl, instance->ctx, -1);
        llama_sampler_accept(smpl, new_token);

        if (llama_vocab_is_eog(instance->vocab, new_token)) {
            LOGI("EOG token reached after %d generation steps", step);
            break;
        }

        std::string piece = common_token_to_piece(instance->ctx, new_token, false);
        output += piece;

        // Prepare batch for next token
        common_batch_clear(batch);
        common_batch_add(batch, new_token, n_cur++, { 0 }, true);

        if (llama_decode(instance->ctx, batch) != 0) {
            LOGE("llama_decode failed during generation step %d", step);
            break;
        }
    }

    llama_sampler_free(smpl);
    llama_batch_free(batch);

    LOGI("Generation output: %s", output.c_str());
    return env->NewStringUTF(output.c_str());
}

JNIEXPORT void JNICALL
Java_com_omnicontrol_native_1llama_LlamaNative_free(
        JNIEnv * /* env */,
        jobject /* this */,
        jlong handle
) {
    auto * instance = reinterpret_cast<OmniLlamaInstance *>(handle);
    if (!instance) return;

    if (instance->ctx) {
        llama_free(instance->ctx);
        instance->ctx = nullptr;
    }
    if (instance->model) {
        llama_model_free(instance->model);
        instance->model = nullptr;
    }
    delete instance;
    LOGI("OmniLlamaInstance freed");
}

} // extern "C"
