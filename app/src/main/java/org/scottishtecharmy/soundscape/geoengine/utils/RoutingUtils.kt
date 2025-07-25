package org.scottishtecharmy.soundscape.geoengine.utils

import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Intersection
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Way
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.WayEnd
import org.scottishtecharmy.soundscape.geoengine.utils.rulers.CheapRuler
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Feature
import org.scottishtecharmy.soundscape.geojsonparser.geojson.FeatureCollection
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import java.util.PriorityQueue

var dijkstraRunCount = 0

fun dijkstraOnWaysWithLoops(
    start: Intersection,
    end: Intersection,
    ruler: CheapRuler,
    maxDistance: Double = Double.MAX_VALUE
): Double {

    dijkstraRunCount++

    // Return distances, previous nodes are in the Intersection internal data
    // The priority queue is sorted by the closest node to the target, but also contains the current
    // accumulated distance to that node
    val priorityQueue = PriorityQueue<Triple<Intersection, Double, Double>>(compareBy { it.third })
    val visited = mutableSetOf<Pair<Intersection, Double>>()
    val distanceToEnd = maxDistance

    priorityQueue.add(Triple(start,0.0,0.0))
    start.dijkstraRunCount = dijkstraRunCount
    start.dijkstraDistance = 0.0
    start.dijkstraPrevious = null
    end.dijkstraRunCount = dijkstraRunCount
    end.dijkstraDistance = Double.MAX_VALUE
    end.dijkstraPrevious = null

    while (priorityQueue.isNotEmpty()) {
        val (node, currentDist) = priorityQueue.poll()!!
        if (visited.add(node to currentDist)) {
            node.members.forEach { way ->
                val weight = way.length
                val adjacent = if (node == way.intersections[WayEnd.START.id])
                    way.intersections[WayEnd.END.id]
                else
                    way.intersections[WayEnd.START.id]

                if (adjacent != null) {
                    val totalDist = currentDist + weight
                    val directDistanceToEnd = ruler.distance(adjacent.location, end.location)
                    if ((totalDist + directDistanceToEnd) < distanceToEnd) {
                        if(adjacent.dijkstraRunCount != dijkstraRunCount) {
                            // Lazy initialization of internal distance
                            adjacent.dijkstraRunCount = dijkstraRunCount
                            adjacent.dijkstraDistance = Double.MAX_VALUE
                            adjacent.dijkstraPrevious = null
                        }
                        if (totalDist < adjacent.dijkstraDistance) {

                            adjacent.dijkstraDistance = totalDist
                            adjacent.dijkstraPrevious = node
                            priorityQueue.add(Triple(adjacent, totalDist, (totalDist + directDistanceToEnd)))
                            if(adjacent == end)
                            {
                                return end.dijkstraDistance
                            }
                        }
                    }
                }
            }
        }
    }
    return end.dijkstraDistance
}

class ShortestDistanceResults(val distance: Double,
                              val startIntersection: Intersection,
                              val startWay: Way,
                              val endIntersection: Intersection,
                              val endWay: Way?) {

    fun tidy() {
        startWay.removeIntersection(startIntersection)
        endWay?.removeIntersection(endIntersection)
    }
}

/**
 * findShortestDistance gets the shortest distance between two points, or between one point and an
 * intersection. The points have to already be associated with a nearest Way and temporary start and
 * end locations are added as connected nodes in the graph.
 * @param startLocation The location of the start point
 * @param startWay The nearest matched Way to the start point
 * @param endLocation The location of the end point (not used if endIntersection is specified)
 * @param endWay The nearest matched Way to the end point (not used if endIntersection is specified)
 * @param endIntersection The intersection to end the search at
 * @param debugFeatureCollection A FeatureCollection used for debug only
 * @param maxDistance The maximum distance to search for
 */
fun findShortestDistance(
    startLocation: LngLatAlt,
    startWay: Way,
    endLocation: LngLatAlt?,
    endWay: Way?,
    endIntersection: Intersection?,
    debugFeatureCollection: FeatureCollection?,
    maxDistance: Double = Double.MAX_VALUE,
) : ShortestDistanceResults  {

    val ruler = startLocation.createCheapRuler()

    val newStartIntersection = startWay.createTemporaryIntersectionAndWays(startLocation, ruler)
    var newEndIntersection = endIntersection
    if(endIntersection == null)
        newEndIntersection = endWay!!.createTemporaryIntersectionAndWays(endLocation!!, ruler)

    val shortestDistance = dijkstraOnWaysWithLoops(
        newStartIntersection,
        newEndIntersection,
        ruler,
        maxDistance
    )

    if (debugFeatureCollection != null) {
        debugFeatureCollection.features.clear()
        val ways = getPathWays(
            newEndIntersection
        )
        for (way in ways) {
            debugFeatureCollection.addFeature(way as Feature)
        }
    }

    return ShortestDistanceResults(
        shortestDistance,
        newStartIntersection,
        startWay,
        newEndIntersection,
        endWay)
}

fun getPathWays(
    endNode: Intersection
): List<Way> {
    val ways = mutableListOf<Way>()
    var currentNode : Intersection? = endNode

    while (currentNode?.dijkstraPrevious != null) {
        val previousNode = currentNode.dijkstraPrevious
        // Add Way which connects the two nodes
        for(member in currentNode.members){
            if(
                (
                    (member.intersections[WayEnd.START.id] == currentNode) and
                    (member.intersections[WayEnd.END.id] == previousNode)
                ) or
                (
                    (member.intersections[WayEnd.END.id] == currentNode) and
                    (member.intersections[WayEnd.START.id] == previousNode)
                )
            ) {
                ways.add(member)
            }
        }
        currentNode = previousNode
    }

    return ways
}
