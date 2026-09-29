package org.scottishtecharmy.soundscape.locationprovider.metaglasses

/**
 * One fused orientation reading from a Meta wearable.
 *
 * Deliberately not the SDK's `MotionSample`: keeping our own type here is what
 * lets [MetaGlassesHeadTrackingProvider] and its tests live in the main source
 * set, while only the thin SDK wrapper sits in the optional `metaGlasses`
 * source set (see app/build.gradle.kts).
 *
 * The sample's own `timestampNs` is deliberately dropped. It comes from the
 * glasses' monotonic clock, which shares no epoch with the phone's - useful for
 * measuring intervals between samples, useless for correlating against a
 * compass reading or for the composite provider's "most recently sampled wins"
 * comparison. The provider timestamps each sample with the phone clock instead.
 *
 * @param fromGlasses false for samples sourced from something other than the
 *   glasses themselves - a Meta Neural Band's wrist orientation arrives on the
 *   same stream and is not a head pose.
 */
data class MetaMotionSample(
    val x: Double,
    val y: Double,
    val z: Double,
    val w: Double,
    val fromGlasses: Boolean,
)

/**
 * Source of [MetaMotionSample]s, so the provider can be driven by the real
 * Device Access Toolkit or by a fake in tests.
 */
interface MetaMotionClient {
    /**
     * Connect, stream samples, and suspend for the lifetime of the connection;
     * cancel the calling coroutine to disconnect. Throws if the session cannot
     * be established or drops - the provider retries after a backoff.
     *
     * [onConnected] fires once samples are actually flowing, not merely when a
     * device has been found.
     */
    suspend fun runSession(
        onConnected: () -> Unit,
        onSample: suspend (MetaMotionSample) -> Unit,
    )
}
