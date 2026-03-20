package org.scottishtecharmy.soundscape.geoengine.callouts

import android.content.Context
import org.scottishtecharmy.soundscape.R
import org.scottishtecharmy.soundscape.audio.AudioType
import org.scottishtecharmy.soundscape.audio.NativeAudioEngine
import org.scottishtecharmy.soundscape.geoengine.TreeId
import org.scottishtecharmy.soundscape.geoengine.GridState
import org.scottishtecharmy.soundscape.geoengine.PositionedString
import org.scottishtecharmy.soundscape.geoengine.UserGeometry
import org.scottishtecharmy.soundscape.geoengine.filters.CalloutHistory
import org.scottishtecharmy.soundscape.geoengine.filters.TrackedCallout
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Intersection
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Way
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.WayEnd
import org.scottishtecharmy.soundscape.geoengine.utils.Direction
import org.scottishtecharmy.soundscape.geoengine.utils.calculateSmallestAngleBetweenLines
import org.scottishtecharmy.soundscape.geoengine.utils.checkWhetherIntersectionIsOfInterest
import org.scottishtecharmy.soundscape.geoengine.utils.confectNamesForRoad
import org.scottishtecharmy.soundscape.geoengine.utils.findShortestDistance
import org.scottishtecharmy.soundscape.geoengine.utils.getCombinedDirectionSegments
import org.scottishtecharmy.soundscape.geoengine.utils.getFovTriangle
import org.scottishtecharmy.soundscape.geoengine.utils.getPathWays
import org.scottishtecharmy.soundscape.geoengine.utils.sortedByDistanceTo
import org.scottishtecharmy.soundscape.geojsonparser.geojson.FeatureCollection
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LineString
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Point
import kotlin.math.abs

data class IntersectionDescription(var nearestRoad: Way? = null,
                                   val userGeometry: UserGeometry = UserGeometry(),
                                   val intersection: Intersection? = null,
)

/**
 * RoundaboutDescription holds the results of traversing a roundabout from an entry intersection.
 * @param entryIntersection The intersection where we first meet the roundabout
 * @param exitRoads The non-roundabout Ways that leave the roundabout, paired with the intersection
 *                  they leave from (used for calculating direction)
 */
data class RoundaboutDescription(
    val entryIntersection: Intersection,
    val exitRoads: List<Pair<Way, Intersection>>
)

/**
 * Checks if an intersection is part of a roundabout (i.e. has at least one roundabout Way member).
 */
fun Intersection.hasRoundaboutWays(): Boolean {
    return members.any { it.isRoundabout() }
}

/**
 * Traverses the roundabout from an entry intersection, collecting all exit roads.
 * Exit roads are non-roundabout, non-sidewalk Ways. Handles two common patterns:
 *
 * 1. Split carriageways: roads split into two one-way lanes before the roundabout for
 *    pedestrian crossings. These connect at adjacent roundabout intersections and are
 *    deduplicated as a single exit. The short unnamed connector pieces are resolved to
 *    their parent road name.
 *
 * 2. Through-roads: roads that cross through the roundabout, connecting on opposite sides.
 *    These appear as separate exits (e.g. "Hayes Way goes left" and "Hayes Way goes right").
 *
 * @param entryIntersection The intersection where the user's road meets the roundabout
 * @param approachRoad The road the user is approaching on (excluded from exits)
 * @param gridState The current GridState for name confection
 * @return A RoundaboutDescription with all unique exit roads, or null if this isn't a roundabout
 */
