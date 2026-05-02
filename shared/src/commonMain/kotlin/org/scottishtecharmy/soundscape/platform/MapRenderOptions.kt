package org.scottishtecharmy.soundscape.platform

import org.maplibre.compose.map.RenderOptions

/**
 * Platform-specific RenderOptions for the MapLibre map. On Android we force a
 * TextureView so the map participates in Compose nav/transition animations
 * (the default SurfaceView is a hardware overlay that doesn't fade or slide).
 * iOS already renders into a layer that composites correctly, so it falls back
 * to RenderOptions.Standard.
 */
expect fun nativeMapRenderOptions(): RenderOptions
