#pragma once

#include <atomic>
#include <utility>

#include "AudioBeaconBuffer.h"
#include "AudioEngine.h"

namespace soundscape {

    enum {
        NEAR_INDEX = 0,
        FAR_INDEX = 1
    };
    const BeaconDescriptor msc_ProximityDescriptor =
            {
                    "Proximity",
                    36,
                    {
                            {"file:///android_asset/Sounds/Proximity_Close.wav", 0}, // NEAR_INDEX
                            {"file:///android_asset/Sounds/Proximity_Far.wav", 0},   // FAR_INDEX
                    }
            };

    class PositionedAudio {
    public:
        PositionedAudio(AudioEngine *engine, PositioningMode mode, bool dimmable = false,
                        std::string utterance_id = "");

        virtual ~PositionedAudio();

        void UpdateGeometry(double listenerLatitude, double listenerLongitude,
                            double heading, double latitude, double longitude,
                            double proximityNear);

        // CreateAudioSource returns whether or not the audio source should
        // be placed in the list of queued beacons.
        virtual bool CreateAudioSource(double degrees_off_axis,
                                       int sampleRate,
                                       int audioFormat,
                                       int channelCount,
                                       bool proximityBeacon) = 0;

        bool IsEof() { return m_Eof; }

        void Eof() { m_Eof = true; }

        void PlayNow();

        void Mute(bool mute);

        virtual bool CanStart() = 0;

        void UpdateAudioConfig(int sample_rate, int audio_format, int channel_count);

        /**
         * Moves the beacon, without disturbing the audio source playing it.
         *
         * Only ever called with m_BeaconsMutex held - see AudioEngine::UpdateBeaconLocation. The
         * coordinates are read by UpdateAzimuth/GetHeadingOffset, which run from
         * AudioEngine::UpdateGeometry under that same mutex, so no extra locking is needed here.
         * The azimuth deliberately isn't recomputed: the next UpdateGeometry is along within
         * 100ms, and the render thread is reading an atomic that only it and UpdateAzimuth touch.
         */
        void SetLocation(double latitude, double longitude) {
            m_Mode.m_Latitude = latitude;
            m_Mode.m_Longitude = longitude;
        }

        AudioEngine *m_pEngine;
        std::string m_UtteranceId;
        uint64_t m_Handle;

    protected:
        void Init(double degrees_off_axis,
                  bool proximityBeacon = false,
                  int sampleRate = 44100,
                  int audioFormat = 1,
                  int channelCount = 1);

        void RegisterWithMixer();

        double GetHeadingOffset(double heading, double latitude, double longitude) const;

        void UpdateAzimuth(double heading, double latitude, double longitude);

        PositioningMode m_Mode;

        std::atomic<bool> m_Eof;

        std::unique_ptr<BeaconAudioSource> m_pAudioSource;
        bool m_Dimmable = false;

        bool m_AudioConfigured = false;
    };

    class Beacon : public PositionedAudio {
    public:
        Beacon(AudioEngine *engine, PositioningMode mode);

    protected:
        bool CanStart() override { return true; }

        bool CreateAudioSource(double degrees_off_axis,
                               int sampleRate,
                               int audioFormat,
                               int channelCount,
                               bool proximityBeacon) final;
    };

    class BeaconWithProximity {
    public:
        BeaconWithProximity(AudioEngine *engine, PositioningMode mode, bool heading_only) :
                m_HeadingBeacon(engine, mode) {
            if (!heading_only) {
                mode.m_AudioMode = PositioningMode::PROXIMITY;
                mode.m_AudioType = PositioningMode::STANDARD;
                m_pProximityBeacon = std::make_unique<soundscape::Beacon>(engine, mode);
            }
        }

        /**
         * Moves both beacons. The proximity beacon is a separate Beacon with its own copy of the
         * PositioningMode, so missing it would leave the distance earcon measuring to the spot the
         * beacon was created at.
         */
        void SetLocation(double latitude, double longitude) {
            m_HeadingBeacon.SetLocation(latitude, longitude);
            if (m_pProximityBeacon)
                m_pProximityBeacon->SetLocation(latitude, longitude);
        }

        Beacon m_HeadingBeacon;
        std::unique_ptr<Beacon> m_pProximityBeacon;
    };


    class TextToSpeech : public PositionedAudio {
    public:
        bool CanStart() override { return m_AudioConfigured; }

        TextToSpeech(AudioEngine *engine,
                     PositioningMode mode,
                     int tts_socket,
                     std::string &utterance_id);

    protected:
        bool CreateAudioSource(double degrees_off_axis,
                               int sampleRate,
                               int audioFormat,
                               int channelCount,
                               bool proximityBeacon) final;

        int m_TtsSocket;
    };

    class Earcon : public PositionedAudio {
    public:
        Earcon(AudioEngine *engine,
               std::string asset,
               PositioningMode mode);

    protected:
        bool CanStart() override { return true; }

        bool CreateAudioSource(double degrees_off_axis,
                               int sampleRate,
                               int audioFormat,
                               int channelCount,
                               bool proximityBeacon) final;

        std::string m_Asset;
    };
}