fun getRoundaboutDescription(
    entryIntersection: Intersection,
    approachRoad: Way?,
    gridState: GridState
): RoundaboutDescription? {

    if (!entryIntersection.hasRoundaboutWays()) return null

    // Step 1: Traverse the roundabout graph, collecting all roundabout intersections
    // and building an adjacency map
    val roundaboutIntersections = mutableSetOf<Intersection>()
    val adjacency = mutableMapOf<Intersection, MutableSet<Intersection>>()
    val exitRoads = mutableListOf<Pair<Way, Intersection>>()
    val queue = ArrayDeque<Intersection>()

    queue.add(entryIntersection)

    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        if (!roundaboutIntersections.add(current)) continue

        for (way in current.members) {
            if (way.isRoundabout()) {
                val otherEnd = way.getOtherIntersection(current)
                if (otherEnd != null) {
                    // Record adjacency (direct roundabout Way connection)
                    adjacency.getOrPut(current) { mutableSetOf() }.add(otherEnd)
                    adjacency.getOrPut(otherEnd) { mutableSetOf() }.add(current)
                    if (otherEnd !in roundaboutIntersections) {
                        queue.add(otherEnd)
                    }
                }
            } else if (!way.isSidewalkOrCrossing()) {
                exitRoads.add(Pair(way, current))
            }
        }
    }

    // Step 2: Resolve unnamed short exits. When a road splits into two one-way carriageways,
    // the short connector pieces between the split and the roundabout may be unnamed. Follow
    // them to see if they connect to a named road.
    data class ResolvedExit(val way: Way, val intersection: Intersection, val resolvedName: String?)

    val resolvedExits = exitRoads.map { (way, intersection) ->
        var name = way.name
        if (name == null && way.length < 30.0) {
            // Follow this short unnamed Way to its other end
            val otherIntersection = way.getOtherIntersection(intersection)
            if (otherIntersection != null) {
                // Look for a named road at the other end
                for (member in otherIntersection.members) {
                    if (member != way && !member.isRoundabout() && !member.isSidewalkOrCrossing()
                        && member.name != null) {
                        name = member.name
                        break
                    }
                }
            }
        }
        ResolvedExit(way, intersection, name)
    }

    // Step 3: Deduplicate split carriageways while preserving through-road exits.
    // Two exits with the same name are the same split carriageway only if their roundabout
    // intersections are adjacent (connected by a single roundabout Way). If they're on
    // opposite sides of the roundabout, they're separate exits (through-road).
    val approachRoadName = approachRoad?.name
    val deduplicatedExits = mutableListOf<Pair<Way, Intersection>>()

    // Group exits by resolved name
    val namedGroups = mutableMapOf<String, MutableList<ResolvedExit>>()
    val unnamedExits = mutableListOf<ResolvedExit>()
    for (exit in resolvedExits) {
        if (exit.resolvedName != null) {
            namedGroups.getOrPut(exit.resolvedName) { mutableListOf() }.add(exit)
        } else {
            unnamedExits.add(exit)
        }
    }

    // For each named group, cluster adjacent exits and keep one per cluster
    for ((name, exits) in namedGroups) {
        // Skip the approach road
        if (name == approachRoadName) continue

        // Build clusters of adjacent exits
        val clusters = mutableListOf<MutableList<ResolvedExit>>()
        val assigned = mutableSetOf<Int>()

        for (i in exits.indices) {
            if (i in assigned) continue
            val cluster = mutableListOf(exits[i])
            assigned.add(i)

            // Find all exits adjacent to this cluster
            var changed = true
            while (changed) {
                changed = false
                for (j in exits.indices) {
                    if (j in assigned) continue
                    // Check if exits[j] is adjacent to any exit in the cluster
                    val isAdjacent = cluster.any { clusterExit ->
                        val neighbors = adjacency[clusterExit.intersection] ?: emptySet()
                        neighbors.contains(exits[j].intersection) ||
                                clusterExit.intersection == exits[j].intersection
                    }
                    if (isAdjacent) {
                        cluster.add(exits[j])
                        assigned.add(j)
                        changed = true
                    }
                }
            }
            clusters.add(cluster)
        }

        // Keep one representative from each cluster (prefer the longest Way)
        for (cluster in clusters) {
            val best = cluster.maxByOrNull { it.way.length }!!
            deduplicatedExits.add(Pair(best.way, best.intersection))
        }
    }

    // Add unnamed exits (no dedup possible without a name)
    for (exit in unnamedExits) {
        deduplicatedExits.add(Pair(exit.way, exit.intersection))
    }

    return RoundaboutDescription(entryIntersection, deduplicatedExits)
}

