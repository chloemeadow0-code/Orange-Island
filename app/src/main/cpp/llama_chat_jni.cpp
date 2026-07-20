#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <cstdint>
#include <cstdio>
#include <android/log.h>
#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"

#define LOG_TAG "LlamaChatEngine"
#ifndef NDEBUG
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#define LOGD(...) ((void)0)
#define LOGE(...) ((void)0)
#endif

struct ChatHandle {
    llama_model * model   = nullptr;
    llama_context * ctx   = nullptr;
    const llama_vocab * vocab = nullptr;
    std::string path;
    int32_t n_ctx = 0;
    volatile bool cancelled = false;
    mtmd_context * mtmd_ctx = nullptr;  // multimodal context (for vision models)
};

static bool abort_callback(void * data) {
    ChatHandle * handle = (ChatHandle *)data;
    return handle->cancelled;
}

// Returns the byte length of the largest prefix of `text` that ends on a
// complete UTF-8 character boundary. llama frequently splits a multi-byte glyph
// (CJK, Arabic/Persian, emoji, …) across token pieces, so a single piece may end
// with a truncated sequence. Handing those raw bytes to NewStringUTF aborts the
// VM ("input is not valid Modified UTF-8"), so callers buffer the incomplete tail
// until the next token completes it.
static size_t utf8_complete_prefix_len(const std::string & text) {
    size_t len = text.length();
    // A truncated lead byte can only be within the last 3 bytes of the buffer.
    for (size_t i = 1; i <= 4 && i <= len; ++i) {
        unsigned char c = static_cast<unsigned char>(text[len - i]);
        if ((c & 0xE0) == 0xC0) return i < 2 ? len - i : len; // 2-byte sequence
        if ((c & 0xF0) == 0xE0) return i < 3 ? len - i : len; // 3-byte sequence
        if ((c & 0xF8) == 0xF0) return i < 4 ? len - i : len; // 4-byte sequence
        // ASCII or continuation byte: keep scanning back for the lead byte.
    }
    return len;
}

