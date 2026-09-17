package org.scottishtecharmy.soundscape.geoengine

import org.scottishtecharmy.soundscape.geoengine.utils.JunctionArms
import org.scottishtecharmy.soundscape.geoengine.utils.PointAhead
import org.scottishtecharmy.soundscape.geoengine.utils.nextJunctionAhead
import org.scottishtecharmy.soundscape.geoengine.utils.pointAhead
import org.scottishtecharmy.soundscape.geoengine.utils.rulers.Ruler
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt

/** Which dynamic-beacon prototype to run, or [Off] for the fixed destination beacon only. */
enum class DynamicBeaconMode(val key: String) {
    Off("off"),

    /**
     * A point [LEAD_DISTANCE_METRES] ahead of the user along the road, parking at junctions.
     *
     * Measured along the road geometry rather than as a bearing, so the beacon goes round bends -
     * which is the thing a junction-to-junction beacon cannot convey, and the reason this mode
     * exists alongside [Junction].
     */
    Lead("lead"),

    /** The next junction ahead, hopping to the following one once the user passes through. */
    Junction("junction");

    companion object {
        val default = Off
        val keys = entries.map { it.key }

        fun fromPreference(value: String?): DynamicBeaconMode =
            entries.firstOrNull { it.key == value } ?: default
    }
}

/**
 * Where a beacon that runs ahead of the user along the road they're on should be.
 *
 * Unlike the destination beacon, this one has no destination: it is re-derived from the user's
 * map-matched position on every location update, and moved rather than recreated (see
 * AudioEngine.updateBeaconLocation - recreating it would restart the audio, once a second).
 *
 * **Parking at a junction is emergent, not a state.** Each update re-walks the Way graph from
 * wherever the user now is, so while a junction sits inside the lead distance the walk keeps
 * stopping at it and the beacon sits still as the user closes in. Once the user is map-matched
 * onto a Way beyond the junction, the walk simply starts from there and runs on up the new road.
 * There is no "parked" flag and no logic that works out which arm the user took - MapMatchFilter
 * has already answered that by the time this runs. Resist adding one.
 *
 * **Nothing but [LngLatAlt] survives between updates.** GridState regenerates the Way graph as the
 * user moves, so a retained Way, Intersection or WayCursor goes quietly stale and starts
 * describing a road that is no longer in the grid.
 */