/**
 * getRoadsDescriptionFromFov returns a description of the nearestRoad and also the 'best'
 * intersection within the field of view. The description includes the roads that join the
 * intersection, the location of the intersection and the name of the intersection.
 *
 * @param gridState The current GridState which is the state of the downloaded tiles
 * @param userGeometry This includes location, heading and other data
 *
 * @return An IntersectionDescription containing all the data required for callouts to describe the
 * intersection.
 */
fun getRoadsDescriptionFromFov(gridState: GridState,
                               userGeometry: UserGeometry
) : IntersectionDescription {

    // Create FOV triangle
    val triangle = getFovTriangle(userGeometry)

    val roadTree = gridState.getFeatureTree(TreeId.WAYS_SELECTION)
    val intersectionTree = gridState.getFeatureTree(TreeId.INTERSECTIONS)

    // Find roads within FOV
    val fovRoads = roadTree.getAllWithinTriangle(triangle)
    if(fovRoads.features.isEmpty()) return IntersectionDescription(
        nearestRoad = userGeometry.mapMatchedWay,
        userGeometry = userGeometry)

    var nearestRoad = userGeometry.mapMatchedWay
    if(nearestRoad == null) {
        if (userGeometry.inStreetPreview) {
            // In StreetPreview mode, the road we're on is that matching the heading into the
            // intersection that we're at.
            val intersection = intersectionTree.getNearestFeature(userGeometry.location, userGeometry.ruler) as Intersection?
            val userHeading = userGeometry.heading()
            if((userHeading != null) && (intersection != null)) {
                for (member in intersection.members) {
                    val wayHeading = (member.heading(intersection) + 180.0) % 360.0
                    if (abs(wayHeading - userHeading) < 1.0) {
                        nearestRoad = member
                        break
                    }
                }
            }
        } else {
            nearestRoad =
                roadTree.getNearestFeatureWithinTriangle(triangle, userGeometry.ruler) as Way?
        }
    }

    // If we're on a mapped sidewalk, use the associated road for intersection detection instead of
    // the sidewalk itself.
    if(nearestRoad?.isSidewalkOrCrossing() == true) {
        if(nearestRoad.properties?.get("pavement") == null) {
            // Confect the names for the sidewalk first, this should come up with the name of the
            // associated road.
            confectNamesForRoad(nearestRoad, gridState)
        }
        // There could be multiple Ways which share the same pavement name, and we want to pick the
        // right one to use. We want the Way to be running in the same direction as the pavement is,
        // and the nearest of those.
        var bestRoad: Way? = null
        var bestRoadDistance = Double.MAX_VALUE
        for(road in fovRoads.features) {
            val way = road as Way
            if(nearestRoad.properties?.get("pavement") == way.name) {
                if(userGeometry.mapMatchedLocation?.point != null) {
                    val roadDistance =
                        userGeometry.ruler.distanceToLineString(userGeometry.mapMatchedLocation.point, road.geometry as LineString)
                    val snappedHeading = userGeometry.snappedHeading()
                    if (snappedHeading != null) {
                        val innerAngle = calculateSmallestAngleBetweenLines(roadDistance.heading, snappedHeading)
                        if (innerAngle > 45.0) {
                            // This way is not at the angle of travel, so skip it
                            continue
                        }
                    }
                    if (roadDistance.distance < bestRoadDistance) {
                        bestRoad = road as Way
                        bestRoadDistance = roadDistance.distance
                    }
                }
            }
        }
        nearestRoad = bestRoad
    }

    // Find intersections within FOV
    val fovIntersections = intersectionTree.getAllWithinTriangle(triangle)
    if(fovIntersections.features.isEmpty()) return IntersectionDescription(nearestRoad, userGeometry)

    // Remove intersections which are only:
    //  1. Short paths leading to sidewalks of the road, or
    //  2. Direct intersections with sidewalks.
    //  3. Within a 5m radius of the current location
    //  4. Internal roundabout intersections (all non-sidewalk Ways are roundabout segments)
    val trimmedIntersections = FeatureCollection()
    for(i in fovIntersections.features) {
        val intersection = i as Intersection
        var add = true
        if(!userGeometry.inStreetPreview && userGeometry.ruler.distance(intersection.location, userGeometry.mapMatchedLocation?.point ?: userGeometry.location) < 5.0)
            add = false
        else {
            var disposalCount = 0
            var roundaboutCount = 0
            for (way in i.members) {
                if (way.isSidewalkOrCrossing())
                    ++disposalCount
                else if (way.isSidewalkConnector(intersection, nearestRoad, gridState))
                    ++disposalCount
                else if (way.isRoundabout())
                    ++roundaboutCount
            }
            // Skip internal roundabout intersections: all non-sidewalk Ways are roundabout
            // segments with no exit roads. These will be described as part of the roundabout
            // when we encounter the entry intersection.
            if ((i.members.size - disposalCount) == roundaboutCount && roundaboutCount > 0) {
                add = false
            }
            else if((i.members.size - disposalCount) < 2) {
                // We're disposing of pavement intersections, if we've got fewer then 2 non-
                // pavement Ways then we're not interested in this intersection. Intersections
                // worth describing have the Way we're coming in on as well as at least two other
                // Ways leaving the intersection.
                add = false
            } else {
                if((i.members.size - disposalCount) == 2) {
                    // If the way names are the same then also skip it
                    if(i.members[0].name == i.members[1].name)
                        add = false
                }
            }
        }
        if(add)
            trimmedIntersections.features.add(intersection)
    }

    // Sort the FOV intersections by distance
    val sortedFovIntersections = sortedByDistanceTo(userGeometry.mapMatchedLocation?.point ?: userGeometry.location, trimmedIntersections)

    // Inspect each intersection so as to skip trivial ones
    val nonTrivialIntersections = mutableListOf<Pair<Int, Intersection>>()

    for (intersection in sortedFovIntersections.features) {
        val intersectionLocation = (intersection.geometry as Point).coordinates
        val graphIntersection = gridState.gridIntersections[intersectionLocation]
        if(graphIntersection != null) {
            if((userGeometry.mapMatchedLocation != null) && (nearestRoad != null)) {
                // If our current matched way ends at this intersection, then we don't need to use
                // more elaborate (Dijkstra) pathfinding to check the connection.
                if(!nearestRoad.intersections.contains(graphIntersection)) {

                    // Check if we can get to the intersection from our current location within a
                    // short distance. If we can, check that we don't go through any other valid
                    // intersections first.
                    val shortestDistanceResults = findShortestDistance(
                        userGeometry.mapMatchedLocation.point,
                        nearestRoad,
                        intersectionLocation,
                        (intersection as Intersection).members.first(),
                        null,
                        null,
                        50.0
                    )
                    if (shortestDistanceResults.distance < 50.0) {
                        var skip = false
                        val ways = getPathWays(graphIntersection)
                        var nextIntersection = graphIntersection
                        for(way in ways) {
                            nextIntersection = way.getOtherIntersection(nextIntersection!!)

                            nextIntersection?.let { next ->
                                var count = 0
                                if (next.members.size > 2) {
                                    for(member in next.members) {
                                        if(member.properties != null) {
                                            if (member.isSidewalkOrCrossing() &&
                                                !member.isSidewalkConnector(intersection, nearestRoad, gridState)
                                            ) {
                                                count++
                                            }
                                        }
                                    }
                                    if(count > 2)
                                        skip = true
                                    return@let
                                }
                            }
                            if(skip)
                                break
                        }
                        if(skip) {
                            // Skip this intersection, as it's not the nearest one of interest
                            shortestDistanceResults.tidy()
                            continue
                        }
                    } else {
                        shortestDistanceResults.tidy()
                        continue
                    }
                    shortestDistanceResults.tidy()
                }
            }

            // We aim to skip 'simple' intersections e.g. ones where the only roads involved have
            // the same name.
            val priority = checkWhetherIntersectionIsOfInterest(graphIntersection, nearestRoad)
            nonTrivialIntersections.add(Pair(priority, graphIntersection))
        }
    }
    if(nonTrivialIntersections.isEmpty()) {
        return IntersectionDescription(nearestRoad, userGeometry)
    }

    var intersection: Intersection? = nonTrivialIntersections.firstOrNull { prioritised ->
                prioritised.first > 0
            }?.second
    if(intersection == null) {
        // No intersection with a priority greater than zero, so just pick the highest
        intersection = nonTrivialIntersections.maxByOrNull { prioritised ->
            prioritised.first
        }?.second
    }

    // Find the bearing that we're coming in at - measured to the nearest intersection
    val heading = nearestRoad?.heading(intersection!!)
    //val heading = userGeometry.heading()
    if(heading != null) {
        // And use the polygons to describe the roads at the intersection
        return IntersectionDescription(
            nearestRoad,
            userGeometry,
            intersection
        )
    }
    return IntersectionDescription(nearestRoad, userGeometry)
}

