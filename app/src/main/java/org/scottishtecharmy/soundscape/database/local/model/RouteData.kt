package org.scottishtecharmy.soundscape.database.local.model

import io.realm.kotlin.ext.backlinks
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.PrimaryKey
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import kotlin.Double.Companion.NaN

class Location : EmbeddedRealmObject {
    constructor(latitude: Double, longitude: Double) {
        coordinates.apply {
            add(longitude)
            add(latitude)
        }
    }

    // Empty constructor required by Realm
    constructor() : this(NaN, NaN)

    // Name and type required by Realm
    var coordinates: RealmList<Double> = realmListOf()

    // Name, type, and value required by Realm
    private var type: String = "Point"

    @Ignore
    var latitude: Double
        get() = coordinates[1]
        set(value) {
            coordinates[1] = value
        }

    @Ignore
    var longitude: Double
        get() = coordinates[0]
        set(value) {
            coordinates[0] = value
        }

    fun location(): LngLatAlt { return LngLatAlt(longitude, latitude) }
}

class RouteData(
    @PrimaryKey var name: String,
    var description: String,
) : RealmObject {
    constructor() : this("", "")

    var waypoints: RealmList<MarkerData> = realmListOf()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RouteData
        return !(
            (name != other.name) ||
                (description != other.description) ||
                (waypoints != other.waypoints)
        )
    }
}
