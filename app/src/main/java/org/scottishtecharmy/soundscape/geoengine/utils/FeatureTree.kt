package org.scottishtecharmy.soundscape.geoengine.utils

import com.github.davidmoten.rtree2.Entry
import com.github.davidmoten.rtree2.Iterables
import com.github.davidmoten.rtree2.RTree
import com.github.davidmoten.rtree2.geometry.Geometries
import com.github.davidmoten.rtree2.geometry.Geometry
import com.github.davidmoten.rtree2.geometry.Line
import com.github.davidmoten.rtree2.geometry.Point
import com.github.davidmoten.rtree2.geometry.Rectangle
import com.github.davidmoten.rtree2.internal.EntryDefault
import org.scottishtecharmy.soundscape.geoengine.mvt.data.MvtLineString
import org.scottishtecharmy.soundscape.geoengine.mvt.data.MvtMultiPoint
import org.scottishtecharmy.soundscape.geoengine.mvt.data.MvtMultiPolygon
import org.scottishtecharmy.soundscape.geoengine.mvt.data.MvtPoint
import org.scottishtecharmy.soundscape.geoengine.mvt.data.MvtPolygon
import org.scottishtecharmy.soundscape.geoengine.mvt.data.SpatialFeature
import org.scottishtecharmy.soundscape.geoengine.types.FeatureList
import org.scottishtecharmy.soundscape.geoengine.types.emptyFeatureList
import org.scottishtecharmy.soundscape.geoengine.utils.rulers.Ruler
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LineString
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import kotlin.math.PI
import kotlin.math.cos

/**
 * FeatureTree is a class which stores FeatureLists within an rtree which gives us faster
 * spatial searching. The APIs all return either FeatureLists or SpatialFeatures.
 */

class FeatureTree(featureList: FeatureList?) {

    var tree: RTree<SpatialFeature, Geometry?>? = null

    init {
        if(featureList != null) {
            tree = createRtree(featureList)
        }
    }

    private fun createRtreeFromEntries(entries: List<Entry<SpatialFeature, Geometry?>>): RTree<SpatialFeature, Geometry?> {
        val tree: RTree<SpatialFeature, Geometry?> = RTree.create(entries)
        return tree
    }

    private fun createRtree(featureList: FeatureList): RTree<SpatialFeature, Geometry?> {
        val rtreeList = mutableListOf<Entry<SpatialFeature, Geometry?>>()
        for (feature in featureList) {
            when (val geom = feature.mvtGeometry) {
                is MvtPoint -> {
                    rtreeList.add(
                        EntryDefault(
                            feature,
                            Geometries.pointGeographic(
                                geom.coordinate.longitude,
                                geom.coordinate.latitude
                            )
                        )
                    )
                }

                is MvtMultiPoint -> {
                    for(location in geom.coordinates) {
                        rtreeList.add(
                            EntryDefault(
                                feature,
                                Geometries.pointGeographic(
                                    location.longitude,
                                    location.latitude
                                )
                            )
                        )
                    }
                }

                is MvtLineString -> {
                    // We add each line segment as a separate entry into the rtree for more precise
                    // searching, however this does mean that searches in the tree will return
                    // duplicates of the same Feature and so these must be de-duplicated when
                    // retrieving the data from the tree.
                    for ((index, point) in geom.coordinates.withIndex()) {
                        if (index < (geom.coordinates.size - 1)) {
                            rtreeList.add(
                                EntryDefault(
                                    feature,
                                    Geometries.line(
                                        point.longitude,
                                        point.latitude,
                                        geom.coordinates[index + 1].longitude,
                                        geom.coordinates[index + 1].latitude
                                    )
                                )
                            )
                        }
                    }
                }

                is MvtPolygon -> {
                    // The rtree only supports points, lines, rectangles and circles. Let's create
                    // a bounding box for the polygon and use that in rtree. We can then validate
                    // the search results in a second pass.
                    val box = getBoundingBoxOfMvtPolygon(geom)
                    rtreeList.add(
                        EntryDefault(
                            feature,
                            Geometries.rectangleGeographic(
                                box.westLongitude, box.southLatitude,
                                box.eastLongitude, box.northLatitude
                            )
                        )
                    )
                }

                is MvtMultiPolygon -> {
                    // The rtree only supports points, lines, rectangles and circles. Let's create
                    // a bounding box for the polygon and use that in rtree. We can then validate
                    // the search results in a second pass.
                    val boxes = getBoundingBoxesOfMvtMultiPolygon(geom)
                    for(box in boxes) {
                        rtreeList.add(
                            EntryDefault(
                                feature,
                                Geometries.rectangleGeographic(
                                    box.westLongitude, box.southLatitude,
                                    box.eastLongitude, box.northLatitude
                                )
                            )
                        )
                    }
                }

                else -> {
                    // Unknown geometry type - skip
                }
            }
        }
        return createRtreeFromEntries(rtreeList)
    }