class DynamicBeacon(
    /**
     * Read through lambdas on each update rather than captured once, so that flipping the debug
     * settings takes effect on the next location update rather than needing a restart - the same
     * arrangement AutoCallout uses for its POI ranking prototype.
     */
    private val mode: () -> DynamicBeaconMode,
    private val junctionArms: () -> JunctionArms = { JunctionArms.default },
) {
    /** Where the beacon should be, or null when it shouldn't be playing at all. */
    var target: LngLatAlt? = null
        private set

    /**
     * Whether [target] is a junction the walk stopped short at, rather than a point out on the
     * open road.
     *
     * In [DynamicBeaconMode.Lead] this is the difference between the beacon running ahead and the
     * beacon waiting for the user at a decision point, which is the behaviour being judged - so it
     * is worth reporting, and eventually worth making audible.
     */
    var targetAtJunction: Boolean = false
        private set

    private var previousLocation: LngLatAlt? = null

    // The direction latch - see confirmedDirection.
    private var acceptedForwards: Boolean? = null
    private var pendingForwards: Boolean? = null
    private var pendingForwardsCount = 0

    /**
     * Recomputes [target] from the user's current position.
     *
     * @return true when [target] changed and the audio engine needs telling.
     */
    fun update(userGeometry: UserGeometry): Boolean {
        val mode = mode()
        if (mode == DynamicBeaconMode.Off) {
            previousLocation = null
            return clear()
        }

        // A destination beacon wins. The service has a single beacon handle, so the two can't play
        // at once, and a user who has deliberately set a destination doesn't want it dragged off
        // up the road. Clearing rather than holding, so that when the destination beacon is
        // stopped this one starts again from wherever the user now is.
        if (userGeometry.currentBeacon != null) {
            previousLocation = null
            return clear()
        }

        val fallback = fallbackHeading(userGeometry)

        // No map match means the user is off the network, the grid is still loading, or the fix
        // wobbled. Hold: a second-stale beacon still points at where the road went, which is more
        // use than silence and much more use than a jump to the raw GPS position.
        val way = userGeometry.mapMatchedWay ?: return false
        val cursor = userGeometry.cursorOn(way, fallback) ?: return false
        // Direction unknown - stationary, with no travel heading and no movement to derive one
        // from. Deliberately not the both-directions walk the feature queries do: a beacon has to
        // be somewhere, and where it already is beats a coin toss.
        val forwards = cursor.forwards ?: return false

        val arms = junctionArms()
        val confirmed = cursor.copy(forwards = confirmedDirection(forwards))
        val ahead = when (mode) {
            DynamicBeaconMode.Lead ->
                pointAhead(confirmed, LEAD_DISTANCE_METRES, userGeometry.ruler, junctionArms = arms)

            DynamicBeaconMode.Junction ->
                nextJunctionAhead(
                    confirmed,
                    JUNCTION_LOOKAHEAD_METRES,
                    userGeometry.ruler,
                    junctionArms = arms,
                )
            // Handled above, but the compiler wants the branch.
            DynamicBeaconMode.Off -> null
        } ?: return false

        // Set whether or not the position itself changed: the beacon can sit on a junction for
        // many updates, and it is at a junction throughout, not just on the update it arrived.
        targetAtJunction = (ahead is PointAhead.AtJunction)
        return propose(ahead.location, userGeometry.ruler)
    }

    /** Drops the target, for the mode being switched off. */
    fun clear(): Boolean {
        targetAtJunction = false
        acceptedForwards = null
        pendingForwards = null
        pendingForwardsCount = 0
        if (target == null) return false
        target = null
        return true
    }

    /**
     * The direction to walk in, latched against a single update's worth of flapping.
     *
     * The hysteresis belongs here rather than on the target position. The two things that actually
     * flap are the travel heading at walking pace and the map match between a road and the
     * pavement beside it, and both of them show up as the cursor's direction reversing - whereas
     * the target moving a long way in one update is usually something real and wanted: the walk
     * reaching a junction and the beacon parking on it, or the hop to the next junction once the
     * user is through. Gating on distance moved would delay every one of those by an update, and
     * still wouldn't catch a reversal that happened to land the target nearby.
     *
     * So a reversal has to be said twice before it is believed, and everything else takes effect
     * at once.
     */
    private fun confirmedDirection(forwards: Boolean): Boolean {
        val accepted = acceptedForwards
        if (accepted == null) {
            acceptedForwards = forwards
            return forwards
        }
        if (forwards == accepted) {
            pendingForwards = null
            pendingForwardsCount = 0
            return accepted
        }

        if (forwards == pendingForwards) {
            pendingForwardsCount++
            if (pendingForwardsCount >= DIRECTION_CONFIRMATIONS) {
                acceptedForwards = forwards
                pendingForwards = null
                pendingForwardsCount = 0
                return forwards
            }
        } else {
            pendingForwards = forwards
            pendingForwardsCount = 1
        }
        return accepted
    }

    /**
     * Takes the new target position unless it is too small a move to be worth the re-spatialisation
     * - which at walking pace is most of the GPS jitter, the lead target only advancing about 1.4m
     * per fix.
     */
    private fun propose(next: LngLatAlt, ruler: Ruler): Boolean {
        val current = target
        if ((current != null) && (ruler.distance(current, next) < MIN_MOVE_METRES)) return false
        target = next
        return true
    }

    /**
     * A heading derived from where the user has actually moved since the last update, for when the
     * GPS fix carries no usable bearing.
     *
     * The same thing AutoCallout's sweep heading does for its own cursors; kept separate because
     * that one is tied up with AutoCallout's callout bookkeeping.
     */
    private fun fallbackHeading(userGeometry: UserGeometry): Double? {
        val previous = previousLocation
        previousLocation = userGeometry.location
        if (previous == null) return null
        if (userGeometry.ruler.distance(previous, userGeometry.location) < MIN_MOVE_METRES) {
            return null
        }
        return userGeometry.ruler.bearing(previous, userGeometry.location)
    }

    companion object {
        /** How far ahead [DynamicBeaconMode.Lead] keeps the beacon. */
        const val LEAD_DISTANCE_METRES = 25.0

        /** How far [DynamicBeaconMode.Junction] will look for a junction before giving up. */
        const val JUNCTION_LOOKAHEAD_METRES = 250.0

        /**
         * Below this the target hasn't really moved. At walking pace the lead target advances
         * about 1.4m per fix, so this suppresses jitter without freezing the normal case.
         */
        const val MIN_MOVE_METRES = 2.0

        /** How many consecutive updates have to agree before a reversal is believed. */
        const val DIRECTION_CONFIRMATIONS = 2
    }
}