/**
 * addIntersectionCalloutFromDescription adds a callout to the results list for the intersection
 * described in the parameters. This will become more configurable e.g. whether to include the
 * distance or not.
 *
 * @param description The description of the intersection to callout
 * @param localizedContext A context for obtaining localized strings
 * @param calloutHistory An optional CalloutHistory to use so as to filter out recently played out
 * @param gridState The current gridState
 *
 * @return A TrackedCallout for the intersection if one was found, otherwise null.
 */
fun addIntersectionCalloutFromDescription(
    description: IntersectionDescription,
    localizedContext: Context?,
    calloutHistory: CalloutHistory? = null,
    gridState: GridState
) : TrackedCallout? {

    // Report nearby road
    if(description.intersection == null) {
        description.nearestRoad?.let { nearestRoad ->

            // Figure out which direction we're travelling along the way
            var direction: Boolean? = null
            if (description.userGeometry.mapMatchedLocation != null) {
                direction = when (description.userGeometry.snappedHeading()) {
                    description.userGeometry.mapMatchedLocation.heading ->
                        true

                    (description.userGeometry.mapMatchedLocation.heading + 180.0) % 360.0 ->
                        false

                    else ->
                        // If the direction of travel is more 'across' than 'along' then skip the
                        // description this time. Once the direction is better aligned we can call it
                        // out. This avoids calling out roads that the user is crossing over as
                        // 'ahead'.
                        null
                }
            }
            if (direction != null) {
                val calloutText = if (localizedContext == null)
                    "Ahead ${(nearestRoad).getName(direction, gridState, localizedContext)}"
                else
                    "${localizedContext.getString(R.string.directions_direction_ahead)} ${
                        (nearestRoad).getName(
                            direction,
                            gridState,
                            localizedContext
                        )
                    }}"

                val trackedCallout = TrackedCallout(
                    description.userGeometry,
                    calloutText,
                    LngLatAlt(),
                    positionedStrings = List(1){
                        PositionedString(
                            text = calloutText,
                            type = AudioType.STANDARD
                        )
                    },
                    isPoint = false,
                    isGeneric = false,
                    calloutHistory = calloutHistory
                )
                if(calloutHistory?.find(trackedCallout) != true) {
                    return trackedCallout
                }
            }
        }
        return null
    }

    val intersectionName = description.intersection.name

    // It's possible to get here and the nearestRoad is NOT a member of the intersection. This is
    // particularly likely where there are sidewalks breaking up the road segments. So we need to
    // follow our nearestRoad to the intersection. However, we need to be careful with the heading
    // as the incoming Way to the intersection could be 90 degrees (or more?) away from the current
    // heading.
    if(description.nearestRoad?.containsIntersection(description.intersection) != true) {
        if(description.nearestRoad == null)
            return null

        val shortestDistanceResults = findShortestDistance(
            description.userGeometry.mapMatchedLocation?.point ?: description.userGeometry.location,
            description.nearestRoad!!,
            null, null, description.intersection,
            null,
            50.0
        )
        val ways = getPathWays(shortestDistanceResults.endIntersection)
        description.nearestRoad = ways.firstOrNull()

        shortestDistanceResults.tidy()
    }
    val heading = description.nearestRoad?.heading(description.intersection) ?: return null

    if(description.intersection.members.size <= 2)
        return null

    // Check if we should be filtering out this callout
    val intersectionLocation = description.intersection.location

    // Check if this is a roundabout intersection
    val roundaboutDescription = getRoundaboutDescription(
        description.intersection,
        description.nearestRoad,
        gridState
    )
    if (roundaboutDescription != null) {
        return addRoundaboutCallout(
            description,
            roundaboutDescription,
            heading,
            localizedContext,
            calloutHistory,
            gridState
        )
    }

    val trackedCallout = TrackedCallout(
        description.userGeometry,
        intersectionName!!,
        intersectionLocation,
        positionedStrings = List(1) {
            PositionedString(
                text = localizedContext?.getString(R.string.intersection_approaching_intersection) ?: "Approaching intersection",
                heading = -10000.0,
                earcon = NativeAudioEngine.EARCON_SENSE_POI,
                type = AudioType.STANDARD
            )
        },
        isPoint = true,
        isGeneric = false,
        calloutHistory = calloutHistory
    )
    if(calloutHistory?.find(trackedCallout) == true) {
        return null
    }

    // Report intersection is coming up

    // Report roads that join the intersection
    val incomingHeading = (heading + 180.0) % 360.0

    val directions = getCombinedDirectionSegments(incomingHeading)
    val intersectionResults = trackedCallout.positionedStrings.toMutableList()
    for (way in description.intersection.members) {

        if(way.properties?.get("pavement") != null)
            continue

        val wayHeading = way.heading(description.intersection)
        val direction = directions.indexOfFirst { segment ->
            segment.contains(wayHeading)
        }

        // Don't call out the road we are on (0) as part of the intersection
        if (direction != Direction.BEHIND.value) {
            val roadDirectionId = when(direction) {
                Direction.BEHIND_LEFT.value,Direction.LEFT.value,Direction.AHEAD_LEFT.value ->
                    R.string.directions_name_goes_left
                Direction.BEHIND_RIGHT.value,Direction.RIGHT.value,Direction.AHEAD_RIGHT.value ->
                    R.string.directions_name_goes_right
                else ->
                    R.string.directions_name_continues_ahead
            }
            var unlocalizedDirection = ""
            if(localizedContext == null) {
                unlocalizedDirection = when(direction) {
                    Direction.BEHIND_LEFT.value,Direction.LEFT.value,Direction.AHEAD_LEFT.value ->
                        "goes left"
                    Direction.BEHIND_RIGHT.value,Direction.RIGHT.value,Direction.AHEAD_RIGHT.value ->
                        "goes right"
                    else ->
                        "continues ahead"
                }

            }

            val presentationHeading = incomingHeading + when(direction) {
                Direction.BEHIND_LEFT.value,Direction.LEFT.value,Direction.AHEAD_LEFT.value -> -90.0
                Direction.BEHIND_RIGHT.value,Direction.RIGHT.value,Direction.AHEAD_RIGHT.value -> 90.0
                else -> 0.0
            }

            val destinationText = way.getName(way.intersections[WayEnd.START.id] == description.intersection, gridState, localizedContext)
            val intersectionCallout =
                localizedContext?.getString(roadDirectionId, destinationText) ?: "\t$destinationText $unlocalizedDirection"
            intersectionResults.add(
                PositionedString(
                    text = intersectionCallout,
                    type = AudioType.COMPASS,
                    heading = presentationHeading
                )
            )
        }
    }
    // Order intersection callout by heading from left to right
    intersectionResults.sortWith(Comparator { p1, p2 ->
        p1.heading!!.compareTo(p2.heading!!)
    })
    trackedCallout.positionedStrings = intersectionResults
    return trackedCallout
}

