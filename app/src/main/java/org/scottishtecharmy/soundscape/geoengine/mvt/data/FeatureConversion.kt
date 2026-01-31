package org.scottishtecharmy.soundscape.geoengine.mvt.data

import org.scottishtecharmy.soundscape.geoengine.TreeId
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.MvtFeature
import org.scottishtecharmy.soundscape.geoengine.utils.SuperCategoryId
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Feature
import org.scottishtecharmy.soundscape.geojsonparser.geojson.GeoJsonObject
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LineString
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.geojsonparser.geojson.MultiLineString
import org.scottishtecharmy.soundscape.geojsonparser.geojson.MultiPoint
import org.scottishtecharmy.soundscape.geojsonparser.geojson.MultiPolygon
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Point
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Polygon

/**
 * Conversion utilities for migrating between GeoJSON-based features and native MVT features.
 * These functions enable gradual migration by allowing both representations to coexist.
 */

/**
 * Converts a GeoJSON geometry to the native MvtGeometry representation.
 */
fun GeoJsonObject.toMvtGeometry(): MvtGeometry? {
    // Note: Order matters! LineString extends MultiPoint, so check LineString first.
    return when (this) {
        is Point -> MvtPoint(coordinates)
        is LineString -> MvtLineString(coordinates)  // Must be before MultiPoint
        is MultiPoint -> MvtMultiPoint(coordinates)
        is MultiLineString -> MvtMultiLineString(
            coordinates.map { MvtLineString(it) }
        )
        is Polygon -> MvtPolygon(
            exteriorRing = coordinates.firstOrNull() ?: emptyList(),
            interiorRings = coordinates.drop(1)
        )
        is MultiPolygon -> MvtMultiPolygon(
            coordinates.map { rings ->
                MvtPolygon(
                    exteriorRing = rings.firstOrNull() ?: emptyList(),
                    interiorRings = rings.drop(1)
                )
            }
        )
        else -> null
    }
}

/**
 * Converts an MvtGeometry back to GeoJSON geometry (for backward compatibility).
 */
fun MvtGeometry.toGeoJsonGeometry(): GeoJsonObject {
    return when (this) {
        is MvtPoint -> Point(coordinate)
        is MvtMultiPoint -> MultiPoint(ArrayList(coordinates))
        is MvtLineString -> LineString(ArrayList(coordinates))
        is MvtMultiLineString -> {
            val mls = MultiLineString()
            for (line in lines) {
                mls.coordinates.add(ArrayList(line.coordinates))
            }
            mls
        }
        is MvtPolygon -> {
            val poly = Polygon(ArrayList(exteriorRing))
            for (ring in interiorRings) {
                poly.addInteriorRing(ArrayList(ring))
            }
            poly
        }
        is MvtMultiPolygon -> {
            val mp = MultiPolygon()
            for (polygon in polygons) {
                val rings = arrayListOf<ArrayList<LngLatAlt>>()
                rings.add(ArrayList(polygon.exteriorRing))
                for (interior in polygon.interiorRings) {
                    rings.add(ArrayList(interior))
                }
                mp.coordinates.add(rings)
            }
            mp
        }
    }
}

/**
 * Converts an old MvtFeature to the new MvtTileFeature.
 *
 * @param layer The MVT layer name (not stored in old MvtFeature, must be provided)
 * @param treeId Optional tree ID assignment
 */
fun MvtFeature.toMvtTileFeature(
    layer: String = "unknown",
    treeId: TreeId? = null
): MvtTileFeature? {
    val mvtGeometry = geometry.toMvtGeometry() ?: return null

    return MvtTileFeature(
        osmId = osmId,
        geometry = mvtGeometry,
        layer = layer,
        treeId = treeId,
        name = name,
        featureClass = featureClass,
        featureSubClass = featureSubClass,
        featureType = featureType,
        featureValue = featureValue,
        superCategory = superCategory,
        housenumber = housenumber,
        street = street,
        side = side,
        streetConfidence = streetConfidence,
        properties = properties?.toMap() ?: emptyMap()
    )
}

/**
 * Converts a new MvtTileFeature back to the old MvtFeature (for backward compatibility).
 */
