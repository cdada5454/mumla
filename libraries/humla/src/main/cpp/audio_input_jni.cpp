#include <jni.h>
#include <oboe/Oboe.h>

#include "third_party_audio_pipeline.h"

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include <android/log.h>

namespace {

constexpr const char* kTag = "HumlaOboeInput";
constexpr int32_t kDefaultFrameSize = 480;
constexpr int64_t kReadTimeoutNanos = 100000000;

void logError(const char* message, oboe::Result result) {
    __android_log_print(ANDROID_LOG_ERROR, kTag, "%s: %s", message, oboe::convertToText(result));
}

std::mutex gPipelineMutex;
ThirdPartyAudioPipeline* gActivePipeline = nullptr;

class OboeAudioInput final {
public:
    OboeAudioInput(JavaVM* vm, jobject listener, int32_t targetSampleRate)
        : vm_(vm),
          listener_(nullptr),
          onAudioInputReceived_(nullptr),
          requestedSampleRate_(targetSampleRate),
          frameSize_(kDefaultFrameSize),
          pipeline_(nullptr),
          recording_(false) {
        JNIEnv* env = nullptr;
        vm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
        listener_ = env->NewGlobalRef(listener);
        jclass listenerClass = env->GetObjectClass(listener);
        onAudioInputReceived_ = env->GetMethodID(listenerClass, "onAudioInputReceived", "([SI)V");
        env->DeleteLocalRef(listenerClass);
    }

    ~OboeAudioInput() {
        stop();
        JNIEnv* env = nullptr;
        bool attached = attach(&env);
        if (env != nullptr && listener_ != nullptr) {
            env->DeleteGlobalRef(listener_);
            listener_ = nullptr;
        }
        detach(attached);
    }

    oboe::Result open() {
        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Input)
                ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
                ->setSharingMode(oboe::SharingMode::Exclusive)
                ->setFormat(oboe::AudioFormat::I16)
                ->setChannelCount(oboe::ChannelCount::Mono)
                ->setInputPreset(oboe::InputPreset::VoiceCommunication)
                ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium);

        if (requestedSampleRate_ > 0) {
            builder.setSampleRate(requestedSampleRate_);
        }

        oboe::Result result = builder.openStream(stream_);
        if (result != oboe::Result::OK) {
            logError("exclusive input stream open failed, retrying shared", result);
            builder.setSharingMode(oboe::SharingMode::Shared);
            result = builder.openStream(stream_);
        }

        if (result == oboe::Result::OK && stream_) {
            actualSampleRate_ = stream_->getSampleRate();
            frameSize_ = std::max(1, actualSampleRate_ / 100);
            pipeline_ = std::make_unique<ThirdPartyAudioPipeline>(actualSampleRate_);
            std::lock_guard<std::mutex> lock(gPipelineMutex);
            gActivePipeline = pipeline_.get();
        }

        return result;
    }

    oboe::Result start() {
        if (!stream_) {
            return oboe::Result::ErrorNull;
        }
        recording_.store(true);
        oboe::Result result = stream_->requestStart();
        if (result == oboe::Result::OK) {
            readThread_ = std::thread(&OboeAudioInput::readLoop, this);
        } else {
            recording_.store(false);
        }
        return result;
    }

    void stop() {
        recording_.store(false);
        std::shared_ptr<oboe::AudioStream> stream = stream_;
        if (stream) {
            stream->requestStop();
        }
        if (readThread_.joinable()) {
            readThread_.join();
        }
        {
            std::lock_guard<std::mutex> lock(streamMutex_);
            stream = stream_;
            stream_.reset();
        }
        if (stream) {
            stream->close();
        }
        std::lock_guard<std::mutex> lock(gPipelineMutex);
        if (gActivePipeline == pipeline_.get()) {
            gActivePipeline = nullptr;
        }
    }

    bool isRecording() const {
        return recording_.load();
    }

    int32_t getSampleRate() const {
        return actualSampleRate_ > 0 ? actualSampleRate_ : requestedSampleRate_;
    }

    int32_t getFrameSize() const {
        return frameSize_;
    }