    private fun createBoundingSquare(center: LngLatAlt, radius: Double): Rectangle {

        // Create a bounding square for our search
        val latOffset = (radius) / EARTH_RADIUS_METERS * (180 / PI)
        val lngOffset = (radius) / (EARTH_RADIUS_METERS * cos(Math.toRadians(center.latitude))) * (180 / PI)
        val rect = Geometries.rectangle(
            center.longitude - lngOffset,
            center.latitude - latOffset,
            center.longitude + lngOffset,
            center.latitude + latOffset
        )
        return rect
    }

    private fun distanceToEntry(entry: Entry<SpatialFeature, Geometry?>,
                                from: LngLatAlt,
                                ruler: Ruler
    ) : Double {
        when (val p = entry.geometry()) {
            is Point -> {
                val position = LngLatAlt(p.x(), p.y())
                return ruler.distance(from, position)
            }

            is Line -> {
                return ruler.pointToSegmentDistance(
                    from,
                    LngLatAlt(p.x1(), p.y1()),
                    LngLatAlt(p.x2(), p.y2())
                )
            }

            is Rectangle -> {
                val geom = entry.value().mvtGeometry
                when (geom) {
                    is MvtPolygon -> {
                        return distanceToMvtPolygon(from, geom, ruler)
                    }
                    is MvtMultiPolygon -> {
                        return distanceToMvtMultiPolygon(from, geom, ruler)
                    }
                    else -> {}
                }
            }
        }
        return Double.POSITIVE_INFINITY
    }

    private fun entryWithinDistance(entry: Entry<SpatialFeature, Geometry?>,
                                    distance: Double,
                                    from: LngLatAlt,
                                    ruler: Ruler
    )  : Boolean {
        return distanceToEntry(entry, from, ruler) < distance
    }

    private fun searchWithinDistance(
        lonLat: Point,
        distance: Double,
        ruler: Ruler
    ): MutableIterable<Entry<SpatialFeature, Geometry?>>? {

        // This should not be called if the tree is null
        assert(tree != null)

        // First we need to calculate an enclosing lat long rectangle for this
        // distance then we refine on the exact distance
        val from = LngLatAlt(lonLat.x(), lonLat.y())
        val bounds: Rectangle = createBoundingSquare(from, distance)

        return Iterables.filter(tree!!.search(bounds))
        { entry ->
            entryWithinDistance(entry, distance, from, ruler)
        }
    }

    private fun nearestWithinDistance(
        lonLat: Point,
        distance: Double,
        maxCount: Int,
        ruler: Ruler
    ): MutableIterable<Entry<SpatialFeature, Geometry?>>? {

        // This should not be called if the tree is null
        assert(tree != null)

        val from = LngLatAlt(lonLat.x(), lonLat.y())
        val treeResults =
            if(maxCount < 1)
                tree!!.search(lonLat, distance)             // Find all
            else
                tree!!.nearest(lonLat, distance, maxCount)  // Find up to maxCount

        return Iterables.filter(treeResults)
        { entry ->
            entryWithinDistance(entry, distance, from, ruler)
        }
    }

