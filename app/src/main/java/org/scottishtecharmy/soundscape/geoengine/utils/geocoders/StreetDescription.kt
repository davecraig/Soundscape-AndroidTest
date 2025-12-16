package org.scottishtecharmy.soundscape.geoengine.utils.geocoders

import android.content.Context
import org.scottishtecharmy.soundscape.geoengine.GridState
import org.scottishtecharmy.soundscape.geoengine.TreeId
import org.scottishtecharmy.soundscape.geoengine.UserGeometry
import org.scottishtecharmy.soundscape.geoengine.getTextForFeature
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.IntersectionType
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.MvtFeature
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Way
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.WayEnd
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.WayType
import org.scottishtecharmy.soundscape.geoengine.utils.PointAndDistanceAndHeading
import org.scottishtecharmy.soundscape.geoengine.utils.Side
import org.scottishtecharmy.soundscape.geoengine.utils.calculateHeadingOffset
import org.scottishtecharmy.soundscape.geoengine.utils.getCentralPointForFeature
import org.scottishtecharmy.soundscape.geoengine.utils.getDistanceToFeature
import org.scottishtecharmy.soundscape.geoengine.utils.getSideOfLine
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LineString
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import java.util.SortedMap
import kotlin.collections.set
import kotlin.math.round
import kotlin.math.sign

class StreetDescription(val name: String, val gridState: GridState) {

    // Street numbers
    // There are two types of street numbers, those which include a street name, and those which are
    // just numbers. The former are reliable, but the latter could be on the corner between streets
    // and so require some validation.
    //

    val ways: MutableList<Pair<Way, Boolean>> = mutableListOf()
    var sortedDescriptivePoints: SortedMap<Double, MvtFeature> = sortedMapOf()
    var leftSortedNumbers: SortedMap<Double, MvtFeature> = sortedMapOf()
    var leftMode: HouseNumberMode = HouseNumberMode.MIXED
    var rightSortedNumbers: SortedMap<Double, MvtFeature> = sortedMapOf()
    var rightMode: HouseNumberMode = HouseNumberMode.MIXED

    fun whichSide(way: Way,
                  direction: Boolean,
                  pdh: PointAndDistanceAndHeading,
                  location: LngLatAlt) : Side {
        val line = way.geometry as LineString
        var start = line.coordinates[pdh.index]
        var end = line.coordinates[pdh.index + 1]
        if (direction) {
            // Swap direction based on Way direction
            val tmp = start
            start = end
            end = tmp
        }

        return getSideOfLine(start, end, location)
    }

    fun sideToBool(side: Side) : Boolean? {
        return when (side) {
            Side.LEFT -> false
            Side.RIGHT -> true
            else -> null
        }
    }
    fun otherSide(side: Side) : Side? {
        return when (side) {
            Side.LEFT -> Side.RIGHT
            Side.RIGHT -> Side.LEFT
            else -> null
        }
    }

    fun parseHouseNumber(houseNumber: String) : Int? {
        val numericPart = houseNumber.takeWhile { it.isDigit() }

        // Check if we actually found any digits before trying to parse.
        if (numericPart.isNotEmpty()) {
            return numericPart.toInt()
        }
        return null
    }

    fun parseHouseNumberRange(houseNumber: String) : Pair<Int,Int>? {
        var highest = 0
        var lowest = Int.MAX_VALUE
        var remaining = houseNumber
        while(true) {
            remaining = remaining.dropWhile { !it.isDigit() }
            if (remaining.isEmpty()) break

            val numericPart = remaining.takeWhile { it.isDigit() }
            if (numericPart.isNotEmpty()) {
                val number = numericPart.toInt()
                if (number < lowest) lowest = number
                if (number > highest) highest = number

                remaining = remaining.drop(numericPart.length)
            }
        }
        return if(lowest == Int.MAX_VALUE)
            null
        else
            Pair(lowest, highest)
    }