private:
    void readLoop() {
        std::vector<int16_t> frame(static_cast<size_t>(frameSize_));
        while (recording_.load()) {
            std::shared_ptr<oboe::AudioStream> stream = stream_;
            if (!stream) {
                return;
            }

            int32_t framesRead = 0;
            while (framesRead < frameSize_ && recording_.load()) {
                auto result = stream->read(frame.data() + framesRead,
                                           frameSize_ - framesRead,
                                           kReadTimeoutNanos);
                if (!result) {
                    if (recording_.load()) {
                        logError("input stream read failed", result.error());
                    }
                    return;
                }
                if (result.value() == 0) {
                    continue;
                }
                framesRead += result.value();
            }

            if (framesRead == frameSize_ && pipeline_ != nullptr) {
                pipeline_->process(frame.data(), frameSize_);
                dispatchFrame(frame.data(), frameSize_);
            }
        }
    }

    bool attach(JNIEnv** env) {
        *env = nullptr;
        jint result = vm_->GetEnv(reinterpret_cast<void**>(env), JNI_VERSION_1_6);
        if (result == JNI_OK) {
            return false;
        }
        if (result == JNI_EDETACHED && vm_->AttachCurrentThread(env, nullptr) == JNI_OK) {
            return true;
        }
        return false;
    }

    void detach(bool attached) {
        if (attached) {
            vm_->DetachCurrentThread();
        }
    }

    void dispatchFrame(const int16_t* pcm, int32_t frameCount) {
        JNIEnv* env = nullptr;
        bool attached = attach(&env);
        if (env == nullptr || listener_ == nullptr || onAudioInputReceived_ == nullptr) {
            detach(attached);
            return;
        }

        jshortArray frame = env->NewShortArray(frameCount);
        if (frame != nullptr) {
            env->SetShortArrayRegion(frame, 0, frameCount, reinterpret_cast<const jshort*>(pcm));
            env->CallVoidMethod(listener_, onAudioInputReceived_, frame, frameCount);
            env->DeleteLocalRef(frame);
        }

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
        detach(attached);
    }

    JavaVM* vm_;
    jobject listener_;
    jmethodID onAudioInputReceived_;
    int32_t requestedSampleRate_;
    int32_t actualSampleRate_ = 0;
    int32_t frameSize_;
    std::unique_ptr<ThirdPartyAudioPipeline> pipeline_;
    std::atomic<bool> recording_;
    std::mutex streamMutex_;
    std::shared_ptr<oboe::AudioStream> stream_;
    std::thread readThread_;
};

OboeAudioInput* fromHandle(jlong handle) {
    return reinterpret_cast<OboeAudioInput*>(handle);
}

void throwIllegalState(JNIEnv* env, const std::string& message, oboe::Result result) {
    jclass exceptionClass = env->FindClass("java/lang/IllegalStateException");
    std::string fullMessage = message + ": " + oboe::convertToText(result);
    env->ThrowNew(exceptionClass, fullMessage.c_str());
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_se_lublin_humla_audio_NativeAudioInput_nativeCreate(
        JNIEnv* env,
        jclass,
        jobject listener,
        jint sampleRate) {
    JavaVM* vm = nullptr;
    env->GetJavaVM(&vm);
    auto input = std::make_unique<OboeAudioInput>(vm, listener, sampleRate);
    oboe::Result result = input->open();
    if (result != oboe::Result::OK) {
        throwIllegalState(env, "Unable to open Oboe input stream", result);
        return 0;
    }
    return reinterpret_cast<jlong>(input.release());
}

extern "C" JNIEXPORT void JNICALL
Java_se_lublin_humla_audio_NativeAudioInput_nativeStart(JNIEnv* env, jclass, jlong handle) {
    OboeAudioInput* input = fromHandle(handle);
    if (input == nullptr) {
        throwIllegalState(env, "Unable to start null Oboe input stream", oboe::Result::ErrorNull);
        return;
    }
    oboe::Result result = input->start();
    if (result != oboe::Result::OK) {
        throwIllegalState(env, "Unable to start Oboe input stream", result);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_se_lublin_humla_audio_NativeAudioInput_nativeStop(JNIEnv*, jclass, jlong handle) {
    OboeAudioInput* input = fromHandle(handle);
    if (input != nullptr) {
        input->stop();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_se_lublin_humla_audio_NativeAudioInput_nativeDestroy(JNIEnv*, jclass, jlong handle) {
    delete fromHandle(handle);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_se_lublin_humla_audio_NativeAudioInput_nativeIsRecording(JNIEnv*, jclass, jlong handle) {
    OboeAudioInput* input = fromHandle(handle);
    return input != nullptr && input->isRecording() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_se_lublin_humla_audio_NativeAudioInput_nativeGetSampleRate(JNIEnv*, jclass, jlong handle) {
    OboeAudioInput* input = fromHandle(handle);
    return input != nullptr ? input->getSampleRate() : 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_se_lublin_humla_audio_NativeAudioInput_nativeGetFrameSize(JNIEnv*, jclass, jlong handle) {
    OboeAudioInput* input = fromHandle(handle);
    return input != nullptr ? input->getFrameSize() : 0;
}

extern "C" JNIEXPORT void JNICALL
Java_se_lublin_humla_audio_NativeAudioInput_nativeQueueEchoReference(
        JNIEnv* env,
        jclass,
        jshortArray pcm,
        jint offset,
        jint frameCount) {
    if (pcm == nullptr || offset < 0 || frameCount <= 0) {
        return;
    }

    const jsize length = env->GetArrayLength(pcm);
    if (offset + frameCount > length) {
        return;
    }

    jboolean isCopy = JNI_FALSE;
    auto* samples = env->GetShortArrayElements(pcm, &isCopy);
    if (samples == nullptr) {
        return;
    }
    {
        std::lock_guard<std::mutex> lock(gPipelineMutex);
        if (gActivePipeline != nullptr) {
            gActivePipeline->queueEchoReference(
                    reinterpret_cast<const int16_t*>(samples + offset),
                    frameCount);
        }
    }
    env->ReleaseShortArrayElements(pcm, samples, JNI_ABORT);
}