fun MvtTileFeature.toMvtFeature(): MvtFeature {
    val feature = MvtFeature()
    feature.geometry = geometry.toGeoJsonGeometry()
    feature.osmId = osmId
    feature.name = name
    feature.featureClass = featureClass
    feature.featureSubClass = featureSubClass
    feature.featureType = featureType
    feature.featureValue = featureValue
    feature.superCategory = superCategory
    feature.housenumber = housenumber
    feature.street = street
    feature.side = side
    feature.streetConfidence = streetConfidence

    if (properties.isNotEmpty()) {
        feature.properties = HashMap(properties)
    }

    return feature
}

/**
 * Adapter that wraps an old MvtFeature to implement SpatialFeature interface.
 * This allows old code to work with the new interface during migration.
 */
class MvtFeatureAdapter(private val feature: MvtFeature) : SpatialFeature {
    override val osmId: Long get() = feature.osmId

    override val center: LngLatAlt by lazy {
        // Compute center from the GeoJSON geometry
        when (val geom = feature.geometry) {
            is Point -> geom.coordinates
            is MultiPoint -> {
                if (geom.coordinates.isEmpty()) LngLatAlt()
                else {
                    var sumLng = 0.0
                    var sumLat = 0.0
                    for (coord in geom.coordinates) {
                        sumLng += coord.longitude
                        sumLat += coord.latitude
                    }
                    LngLatAlt(sumLng / geom.coordinates.size, sumLat / geom.coordinates.size)
                }
            }
            is LineString -> {
                if (geom.coordinates.isEmpty()) LngLatAlt()
                else geom.coordinates[geom.coordinates.size / 2]
            }
            is Polygon -> {
                val exterior = geom.coordinates.firstOrNull() ?: return@lazy LngLatAlt()
                if (exterior.isEmpty()) return@lazy LngLatAlt()
                var sumLng = 0.0
                var sumLat = 0.0
                val count = if (exterior.size > 1 && exterior.first() == exterior.last()) {
                    exterior.size - 1
                } else {
                    exterior.size
                }
                for (i in 0 until count) {
                    sumLng += exterior[i].longitude
                    sumLat += exterior[i].latitude
                }
                LngLatAlt(sumLng / count, sumLat / count)
            }
            else -> LngLatAlt()
        }
    }

    override val name: String? get() = feature.name
    override val featureClass: String? get() = feature.featureClass
    override val featureSubClass: String? get() = feature.featureSubClass
    override val featureType: String? get() = feature.featureType
    override val featureValue: String? get() = feature.featureValue
    override val superCategory: SuperCategoryId get() = feature.superCategory
    override val housenumber: String? get() = feature.housenumber
    override val street: String? get() = feature.street
    override val side: Boolean? get() = feature.side
    override val streetConfidence: Boolean get() = feature.streetConfidence

    override val mvtGeometry: MvtGeometry by lazy {
        feature.geometry.toMvtGeometry() ?: MvtPoint(LngLatAlt())
    }

    override val treeId: TreeId? get() = null  // Old MvtFeature doesn't store this

    override fun getProperty(key: String): Any? {
        return when (key) {
            "name" -> feature.name
            "class" -> feature.featureClass
            "subclass" -> feature.featureSubClass
            "housenumber" -> feature.housenumber
            "street" -> feature.street
            else -> feature.properties?.get(key)
        }
    }

    override fun hasProperty(key: String): Boolean {
        return when (key) {
            "name" -> feature.name != null
            "class" -> feature.featureClass != null
            "subclass" -> feature.featureSubClass != null
            "housenumber" -> feature.housenumber != null
            "street" -> feature.street != null
            else -> feature.properties?.containsKey(key) == true
        }
    }

    /**
     * Returns the underlying MvtFeature.
     */
    fun unwrap(): MvtFeature = feature
}

/**
 * Extension function to wrap MvtFeature as SpatialFeature.
 */
fun MvtFeature.asSpatialFeature(): SpatialFeature = MvtFeatureAdapter(this)

/**
 * Extension function to wrap Feature as SpatialFeature (for non-MVT features like API results).
 */
fun Feature.asSpatialFeature(): SpatialFeature? {
    // Only works if this is actually an MvtFeature
    return (this as? MvtFeature)?.asSpatialFeature()
}