    fun distanceAlongLine(nearestWay: Way, pdh: PointAndDistanceAndHeading) : Double {
        var totalDistance = 0.0
        for (way in ways) {
            if (way.first == nearestWay) {
                val line = way.first.geometry as LineString
                for (i in 0 until pdh.index) {
                    totalDistance += gridState.ruler.distance(
                        line.coordinates[i],
                        line.coordinates[i + 1]
                    )
                }
                totalDistance += (pdh.positionAlongLine - pdh.index) * gridState.ruler.distance(
                    line.coordinates[pdh.index],
                    line.coordinates[pdh.index + 1]
                )
                break
            }
            totalDistance += way.first.length
        }
        return totalDistance
    }

    fun nearestWayOnStreet(mvt: MvtFeature) : Pair<Way, Boolean>? {
        var nearestWay : Pair<Way, Boolean>? = null
        var nearestPdh = PointAndDistanceAndHeading()

        val location = getCentralPointForFeature(mvt) ?: return null
        for(way in ways) {
            val pdh = getDistanceToFeature(location, way.first, gridState.ruler)
            if(pdh.distance < nearestPdh.distance) {
                nearestWay = way
                nearestPdh = pdh
            }
        }
        return nearestWay
    }

    enum class HouseNumberMode {
        EVEN,
        ODD,
        MIXED
    }
    fun assignHouseNumberModes(odd: Array<Int>, even: Array<Int>) {
        if((odd[0] + odd[1] + even[0] + even[1]) >= 2) {
            // We have at least 2 house numbers
            if (
                (odd[0] == 0) &&
                (even[0] >= 0) &&
                (odd[1] >= 0) &&
                (even[1] == 0)
            ) {
                leftMode = HouseNumberMode.EVEN
                rightMode = HouseNumberMode.ODD
                println("Odd on right side, even on left")
                return
            } else if (
                (odd[1] == 0) &&
                (even[1] >= 0) &&
                (odd[0] >= 0) &&
                (even[0] == 0)

            ) {
                leftMode = HouseNumberMode.ODD
                rightMode = HouseNumberMode.EVEN
                println("Odd on left side, even on right")
                return
            }
        }
        println("Mixed house numbering")
        leftMode = HouseNumberMode.MIXED
        rightMode = HouseNumberMode.MIXED
    }

    private fun addHouse(house: MvtFeature,
                         nearestWay: Pair<Way,Boolean>?,
                         points: MutableMap<Double, MvtFeature>,
                         streetConfidence: Boolean) {
        if(nearestWay != null) {
            val location = getCentralPointForFeature(house) ?: return
            val pdh = getDistanceToFeature(location, nearestWay.first, gridState.ruler)
            val totalDistance = distanceAlongLine(nearestWay.first, pdh)
            val side = whichSide(
                nearestWay.first,
                nearestWay.second,
                pdh,
                location
            )
            house.side = sideToBool(side)
            house.streetConfidence = streetConfidence

            points[totalDistance] = house
//            // We want to allow street numbers and POI to exist within the same points map, so a
//            // bit of cheating here to make the distances unique.
//            var offset = 0.0
//            while(true) {
//                if (!points.contains(totalDistance + offset)) {
//                    points[totalDistance] = house
//                    break
//                }
//                offset += 0.1
//            }
        }
    }