    private fun createBoundingSquareContainingTriangle(triangle: Triangle) : Rectangle {

        // Create a bounding rectangle that contains the triangle
        val minLatitude = minOf(triangle.origin.latitude, triangle.left.latitude, triangle.right.latitude)
        val maxLatitude = maxOf(triangle.origin.latitude, triangle.left.latitude, triangle.right.latitude)
        val minLongitude = minOf(triangle.origin.longitude, triangle.left.longitude, triangle.right.longitude)
        val maxLongitude = maxOf(triangle.origin.longitude, triangle.left.longitude, triangle.right.longitude)

        return Geometries.rectangle(minLongitude,minLatitude,maxLongitude,maxLatitude)
    }

    private fun entryWithinTriangle(entry: Entry<SpatialFeature, Geometry?>,
                                    triangle: Triangle): Boolean {

        when (val p = entry.geometry()) {
            is Point -> {
                val testPoint = LngLatAlt(p.x(), p.y())
                // Create a closed polygon
                val polygon = createPolygonFromTriangle(triangle)
                return polygonContainsCoordinates(testPoint, polygon)
            }

            is Line -> {
                return lineSegmentPassesWithinTriangle(LngLatAlt(p.x1(), p.y1()),
                    LngLatAlt(p.x2(), p.y2()),
                    triangle)
            }

            is Rectangle -> {
                // The rtree entry is a bounding box for a more complex polygon. We return true if
                // any of the polygon coordinates are within the FOV triangle or if any of the
                // FOV triangle coordinates are within the polygon.
                val feature = entry.value()
                val geom = feature.mvtGeometry
                when (geom) {
                    is MvtPolygon -> {
                        return testMvtPolygonInFov(geom, triangle)
                    }
                    is MvtMultiPolygon -> {
                        return testMvtMultiPolygonInFov(geom, triangle)
                    }
                    else -> {}
                }
                return false
            }
            else -> {
                println("Unknown geometry type: $p")
            }
        }
        return false
    }

    private fun searchWithinTriangle(
        triangle: Triangle
    ): MutableIterable<Entry<SpatialFeature, Geometry?>>? {

        // This should not be called if the tree is null
        assert(tree != null)

        // First we need to calculate an enclosing lat long rectangle for this triangle
        // then we refine on the exact contents
        val bounds: Rectangle = createBoundingSquareContainingTriangle(triangle)

        return Iterables.filter(tree!!.search(bounds))
        { entry ->
            entryWithinTriangle(entry, triangle)
        }
    }

    private fun nearestWithinTriangle(
        triangle: Triangle,
        maxCount: Int,
        ruler: Ruler
    ): FeatureList {

        val results = emptyFeatureList()

        if(tree != null) {
            // First find the features within the triangle
            val resultsWithinTriangle = searchWithinTriangle(triangle) ?: return results

            // Sort the results based on the distance. The sortedBy algorithm calls the distance
            // calculation every time it compares values in the list and as a result is fairly
            // inefficient. We could either:
            //
            //  1. Cache calculations - this could be done inside the Feature, and as we are single
            //     threaded when using FeatureTree this will be okay, though slightly ugly.
            //  2. Calculate the distances in advance and sort those instead. We'll take this approach.
            //
            data class EntryWithDistance(val entry: Entry<SpatialFeature, Geometry?>, val distance: Double)

            val unsortedList = mutableListOf<EntryWithDistance>()
            for (entry in resultsWithinTriangle) {
                unsortedList.add(EntryWithDistance(entry, distanceToEntry(entry, triangle.origin, ruler)))
            }
            val sortedList = unsortedList.sortedBy { entryWithinDistance ->
                entryWithinDistance.distance
            }

            // Move the sorted items into a FeatureList to return, breaking out if we reach the
            // maximum number requested.
            for ((index, item) in sortedList.withIndex()) {
                if (index >= maxCount)
                    break
                results.add(item.entry.value())
            }
        }

        return results
    }

