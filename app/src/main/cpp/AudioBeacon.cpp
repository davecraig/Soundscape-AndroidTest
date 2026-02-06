#include <string>
#include <utility>
#include <jni.h>

#include "GeoUtils.h"
#include "Trace.h"
#include "AudioBeacon.h"
#include "AudioEngine.h"
#include "fmod_dsp.h"
using namespace soundscape;

PositionedAudio::PositionedAudio(AudioEngine *engine,
                                 PositioningMode mode,
                                 bool dimmable,
                                 std::string utterance_id)
                : m_Mode(mode),
                  m_Eof(false),
                  m_Dimmable(dimmable)
{
    m_pEngine = engine;
    m_pSystem = engine->GetFmodSystem();
    m_UtteranceId =  std::move(utterance_id);
}

PositionedAudio::~PositionedAudio() {
//    TRACE("%s %p", __FUNCTION__, this);
    m_pEngine->RemoveBeacon(this);

    if (m_pResonanceSource) {
        if (m_pChannel) {
            m_pChannel->removeDSP(m_pResonanceSource);
        }
        m_pResonanceSource->release();
        m_pResonanceSource = nullptr;
    }

    if(m_pSound) {
        auto result = m_pSound->release();
        ERROR_CHECK(result);
    }

//    TRACE("%s %p done", __FUNCTION__, this);
}

void PositionedAudio::InitFmodSound() {
    FMOD_RESULT result;

    m_pAudioSource->CreateSound(m_pSystem, &m_pSound, m_Mode);
    if(!m_pSound)
        return;

    double heading, current_latitude, current_longitude;
    m_pEngine->GetListenerPosition(heading, current_latitude, current_longitude);

    // Sound is as loud as it gets when 10m away and never gits softer if the
    // distance goes above 20m
    result = m_pSound->set3DMinMaxDistance(10.0f,
                                           20.0f);
    ERROR_CHECK(result);

    {
        // Create paused sound channel using appropriate channel group
        FMOD::ChannelGroup *channelGroup;
        if(m_Dimmable)
            channelGroup = m_pEngine->GetBeaconGroup();
        else
            channelGroup = m_pEngine->GetSpeechGroup();

        result = m_pSystem->playSound(m_pSound, channelGroup, true, &m_pChannel);
        ERROR_CHECK(result);

        switch(m_Mode.m_AudioType) {
            default:
            case PositioningMode::STANDARD:
                break;
            case PositioningMode::LOCALIZED:
                if(!isnan(m_Mode.m_Latitude) && !isnan(m_Mode.m_Longitude)) {
                    // Only set the 3D position if the latitude and longitude are valid
                    FMOD_VECTOR pos =m_pEngine->TranslateToFmodVector(m_Mode.m_Longitude, m_Mode.m_Latitude);
                    FMOD_VECTOR vel = {0.0f, 0.0f, 0.0f};
                    result = m_pChannel->set3DAttributes(&pos, &vel);
                }
                ERROR_CHECK(result);
                break;
            case PositioningMode::RELATIVE: {
                // The position is relative, so use the heading
                auto radians = toRadians(m_Mode.m_Heading);
                auto pos = FMOD_VECTOR{(float)sin(radians), 0.0f, (float)cos(radians)};
                FMOD_VECTOR vel = {0.0f, 0.0f, 0.0f};
                result = m_pChannel->set3DAttributes(&pos, &vel);
                ERROR_CHECK(result);
                break;
            }
            case PositioningMode::COMPASS: {
                // Make up a position using the current position and the heading
                double lat, lon;
                getDestinationCoordinate(current_latitude, current_longitude, m_Mode.m_Heading, &lat, &lon);

                FMOD_VECTOR pos =m_pEngine->TranslateToFmodVector(lon, lat);
                FMOD_VECTOR vel = {0.0f, 0.0f, 0.0f};
                result = m_pChannel->set3DAttributes(&pos, &vel);
                ERROR_CHECK(result);
                break;
            }
        }

        // Attach a Resonance Audio Source DSP for HRTF binaural rendering (3D modes only).
        // FMOD Core API doesn't auto-propagate 3D attributes to plugin DSPs (only when using
        // Studio APIS) so we manually set FMOD_DSP_PARAMETER_3DATTRIBUTES on the Source DSP.
        if (m_Mode.m_AudioType != PositioningMode::STANDARD) {
            m_pResonanceSource = m_pEngine->CreateResonanceSource();
            if (m_pResonanceSource) {
                // Insert before the fader so the Source DSP receives raw mono audio,
                // not audio already 3D-panned by FMOD's built-in processing.
                result = m_pChannel->addDSP(FMOD_CHANNELCONTROL_DSP_TAIL, m_pResonanceSource);
                ERROR_CHECK(result);

                // Disable Resonance Audio's own distance attenuation (FMOD handles it)
                m_pResonanceSource->setParameterInt(4, 2);  // kDistanceModel = custom (none)

                // Set initial 3D attributes manually
                UpdateResonanceSource(current_latitude, current_longitude, heading);
            }
        }

        // Start the channel playing
        result = m_pChannel->setPaused(false);
        ERROR_CHECK(result);
    }
}