    fun checkSortedNumberConsistency(sortedNumbers: SortedMap<Double, MvtFeature>) : SortedMap<Double, MvtFeature> {
        if(sortedNumbers.size <= 2)
            return sortedMapOf()

        var lastDelta = 0
        var lastHouse : MvtFeature? = null
        var lastNumbers : Pair<Int, Int>? = null
        val removalSet = mutableSetOf<MvtFeature>()
        for (house in sortedNumbers) {
            val numbers = parseHouseNumberRange(house.value.housenumber ?: "")
            if((lastNumbers != null) && (numbers != null)) {
                // Check for overlap of range
                if((numbers.first <= lastNumbers.second) && (numbers.second >= lastNumbers.first)) {
                    // The range overlaps
                } else {
                    val newDelta = numbers.first - lastNumbers.first
                    if ((lastDelta != 0) && (newDelta != 0) && (newDelta.sign != lastDelta.sign)) {
                        // Numbers have changed direction
                        if (!house.value.streetConfidence) {
                            // The confidence in this street number isn't high as was found via a search
                            // Remove it
                            removalSet.add(house.value)
                        }
                        if (lastHouse != null) {
                            if (!lastHouse.streetConfidence) {
                                // The confidence in this street number isn't high as was found via a search
                                removalSet.add(lastHouse)
                            }
                        }
                        continue
                    }
                    if (newDelta != 0)
                        lastDelta = newDelta
                }
            }
            lastHouse = house.value
            lastNumbers = numbers
        }
        if(removalSet.isEmpty())
            return sortedNumbers

        // We need to remove some houses, so create a new map
        val newMap = mutableMapOf<Double, MvtFeature>()
        for(house in sortedNumbers) {
            if (!removalSet.contains(house.value)) {
                newMap[house.key] = house.value
            }
        }
        return newMap.toSortedMap()
    }
    /**
     * createDescription creates the street description
     */
    fun createDescription(matchedWay: Way) {
        val descriptivePoints: MutableMap<Double, MvtFeature> = mutableMapOf()
        val houseNumberPoints: MutableMap<Double, MvtFeature> = mutableMapOf()

        // We've got part of our street, so follow it in each direction adding to our list
        var intersection = matchedWay.intersections[WayEnd.START.id]
        var currentWay = matchedWay
        for(index in -1..0) {
            while (intersection != null) {
                intersection = currentWay.getOtherIntersection(intersection)
                val direction = (intersection == currentWay.intersections[WayEnd.END.id])
                if(index == 0) {
                    if (ways[0].first != currentWay) {
                        val newPair = Pair(currentWay, !direction)
                        if(ways.contains(newPair)) {
                            // We've looped around to a Way that we already have
                            break
                        }
                        ways.add(index, newPair)
                    }
                }
                else {
                    val newPair = Pair(currentWay, direction)
                    if(ways.contains(newPair)) {
                        // We've looped around to a Way that we already have
                        break
                    }
                    ways.add(Pair(currentWay, direction))
                }

                if (intersection != null) {
                    var found = false
                    var newWay = currentWay
                    // TODO: We need to deal with named roads splintering into dual carriageways e.g.
                    //  St Vincent Street https://www.openstreetmap.org/way/262604454. In fact there
                    //  all sorts of other challenges including non-linear roads e.g. Prestonfield
                    //  https://www.openstreetmap.org/way/1053351053 or Marchfield
                    //  https://www.openstreetmap.org/way/138354016. The main problem here is that
                    //  the housenumber map has ALL of the house numbers with that street and so it
                    //  confuses the odd/even numbering analysis.
                    for (member in intersection.members) {
                        if ((currentWay != member) &&
                            ((member.name == name) || (member.wayType == WayType.JOINER))) {
                            // We've got a Way of the same name extending away. See if it's continuing
                            // on in the same direction
                            newWay = member
                            found = true
                            break
                        }
                    }
                    if (found) {
                        currentWay = newWay
                    }
                    else {
                        // We reached an intersection which has no Way of the same name, so we're done
                        intersection = null
                    }
                }
            }
            intersection = matchedWay.intersections[WayEnd.END.id]
            currentWay = matchedWay
        }

        // We've now got an ordered list of Ways for our named street. Add all of the intersections
        // to our linear map
        var totalDistance = 0.0
        for(way in ways) {
            val intersection =
                if (way.second)
                    way.first.intersections[WayEnd.START.id]
                else
                    way.first.intersections[WayEnd.END.id]

            if (intersection != null) {
                totalDistance += way.first.length
                if(intersection.intersectionType != IntersectionType.TILE_EDGE) {
                    descriptivePoints[totalDistance] = intersection
                    if (way == ways.last()) {
                        val lastIntersection = way.first.getOtherIntersection(intersection)
                        if (lastIntersection != null) {
                            descriptivePoints[totalDistance + way.first.length] = lastIntersection
                        }
                    }
                }
            }
        }

        // Add all of the house numbers with known street to our linear map
        val houseNumberTree = gridState.gridStreetNumberTreeMap[name]
        if(houseNumberTree != null) {
            val houseCollection = houseNumberTree.getAllCollection()
            for(house in houseCollection) {
                val nearestWay = nearestWayOnStreet(house as MvtFeature)
                addHouse(house, nearestWay, houseNumberPoints, true)
            }
        }

        // Now search in the house numbers which don't have a known street
        val unknownStreetTree = gridState.gridStreetNumberTreeMap["null"]
        if(unknownStreetTree != null) {
            // Search each of our ways for street numbers with no street
            for(way in ways) {
                val results = unknownStreetTree.getNearbyLine(
                    way.first.geometry as LineString,
                    25.0,
                    gridState.ruler
                )
                for(house in results) {
                    // A searched for house should only be added if it's the nearest Way that it was
                    // found in.
                    val nearestWay = nearestWayOnStreet(house as MvtFeature)
                    if(way.first == nearestWay?.first) {
                        addHouse(house, way, houseNumberPoints, false)
                    }
                }
            }
        }

        // Look for POI near the road
        val poiTree = gridState.getFeatureTree(TreeId.LANDMARK_POIS)
        // Search each of our ways for street numbers with no street
        for(way in ways) {
            val results = poiTree.getNearbyLine(
                way.first.geometry as LineString,
                25.0,
                gridState.ruler
            )
            for(poi in results) {
                val nearestWay = nearestWayOnStreet(poi as MvtFeature)
                if(way.first == nearestWay?.first) {
                    addHouse(poi, way, descriptivePoints, false)
                }
            }
        }

        sortedDescriptivePoints = descriptivePoints.toSortedMap()

        // Analyse the house numbers on each side of the road
        val odd = arrayOf(0,0)
        val even = arrayOf(0,0)
        val sides = arrayOf(true,false)
        for(side in 0..1) {
            val numberPoints: MutableMap<Double, MvtFeature> = mutableMapOf()
            for (point in houseNumberPoints) {
                if(point.value.side != sides[side])
                    continue

                // We have a house number on the side of the street that we're interested in
                if(point.value.housenumber != null) {
                    val houseNumber = parseHouseNumber(point.value.housenumber!!)
                    if(houseNumber != null) {
                        numberPoints[point.key] = point.value
                        if(houseNumber % 2 == 0)
                            even[side]++
                        else
                            odd[side]++
                    }
                }
            }
            if(sides[side]) {
                leftSortedNumbers = numberPoints.toSortedMap()
            } else {
                rightSortedNumbers = numberPoints.toSortedMap()
            }
        }

        leftSortedNumbers = checkSortedNumberConsistency(leftSortedNumbers)
        rightSortedNumbers = checkSortedNumberConsistency(rightSortedNumbers)

        assignHouseNumberModes(odd, even)
    }

