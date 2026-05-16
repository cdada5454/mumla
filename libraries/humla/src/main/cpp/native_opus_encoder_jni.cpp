#include <jni.h>

#include <cstdint>
#include <memory>
#include <string>

#include <opus.h>

namespace {

class NativeOpusEncoder final {
public:
    NativeOpusEncoder(int32_t sampleRate, int32_t channels, int32_t bitrate, int* error) {
        encoder_ = opus_encoder_create(sampleRate, channels, OPUS_APPLICATION_VOIP, error);
        if (encoder_ == nullptr || *error < OPUS_OK) {
            return;
        }

        int result = opus_encoder_ctl(encoder_, OPUS_SET_VBR(0));
        if (result < OPUS_OK) {
            *error = result;
            return;
        }

        result = opus_encoder_ctl(encoder_, OPUS_SET_BITRATE(bitrate));
        if (result < OPUS_OK) {
            *error = result;
            return;
        }

        *error = OPUS_OK;
    }

    ~NativeOpusEncoder() {
        if (encoder_ != nullptr) {
            opus_encoder_destroy(encoder_);
            encoder_ = nullptr;
        }
    }

    NativeOpusEncoder(const NativeOpusEncoder&) = delete;
    NativeOpusEncoder& operator=(const NativeOpusEncoder&) = delete;

    int encode(const int16_t* pcm, int32_t frameSize, unsigned char* output, int32_t maxOutputSize) {
        if (encoder_ == nullptr) {
            return OPUS_INVALID_STATE;
        }
        return opus_encode(encoder_, pcm, frameSize, output, maxOutputSize);
    }

    int bitrate() const {
        if (encoder_ == nullptr) {
            return OPUS_INVALID_STATE;
        }
        opus_int32 value = 0;
        int result = opus_encoder_ctl(encoder_, OPUS_GET_BITRATE(&value));
        return result < OPUS_OK ? result : static_cast<int>(value);
    }

private:
    OpusEncoder* encoder_ = nullptr;
};

NativeOpusEncoder* fromHandle(jlong handle) {
    return reinterpret_cast<NativeOpusEncoder*>(handle);
}

void throwIllegalState(JNIEnv* env, const std::string& message) {
    jclass exceptionClass = env->FindClass("java/lang/IllegalStateException");
    env->ThrowNew(exceptionClass, message.c_str());
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_se_lublin_humla_audio_encoder_NativeOpusEncoder_nativeCreate(
        JNIEnv* env,
        jclass,
        jint sampleRate,
        jint channels,
        jint bitrate) {
    int error = OPUS_OK;
    auto encoder = std::make_unique<NativeOpusEncoder>(sampleRate, channels, bitrate, &error);
    if (error < OPUS_OK) {
        throwIllegalState(env, "Opus encoder initialization failed with error: " + std::to_string(error));
        return 0;
    }
    return reinterpret_cast<jlong>(encoder.release());
}

extern "C" JNIEXPORT jint JNICALL
Java_se_lublin_humla_audio_encoder_NativeOpusEncoder_nativeEncode(
        JNIEnv* env,
        jclass,
        jlong handle,
        jshortArray input,
        jint frameSize,
        jbyteArray output,
        jint maxOutputSize) {
    NativeOpusEncoder* encoder = fromHandle(handle);
    if (encoder == nullptr || input == nullptr || output == nullptr) {
        return OPUS_BAD_ARG;
    }

    const jsize inputLength = env->GetArrayLength(input);
    const jsize outputLength = env->GetArrayLength(output);
    if (frameSize <= 0 || frameSize > inputLength || maxOutputSize <= 0 || maxOutputSize > outputLength) {
        return OPUS_BAD_ARG;
    }

    jboolean inputIsCopy = JNI_FALSE;
    auto* pcm = env->GetShortArrayElements(input, &inputIsCopy);
    if (pcm == nullptr) {
        return OPUS_ALLOC_FAIL;
    }

    jboolean outputIsCopy = JNI_FALSE;
    auto* encoded = env->GetByteArrayElements(output, &outputIsCopy);
    if (encoded == nullptr) {
        env->ReleaseShortArrayElements(input, pcm, JNI_ABORT);
        return OPUS_ALLOC_FAIL;
    }

    int result = encoder->encode(
            reinterpret_cast<const int16_t*>(pcm),
            frameSize,
            reinterpret_cast<unsigned char*>(encoded),
            maxOutputSize);

    env->ReleaseByteArrayElements(output, encoded, result >= OPUS_OK ? 0 : JNI_ABORT);
    env->ReleaseShortArrayElements(input, pcm, JNI_ABORT);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_se_lublin_humla_audio_encoder_NativeOpusEncoder_nativeGetBitrate(
        JNIEnv*,
        jclass,
        jlong handle) {
    NativeOpusEncoder* encoder = fromHandle(handle);
    return encoder != nullptr ? encoder->bitrate() : OPUS_BAD_ARG;
}

extern "C" JNIEXPORT void JNICALL
Java_se_lublin_humla_audio_encoder_NativeOpusEncoder_nativeDestroy(
        JNIEnv*,
        jclass,
        jlong handle) {
    delete fromHandle(handle);
}