// Build a jstring from standard UTF-8 bytes WITHOUT going through NewStringUTF.
// NewStringUTF expects *Modified* UTF-8, in which supplementary-plane code points
// (U+10000+ — emoji, CJK extensions) must be a 6-byte CESU-8 surrogate pair; a
// standard 4-byte UTF-8 sequence is invalid Modified UTF-8 and aborts the VM. We
// decode UTF-8 → UTF-16 (emitting surrogate pairs) and use NewString, which takes
// genuine UTF-16 and handles the whole BMP + supplementary range safely.
static jstring utf8_to_jstring(JNIEnv * env, const char * data, size_t len) {
    std::vector<jchar> utf16;
    utf16.reserve(len);
    size_t i = 0;
    while (i < len) {
        unsigned char c = static_cast<unsigned char>(data[i]);
        uint32_t cp;
        size_t adv;
        if (c < 0x80) {
            cp = c; adv = 1;
        } else if ((c & 0xE0) == 0xC0 && i + 1 < len) {
            cp = (uint32_t(c & 0x1F) << 6) | (data[i + 1] & 0x3F); adv = 2;
        } else if ((c & 0xF0) == 0xE0 && i + 2 < len) {
            cp = (uint32_t(c & 0x0F) << 12) | (uint32_t(data[i + 1] & 0x3F) << 6) | (data[i + 2] & 0x3F); adv = 3;
        } else if ((c & 0xF8) == 0xF0 && i + 3 < len) {
            cp = (uint32_t(c & 0x07) << 18) | (uint32_t(data[i + 1] & 0x3F) << 12)
               | (uint32_t(data[i + 2] & 0x3F) << 6) | (data[i + 3] & 0x3F); adv = 4;
        } else {
            cp = 0xFFFD; adv = 1; // malformed lead/continuation → replacement char
        }
        i += adv;
        if (cp <= 0xFFFF) {
            utf16.push_back(static_cast<jchar>(cp));
        } else {
            cp -= 0x10000;
            utf16.push_back(static_cast<jchar>(0xD800 + (cp >> 10)));
            utf16.push_back(static_cast<jchar>(0xDC00 + (cp & 0x3FF)));
        }
    }
    return env->NewString(utf16.data(), static_cast<jsize>(utf16.size()));
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_orangeisland_app_api_LlamaChatEngine_nativeChatLoadModel(
    JNIEnv * env, jclass /*clazz*/, jstring path, jint n_ctx) {

    const char * path_str = env->GetStringUTFChars(path, nullptr);
    if (!path_str) return 0;

    ChatHandle * handle = new ChatHandle();
    if (!handle) {
        env->ReleaseStringUTFChars(path, path_str);
        return 0;
    }

    llama_backend_init();
    ggml_backend_load_all();

    llama_model_params model_params = llama_model_default_params();
    handle->model = llama_model_load_from_file(path_str, model_params);
    env->ReleaseStringUTFChars(path, path_str);

    if (!handle->model) {
        LOGE("Failed to load model from file");
        delete handle;
        return 0;
    }

    handle->vocab = llama_model_get_vocab(handle->model);
    handle->n_ctx = n_ctx;

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx   = n_ctx;
    ctx_params.n_batch = n_ctx;

    handle->ctx = llama_init_from_model(handle->model, ctx_params);
    if (!handle->ctx) {
        LOGE("Failed to create context");
        llama_model_free(handle->model);
        delete handle;
        return 0;
    }

    llama_set_abort_callback(handle->ctx, abort_callback, handle);

    LOGD("Chat model loaded: n_ctx=%d, n_ctx_train=%d",
         n_ctx, llama_model_n_ctx_train(handle->model));

    return reinterpret_cast<jlong>(handle);
}

JNIEXPORT jstring JNICALL
Java_com_orangeisland_app_api_LlamaChatEngine_nativeChatGetTemplate(
    JNIEnv * env, jclass /*clazz*/, jlong handle_ptr) {

    if (!handle_ptr) return nullptr;
    ChatHandle * handle = reinterpret_cast<ChatHandle *>(handle_ptr);
    if (!handle->model) return nullptr;

    const char * tmpl = llama_model_chat_template(handle->model, nullptr);
    if (!tmpl) return nullptr;
    return utf8_to_jstring(env, tmpl, strlen(tmpl));
}

JNIEXPORT jstring JNICALL
Java_com_orangeisland_app_api_LlamaChatEngine_nativeChatApplyTemplate(
    JNIEnv * env, jclass /*clazz*/, jlong handle_ptr,
    jobjectArray messages, jboolean add_ass) {

    if (!handle_ptr) return nullptr;
    ChatHandle * handle = reinterpret_cast<ChatHandle *>(handle_ptr);
    if (!handle->model) return nullptr;

    jint n_msg = env->GetArrayLength(messages);

    std::vector<llama_chat_message> chat_msgs(n_msg);
    std::vector<std::string> role_storage(n_msg);
    std::vector<std::string> content_storage(n_msg);

    for (jint i = 0; i < n_msg; i++) {
        jobject msg = env->GetObjectArrayElement(messages, i);
        jclass msg_class = env->GetObjectClass(msg);

        jfieldID role_field = env->GetFieldID(msg_class, "role", "Ljava/lang/String;");
        jfieldID content_field = env->GetFieldID(msg_class, "content", "Ljava/lang/String;");

        jstring role_jstr = (jstring)env->GetObjectField(msg, role_field);
        jstring content_jstr = (jstring)env->GetObjectField(msg, content_field);

        const char * role_cstr = env->GetStringUTFChars(role_jstr, nullptr);
        const char * content_cstr = env->GetStringUTFChars(content_jstr, nullptr);

        role_storage[i] = std::string(role_cstr ? role_cstr : "user");
        content_storage[i] = std::string(content_cstr ? content_cstr : "");

        if (role_cstr) env->ReleaseStringUTFChars(role_jstr, role_cstr);
        if (content_cstr) env->ReleaseStringUTFChars(content_jstr, content_cstr);

        chat_msgs[i].role = role_storage[i].c_str();
        chat_msgs[i].content = content_storage[i].c_str();

        env->DeleteLocalRef(msg_class);
        env->DeleteLocalRef(msg);
    }

    // Follow simple-chat.cpp: get the template from the model, pass it
    // directly to llama_chat_apply_template. Falls back to nullptr (auto-
    // detect) if the model has no template.
    const char * tmpl = llama_model_chat_template(handle->model, nullptr);
    LOGD("Chat template: %s", tmpl ? "found" : "none (will auto-detect)");

    int32_t total_chars = 0;
    for (const auto & m : chat_msgs) {
        if (m.content) total_chars += strlen(m.content);
    }
    int32_t buf_size = std::max(4096, total_chars * 2);

    std::vector<char> buf(buf_size);
    int32_t result = llama_chat_apply_template(
        tmpl,
        chat_msgs.data(), chat_msgs.size(),
        add_ass,
        buf.data(), buf_size
    );

    if (result > buf_size) {
        buf.resize(result + 1); // +1 so an exact-fit result still has room for NUL/bounds
        result = llama_chat_apply_template(
            tmpl,
            chat_msgs.data(), chat_msgs.size(),
            add_ass,
            buf.data(), result
        );
    }

    if (result < 0) {
        LOGE("llama_chat_apply_template failed with %d", result);
        return nullptr;
    }

    // result is the byte length written; decode explicitly rather than relying on a
    // NUL terminator, and handle 4-byte UTF-8 in message content safely.
    return utf8_to_jstring(env, buf.data(), static_cast<size_t>(result));
}

JNIEXPORT jint JNICALL
Java_com_orangeisland_app_api_LlamaChatEngine_nativeChatGenerate(
    JNIEnv * env, jclass /*clazz*/, jlong handle_ptr,
    jstring prompt, jfloat temperature, jfloat top_p, jint max_tokens,
    jobject callback) {

    if (!handle_ptr) return -1;
    ChatHandle * handle = reinterpret_cast<ChatHandle *>(handle_ptr);
    if (!handle->ctx || !handle->vocab) return -1;

    // Reset cancelled flag
    handle->cancelled = false;

    const char * prompt_str = env->GetStringUTFChars(prompt, nullptr);
    if (!prompt_str) return -1;

    std::string prompt_text(prompt_str);
    env->ReleaseStringUTFChars(prompt, prompt_str);

    if (prompt_text.empty()) return -1;

    // Get callback class and method IDs
    jclass cb_class = env->GetObjectClass(callback);
    jmethodID on_token = env->GetMethodID(cb_class, "onToken", "(Ljava/lang/String;)V");
    jmethodID on_done = env->GetMethodID(cb_class, "onDone", "()V");
    jmethodID on_error = env->GetMethodID(cb_class, "onError", "(Ljava/lang/String;)V");

    if (!on_token || !on_done || !on_error) {
        LOGE("Failed to get callback method IDs");
        env->DeleteLocalRef(cb_class);
        return -1;
    }

    // Tokenize the prompt
    int32_t n_tokens_max = prompt_text.length() + 256;
    std::vector<llama_token> tokens(n_tokens_max);
    int32_t n_tokens = llama_tokenize(handle->vocab, prompt_text.c_str(),
                                       prompt_text.size(), tokens.data(),
                                       n_tokens_max, true, true);
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(handle->vocab, prompt_text.c_str(),
                                   prompt_text.size(), tokens.data(),
                                   -n_tokens, true, true);
    }
    if (n_tokens <= 0) {
        LOGE("Tokenization returned 0 tokens for prompt len=%zu", prompt_text.size());
        env->CallVoidMethod(callback, on_error, env->NewStringUTF("Tokenization failed"));
        env->DeleteLocalRef(cb_class);
        return -1;
    }
    tokens.resize(n_tokens);

    const int32_t n_ctx = llama_n_ctx(handle->ctx);
    const int32_t min_generation_room = 4;
    if (n_tokens + min_generation_room > n_ctx) {
        LOGE("Prompt too long: prompt=%d + reserved=%d > ctx=%d",
             n_tokens, min_generation_room, n_ctx);
        char error_msg[64];
        std::snprintf(error_msg, sizeof(error_msg),
                      "LOCAL_CONTEXT_EXCEEDED:%d:%d", n_tokens, n_ctx);
        jstring jmsg = env->NewStringUTF(error_msg);
        env->CallVoidMethod(callback, on_error, jmsg);
        env->DeleteLocalRef(jmsg);
        env->DeleteLocalRef(cb_class);
        return -1;
    }

    LOGD("Generating: prompt_len=%zu, n_tokens=%d, max_tokens=%d",
         prompt_text.size(), n_tokens, max_tokens);

    // Sampler chain: min_p → top_p → temp → dist
    // (simple-chat.cpp uses min_p → temp → dist; we add top_p for configurability)
    auto sparams = llama_sampler_chain_default_params();
    llama_sampler * smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_min_p(0.05f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    // Prefill + generation loop (pattern from simple-chat.cpp)
    // Context space check before prefill
    int32_t n_ctx_used = llama_memory_seq_pos_max(llama_get_memory(handle->ctx), 0) + 1;
    if (n_ctx_used + n_tokens > n_ctx) {
        LOGE("Context size exceeded: used=%d + prompt=%d > ctx=%d", n_ctx_used, n_tokens, n_ctx);
        llama_sampler_free(smpl);
        env->CallVoidMethod(callback, on_error, env->NewStringUTF("Context size exceeded"));
        env->DeleteLocalRef(cb_class);
        return -1;
    }

    // Prefill all prompt tokens in one batch (n_batch == n_ctx, so this always fits)
    // llama_batch_get_one returns a lightweight batch that borrows the tokens pointer —
    // it does NOT allocate, so do NOT call llama_batch_free on it (would free vector memory)
    llama_batch batch = llama_batch_get_one(tokens.data(), n_tokens);
    if (llama_decode(handle->ctx, batch) != 0) {
        LOGE("Prefill decode failed");
        llama_sampler_free(smpl);
        env->CallVoidMethod(callback, on_error, env->NewStringUTF("Prefill decode failed"));
        env->DeleteLocalRef(cb_class);
        return -1;
    }

    // Generation loop
    int32_t generated = 0;
    llama_token new_token_id;
    std::string utf8_buf; // holds bytes not yet on a UTF-8 boundary

    // Generation loop
    while (generated < max_tokens) {
        if (handle->cancelled) {
            LOGD("Generation cancelled at %d tokens", generated);
            break;
        }

        // Context space check
        int32_t n_ctx_used = llama_memory_seq_pos_max(llama_get_memory(handle->ctx), 0) + 1;
        if (n_ctx_used + 1 > n_ctx) {
            LOGD("Context full at %d tokens", generated);
            break;
        }

        // Synchronize before sampling (best practice)
        llama_synchronize(handle->ctx);

        // Sample the next token
        new_token_id = llama_sampler_sample(smpl, handle->ctx, -1);

        if (llama_vocab_is_eog(handle->vocab, new_token_id)) {
            LOGD("EOG token %d at position %d", new_token_id, generated);
            break;
        }

        // Convert token to text
        char piece[256];
        int32_t n = llama_token_to_piece(handle->vocab, new_token_id, piece, sizeof(piece), 0, true);
        if (n < 0) {
            LOGE("llama_token_to_piece failed");
            break;
        }
        // Accumulate, then emit only the complete-UTF-8 prefix; the truncated
        // tail (if any) is carried into the next token.
        utf8_buf.append(piece, n);
        size_t emit_len = utf8_complete_prefix_len(utf8_buf);
        if (emit_len > 0) {
            jstring jtoken = utf8_to_jstring(env, utf8_buf.data(), emit_len);
            env->CallVoidMethod(callback, on_token, jtoken);
            env->DeleteLocalRef(jtoken);
            utf8_buf.erase(0, emit_len);

            if (env->ExceptionCheck()) {
                env->ExceptionDescribe();
                env->ExceptionClear();
                LOGD("Java exception in onToken, stopping generation");
                break;
            }
        }

        generated++;

        // Decode the new token
        llama_batch single = llama_batch_get_one(&new_token_id, 1);
        if (llama_decode(handle->ctx, single) != 0) {
            LOGE("Decode failed at token %d", generated);
            break;
        }
    }

    llama_sampler_free(smpl);
    env->CallVoidMethod(callback, on_done);
    env->DeleteLocalRef(cb_class);

    LOGD("Generation complete: %d tokens generated", generated);
    return generated;
}

JNIEXPORT void JNICALL
Java_com_orangeisland_app_api_LlamaChatEngine_nativeChatReset(
    JNIEnv * /*env*/, jclass /*clazz*/, jlong handle_ptr) {

    if (!handle_ptr) return;
    ChatHandle * handle = reinterpret_cast<ChatHandle *>(handle_ptr);
    if (handle->ctx) {
        llama_memory_clear(llama_get_memory(handle->ctx), true);
        LOGD("KV cache cleared");
    }
}

JNIEXPORT jboolean JNICALL
Java_com_orangeisland_app_api_LlamaChatEngine_nativeChatLoadMmproj(
    JNIEnv * env, jclass /*clazz*/, jlong handle_ptr, jstring mmproj_path) {

    if (!handle_ptr) return JNI_FALSE;
    ChatHandle * handle = reinterpret_cast<ChatHandle *>(handle_ptr);
    if (!handle->model) return JNI_FALSE;

    const char * mmproj_str = env->GetStringUTFChars(mmproj_path, nullptr);
    if (!mmproj_str) return JNI_FALSE;

    mtmd_context_params params = mtmd_context_params_default();
    params.use_gpu = false;
    params.n_threads = 4;
    params.print_timings = false;

    // Try loading new mmproj first (don't free old one yet)
    mtmd_context * new_mtmd = mtmd_init_from_file(mmproj_str, handle->model, params);
    env->ReleaseStringUTFChars(mmproj_path, mmproj_str);

    if (!new_mtmd) {
        LOGE("Failed to load mmproj, keeping previous if any");
        return JNI_FALSE;
    }

    // Success: free old, install new
    if (handle->mtmd_ctx) {
        mtmd_free(handle->mtmd_ctx);
    }
    handle->mtmd_ctx = new_mtmd;

    LOGD("mmproj loaded successfully");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_orangeisland_app_api_LlamaChatEngine_nativeChatUnloadMmproj(
    JNIEnv * /*env*/, jclass /*clazz*/, jlong handle_ptr) {

    if (!handle_ptr) return;
    ChatHandle * handle = reinterpret_cast<ChatHandle *>(handle_ptr);
    if (handle->mtmd_ctx) {
        mtmd_free(handle->mtmd_ctx);
        handle->mtmd_ctx = nullptr;
        LOGD("mmproj unloaded");
    }
}

JNIEXPORT jboolean JNICALL
Java_com_orangeisland_app_api_LlamaChatEngine_nativeChatHasMmproj(
    JNIEnv * /*env*/, jclass /*clazz*/, jlong handle_ptr) {

    if (!handle_ptr) return JNI_FALSE;
    ChatHandle * handle = reinterpret_cast<ChatHandle *>(handle_ptr);
    return handle->mtmd_ctx != nullptr ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_orangeisland_app_api_LlamaChatEngine_nativeChatGenerateWithImages(
    JNIEnv * env, jclass /*clazz*/, jlong handle_ptr,
    jstring prompt, jobjectArray image_paths,
    jfloat temperature, jfloat top_p, jint max_tokens,
    jobject callback) {

    if (!handle_ptr) return -1;
    ChatHandle * handle = reinterpret_cast<ChatHandle *>(handle_ptr);
    if (!handle->ctx || !handle->vocab) return -1;
    if (!handle->mtmd_ctx) {
        env->CallVoidMethod(callback,
            env->GetMethodID(env->GetObjectClass(callback), "onError", "(Ljava/lang/String;)V"),
            env->NewStringUTF("Vision projector not loaded. Add mmproj file in model settings."));
        return -1;
    }

    handle->cancelled = false;

    const char * prompt_str = env->GetStringUTFChars(prompt, nullptr);
    if (!prompt_str) return -1;
    std::string prompt_text(prompt_str);
    env->ReleaseStringUTFChars(prompt, prompt_str);

    // --- Build bitmaps from image paths ---
    jint n_images = env->GetArrayLength(image_paths);
    std::vector<mtmd_bitmap *> bitmaps(n_images, nullptr);
    std::vector<std::string> image_path_storage(n_images);

    for (jint i = 0; i < n_images; i++) {
        jstring jpath = (jstring)env->GetObjectArrayElement(image_paths, i);
        const char * cpath = env->GetStringUTFChars(jpath, nullptr);
        image_path_storage[i] = std::string(cpath);
        env->ReleaseStringUTFChars(jpath, cpath);
        env->DeleteLocalRef(jpath);

        bitmaps[i] = mtmd_helper_bitmap_init_from_file(handle->mtmd_ctx,
                                                       image_path_storage[i].c_str());
        if (!bitmaps[i]) {
            LOGE("Failed to load image: %s", image_path_storage[i].c_str());
            // Clean up already-loaded bitmaps
            for (jint j = 0; j < i; j++) {
                if (bitmaps[j]) mtmd_bitmap_free(bitmaps[j]);
            }
            env->CallVoidMethod(callback,
                env->GetMethodID(env->GetObjectClass(callback), "onError", "(Ljava/lang/String;)V"),
                env->NewStringUTF("Failed to load image for multimodal input."));
            return -1;
        }
    }

    // --- Tokenize prompt with image markers ---
    mtmd_input_text text_input;
    text_input.text         = prompt_text.c_str();
    text_input.add_special  = true;
    text_input.parse_special = true;

    std::vector<const mtmd_bitmap *> bitmap_ptrs;
    for (auto & b : bitmaps) bitmap_ptrs.push_back(b);

    mtmd_input_chunks * chunks = mtmd_input_chunks_init();
    int32_t tok_ret = mtmd_tokenize(handle->mtmd_ctx, chunks, &text_input,
                                    bitmap_ptrs.data(), bitmap_ptrs.size());
    if (tok_ret != 0) {
        LOGE("mtmd_tokenize failed with code %d (images=%d)", tok_ret, n_images);
        for (auto & b : bitmaps) if (b) mtmd_bitmap_free(b);
        mtmd_input_chunks_free(chunks);
        env->CallVoidMethod(callback,
            env->GetMethodID(env->GetObjectClass(callback), "onError", "(Ljava/lang/String;)V"),
            env->NewStringUTF("Failed to tokenize multimodal prompt."));
        return -1;
    }

    // --- Eval all chunks (text + image) via mtmd helper ---
    jclass cb_class = env->GetObjectClass(callback);
    jmethodID on_token = env->GetMethodID(cb_class, "onToken", "(Ljava/lang/String;)V");
    jmethodID on_done  = env->GetMethodID(cb_class, "onDone", "()V");
    jmethodID on_error = env->GetMethodID(cb_class, "onError", "(Ljava/lang/String;)V");

    llama_pos n_past = 0;
    int32_t n_ctx = llama_n_ctx(handle->ctx);
    int32_t eval_ret = mtmd_helper_eval_chunks(handle->mtmd_ctx, handle->ctx,
                                                chunks, n_past, 0, n_ctx,
                                                true, &n_past);
    // Free bitmaps and chunks after evaluation
    for (auto & b : bitmaps) if (b) mtmd_bitmap_free(b);
    mtmd_input_chunks_free(chunks);

    if (eval_ret != 0) {
        LOGE("mtmd_helper_eval_chunks failed with code %d", eval_ret);
        env->CallVoidMethod(callback, on_error,
            env->NewStringUTF("Multimodal prefill failed."));
        env->DeleteLocalRef(cb_class);
        return -1;
    }

    LOGD("Multimodal prefill done: n_past=%lld, starting generation", (long long)n_past);

    // --- Generation loop (same as text-only path) ---
    auto sparams = llama_sampler_chain_default_params();
    llama_sampler * smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_min_p(0.05f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    int32_t generated = 0;
    std::string utf8_buf; // holds bytes not yet on a UTF-8 boundary
    while (generated < max_tokens) {
        if (handle->cancelled) break;

        int32_t n_ctx_used = llama_memory_seq_pos_max(llama_get_memory(handle->ctx), 0) + 1;
        if (n_ctx_used + 1 > n_ctx) break;

        llama_synchronize(handle->ctx);
        llama_token new_token_id = llama_sampler_sample(smpl, handle->ctx, -1);

        if (llama_vocab_is_eog(handle->vocab, new_token_id)) break;

        char piece[256];
        int32_t n = llama_token_to_piece(handle->vocab, new_token_id, piece, sizeof(piece), 0, true);
        if (n < 0) break;

        utf8_buf.append(piece, n);
        size_t emit_len = utf8_complete_prefix_len(utf8_buf);
        if (emit_len > 0) {
            jstring jtoken = utf8_to_jstring(env, utf8_buf.data(), emit_len);
            env->CallVoidMethod(callback, on_token, jtoken);
            env->DeleteLocalRef(jtoken);
            utf8_buf.erase(0, emit_len);

            if (env->ExceptionCheck()) { env->ExceptionClear(); break; }
        }

        generated++;
        llama_batch single = llama_batch_get_one(&new_token_id, 1);
        if (llama_decode(handle->ctx, single) != 0) break;
    }

    llama_sampler_free(smpl);
    env->CallVoidMethod(callback, on_done);
    env->DeleteLocalRef(cb_class);

    LOGD("Multimodal generation complete: %d tokens", generated);
    return generated;
}

JNIEXPORT void JNICALL
Java_com_orangeisland_app_api_LlamaChatEngine_nativeChatFreeModel(
    JNIEnv * /*env*/, jclass /*clazz*/, jlong handle_ptr) {

    if (!handle_ptr) return;
    ChatHandle * handle = reinterpret_cast<ChatHandle *>(handle_ptr);

    if (handle->mtmd_ctx) mtmd_free(handle->mtmd_ctx);
    if (handle->ctx)   llama_free(handle->ctx);
    if (handle->model) llama_model_free(handle->model);

    LOGD("Chat model freed");
    delete handle;
}

JNIEXPORT void JNICALL
Java_com_orangeisland_app_api_LlamaChatEngine_nativeChatCancel(
    JNIEnv * /*env*/, jclass /*clazz*/, jlong handle_ptr) {

    if (!handle_ptr) return;
    ChatHandle * handle = reinterpret_cast<ChatHandle *>(handle_ptr);
    handle->cancelled = true;
    LOGD("Cancellation requested");
}

} // extern "C"
