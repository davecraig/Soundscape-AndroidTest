/*
Copyright 2018 Google Inc. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS-IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

#ifndef RESONANCE_AUDIO_PLATFORM_COMMON_ROOM_PROPERTIES_H_
#define RESONANCE_AUDIO_PLATFORM_COMMON_ROOM_PROPERTIES_H_

#include <cstddef>

namespace vraudio {

// Room material names for room effects.
enum MaterialName {
    kTransparent = 0,
    kAcousticCeilingTiles,
    kBrickBare,
    kBrickPainted,
    kConcreteBlockCoarse,
    kConcreteBlockPainted,
    kCurtainHeavy,
    kFiberGlassInsulation,
    kGlassThin,
    kGlassThick,
    kGrass,
    kLinoleumOnConcrete,
    kMarble,
    kMetal,
    kParquetOnConcrete,
    kPlasterRough,
    kPlasterSmooth,
    kPlywoodPanel,
    kPolishedConcreteOrTile,
    kSheetrock,
    kWaterOrIceSurface,
    kWoodCeiling,
    kWoodPanel,
    kUniform,
    kNumMaterialNames
};

// Room properties for room effects.
struct RoomProperties {
    // Position of the room center in world coordinates.
    float position[3] = {0.0f, 0.0f, 0.0f};

    // Rotation (quaternion) of the room in world coordinates (w, x, y, z).
    float rotation[4] = {1.0f, 0.0f, 0.0f, 0.0f};

    // Room dimensions in world coordinates (x, y, z).
    float dimensions[3] = {0.0f, 0.0f, 0.0f};

    // Material names for the six surfaces of the room (in the order: left, right,
    // bottom, top, front, back with respect to the room orientation).
    MaterialName material_names[6] = {
        kTransparent, kTransparent, kTransparent,
        kTransparent, kTransparent, kTransparent};

    // User defined uniform scaling factor for reflectivity. This parameter has no
    // effect when set to 1.0f.
    float reflection_scalar = 1.0f;

    // User defined reverb tail gain multiplier. This parameter has no effect when
    // set to 1.0f.
    float reverb_gain = 1.0f;

    // Adjusts the reverberation time by a positive multiplier. This parameter has
    // no effect when set to 1.0f.
    float reverb_time = 1.0f;

    // Controls the balance between the energy of the early reflections and the
    // reverb tail. This value is between 0.0f and 1.0f. 0.0f means all energy
    // goes to reverb tail, 1.0f means all energy goes to early reflections. The
    // default value is 0.5f.
    float reverb_brightness = 0.5f;
};

}  // namespace vraudio

#endif  // RESONANCE_AUDIO_PLATFORM_COMMON_ROOM_PROPERTIES_H_