    /**
     * Given a point and a Way this function returns the best guess house number for it. The Boolean
     * is true if the house number is on the other side of the street.
     */
    fun getStreetNumber(way: Way, location: LngLatAlt) : Pair<String, Boolean> {

        // Find the way in our list and see which direction it's going
        var direction: Boolean? = null
        for(member in ways) {
            if(way == member.first) {
                direction = member.second
                break
            }
        }
        if(direction == null) return Pair("", false)

        // Get the distance along our lines of points
        val pdh = getDistanceToFeature(location, way, gridState.ruler)
        val distance = distanceAlongLine(way, pdh)

        // Find which side of the road the point is on
        val locationSide = whichSide(way, !direction, pdh, location)

        // Try that side first, but it could be that there are no street numbers on this side,
        // so we also have to fallback to trying the other side too.
        for(side in listOf(locationSide, otherSide(locationSide))) {
            val sortedNumbers = when (side) {
                Side.LEFT -> leftSortedNumbers
                Side.RIGHT -> rightSortedNumbers
                else -> continue
            }
            val mode = if (side == Side.LEFT) leftMode else rightMode

            val ceiling = sortedNumbers.keys.firstOrNull { it >= distance }
            val floor = sortedNumbers.keys.lastOrNull { it <= distance }
            var houseNumber = ""
            if (ceiling != null) {
                val ceilingValue = sortedNumbers[ceiling]
                if ((ceiling - distance) < 10.0)
                    houseNumber = ceilingValue?.housenumber ?: ""
                if (floor != null) {
                    val floorValue = sortedNumbers[floor]
                    if ((distance - floor) < 10.0)
                        houseNumber = floorValue?.housenumber ?: ""

                    if (houseNumber.isNotEmpty())
                        return Pair(houseNumber, side != locationSide)

                    val floorNumber = parseHouseNumber(floorValue?.housenumber ?: "")!!
                    val ceilingNumber = parseHouseNumber(ceilingValue?.housenumber ?: "")!!
                    val adjustment = ((distance - floor) / (ceiling - floor))
                    val interpolatedDouble = ((ceilingNumber - floorNumber) * adjustment)
                    var interpolatedInt = round(interpolatedDouble).toInt()
                    // Only ever interpolate by an even number so as to keep evenness/oddness on
                    // this side of the road. If the numbering really is MIXED then this is not
                    // required, but it hardly affects accuracy.
                    interpolatedInt = round(interpolatedDouble / 2.0).toInt() * 2
                    return Pair((interpolatedInt + floorNumber).toString(),side != locationSide)
                }
            }
            if (houseNumber.isNotEmpty())
                return Pair(houseNumber, side != locationSide)
        }
        return Pair("", false)
    }

