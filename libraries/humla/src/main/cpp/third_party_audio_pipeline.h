#pragma once

#include <cstdint>
#include <memory>
#include <mutex>
#include <vector>

#include <modules/audio_processing/include/audio_processing.h>
#include <rnnoise.h>

class ThirdPartyAudioPipeline {
public:
    explicit ThirdPartyAudioPipeline(int32_t sampleRate);
    ~ThirdPartyAudioPipeline();

    ThirdPartyAudioPipeline(const ThirdPartyAudioPipeline&) = delete;
    ThirdPartyAudioPipeline& operator=(const ThirdPartyAudioPipeline&) = delete;

    void process(int16_t* pcm, int32_t frameCount);
    void queueEchoReference(const int16_t* pcm, int32_t frameCount);

private:
    bool isWebRtcFrameSupported(int32_t frameCount) const;
    void processWebRtcAec(int16_t* pcm, int32_t frameCount);
    void processRnnoise(int16_t* pcm, int32_t frameCount);
    void processWebRtcAgc(int16_t* pcm, int32_t frameCount);
    bool processWebRtcStream(webrtc::AudioProcessing* processing, int16_t* pcm, int32_t frameCount);

    int32_t sampleRate_;
    webrtc::StreamConfig streamConfig_;
    std::unique_ptr<webrtc::AudioProcessing> aecProcessing_;
    std::unique_ptr<webrtc::AudioProcessing> agcProcessing_;
    DenoiseState* rnnoiseState_;
    std::mutex echoReferenceMutex_;
    std::vector<int16_t> echoReference_;
    std::vector<float> rnnoiseFrame_;
    std::vector<float> webRtcInputFrame_;
    std::vector<float> webRtcOutputFrame_;
};