    /**
     * getAllCollection returns a FeatureList containing all of the features from
     * within the rtree.
     * @result FeatureList containing all of the features from the rtree
     */
    fun getAllCollection(): FeatureList {
        val featureList = emptyFeatureList()
        if(tree != null) {
            val deduplicationSet = mutableSetOf<SpatialFeature>()
            val entries = tree!!.entries()
            for (feature in entries) {
                if(!deduplicationSet.contains(feature.value())) {
                    featureList.add(feature.value())
                    deduplicationSet.add(feature.value())
                }
            }
        }
        return featureList
    }

    /**
     * getNearbyCollection returns a FeatureList containing all of the features
     * within distance of the location provided
     * @param location Location to calculate distance from
     * @param distance Maximum distance to return results for
     * @result FeatureList containing all of the features from the rtree that are within
     * distance of the location
     */
    fun getNearbyCollection(location: LngLatAlt, distance: Double, ruler: Ruler): FeatureList {
        val featureList = emptyFeatureList()
        if(tree != null) {
            // Return only the entries within distance of our location
            val distanceResults = Iterables.toList(searchWithinDistance(
                Geometries.pointGeographic(location.longitude, location.latitude),
                distance,
                ruler)
            )

            val deduplicationSet = mutableSetOf<SpatialFeature>()
            for (feature in distanceResults) {
                if(!deduplicationSet.contains(feature.value())) {
                    featureList.add(feature.value())
                    deduplicationSet.add(feature.value())
                }
            }
        }
        return featureList
    }

    /**
     * getNearestCollection returns a FeatureList containing the nearest members of the rtree
     * that are also within distance.
     * @param location Location to calculate distance from
     * @param distance Maximum distance to return results for
     * @param maxCount Maximum number of results to return
     * @param initialCollection A FeatureList to add to the results. This must be sorted by
     * distance and is useful when combining the results of searches in two separate trees e.g. POI
     * and markers.
     */

    fun getNearestCollection(location: LngLatAlt,
                             distance: Double,
                             maxCount: Int,
                             ruler: Ruler,
                             initialCollection: FeatureList? = null): FeatureList {
        val featureList = emptyFeatureList()
        if(tree != null) {
            val distanceResults = Iterables.toList(nearestWithinDistance(
                Geometries.pointGeographic(location.longitude, location.latitude),
                distance,
                -1,
                ruler)
            )

            // Deduplicate returned entries and add them to a list ready to sort by distance
            data class EntryWithDistance(val entry: Entry<SpatialFeature, Geometry?>, val distance: Double)
            val unsortedList = distanceResults
                .map { entry -> EntryWithDistance(entry, distanceToEntry(entry, location, ruler)) }
                .groupBy { it.entry.value() }
                .map { (_, entries) -> entries.minBy { it.distance } }

            // Sort the list
            val sortedList = unsortedList.sortedBy { entryWithinDistance->
                entryWithinDistance.distance
            }

            // Merge the sorted initial list into the sorted list that we just generated
            val initialItemIterator = initialCollection?.iterator()
            val newItemIterator = sortedList.iterator()

            var initialItem: SpatialFeature? = if (initialItemIterator?.hasNext() == true) initialItemIterator.next() else null
            var newItem: EntryWithDistance? = if (newItemIterator.hasNext()) newItemIterator.next() else null

            while((initialItem != null) or (newItem != null)) {
                if(featureList.size > maxCount) break
                if (initialItem != null) {
                    var addInitial = false
                    if (newItem == null) addInitial = true
                    if(!addInitial) addInitial = getDistanceToSpatialFeature(location, initialItem, ruler).distance < newItem!!.distance
                    if(addInitial) {
                        featureList.add(initialItem)
                        initialItem = if (initialItemIterator?.hasNext() == true) initialItemIterator.next() else null
                        continue
                    }
                }
                featureList.add(newItem!!.entry.value())
                newItem = if (newItemIterator.hasNext()) newItemIterator.next() else null
            }
        }
        return featureList
    }

