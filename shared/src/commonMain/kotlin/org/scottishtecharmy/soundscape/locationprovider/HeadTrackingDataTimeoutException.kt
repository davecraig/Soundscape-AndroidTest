package org.scottishtecharmy.soundscape.locationprovider

/**
 * Thrown when a connected head-tracking sensor stops delivering samples.
 *
 * Exists because "connected but silent" was otherwise indistinguishable from a healthy
 * session. Both BLE providers run their session inside a `try` whose `catch` is the only
 * thing that rescans and reconnects, so a session that never throws never retries: the
 * loop stays suspended inside `runSession`, `HeadTrackingStatus` still reads `Connected`,
 * and head tracking is silently dead for the rest of the app's life.
 *
 * Observed with Bose Frames left still on a desk - audio kept routing (that is classic
 * Bluetooth, a different transport) while the AR sensor went quiet and never came back,
 * even once the glasses were picked up and moved with the app in the foreground.
 *
 * Throwing this is what recovers it. The reconnect also re-runs the per-session sensor
 * configuration write, which covers the case where the sensor is still connected but has
 * dropped the configuration rather than the link.
 */
class HeadTrackingDataTimeoutException(
    timeoutMillis: Long,
) : Exception("No sensor data for ${timeoutMillis}ms")