void PositionedAudio::Init(double degrees_off_axis,
                           bool proximityBeacon,
                           int sampleRate,
                           int audioFormat,
                           int channelCount)
{
    bool queued = CreateAudioSource(degrees_off_axis,
                                    sampleRate,
                                    audioFormat,
                                    channelCount,
                                    proximityBeacon);

    //TRACE("%s %p", __FUNCTION__, this);

    if(!queued)
        InitFmodSound();

    m_pEngine->AddBeacon(this, queued);
}

void PositionedAudio::PlayNow()
{
    InitFmodSound();
}

double PositionedAudio::GetHeadingOffset(double heading, double latitude, double longitude) const {
    // Calculate how far off axis the beacon is given this new heading

    // Calculate the beacon heading
    auto beacon_heading = bearingFromTwoPoints(m_Mode.m_Latitude, m_Mode.m_Longitude, latitude, longitude);
    auto degrees_off_axis = beacon_heading - heading;
    if (degrees_off_axis > 180)
        degrees_off_axis -= 360;
    else if (degrees_off_axis < -180)
        degrees_off_axis += 360;

    return degrees_off_axis;
}
void PositionedAudio::UpdateGeometry(double listenerLatitude, double listenerLongitude,
                                     double heading, double latitude, double longitude,
                                     double proximityNear) {
    // The beacons have two modes:
    //  1. Directional - when the beacon is further away it sounds from its direction.
    //  2. Proximity - when the beacon is close by the sound changes to be a sound based on the
    //     distance to the destination.
    BeaconAudioSource::SourceMode mode = BeaconAudioSource::DIRECTION_MODE;

    // If the beacon signals proximity as well as heading then we need to see how far we are from
    // the listener.
    if(m_Mode.m_AudioMode == PositioningMode::PROXIMITY) {
        auto d = distance(listenerLatitude, listenerLongitude, m_Mode.m_Latitude,
                          m_Mode.m_Longitude);
        if (d < proximityNear) {
            mode = BeaconAudioSource::NEAR_MODE;
        } else if (d < (2 * proximityNear)) {
            mode = BeaconAudioSource::FAR_MODE;
        } else {
            mode = BeaconAudioSource::TOO_FAR_MODE;
        }
    }
    if(isnan(heading)) {
        // If dimmable, the audio is placed behind us if there's no heading
        m_pAudioSource->UpdateGeometry(m_Dimmable ? 180.0 : 0.0, mode);
    } else {
        auto degrees_off_axis = GetHeadingOffset(heading, latitude, longitude);
        m_pAudioSource->UpdateGeometry(degrees_off_axis, mode);
    }

    // Update Resonance Audio Source DSP with current listener position
    UpdateResonanceSource(listenerLatitude, listenerLongitude, heading);
}

void PositionedAudio::UpdateResonanceSource(double listenerLatitude, double listenerLongitude,
                                             double heading) {
    if (!m_pResonanceSource || m_Mode.m_AudioType == PositioningMode::STANDARD || isnan(heading))
        return;

    FMOD_VECTOR sourcePos = {0.0f, 0.0f, 0.0f};
    FMOD_VECTOR listenerPos = m_pEngine->TranslateToFmodVector(listenerLongitude, listenerLatitude);
    auto headingRad = static_cast<float>(toRadians(heading));
    float sinH = sinf(headingRad);
    float cosH = cosf(headingRad);

    switch (m_Mode.m_AudioType) {
        case PositioningMode::LOCALIZED:
            if (!isnan(m_Mode.m_Latitude) && !isnan(m_Mode.m_Longitude)) {
                sourcePos = m_pEngine->TranslateToFmodVector(m_Mode.m_Longitude, m_Mode.m_Latitude);
            }
            break;
        case PositioningMode::RELATIVE: {
            // Source position is relative to listener's heading
            auto modeRad = static_cast<float>(toRadians(m_Mode.m_Heading));
            float totalRad = headingRad + modeRad;
            sourcePos = {listenerPos.x + sinf(totalRad), 0.0f, listenerPos.z + cosf(totalRad)};
            break;
        }
        case PositioningMode::COMPASS: {
            double lat, lon;
            getDestinationCoordinate(listenerLatitude, listenerLongitude, m_Mode.m_Heading, &lat, &lon);
            sourcePos = m_pEngine->TranslateToFmodVector(lon, lat);
            break;
        }
        default:
            return;
    }

    // Compute source position in listener's coordinate frame
    float dx = sourcePos.x - listenerPos.x;
    float dz = sourcePos.z - listenerPos.z;

    // Build the 3D attributes struct that Resonance Audio expects
    FMOD_DSP_PARAMETER_3DATTRIBUTES attrs = {};

    // Source world position
    attrs.absolute.position = sourcePos;
    attrs.absolute.forward = {0.0f, 0.0f, 1.0f};
    attrs.absolute.up = {0.0f, 1.0f, 0.0f};

    // Source position rotated into listener's reference frame
    attrs.relative.position = {cosH * dx - sinH * dz, 0.0f, sinH * dx + cosH * dz};
    attrs.relative.forward = {-sinH, 0.0f, cosH};
    attrs.relative.up = {0.0f, 1.0f, 0.0f};

    m_pResonanceSource->setParameterData(8, &attrs, sizeof(attrs));
}

void PositionedAudio::Mute(bool mute) {
    m_pChannel->setMute(mute);
}

void PositionedAudio::UpdateAudioConfig(int sample_rate, int audio_format, int channel_count)
{
    m_pAudioSource->UpdateAudioConfig(sample_rate, audio_format, channel_count);
    m_AudioConfigured = true;
}
