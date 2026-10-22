// JNI bridge between com.musictracker.lyrics.Whisper and whisper.cpp.
#include <jni.h>
#include <string>
#include "whisper.h"

extern "C" JNIEXPORT jlong JNICALL
Java_com_musictracker_lyrics_Whisper_nativeInit(JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);
    return reinterpret_cast<jlong>(ctx);
}

// Returns "<language>\t<text>", or an empty string on failure.
extern "C" JNIEXPORT jstring JNICALL
Java_com_musictracker_lyrics_Whisper_nativeTranscribe(JNIEnv *env, jobject, jlong handle,
                                                      jfloatArray samples, jint threads) {
    auto *ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx == nullptr) return env->NewStringUTF("");

    whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.n_threads = threads;
    p.translate = false;
    p.no_context = true;
    p.no_timestamps = true;
    p.single_segment = false;
    p.print_special = false;
    p.print_progress = false;
    p.print_realtime = false;
    p.print_timestamps = false;
    p.language = "auto";
    p.detect_language = false;
    p.suppress_blank = true;
    p.suppress_nst = true;

    jsize n = env->GetArrayLength(samples);
    jfloat *data = env->GetFloatArrayElements(samples, nullptr);
    int rc = whisper_full(ctx, p, data, n);
    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);
    if (rc != 0) return env->NewStringUTF("");

    std::string out;
    int langId = whisper_full_lang_id(ctx);
    const char *lang = langId >= 0 ? whisper_lang_str(langId) : "";
    out += (lang ? lang : "");
    out += '\t';
    int segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < segments; i++) {
        const char *text = whisper_full_get_segment_text(ctx, i);
        if (text) out += text;
    }
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_musictracker_lyrics_Whisper_nativeFree(JNIEnv *, jobject, jlong handle) {
    auto *ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx != nullptr) whisper_free(ctx);
}
