#include "third_party_audio_pipeline.h"

#include <algorithm>

#include <android/log.h>

namespace {

constexpr const char* kTag = "HumlaAudioPipeline";
constexpr int32_t kChannels = 1;
constexpr int32_t kRnnoiseFrameSize = 480;

float pcmToFloat(int16_t sample) {
    return static_cast<float>(sample);
}

float pcmToUnitFloat(int16_t sample) {
    return static_cast<float>(sample) / 32768.0f;
}

int16_t floatToPcm(float sample) {
    sample = std::max(-32768.0f, std::min(32767.0f, sample));
    return static_cast<int16_t>(sample);
}

int16_t unitFloatToPcm(float sample) {
    sample = std::max(-1.0f, std::min(1.0f, sample));
    return static_cast<int16_t>(sample * 32767.0f);
}

} // namespace

ThirdPartyAudioPipeline::ThirdPartyAudioPipeline(int32_t sampleRate)
    : sampleRate_(sampleRate),
      streamConfig_(sampleRate, kChannels),
      rnnoiseState_(rnnoise_create(nullptr)),
      rnnoiseFrame_(kRnnoiseFrameSize),
      webRtcInputFrame_(static_cast<size_t>(std::max(1, sampleRate / 100))),
      webRtcOutputFrame_(static_cast<size_t>(std::max(1, sampleRate / 100))) {
    aecProcessing_.reset(webrtc::AudioProcessing::Create());
    if (aecProcessing_) {
        aecProcessing_->Initialize(sampleRate_,
                                   sampleRate_,
                                   sampleRate_,
                                   webrtc::AudioProcessing::kMono,
                                   webrtc::AudioProcessing::kMono,
                                   webrtc::AudioProcessing::kMono);
        aecProcessing_->high_pass_filter()->Enable(true);
        aecProcessing_->echo_cancellation()->enable_drift_compensation(false);
        aecProcessing_->echo_cancellation()->Enable(true);
    }

    agcProcessing_.reset(webrtc::AudioProcessing::Create());
    if (agcProcessing_) {
        agcProcessing_->Initialize(sampleRate_,
                                   sampleRate_,
                                   sampleRate_,
                                   webrtc::AudioProcessing::kMono,
                                   webrtc::AudioProcessing::kMono,
                                   webrtc::AudioProcessing::kMono);
        agcProcessing_->gain_control()->set_mode(webrtc::GainControl::kAdaptiveDigital);
        agcProcessing_->gain_control()->Enable(true);
    }
}

ThirdPartyAudioPipeline::~ThirdPartyAudioPipeline() {
    if (rnnoiseState_ != nullptr) {
        rnnoise_destroy(rnnoiseState_);
        rnnoiseState_ = nullptr;
    }
}

void ThirdPartyAudioPipeline::queueEchoReference(const int16_t* pcm, int32_t frameCount) {
    if (pcm == nullptr || frameCount <= 0) {
        return;
    }
    std::lock_guard<std::mutex> lock(echoReferenceMutex_);
    echoReference_.assign(pcm, pcm + frameCount);
}

void ThirdPartyAudioPipeline::process(int16_t* pcm, int32_t frameCount) {
    if (pcm == nullptr || frameCount <= 0) {
        return;
    }

    processWebRtcAec(pcm, frameCount);
    processRnnoise(pcm, frameCount);
    processWebRtcAgc(pcm, frameCount);
}

bool ThirdPartyAudioPipeline::isWebRtcFrameSupported(int32_t frameCount) const {
    const bool supportedRate =
            sampleRate_ == 8000 ||
            sampleRate_ == 16000 ||
            sampleRate_ == 32000 ||
            sampleRate_ == 48000;
    return supportedRate && frameCount == sampleRate_ / 100;
}

void ThirdPartyAudioPipeline::processWebRtcAec(int16_t* pcm, int32_t frameCount) {
    if (!aecProcessing_ || !isWebRtcFrameSupported(frameCount)) {
        return;
    }

    std::vector<int16_t> echoReference;
    {
        std::lock_guard<std::mutex> lock(echoReferenceMutex_);
        echoReference = echoReference_;
    }
    if (echoReference.empty()) {
        return;
    }

    if (static_cast<int32_t>(echoReference.size()) >= frameCount) {
        for (int32_t i = 0; i < frameCount; ++i) {
            webRtcInputFrame_[i] = pcmToUnitFloat(echoReference[i]);
        }
        float* reverseDest[] = {webRtcOutputFrame_.data()};
        const float* reverseSrc[] = {webRtcInputFrame_.data()};
        const int reverseResult = aecProcessing_->ProcessReverseStream(
                reverseSrc,
                streamConfig_,
                streamConfig_,
                reverseDest);
        if (reverseResult != webrtc::AudioProcessing::kNoError) {
            __android_log_print(ANDROID_LOG_WARN, kTag,
                                "WebRTC AEC reverse stream failed: %d", reverseResult);
        }
    }

    processWebRtcStream(aecProcessing_.get(), pcm, frameCount);
}

void ThirdPartyAudioPipeline::processRnnoise(int16_t* pcm, int32_t frameCount) {
    if (rnnoiseState_ == nullptr || sampleRate_ != 48000) {
        return;
    }

    int32_t offset = 0;
    while (offset + kRnnoiseFrameSize <= frameCount) {
        for (int32_t i = 0; i < kRnnoiseFrameSize; ++i) {
            rnnoiseFrame_[i] = pcmToFloat(pcm[offset + i]);
        }
        rnnoise_process_frame(rnnoiseState_, rnnoiseFrame_.data(), rnnoiseFrame_.data());
        for (int32_t i = 0; i < kRnnoiseFrameSize; ++i) {
            pcm[offset + i] = floatToPcm(rnnoiseFrame_[i]);
        }
        offset += kRnnoiseFrameSize;
    }
}

void ThirdPartyAudioPipeline::processWebRtcAgc(int16_t* pcm, int32_t frameCount) {
    if (!agcProcessing_ || !isWebRtcFrameSupported(frameCount)) {
        return;
    }

    processWebRtcStream(agcProcessing_.get(), pcm, frameCount);
}

bool ThirdPartyAudioPipeline::processWebRtcStream(
        webrtc::AudioProcessing* processing,
        int16_t* pcm,
        int32_t frameCount) {
    if (processing == nullptr || pcm == nullptr || frameCount <= 0) {
        return false;
    }

    if (static_cast<int32_t>(webRtcInputFrame_.size()) < frameCount) {
        webRtcInputFrame_.resize(static_cast<size_t>(frameCount));
        webRtcOutputFrame_.resize(static_cast<size_t>(frameCount));
    }

    for (int32_t i = 0; i < frameCount; ++i) {
        webRtcInputFrame_[i] = pcmToUnitFloat(pcm[i]);
    }

    float* dest[] = {webRtcOutputFrame_.data()};
    const float* src[] = {webRtcInputFrame_.data()};
    const int result = processing->ProcessStream(src, streamConfig_, streamConfig_, dest);
    if (result != webrtc::AudioProcessing::kNoError) {
        __android_log_print(ANDROID_LOG_WARN, kTag,
                            "WebRTC process stream failed: %d", result);
        return false;
    }

    for (int32_t i = 0; i < frameCount; ++i) {
        pcm[i] = unitFloatToPcm(webRtcOutputFrame_[i]);
    }
    return true;
}
