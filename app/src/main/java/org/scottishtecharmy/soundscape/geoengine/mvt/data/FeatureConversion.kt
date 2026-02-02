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
): MvtTileFeature {
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
    feature.setMvtGeometry(geometry)
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
 * Adapter that wraps an MvtFeature to implement SpatialFeature interface.
 * NOTE: Since MvtFeature now implements SpatialFeature directly, this adapter
 * is mostly redundant but kept for backward compatibility with existing code.
 */
class MvtFeatureAdapter(private val feature: MvtFeature) : SpatialFeature {
    override val osmId: Long get() = feature.osmId
    override val center: LngLatAlt get() = feature.center
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
    override val mvtGeometry: MvtGeometry get() = feature.mvtGeometry
    override val treeId: TreeId? get() = feature.treeId

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

/**
 * Helper extension to get mvtGeometry as MvtPoint.
 */
fun SpatialFeature.asPoint(): MvtPoint = mvtGeometry as MvtPoint

/**
 * Helper extension to get mvtGeometry as MvtLineString.
 */
fun SpatialFeature.asLineString(): MvtLineString = mvtGeometry as MvtLineString

/**
 * Helper extension to get mvtGeometry as MvtPolygon.
 */
fun SpatialFeature.asPolygon(): MvtPolygon = mvtGeometry as MvtPolygon

/**
 * Helper extension to convert MvtLineString to GeoJSON LineString for Ruler functions.
 */
fun MvtLineString.toLineString(): LineString {
    val ls = LineString()
    ls.coordinates = ArrayList(coordinates)
    return ls
}