/**
 * addRoundaboutCallout generates a callout describing a roundabout and its exits.
 * Instead of describing every short roundabout segment as a separate road, it traverses
 * the entire roundabout and lists the exit roads with their directions relative to the
 * user's approach heading.
 *
 * For each exit, the direction is calculated from the entry intersection to the exit
 * intersection, giving a bearing relative to the user's approach that indicates roughly
 * where around the roundabout each exit is.
 */
private fun addRoundaboutCallout(
    description: IntersectionDescription,
    roundaboutDescription: RoundaboutDescription,
    heading: Double,
    localizedContext: Context?,
    calloutHistory: CalloutHistory?,
    gridState: GridState
): TrackedCallout? {

    val intersectionLocation = description.intersection!!.location
    val exitCount = roundaboutDescription.exitRoads.size

    val approachingText = if (exitCount > 0) {
        localizedContext?.getString(R.string.intersection_approaching_roundabout_with_exits, exitCount.toString())
            ?: "Approaching roundabout with $exitCount exits"
    } else {
        localizedContext?.getString(R.string.intersection_approaching_roundabout)
            ?: "Approaching roundabout"
    }

    val trackedCallout = TrackedCallout(
        description.userGeometry,
        approachingText,
        intersectionLocation,
        positionedStrings = List(1) {
            PositionedString(
                text = approachingText,
                heading = -10000.0,
                earcon = NativeAudioEngine.EARCON_SENSE_POI,
                type = AudioType.STANDARD
            )
        },
        isPoint = true,
        isGeneric = false,
        calloutHistory = calloutHistory
    )
    if (calloutHistory?.find(trackedCallout) == true) {
        return null
    }

    // Describe exit roads with directions relative to our approach heading
    val incomingHeading = (heading + 180.0) % 360.0
    val directions = getCombinedDirectionSegments(incomingHeading)
    val intersectionResults = trackedCallout.positionedStrings.toMutableList()

    for ((way, exitIntersection) in roundaboutDescription.exitRoads) {
        // Calculate the direction of the exit relative to our approach.
        // We use the bearing from entry intersection to exit intersection to determine
        // which side of the roundabout this exit is on, and then use the Way's own heading
        // to determine the exit direction more precisely.
        val wayHeading = way.heading(exitIntersection)
        val direction = directions.indexOfFirst { segment ->
            segment.contains(wayHeading)
        }

        // Skip exits that are behind us (i.e. effectively back the way we came)
        if (direction == Direction.BEHIND.value) continue

        val roadDirectionId = when(direction) {
            Direction.BEHIND_LEFT.value, Direction.LEFT.value, Direction.AHEAD_LEFT.value ->
                R.string.directions_name_goes_left
            Direction.BEHIND_RIGHT.value, Direction.RIGHT.value, Direction.AHEAD_RIGHT.value ->
                R.string.directions_name_goes_right
            else ->
                R.string.directions_name_continues_ahead
        }
        var unlocalizedDirection = ""
        if (localizedContext == null) {
            unlocalizedDirection = when(direction) {
                Direction.BEHIND_LEFT.value, Direction.LEFT.value, Direction.AHEAD_LEFT.value ->
                    "goes left"
                Direction.BEHIND_RIGHT.value, Direction.RIGHT.value, Direction.AHEAD_RIGHT.value ->
                    "goes right"
                else ->
                    "continues ahead"
            }
        }

        val presentationHeading = incomingHeading + when(direction) {
            Direction.BEHIND_LEFT.value, Direction.LEFT.value, Direction.AHEAD_LEFT.value -> -90.0
            Direction.BEHIND_RIGHT.value, Direction.RIGHT.value, Direction.AHEAD_RIGHT.value -> 90.0
            else -> 0.0
        }

        val exitDirection = way.intersections[WayEnd.START.id] == exitIntersection
        val destinationText = way.getName(exitDirection, gridState, localizedContext)
        val exitCallout =
            localizedContext?.getString(roadDirectionId, destinationText) ?: "\t$destinationText $unlocalizedDirection"
        intersectionResults.add(
            PositionedString(
                text = exitCallout,
                type = AudioType.COMPASS,
                heading = presentationHeading
            )
        )
    }

    // Order exit callouts by heading from left to right
    intersectionResults.sortWith(Comparator { p1, p2 ->
        p1.heading!!.compareTo(p2.heading!!)
    })
    trackedCallout.positionedStrings = intersectionResults
    return trackedCallout
}
