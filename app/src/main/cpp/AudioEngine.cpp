#include <jni.h>
#include <string>
#include <oboe/Oboe.h>
#include <android/log.h>
#include <memory>
#include <mutex>
#include <map>

#define DR_WAV_IMPLEMENTATION
#include "dr_wav.h"

#define TAG "AudioEngine"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct SoundSample {
    std::vector<float> data;
};

struct ActiveVoice {
    int soundId;
    size_t frameIndex;
};

class PianoCallback : public oboe::AudioStreamDataCallback {
public:
    std::map<int, SoundSample> samples;
    std::vector<ActiveVoice> activeSounds;
    std::mutex soundMutex;

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *audioStream, void *audioData, int32_t numFrames) override {
        const int totalSamples = numFrames * 2;
        auto *outputData = static_cast<float *>(audioData);

        std::fill_n(outputData, totalSamples, 0.0f);
        std::lock_guard<std::mutex> lock(soundMutex);

        for (auto it = activeSounds.begin(); it != activeSounds.end(); ) {
            auto sampleIt = samples.find(it->soundId);
            if (sampleIt == samples.end()) {
                it = activeSounds.erase(it);
                continue;
            }

            const auto &sample = sampleIt->second.data;
            size_t remaining = sample.size() - it->frameIndex;
            size_t framesToMix = std::min((size_t)totalSamples, remaining);

            for (size_t i = 0; i < framesToMix; ++i) {
                outputData[i] += sample[it->frameIndex + i];
            }

            it->frameIndex += framesToMix;
            if (it->frameIndex >= sample.size()) {
                it = activeSounds.erase(it);
            } else {
                ++it;
            }
        }

        return oboe::DataCallbackResult::Continue;
    }
};

class AudioEngine {
private:
    std::shared_ptr<oboe::AudioStream> stream;
    PianoCallback callback;

public:
    void start() {
        if (stream) return;

        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Output)
                ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
                ->setSharingMode(oboe::SharingMode::Exclusive)
                ->setFormat(oboe::AudioFormat::Float)
                ->setChannelCount(oboe::ChannelCount::Stereo)
                ->setSampleRate(44100)
                ->setDataCallback(&callback);

        oboe::Result result = builder.openStream(stream);
        if (result != oboe::Result::OK) {
            LOGE("Failed to open stream: %s", oboe::convertToText(result));
            return;
        }

        result = stream->requestStart();
        if (result != oboe::Result::OK) {
            LOGE("Failed to start stream: %s", oboe::convertToText(result));
        }
    }

    void stop() {
        if (stream) {
            stream->close();
            stream.reset();
        }
    }

    void loadSound(int soundId, const float* data, int numSamples) {
        std::lock_guard<std::mutex> lock(callback.soundMutex);
        callback.samples[soundId] = SoundSample{ std::vector<float>(data, data + numSamples) };
    }

    void playSound(int soundId) {
        std::lock_guard<std::mutex> lock(callback.soundMutex);
        callback.activeSounds.push_back({soundId, 0});
    }

    void loadWavFromMemory(int soundId, const void* data, size_t size) {
        unsigned int channels;
        unsigned int sampleRate;
        drwav_uint64 totalPCMFrameCount;

        // Decode WAV in one call directly to float samples
        float* pSampleData = drwav_open_memory_and_read_pcm_frames_f32(
                data, size, &channels, &sampleRate, &totalPCMFrameCount, NULL);

        if (pSampleData) {
            std::lock_guard<std::mutex> lock(callback.soundMutex);
            callback.samples[soundId] = SoundSample{
                    std::vector<float>(pSampleData, pSampleData + (totalPCMFrameCount * channels))
            };
            drwav_free(pSampleData, NULL);
        }
    }
};

static AudioEngine engine;

// --- JNI Bridges ---
extern "C" JNIEXPORT void JNICALL
Java_com_escape99_minimalpiano_MainActivity_startEngine(JNIEnv *env, jobject /* this */) {
    engine.start();
}

extern "C" JNIEXPORT void JNICALL
Java_com_escape99_minimalpiano_MainActivity_stopEngine(JNIEnv *env, jobject /* this */) {
    engine.stop();
}

extern "C" JNIEXPORT void JNICALL
Java_com_escape99_minimalpiano_MainActivity_loadSound(JNIEnv *env, jobject, jint soundId, jbyteArray wavBytes) {
    jsize len = env->GetArrayLength(wavBytes);
    jbyte* buffer = env->GetByteArrayElements(wavBytes, nullptr);
    engine.loadWavFromMemory(soundId, buffer, len);
    env->ReleaseByteArrayElements(wavBytes, buffer, JNI_ABORT);
}

extern "C" JNIEXPORT void JNICALL
Java_com_escape99_minimalpiano_MainActivity_playSound(JNIEnv *env, jobject /* this */, jint soundId) {
    engine.playSound(soundId);
}