    /**
     * getNearestFeature returns a SpatialFeature that is the nearest member of the rtree
     * that is also within distance.
     * @param location Location to calculate distance from
     * @param distance Maximum distance to return results for
     * @result SpatialFeature that is the nearest member of the rtree that is also within distance
     */
    fun getNearestFeature(location: LngLatAlt,
                          ruler: Ruler,
                          distance: Double = Double.POSITIVE_INFINITY): SpatialFeature? {
        if(tree != null) {
            val distanceResults = Iterables.toList(
                nearestWithinDistance(
                    Geometries.pointGeographic(location.longitude, location.latitude),
                    distance,
                    1,
                    ruler
                )
            )

            for (feature in distanceResults) return feature.value()
        }

        return null
    }

    /**
     * getNearestCollectionWithinTriangle returns a FeatureList containing the nearest members
     * of the rtree within the triangle provided
     * @param triangle Triangle to search within
     * @param maxCount Maximum number of results to return
     * @result FeatureList containing the nearest members of the rtree within the triangle
     * provided
     */
    fun getNearestCollectionWithinTriangle(triangle: Triangle,
                                           maxCount: Int,
                                           ruler: Ruler
    ): FeatureList {

        if(tree == null) return emptyFeatureList()

        return nearestWithinTriangle(triangle, maxCount, ruler)
    }

    /**
     * getAllWithinTriangle returns a FeatureList containing all of the features with the triangle
     * @param triangle Triangle to search within
     * @result FeatureList containing all of the features with the triangle
     */
    fun getAllWithinTriangle(triangle: Triangle): FeatureList {
        val featureList = emptyFeatureList()
        if(tree != null) {
            val results = Iterables.toList(searchWithinTriangle(triangle))

            val deduplicationSet = mutableSetOf<SpatialFeature>()
            for (feature in results) {
                if(!deduplicationSet.contains(feature.value())) {
                    featureList.add(feature.value())
                    deduplicationSet.add(feature.value())
                }
            }
        }
        return featureList
    }

    /** getNearestFeatureWithinTriangle returns a SpatialFeature that is the nearest member of the rtree
     * within the triangle provided
     * @param triangle Triangle to search within
     * @result SpatialFeature that is the nearest member of the rtree within the triangle provided
     */
    fun getNearestFeatureWithinTriangle(triangle: Triangle, ruler: Ruler): SpatialFeature? {

        if (tree == null)
            return null

        val results = nearestWithinTriangle(triangle, 1, ruler)
        if(results.isEmpty()) return null

        return results[0]
    }

    /**
     * For a given point, getContainingPolygons returns any features which contain it.
     * @param location Point to search for
     * @result FeatureList containing all features which contain the point
     */
    fun getContainingPolygons(location: LngLatAlt): FeatureList {

        if (tree == null)
            return emptyFeatureList()

        val possiblePolygons = Iterables.filter(
            tree!!.search(
                Geometries.pointGeographic(
                    location.longitude,
                    location.latitude
                )
            )
        ) { entry ->
            // We can get here if the point is in a line, so we need to double check it's a polygon
            val geom = entry.value().mvtGeometry
            when (geom) {
                is MvtPolygon -> mvtPolygonContainsCoordinates(location, geom)
                is MvtMultiPolygon -> mvtMultiPolygonContainsCoordinates(location, geom)
                else -> false
            }
        }

        val result = emptyFeatureList()
        for(feature in possiblePolygons) {
            result.add(feature.value())
        }
        return result
    }



