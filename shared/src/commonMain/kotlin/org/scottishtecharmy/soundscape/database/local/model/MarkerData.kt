package org.scottishtecharmy.soundscape.database.local.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.scottishtecharmy.soundscape.geoengine.utils.distance
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt

/** [MarkerEntity.source] for a waypoint derived from a recorded journey rather than saved by hand. */
const val MARKER_SOURCE_JOURNEY = "journey"

@Entity(
    tableName = "markers",
    indices = [Index("latitude", "longitude", name = "location_index")]
)
class MarkerEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "marker_id")
    val markerId: Long = 0L,

    val name: String,
    val longitude: Double,
    val latitude: Double,
    val fullAddress: String = "",

    /**
     * Where this marker came from, or null for one the user saved themselves.
     *
     * Only [MARKER_SOURCE_JOURNEY] is used today, and it means "this is route furniture, not one of
     * the user's places". A journey can leave thirty junctions behind it; announced as markers -
     * they carry a 50m trigger range - they would be called out every time the user passed them
     * ever after, and would bury the handful of places they actually chose to save. See
     * RouteDao.getUserMarkersFlow, which is what everything reading the user's markers uses.
     */
    val source: String? = null,

    /**
     * The instruction to give at this waypoint when the route is played backwards, or null when
     * there isn't one.
     *
     * A waypoint recorded at a turn is named after the junction, which reads the same whichever way
     * the route is walked; what flips is the instruction, held in [fullAddress]. Both wordings are
     * worked out when the journey is recorded, while the turn angle is still to hand, so nothing is
     * derived at playback. Null for every marker that isn't a recorded turn, and for anything
     * imported or shared, which then reads the same in both directions as it does today.
     */
    val reverseDirection: String? = null,
) {
    fun getLngLatAlt(): LngLatAlt {
        return LngLatAlt(longitude, latitude)
    }

    /**
     * The instruction to give on arriving here, or null when there isn't one to give.
     *
     * Only a recorded waypoint has one. On a marker the user saved, [fullAddress] holds the street
     * address instead - a route has never read that out, and reading it only when walked one way
     * would make the two directions differ.
     */
    fun instructionFor(reversed: Boolean): String? {
        if (source != MARKER_SOURCE_JOURNEY) return null
        return if (reversed) reverseDirection else fullAddress.takeIf { it.isNotBlank() }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MarkerEntity) return false
        val dist = distance(latitude, longitude, other.latitude, other.longitude)
        return (name == other.name) && (fullAddress == other.fullAddress) && (dist < 1.0)
    }
}