    data class StreetPosition(
        val name: String = "",
        val distance: Double = Double.MAX_VALUE
    )

    data class StreetLocationDescription(
        var name: String? = null,
        var behind: StreetPosition = StreetPosition(),
        var ahead: StreetPosition = StreetPosition()
    )

    fun describeLocation(userGeometry: UserGeometry, localizedContext: Context?) : StreetLocationDescription {
        if(userGeometry.mapMatchedWay == null) return StreetLocationDescription()

        // Get the distance along our lines of points
        val pdh = getDistanceToFeature(userGeometry.location, userGeometry.mapMatchedWay, gridState.ruler)
        val distance = distanceAlongLine(userGeometry.mapMatchedWay, pdh)

        val result = StreetLocationDescription()
        var direction = false

        val heading = userGeometry.heading()
        if (heading != null) {
            val headingDifference = calculateHeadingOffset(heading, pdh.heading)
            direction = (headingDifference < 90.0)
        }

        val ahead = sortedDescriptivePoints.keys.firstOrNull { it >= distance }
        val behind = sortedDescriptivePoints.keys.lastOrNull { it <= distance }

        var tmpAhead = StreetPosition()
        if (ahead != null) {
            val aheadValue = sortedDescriptivePoints[ahead]
            if(aheadValue != null) {
                tmpAhead = StreetPosition(
                    getTextForFeature(localizedContext, aheadValue).text,
                    ahead - distance
                )
            }
        }
        var tmpBehind = StreetPosition()
        if (behind != null) {
            val behindValue = sortedDescriptivePoints[behind]
            if(behindValue != null) {
                tmpBehind = StreetPosition(
                    getTextForFeature(localizedContext, behindValue).text,
                    distance - behind
                )
            }
        }

        // The StreetLocationDescription is relative to the direction that the user is travelling
        if(direction) {
            result.ahead = tmpAhead
            result.behind = tmpBehind
        }
        else {
            result.behind = tmpAhead
            result.ahead = tmpBehind
        }
        return result
    }

    fun describeStreet() {
        println("Describe $name")
        for(point in sortedDescriptivePoints) {
            val text = getTextForFeature(null,point.value)
            when(point.value.side) {
                null -> println("\t\t\t\t\t${point.key.toInt()}m (${text.text})")
                true -> println("\t\t\t\t\t${point.key.toInt()}m ${text.text}")
                false -> println("${text.text}\t${point.key.toInt()}m")
            }
        }
    }
}