    private fun entryNearLine(entry: Entry<SpatialFeature, Geometry?>,
                              p1: LngLatAlt,
                              p2: LngLatAlt,
                              distance: Double,
                              ruler: Ruler): Boolean {

        when (val p = entry.geometry()) {
            is Point -> {
                val testPoint = LngLatAlt(p.x(), p.y())
                return ruler.pointToSegmentDistance(testPoint, p1, p2) < distance
            }

            is Line,
            is Rectangle -> {
                val geom = entry.value().mvtGeometry
                when (geom) {
                    is MvtPolygon -> {
                        for (point in geom.exteriorRing) {
                            if (ruler.pointToSegmentDistance(point, p1, p2) < distance)
                                return true
                        }
                        return false
                    }

                    is MvtMultiPolygon -> {
                        for (polygon in geom.polygons) {
                            for (point in polygon.exteriorRing) {
                                if (ruler.pointToSegmentDistance(point, p1, p2) < distance)
                                    return true
                            }
                        }
                        return false
                    }
                    else -> return false
                }
            }
            else -> {
                println("Unknown geometry type: $p")
            }
        }
        return false
    }

    private fun createBoundingSquareContainingLine(p1: LngLatAlt,
                                                   p2: LngLatAlt,
                                                   distance: Double
    ): Rectangle {

        // Create a bounding square for our search
        val latOffset = (distance) / EARTH_RADIUS_METERS * (180 / PI)
        val lngOffset = (distance) / (EARTH_RADIUS_METERS * cos(Math.toRadians(p1.latitude))) * (180 / PI)

        val minLat = minOf(p1.latitude, p2.latitude)
        val maxLat = maxOf(p1.latitude, p2.latitude)
        val minLng = minOf(p1.longitude, p2.longitude)
        val maxLng = maxOf(p1.longitude, p2.longitude)


        val rect = Geometries.rectangle(
            minLng - lngOffset,
            minLat - latOffset,
            maxLng + lngOffset,
            maxLat + latOffset
        )
        return rect
    }

    private fun searchNearLine(
        p1: LngLatAlt,
        p2: LngLatAlt,
        distance: Double,
        deduplicationSet: MutableSet<SpatialFeature>,
        ruler: Ruler
    ): MutableIterable<Entry<SpatialFeature, Geometry?>>? {

        // This should not be called if the tree is null
        assert(tree != null)

        // First we need to calculate an enclosing lat long rectangle for this triangle
        // then we refine on the exact contents
        val bounds: Rectangle = createBoundingSquareContainingLine(p1, p2, distance)

        return Iterables.filter(tree!!.search(bounds))
        { entry ->
            if(!deduplicationSet.contains(entry.value()))
                entryNearLine(entry, p1, p2, distance, ruler)
            else
                false
        }
    }

    /**
     * getNearbyLine returns a FeatureList containing all of the features near the line
     * @param line LineString to search near to
     * @param distance How far from LineString to search
     * @result FeatureList containing all of the features within distance of line
     */
    fun getNearbyLine(line: LineString, distance: Double, ruler: Ruler): FeatureList {
        val featureList = emptyFeatureList()
        if(tree != null) {

            val deduplicationSet = mutableSetOf<SpatialFeature>()

            // We search segment by segment and accumulate the results in a Set to deduplicate
            var lastPoint = LngLatAlt()
            for ((index, point) in line.coordinates.withIndex()) {
                if(index > 0) {
                    val results = Iterables.toList(
                        searchNearLine(
                            lastPoint,
                            point,
                            distance,
                            deduplicationSet,
                            ruler
                        )
                    )
                    deduplicationSet += results.map { it.value() }
                }
                lastPoint = point
            }

            for (feature in deduplicationSet) {
                featureList.add(feature)
            }
        }
        return featureList
    }
}
