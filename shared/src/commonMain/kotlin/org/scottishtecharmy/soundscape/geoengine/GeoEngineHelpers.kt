package org.scottishtecharmy.soundscape.geoengine

import org.scottishtecharmy.soundscape.audio.AudioType
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.AlongWayKind
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.MvtFeature
import org.scottishtecharmy.soundscape.geoengine.utils.nextAlongWayFeature
import org.scottishtecharmy.soundscape.geoengine.utils.WayCursor
import org.scottishtecharmy.soundscape.geoengine.utils.WayContinuation
import org.scottishtecharmy.soundscape.geoengine.utils.AlongWayFeatureAhead
import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Way
import org.scottishtecharmy.soundscape.geoengine.utils.SuperCategoryId
import org.scottishtecharmy.soundscape.geoengine.utils.Triangle
import org.scottishtecharmy.soundscape.geoengine.utils.calculateHeadingOffset
import org.scottishtecharmy.soundscape.geoengine.utils.getCompassLabel
import org.scottishtecharmy.soundscape.geoengine.utils.getCompassLabelFacingDirectionAlong
import org.scottishtecharmy.soundscape.geoengine.utils.getDestinationCoordinate
import org.scottishtecharmy.soundscape.geoengine.utils.getRelativeClockTime
import org.scottishtecharmy.soundscape.geoengine.utils.getRelativeLeftRightLabel
import org.scottishtecharmy.soundscape.geoengine.utils.normalizeHeading
import org.scottishtecharmy.soundscape.geoengine.utils.toRadians
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import org.scottishtecharmy.soundscape.geojsonparser.geojson.Point
import org.scottishtecharmy.soundscape.i18n.LocalizedStrings
import org.scottishtecharmy.soundscape.i18n.PluralKey
import org.scottishtecharmy.soundscape.i18n.StringKey
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The nearest settlement to a location, and which size tier it was found in.
 *
 * The tier matters to callers for two reasons. Cities are phrased differently, because they're
 * large, often-merged conurbations: you can't sensibly be "towards Glasgow" while already inside
 * its urban area, unlike the discrete hamlet/village/town points a road passes near. And it's what
 * a settlement found ahead is weighed against, so that a village up the road can displace a farm
 * to hand but not the village being driven through - see [settlementAhead].
 */
data class NearestSettlement(val feature: MvtFeature?, val tier: SettlementTier?) {
    val name: String? get() = feature?.name
    val displayName: String? get() = feature?.displayName
    val isCity: Boolean get() = tier == SettlementTier.CITY
}

/**
 * The size tiers settlements are grouped into, smallest first, so that a caller which only cares
 * about larger places can say so - see [nearestSettlement]'s smallestTier.
 */
enum class SettlementTier { HAMLET, VILLAGE, TOWN, CITY }

/**
 * A settlement tier, the tree its features live in, and Nominatim's proximity for it: the
 * smaller and more local a settlement is, the closer you have to be for it to be the one worth
 * naming.
 */
private data class SettlementSearch(
    val tier: SettlementTier,
    val treeId: TreeId,
    val proximity: Double
)

// Smallest first, so nearestSettlement returns the most local settlement that's close enough.
private val settlementSearches = listOf(
    SettlementSearch(SettlementTier.HAMLET, TreeId.SETTLEMENT_HAMLET, 1000.0),
    SettlementSearch(SettlementTier.VILLAGE, TreeId.SETTLEMENT_VILLAGE, 2000.0),
    SettlementSearch(SettlementTier.TOWN, TreeId.SETTLEMENT_TOWN, 4000.0),
    SettlementSearch(SettlementTier.CITY, TreeId.SETTLEMENT_CITY, 15000.0),
)

/**
 * Finds the nearest settlement using Nominatim's proximities, so that the smaller and more local
 * a settlement is, the closer you have to be for it to be the one worth naming:
 *
 *     cities, municipalities, islands | 15 km
 *     towns, boroughs                 |  4 km
 *     villages, suburbs               |  2 km
 *     hamlets, farms, neighbourhoods  |  1 km
 *
 * Settlements live in their own low-zoom grid - the high-zoom tiles the rest of the geo engine
 * uses don't carry the "place" layer at all - so [settlementGrid] is a different GridState from
 * the one everything else is looked up in. Must be called from within its treeContext.
 *
 * @param smallestTier the smallest kind of settlement worth naming. Tiers below it are skipped
 * entirely rather than being widened out, so a caller which only wants towns gets a town within
 * 4 km or a city within 15 km, and otherwise nothing. Defaults to naming anything at all.
 */
fun nearestSettlement(
    settlementGrid: GridState,
    location: LngLatAlt,
    smallestTier: SettlementTier = SettlementTier.HAMLET
): NearestSettlement {
    val ruler = settlementGrid.ruler
    for (search in settlementSearches) {
        if (search.tier < smallestTier) continue
        val settlement = settlementGrid.getFeatureTree(search.treeId)
            .getNearestFeature(location, ruler, search.proximity) as MvtFeature?
        if (settlement?.name != null) {
            return NearestSettlement(settlement, search.tier)
        }
    }
    return NearestSettlement(null, null)
}

/**
 * A settlement the direction of travel is heading towards - see [settlementAhead].
 *
 * [distance] is the straight-line distance, which is what gets spoken; [alongTrack] is its
 * component along the direction of travel, which is what candidates are ranked by.
 */
data class SettlementAhead(
    val feature: MvtFeature,
    val tier: SettlementTier,
    val distance: Double,
    val alongTrack: Double
)

/**
 * How far ahead [settlementAhead] looks. GeoEngine.lookaheadGrid is 3x3 tiles at zoom 10, about
 * 66 km across at UK latitudes and so comfortably wider than this - meaning this is the limit that
 * actually binds, which is the point of that grid existing. A caller passing a higher-zoom grid
 * instead gets whatever that one happens to cover; a settlement outside it simply isn't in the
 * tree, and the search quietly finds nothing rather than misbehaving.
 */
private const val SETTLEMENT_AHEAD_MAX_DISTANCE = 10000.0

/**
 * Half the width of the wedge searched ahead. Roads bend, so this has to be wide enough that a
 * settlement the road curves round to still counts, while staying narrow enough that the answer is
 * recognisably "up ahead" rather than off to one side.
 */
private const val SETTLEMENT_AHEAD_HALF_ANGLE = 30.0

/**
 * How far to the side of the direction of travel a settlement may sit. A fixed angle alone is the
 * wrong shape: 30 degrees is a sensible spread a kilometre out but four kilometres of it at eight,
 * which is wide enough to pick up a village in the next valley that this road never reaches. Adding
 * a corridor width turns the search area into a wedge that stops widening - in practice the angle
 * governs what is close and this governs what is far.
 */
private const val SETTLEMENT_AHEAD_MAX_CROSS_TRACK = 2000.0

/**
 * The same two limits, relaxed, for the settlement already being headed for - see
 * [SettlementAheadTracker]. It is harder to become the destination than to remain it, because the
 * two jobs are different: choosing needs to be sure the place is really up ahead, while holding
 * only needs it to still be roughly where it was. Without the gap, one bend putting a village a few
 * degrees outside the wedge was enough to drop it and pick another, and the next bend brought it
 * back - the A81 south through Strathblane gave Blanefield, Milngavie, Blanefield in three
 * consecutive callouts. The wedge searched is widened to the hold angle so an incumbent that has
 * drifted is still found; newcomers are then held to the stricter limits above.
 */
private const val SETTLEMENT_AHEAD_HOLD_HALF_ANGLE = 60.0
private const val SETTLEMENT_AHEAD_HOLD_MAX_CROSS_TRACK = 3000.0

/**
 * Finds the settlement the user is travelling towards, as opposed to the one they are currently at.
 *
 * [nearestSettlement] answers "which settlement am I in", which is what an address wants, and its
 * proximities are sized accordingly - a village counts only within 2 km. Someone travelling wants
 * the opposite: the village the road is heading for, which may be well beyond that and is the more
 * useful landmark even when something smaller is closer to hand.
 *
 * Only villages and towns are searched. Hamlets are excluded because an isolated dwelling or farm
 * several kilometres off is not something anyone navigates by. Cities are excluded because their
 * near-field proximity is already 15 km, so a city ahead is found by [nearestSettlement] anyway -
 * including it here would change nothing but the wording.
 *
 * Candidates must also lie *beyond* their own tier's near-field proximity, so this only ever
 * extends the reach of the search; whatever [nearestSettlement]'s smallest-first cascade could
 * already see is left for it to decide. Of what remains, the nearest wins - that's the one the
 * user will actually reach first - except that a settlement already being headed for holds its
 * place for as long as it still qualifies, see [tracker].
 *
 * Must be called from within [lookaheadGrid]'s treeContext.
 *
 * @param lookaheadGrid the low-zoom grid to search - GeoEngine.lookaheadGrid in production. It has
 * to be a grid wide enough to hold somewhere several kilometres off, which is why it isn't the
 * settlement grid the near-field search uses. Only villages and towns are read from it, and those
 * survive down to zoom 10 intact even though hamlets and suburbs do not.
 * @param smallestTier the smallest kind of settlement worth naming, raised to at least
 * [SettlementTier.VILLAGE] by the caller's road class - see roadClassSmallestSettlement.
 * @param tracker what was named last time, if anything. It wins over a better-ranked newcomer, but
 * only while it still passes every test below - so it is held through the bends that would
 * otherwise unseat it, and dropped the moment it stops being somewhere this road is heading.
 */
fun settlementAhead(
    lookaheadGrid: GridState,
    location: LngLatAlt,
    headingDegrees: Double,
    smallestTier: SettlementTier = SettlementTier.VILLAGE,
    maxDistance: Double = SETTLEMENT_AHEAD_MAX_DISTANCE,
    tracker: SettlementAheadTracker? = null
): SettlementAhead? {
    val ruler = lookaheadGrid.ruler
    // Left edge then right edge, as getFovTriangle orders them. The bearings aren't normalized
    // because getDestinationCoordinate works in radians through sin/cos, which are periodic.
    val triangle = Triangle(
        location,
        getDestinationCoordinate(
            location, headingDegrees - SETTLEMENT_AHEAD_HOLD_HALF_ANGLE, maxDistance
        ),
        getDestinationCoordinate(
            location, headingDegrees + SETTLEMENT_AHEAD_HOLD_HALF_ANGLE, maxDistance
        )
    )

    var nearest: SettlementAhead? = null
    var remembered: SettlementAhead? = null
    for (search in settlementSearches) {
        if (search.tier < smallestTier) continue
        if (search.tier > SettlementTier.TOWN) continue
        for (feature in lookaheadGrid.getFeatureTree(search.treeId)
            .getAllWithinTriangle(triangle).features) {
            val settlement = feature as? MvtFeature ?: continue
            if (settlement.name == null) continue
            val point = (settlement.geometry as? Point)?.coordinates ?: continue
            val distance = ruler.distance(location, point)
            // Anything this close is already the near-field search's business.
            if (distance <= search.proximity) continue

            val offsetDegrees = calculateHeadingOffset(
                headingDegrees, ruler.bearing(location, point)
            )
            val offset = toRadians(offsetDegrees)
            val crossTrack = distance * sin(offset)
            // Ranked by how far along the direction of travel it is rather than by straight-line
            // distance, so that of two settlements the same distance away the one more nearly
            // straight ahead is the one named.
            val alongTrack = distance * cos(offset)
            val candidate = SettlementAhead(settlement, search.tier, distance, alongTrack)

            if (tracker?.matches(settlement) == true) {
                if ((offsetDegrees <= SETTLEMENT_AHEAD_HOLD_HALF_ANGLE) &&
                    (crossTrack <= SETTLEMENT_AHEAD_HOLD_MAX_CROSS_TRACK)
                ) {
                    remembered = candidate
                }
                continue
            }

            if (offsetDegrees > SETTLEMENT_AHEAD_HALF_ANGLE) continue
            if (crossTrack > SETTLEMENT_AHEAD_MAX_CROSS_TRACK) continue
            if ((nearest == null) || (alongTrack < nearest.alongTrack)) {
                nearest = candidate
            }
        }
    }
    // Reaching here at all means the remembered settlement passed every filter a new candidate has
    // to, so it's still genuinely ahead and still worth naming - just possibly no longer the
    // best-ranked. Continuity is worth more than that ranking: being told the road leads to the
    // same place it led to a minute ago is the whole point of saying it.
    return remembered ?: nearest
}

/**
 * Distances are rounded so that they're as short as possible to read out, because every syllable
 * spoken is time the user isn't hearing the next callout:
 *
 *  small units (metres/feet) | below 100, to the nearest 5; from 100 up, to the nearest 10 so
 *                            | that there's no unit digit to read out ("110", not "114")
 *  big units (km/miles)      | below 10, to 1 decimal place; from 10 up, to a whole big unit.
 *                            | A trailing ".0" is always dropped - "1 km", never "1.0 km".
 *
 * Above [UserGeometry.BIG_UNIT_SPEED_THRESHOLD_MPS] big units are used whatever the distance -
 * see [speed].
 *
 * The iOS imperial docs are wrong, and in fact distances are all in feet and we can round in the
 * same way as metric.
 */
var metric = true

/**
 * @param speed the user's speed in m/s, if known. Above
 * [UserGeometry.BIG_UNIT_SPEED_THRESHOLD_MPS] the distance is always given in big units
 * (kilometres/miles), as metre/foot precision is worthless at that speed. Callers with no idea of
 * the user's speed (UI showing a distance to a saved marker, say) leave it at the default and
 * always get small units for short distances.
 */
fun formatDistanceAndDirection(
    distance: Double,
    heading: Double?,
    localized: LocalizedStrings?,
    userHeading: Double? = null,
    relativeTimeMode: String = "ClockFace",
    forAccessibility: Boolean = false,
    speed: Double = 0.0
): String {
    var units = distance
    var bigUnitDivisor = 100
    if (!metric) {
        units = (distance * 1.09361 * 3)
        bigUnitDivisor = (176 * 3)
    }

    val roundToNearest = if (units < 100) 5.0 else 10.0
    val roundedDistance = (units / roundToNearest).roundToInt() * roundToNearest

    val bigUnits = units / (bigUnitDivisor * 10.0)
    // Tenths of a big unit, which is what decides between the big unit roundings, and whether
    // there's enough distance to express in big units at all.
    val bigUnitTenths = round(bigUnits * 10)
    // Whole big units from 10 up, and any exact number of big units below that - there's nothing
    // for a decimal place to say about "1.0 km" that "1 km" doesn't.
    val bigUnitDecimals = if ((bigUnitTenths >= 100) || (bigUnitTenths % 10.0 == 0.0)) 0 else 1

    // At speed, small units are false precision and cost more to say than they're worth - unless
    // we're so close that big units would round down to nothing.
    val alwaysBigUnits =
        (speed > UserGeometry.BIG_UNIT_SPEED_THRESHOLD_MPS) && (bigUnitTenths >= 1)

    val distanceText: String
    if ((roundedDistance < 1000) && !alwaysBigUnits) {
        val wholeUnits = roundedDistance.toInt()
        val smallUnitKey = if (metric) PluralKey.DistanceMeters else PluralKey.DistanceFeet
        distanceText = localized?.getPlural(smallUnitKey, wholeUnits, wholeUnits.toString())
            ?: "$wholeUnits ${if (wholeUnits == 1) "metre" else "metres"}"
    } else {
        val separator = decimalSeparator(localized, forAccessibility)
        val formatted = formatDecimal(
            bigUnits,
            decimals = bigUnitDecimals,
            separator = separator,
            spaceFractionalDigits = forAccessibility,
        )
        // Plural rules select on a whole number, but "1.4 km" isn't one, so the fraction never
        // reaches them. Which whole number stands in for it is a per-language question - French
        // and Portuguese take the singular for "1,4 kilomètre", where English takes the plural -
        // so ask the strings themselves rather than assuming.
        val quantity =
            if (bigUnitDecimals == 0) round(bigUnits).toInt()
            else localized?.fractionalPluralQuantity ?: 2
        val bigUnitKey = if (metric) {
            if (forAccessibility) PluralKey.DistanceKmA11y else PluralKey.DistanceKm
        } else {
            PluralKey.DistanceMiles
        }
        distanceText = localized?.getPlural(bigUnitKey, quantity, formatted) ?: "$formatted km"
    }

    var headingText = ""
    if (heading != null) {
        if (userHeading == null) {
            if (localized != null)
                headingText = ", " + localized.get(getCompassLabel(heading.toInt()))
        } else {
            when (relativeTimeMode) {
                "ClockFace" -> {
                    val timeHeading = getRelativeClockTime(heading.toInt(), userHeading.toInt())
                    headingText = ", " +
                            (localized?.get(
                                StringKey.RelativeClockDirection,
                                timeHeading.toString()
                            )
                                ?: "at $timeHeading o'clock")
                }

                "Degrees" -> {
                    val relativeHeading = (heading - userHeading)
                    val degrees = normalizeHeading(((relativeHeading / 5.0).roundToInt() * 5))
                    headingText = ", " +
                            (localized?.get(StringKey.RelativeDegreesDirection, degrees.toString())
                                ?: "at $degrees degrees")
                }

                "LeftRight" -> {
                    val labelKey = getRelativeLeftRightLabel((heading - userHeading).toInt())
                    headingText = ", " + (localized?.get(labelKey) ?: when (labelKey) {
                        StringKey.RelativeLeftRightDirectionAhead -> "Ahead"
                        StringKey.RelativeLeftRightDirectionAheadRight -> "Ahead right"
                        StringKey.RelativeLeftRightDirectionRight -> "Right"
                        StringKey.RelativeLeftRightDirectionBehindRight -> "Behind right"
                        StringKey.RelativeLeftRightDirectionBehind -> "Behind"
                        StringKey.RelativeLeftRightDirectionBehindLeft -> "Behind left"
                        StringKey.RelativeLeftRightDirectionLeft -> "Left"
                        StringKey.RelativeLeftRightDirectionAheadLeft -> "Ahead left"
                        else -> "Unknown"
                    })
                }
            }
        }
    }
    return "$distanceText$headingText"
}

internal fun decimalSeparator(localized: LocalizedStrings?, forAccessibility: Boolean): String {
    val key = if (forAccessibility) StringKey.NumberDecimalSeparatorA11y
    else StringKey.NumberDecimalSeparator
    return localized?.get(key) ?: if (forAccessibility) " point " else "."
}

internal fun formatDecimal(
    value: Double,
    decimals: Int,
    separator: String = ".",
    spaceFractionalDigits: Boolean = false,
): String {
    val factor = when (decimals) {
        0 -> 1L
        1 -> 10L
        2 -> 100L
        3 -> 1000L
        else -> 100L
    }
    val rounded = round(value * factor).toLong()
    val sign = if (rounded < 0) "-" else ""
    val absVal = abs(rounded)
    val whole = absVal / factor
    val frac = absVal % factor
    if (decimals == 0) return "$sign$whole"
    val fracStr = frac.toString().padStart(decimals, '0')
    val fracOut = if (spaceFractionalDigits) fracStr.toCharArray().joinToString(" ") else fracStr
    return "$sign$whole$separator$fracOut"
}

/**
 * How a road is named to someone travelling: its route number and its local street name together
 * where it has both, "A81 (Glasgow Road)".
 *
 * The number matters to a traveller in a way it doesn't to a pedestrian. It is how the road is
 * signposted and how it is talked about, and it is the part that stays put while the street name
 * changes along it - the A81 through Milngavie is Strathblane Road, then Glasgow Road, then Main
 * Street.
 *
 * Used both for the road being travelled along and for a road being crossed - see
 * AutoCallout.announceableCrossing, where a train passing over the A81 should say so rather than
 * naming only the street it happens to be called there.
 *
 * @param ref the road's route number, or null where it has none or shouldn't be spoken - a railway
 * line carries no road-style ref, so callers pass null for a train.
 * @param ownName the name the road actually carries in OSM ([MvtFeature.displayName]), which is
 * what the ref is worth pairing with.
 * @param describedName what [Way.getName] made of the road, used when there is no ref to pair
 * with. That may be a confected description rather than a real name, which is exactly why it is
 * kept separate from [ownName]: a road with a ref and no name of its own gets a description built
 * around the ref ("A779 that joins Tailend Moss and Old Deans Road"), and pairing *that* with the
 * ref produced "A91 (A91 that joins Mathieson Gardens and Middleflat)". Such a road is just its
 * number.
 */
fun roadNameWithRef(
    ref: String?,
    ownName: String?,
    describedName: String?,
    localized: LocalizedStrings?
): String? = when {
    ref == null -> describedName
    (ownName == null) || (ownName == ref) -> ref
    else -> localized?.get(StringKey.DirectionsRoadWithRefAndName, ref, ownName)
        ?: "$ref ($ownName)"
}

/**
 * Tracks the last railway station passed while travelling by train, so travel-mode reverse
 * geocoding can describe progress along the line as "distance since {station}" rather than just
 * naming the line - see [UserGeometry.probablyOnTrain]. A single reverse-geocode call has no
 * memory of previous ones, so this is held by the caller (AutoCallout) and passed in each time.
 */
class LastStationTracker {
    var name: String? = null
    var location: LngLatAlt? = null

    fun updateStation(newName: String, newLocation: LngLatAlt?) {
        name = newName
        location = newLocation
    }

    /**
     * Called when the ride the station was measured on has ended. A distance since a station is a
     * statement about progress along one journey, and means nothing once that journey is over -
     * see AutoCallout.buildCalloutForRoadSense, which decides when that has happened.
     */
    fun clear() {
        name = null
        location = null
    }
}

/**
 * Tracks which settlement was last named as the one being travelled towards, so that it keeps being
 * named for as long as it remains a sensible answer.
 *
 * Without this the destination flips about. [settlementAhead] re-ranks candidates from scratch on
 * every call, and the wedge it searches turns with the road, so a bend is enough to bring a
 * different village to the front: driving south on the A81 gave "towards Blanefield", then "towards
 * Milngavie", then "towards Blanefield" again, each true at the moment it was said and the sequence
 * as a whole meaningless. Worse, each change is a fresh dedup key, so a flip is a callout.
 *
 * Identity is the OSM id rather than the feature itself: the grids are rebuilt as the user moves,
 * and holding a feature across a rebuild would pin an object belonging to a discarded tile and
 * describe it with coordinates that are no longer the ones being searched.
 *
 * A single reverse-geocode call has no memory of previous ones, so this is held by the caller
 * (AutoCallout) and passed in each time.
 */
class SettlementAheadTracker {
    private var osmId: Long? = null

    fun remember(feature: MvtFeature) {
        osmId = feature.osmId
    }

    fun matches(feature: MvtFeature): Boolean = (osmId != null) && (feature.osmId == osmId)

    /**
     * Called when the settlement being headed for stops being the answer - either the journey it
     * belonged to has ended, or the near-field search has taken over because the user is now
     * somewhere rather than going somewhere. Holding it past that point would resurrect a stale
     * destination the next time an open road came along.
     */
    fun clear() {
        osmId = null
    }
}

/**
 * Tracks how recently something notable (a major road junction, or a passed large POI) was last
 * announced while travelling by car/bus, so a quiet stretch with nothing major nearby can still
 * fall back to mentioning a minor road junction instead of staying silent indefinitely. A single
 * reverse-geocode call has no memory of previous ones, so this is held by the caller (AutoCallout)
 * and passed in/updated each time - see the junction selection in [travellingReverseGeocodeName]
 * and AutoCallout.buildCalloutForVehicleLandmark.
 */
class NotableVehicleEventTracker {
    private var lastEventTimestampMs: Long? = null

    fun recordEvent(timestampMilliseconds: Long) {
        lastEventTimestampMs = timestampMilliseconds
    }

    fun quietFor(timestampMilliseconds: Long, thresholdMilliseconds: Long): Boolean {
        val last = lastEventTimestampMs ?: return true
        return (timestampMilliseconds - last) > thresholdMilliseconds
    }
}

// How long nothing notable (major junction/large POI) needs to have been announced before a minor
// road junction becomes eligible for a callout too - see NotableVehicleEventTracker.
private const val MINOR_JUNCTION_QUIET_THRESHOLD_MS = 90_000L

// Highway junction "class" tiers (from the junction feature's "class" property - see
// extractHighwayJunctions), used to prefer major junctions and only fall back to minor ones after
// a quiet spell. Deliberately excludes paths/tracks/service roads and anything with no known class
// - a junction with an unrecognised or missing class is never called out.
private val majorHighwayJunctionClasses = setOf("motorway", "trunk", "primary")
private val minorHighwayJunctionClasses =
    setOf("secondary", "tertiary", "residential", "unclassified", "living_street")

// How small a settlement is worth naming alongside the road being travelled, keyed on the road's
// class (Way.featureValue - see translateProperties). A hamlet is a useful landmark on a country
// lane, but on a motorway it's a name nobody navigates by and one that's gone before it's been
// spoken - so the faster the road, the larger the settlement has to be to earn a mention. Roads
// not listed here (and railways, which have no road class) keep the default of naming anything.
private val roadClassSmallestSettlement = mapOf(
    "motorway" to SettlementTier.TOWN,
    "trunk" to SettlementTier.VILLAGE,
)

/**
 * @param text the text to actually speak.
 * @param dedupText the text to use for callout-history comparison - defaults to [text], but for
 * callouts that embed an ever-changing value (e.g. a live "distance since X") this should be the
 * same text with that value left out, so the callout can still dedup against an earlier one that
 * differs only in that value. See [PositionedString.dedupText].
 */
private data class ReverseGeocodeText(
    val text: String,
    val dedupText: String = text,
    val extraDedupText: String? = null
)

// How near a station has to be, measured along the line, to be the thing worth saying. "At" is
// symmetric - a station just behind is as much where the train is as one just ahead - while
// "approaching" only looks forward, and reaches far enough to be of use at line speed.
private const val stationAtDistanceMetres = 200.0
private const val stationApproachingDistanceMetres = 500.0

/** The nearest named station within [maxDistance] along the line from [cursor], or null. */
private fun namedStationWithin(cursor: WayCursor, maxDistance: Double): AlongWayFeatureAhead? =
    nextAlongWayFeature(
        cursor, maxDistance, AlongWayKind.RAILWAY_STOP, WayContinuation.SAME_ROAD
    )?.takeIf { it.feature.name != null }

private fun travellingReverseGeocodeName(
    userGeometry: UserGeometry,
    gridState: GridState,
    settlementGrid: GridState,
    localized: LocalizedStrings?,
    lastStationTracker: LastStationTracker? = null,
    notableEventTracker: NotableVehicleEventTracker? = null,
    lookaheadGrid: GridState = settlementGrid,
    settlementAheadTracker: SettlementAheadTracker? = null,
): ReverseGeocodeText? {
    val location = userGeometry.location
    if (!gridState.isLocationWithinGrid(location)) return null

    // Passing a bus/tram/train stop is announced separately - see
    // AutoCallout.buildCalloutForVehicleTransitStop - since it needs to sweep the path travelled
    // since the last location update (a point-radius check here would miss most stops, as this
    // function is only checked periodically and a stop's detection radius is easily crossed
    // between checks at driving speed).

    val probablyOnTrain = userGeometry.probablyOnTrain()

    // On a train, a station close by is the whole answer - the line and the settlement say nothing
    // a passenger wants at that moment. Measured along the rails rather than as the crow flies, so
    // "approaching" is 500m of travel rather than 500m of map, and so that a station behind can be
    // told from one ahead on a curving approach.
    if (probablyOnTrain) {
        val cursor = userGeometry.mapMatchedRailway?.let { userGeometry.cursorOn(it) }
        if (cursor != null) {
            val forwards = cursor.forwards
            // Without a direction of travel the walk goes both ways on its own, which is what
            // "either direction" wants - so this one call covers both when forwards is null.
            val nearAhead = namedStationWithin(cursor, stationAtDistanceMetres)
            val nearBehind = forwards?.let {
                namedStationWithin(cursor.copy(forwards = !it), stationAtDistanceMetres)
            }

            val at = listOfNotNull(nearAhead, nearBehind).minByOrNull { it.distance }
            if (at != null) {
                // Also what "since" counts from further down, so the two agree about which station
                // was last called at.
                lastStationTracker?.updateStation(at.feature.displayName!!, at.feature.point)
                return ReverseGeocodeText(
                    localized?.get(StringKey.DirectionsAtPoi, at.feature.displayName!!)
                        ?: "At ${at.feature.displayName}"
                )
            }

            // Only with a known direction: without one, the walk above would have reported a
            // station behind as though it were coming up.
            if (forwards != null) {
                val approaching = namedStationWithin(cursor, stationApproachingDistanceMetres)
                if (approaching != null) {
                    return ReverseGeocodeText(
                        localized?.get(StringKey.DirectionsApproachingName, approaching.feature.displayName!!)
                            ?: "Approaching ${approaching.feature.displayName}"
                    )
                }
            }
        }
    }

    // Prefer the map-matched way (the road/railway we're actually confirmed to be on) over an
    // independent nearest-feature search, which can pick the wrong road at junctions or parallel
    // carriageways. Since we're confirmed to be on it (rather than merely near it), phrase it as
    // "On X" rather than "Near X". A train is matched against the separate railway network -
    // there's no independent-search fallback for it, since a lower-confidence guess at a railway
    // line is much less useful than one for a road (you can't be "near" a railway in the way you
    // can be near a road, e.g. on a parallel street - either the matcher has locked onto the line
    // you're travelling on, or it hasn't).
    // This whole function only ever runs while in a vehicle (see AutoCallout.buildCalloutForRoadSense),
    // so the fallback search is restricted to TreeId.ROADS - a car/bus can't be on a footway/cycleway,
    // and TreeId.ROADS_AND_PATHS would otherwise let one get picked as the nearest "road".
    val nearestRoad = if (probablyOnTrain) {
        userGeometry.mapMatchedRailway
    } else {
        userGeometry.mapMatchedWay ?: gridState.getNearestFeature(
            TreeId.ROADS, gridState.ruler, location, 100.0
        ) as Way?
    }
    val roadName = nearestRoad?.getName(null, gridState, localized, true)?.takeIf { it.isNotEmpty() }

    // A numbered road keeps its identity across name changes: the A81 through Milngavie is
    // Strathblane Road, then Glasgow Road, then Main Street, but to someone travelling along it
    // that's one road, and re-announcing at each boundary is just noise. So where a road carries
    // a route number, that ref becomes both the dedup identity (see roadDedup below) and part of
    // the spoken name. Railway lines don't carry a road-style ref and have their own naming path
    // in Way.getName(), so trains are excluded.
    val roadRef = if (!probablyOnTrain) nearestRoad?.ref else null
    val spokenRoadName =
        roadNameWithRef(roadRef, nearestRoad?.displayName, roadName, localized)
    // Used in the dedup keys below in place of the spoken name, which embeds the street name and
    // so changes along an unchanged road.
    val roadIdentity = roadRef ?: roadName

    // A numbered road is identified by its ref alone: neither a change of street name nor a new
    // settlement alongside it means we've reached a different road, so neither is a reason to
    // announce it again. A road with no ref has nothing but its name to identify it, so it keeps
    // the fuller key and a genuine change of street still gets announced.
    fun roadDedup(fullKey: String): String = roadRef ?: fullKey

    // The direction of travel along a road (e.g. "Traveling north along M8") - using the
    // map-matched heading, which snaps to the road's own tangent (see UserGeometry.snappedHeading)
    // - is only meaningful for an actual road, not a railway line. Every road callout below goes
    // through roadPhrase() so they read consistently, rather than some saying "On M8" and others
    // "Traveling north along M8"; it only falls back to the bare "On M8" form when there's no
    // heading to work with (getCompassLabelFacingDirectionAlong has its own English fallback text
    // for a null localized, e.g. in tests, so this doesn't need to check for that separately).
    val travelHeadingDegrees = if (!probablyOnTrain) userGeometry.snappedHeading()?.toInt() else null
    fun roadPhrase(name: String): String =
        if (travelHeadingDegrees != null) {
            getCompassLabelFacingDirectionAlong(localized, travelHeadingDegrees, name, true, true)
        } else {
            localized?.get(StringKey.DirectionsOnRoad, name) ?: "On $name"
        }

    // Check if we're near a highway junction (motorway exit, interchange etc.) - not relevant
    // when travelling by train. Major junctions (motorway/trunk/primary) are always eligible;
    // minor ones only become eligible once nothing notable has been announced for a while, so a
    // quiet residential junction doesn't compete with a nearby motorway interchange. Junctions
    // with an unrecognised/missing class (this also covers paths/tracks/service roads, which
    // should never be called out) are never eligible.
    if (!probablyOnTrain) {
        val junctionTree = gridState.getFeatureTree(TreeId.HIGHWAY_JUNCTIONS)
        val nearbyJunctions = junctionTree.getNearestCollection(location, 500.0, 5, gridState.ruler)
        val allowMinorJunctions = notableEventTracker?.quietFor(
            userGeometry.timestampMilliseconds, MINOR_JUNCTION_QUIET_THRESHOLD_MS
        ) ?: true
        val nearestJunction = nearbyJunctions.features.firstOrNull { feature ->
            when ((feature as MvtFeature).properties?.get("class") as? String) {
                in majorHighwayJunctionClasses -> true
                in minorHighwayJunctionClasses -> allowMinorJunctions
                else -> false
            }
        } as MvtFeature?
        if (nearestJunction != null) {
            val ref = nearestJunction.ref
            val name = nearestJunction.displayName
            val junctionText = if (ref != null) {
                if (name != null) {
                    localized?.get(StringKey.DirectionsJunctionWithRefAndName, ref, name)
                        ?: "Junction $ref, $name"
                } else {
                    localized?.get(StringKey.DirectionsJunctionWithRef, ref) ?: "Junction $ref"
                }
            } else {
                name
            }
            if (junctionText != null) {
                notableEventTracker?.recordEvent(userGeometry.timestampMilliseconds)
                // dedupText excludes the direction of travel (unlike the spoken text) - on a
                // winding road, the compass direction can shift tick to tick while the road and
                // junction stay the same, and that shouldn't be treated as a new thing to
                // announce (see the equivalent reasoning below for the plain "on road" callouts).
                if (spokenRoadName != null) {
                    return ReverseGeocodeText(
                        text = if (travelHeadingDegrees != null) {
                            "${roadPhrase(spokenRoadName)} " + (
                                localized?.get(StringKey.DirectionsAtJunctionInline, junctionText)
                                    ?: "at $junctionText"
                                )
                        } else {
                            localized?.get(
                                StringKey.DirectionsOnRoadAtJunction, spokenRoadName, junctionText
                            ) ?: "On $spokenRoadName at $junctionText"
                        },
                        // Unlike the callouts below, this key keeps the junction in it rather
                        // than collapsing to the road's identity - reaching a junction is a new
                        // thing to announce even though we're still on the same road.
                        dedupText = "On $roadIdentity at $junctionText",
                        // Having just been told we're at a junction on the A81, being told a few
                        // seconds later that we're still on the A81 adds nothing. So the junction
                        // callout also claims the plain road key that such a callout would use,
                        // which holds it off until the history expires. Only for a numbered road:
                        // a ref-less road's plain key varies with the settlement phrasing, so
                        // there's no single key to claim. See TrackedCallout.extraDedupText for
                        // why this is recorded separately rather than matched on.
                        extraDedupText = roadRef
                    )
                }
                return ReverseGeocodeText(
                    localized?.get(StringKey.DirectionsNearName, junctionText) ?: "Near $junctionText"
                )
            }
        }
    }

    // Check if we're inside a POI
    val gridPoiTree = gridState.getFeatureTree(TreeId.POIS)
    val insidePois = gridPoiTree.getContainingPolygons(location)
    for (poi in insidePois) {
        val mvtPoi = poi as MvtFeature
        val poiName = mvtPoi.displayName
        if (poiName != null) {
            return ReverseGeocodeText(
                localized?.get(StringKey.DirectionsAtPoi, poiName) ?: "At $poiName"
            )
        }
    }

    // Hamlet/village/town are discrete points a road passes near or through, so they're eligible
    // for the directional "towards X"/"away from X"/"near X" phrasing below. A city is a large,
    // often-merged conurbation where that phrasing would be misleading - you can't be "towards
    // Glasgow" while already inside its urban area - so it keeps the vaguer "close to" instead.
    // The road's own class sets a floor on how small a settlement can be and still be worth
    // naming - see roadClassSmallestSettlement.
    val roadSettlementFloor =
        roadClassSmallestSettlement[nearestRoad?.featureValue] ?: SettlementTier.HAMLET
    val nearby = nearestSettlement(settlementGrid, location, roadSettlementFloor)

    // On an open road the settlement being travelled towards is the more useful landmark: a farm
    // 400m away says nothing about where this road goes, while the village 6km up it does. So a
    // larger settlement ahead displaces a smaller one to hand. It has to be *larger* - a village
    // being passed through beats one further up the road - and, because settlementAhead only looks
    // beyond the near-field proximities, this can never re-rank two settlements the cascade in
    // nearestSettlement already weighed against each other.
    val ahead = travelHeadingDegrees?.let {
        settlementAhead(
            lookaheadGrid, location, it.toDouble(),
            maxOf(SettlementTier.VILLAGE, roadSettlementFloor),
            tracker = settlementAheadTracker
        )
    }
    val useAhead = (ahead != null) && ((nearby.tier == null) || (ahead.tier > nearby.tier))
    // Only remember what actually gets said. Recording the search's result regardless would let a
    // destination that lost to the near-field settlement come back later as the sticky answer,
    // which is the opposite of what the tracker is for; and letting go here means arriving
    // somewhere hands the thread back to the near-field search cleanly.
    if (useAhead) {
        settlementAheadTracker?.remember(ahead!!.feature)
    } else {
        settlementAheadTracker?.clear()
    }
    val settlement = if (useAhead) {
        NearestSettlement(ahead!!.feature, ahead.tier)
    } else {
        nearby
    }
    val settlementFeature = settlement.feature
    val settlementName = settlement.displayName
    val settlementIsCity = settlement.isCity

    if (spokenRoadName != null) {
        val phrase = roadPhrase(spokenRoadName)

        // Distance since the last station is only worth mentioning alongside something else worth
        // describing (a nearby settlement) - otherwise it ends up as a standalone callout that
        // fires on every location update as the distance keeps climbing, which is far too
        // frequent on its own (see real train-1/train-2.gpx replays).
        val sinceStationName = lastStationTracker?.name
        val sinceStationLocation = lastStationTracker?.location
        if (probablyOnTrain && (settlementName != null) &&
            (sinceStationName != null) && (sinceStationLocation != null)
        ) {
            // The distance climbs on every call, so it's never suppressed as a duplicate if it
            // were included in the dedup comparison - dedupText leaves it out, so this only
            // re-announces when the road/settlement/station combination actually changes.
            val distanceText = formatDistanceAndDirection(
                gridState.ruler.distance(location, sinceStationLocation), null, localized,
                speed = userGeometry.speed
            )
            return ReverseGeocodeText(
                text = localized?.get(
                    StringKey.DirectionsOnRoadAndSettlementSince,
                    spokenRoadName, settlementName, distanceText, sinceStationName
                ) ?: "On $spokenRoadName and close to $settlementName, $distanceText since $sinceStationName",
                // The station this is measured from is the whole key. The settlement is left out
                // for the same reason a numbered road's street name is (see roadDedup): at line
                // speed the nearest one changes almost every location update, so keying on it
                // re-announced the same stretch of the same journey over and over. The distance is
                // left out because it climbs on every call and would defeat deduping entirely. And
                // the line is left out because its name isn't stable enough to key on - the rail
                // matcher flickers onto depot sidings and adjacent lines mid-journey, and each
                // flicker would be a fresh announcement. What is left is the one thing that
                // genuinely marks progress: the last station called at. This key is never spoken,
                // so it needs no localizing.
                dedupText = "since $sinceStationName"
            )
        }

        // Trains don't get the directional towards/away from/near settlement phrasing below
        // (travelHeadingDegrees is always null for a train - see roadPhrase above), just a plain
        // settlement mention.
        if (probablyOnTrain) {
            return ReverseGeocodeText(
                text = if (settlementName != null) {
                    localized?.get(
                        StringKey.DirectionsOnRoadAndSettlement, spokenRoadName, settlementName
                    ) ?: "On $spokenRoadName and close to $settlementName"
                } else {
                    phrase
                },
                // The settlement is out of the key for the same reason as in the "since station"
                // case above - passing one isn't news about which line we're on. This is the form
                // used before any station has been tracked, so there's nothing else to mark
                // progress by and the line alone is the whole key. Equivalent to roadIdentity (a
                // train never carries a roadRef - see above), but expressed through spokenRoadName
                // so the compiler can see it's non-null in this branch.
                dedupText = "On $spokenRoadName"
            )
        }

        // A discrete settlement (hamlet/village/town) the road runs towards, away from, or past
        // gets phrased relative to the direction of travel; a city keeps the vaguer "close to"
        // (see settlementIsCity above). Both need a heading to work out the relative
        // bearing, so without one this falls through to the plain road/settlement mention below.
        val settlementLocation = (settlementFeature?.geometry as? Point)?.coordinates
        if ((settlementName != null) && (travelHeadingDegrees != null) &&
            (settlementLocation != null)
        ) {
            if (settlementIsCity) {
                return ReverseGeocodeText(
                    text = "$phrase " + (
                        localized?.get(StringKey.DirectionsCloseToSettlementInline, settlementName)
                            ?: "close to $settlementName"
                        ),
                    // Excludes the direction of travel - see the dedupText comment further below.
                    dedupText = roadDedup("On $roadName close to $settlementName")
                )
            }

            val headingOffset = calculateHeadingOffset(
                travelHeadingDegrees.toDouble(), gridState.ruler.bearing(location, settlementLocation)
            )
            val (settlementPhrase, dedupSuffix) = when {
                headingOffset <= 45.0 -> {
                    val distanceText = formatDistanceAndDirection(
                        gridState.ruler.distance(location, settlementLocation), null, localized,
                        speed = userGeometry.speed
                    )
                    Pair(
                        localized?.get(
                            StringKey.DirectionsTowardsSettlement, settlementName, distanceText
                        ) ?: "towards $settlementName, $distanceText away",
                        "towards $settlementName"
                    )
                }
                headingOffset >= 135.0 -> {
                    val distanceText = formatDistanceAndDirection(
                        gridState.ruler.distance(location, settlementLocation), null, localized,
                        speed = userGeometry.speed
                    )
                    Pair(
                        localized?.get(
                            StringKey.DirectionsAwayFromSettlement, settlementName, distanceText
                        ) ?: "away from $settlementName, $distanceText away",
                        "away from $settlementName"
                    )
                }
                else -> Pair(
                    localized?.get(StringKey.DirectionsNearSettlementInline, settlementName)
                        ?: "near $settlementName",
                    "near $settlementName"
                )
            }
            return ReverseGeocodeText(
                text = "$phrase $settlementPhrase",
                // Excludes both the ever-changing distance (see the "since station" case above)
                // and the direction of travel - on a winding road the compass direction can shift
                // tick to tick while the road and the towards/away/near relationship stay the
                // same, and that alone shouldn't trigger a fresh announcement.
                dedupText = roadDedup("On $roadName $dedupSuffix")
            )
        }

        return ReverseGeocodeText(
            text = if (settlementName != null) {
                localized?.get(
                    StringKey.DirectionsOnRoadAndSettlement, spokenRoadName, settlementName
                ) ?: "On $spokenRoadName and close to $settlementName"
            } else {
                phrase
            },
            // Excludes the direction of travel (see the equivalent dedupText comments above) - on
            // a winding road, phrase's compass direction can shift tick to tick purely from the
            // road's own bends, well before the road or settlement actually changes.
            dedupText = if (settlementName != null) {
                roadDedup("On $roadName and close to $settlementName")
            } else {
                // Equivalent to roadIdentity, but expressed through roadDedup so the compiler
                // can see it's non-null in this branch (spokenRoadName already is).
                roadDedup(spokenRoadName)
            }
        )
    }

    // With no road to hang it on there's nothing to be travelling *along*, so this says where the
    // user is rather than where they're going - and it has to use the near-field settlement, since
    // "Near Croftamie" about somewhere 5 km up the road would be a plain lie.
    val nearbyName = nearby.displayName
    if (nearbyName != null) {
        return ReverseGeocodeText(
            localized?.get(StringKey.DirectionsNearName, nearbyName) ?: "Near $nearbyName"
        )
    }

    return null
}

/** Reverse geocodes a location into 1 of 4 possible states
 * - within a POI
 * - alongside a road
 * - general location
 * - unknown location.
 */
fun describeReverseGeocode(
    userGeometry: UserGeometry,
    gridState: GridState,
    settlementGrid: GridState,
    localized: LocalizedStrings?,
    lastStationTracker: LastStationTracker? = null,
    notableEventTracker: NotableVehicleEventTracker? = null,
    /**
     * The even lower-zoom grid the "heading towards" search runs against - see [settlementAhead]
     * and GeoEngine.lookaheadGrid. It trails the trackers so that the many existing positional
     * callers don't have to move, and defaults to [settlementGrid] for callers that have no
     * separate one; that costs them only reach, since the z12 grid holds a superset of the
     * villages and towns the search looks for.
     */
    lookaheadGrid: GridState = settlementGrid,
    /** Keeps the settlement being headed for stable across calls - see [SettlementAheadTracker]. */
    settlementAheadTracker: SettlementAheadTracker? = null,
): PositionedString? {
    val description =
        travellingReverseGeocodeName(
            userGeometry, gridState, settlementGrid, localized, lastStationTracker,
            notableEventTracker, lookaheadGrid, settlementAheadTracker
        ) ?: return null
    return PositionedString(
        text = description.text,
        dedupText = description.dedupText,
        extraDedupText = description.extraDedupText,
        // The location is kept for callout-history purposes (see TrackedCallout), but the audio
        // is deliberately not spatialized. Every phrasing above describes where the traveller
        // themselves is - the line they're on, the settlement they're near, how far they've come
        // since a station - so there's no feature for a bearing to point at, and the only
        // position available to point at is the traveller's own. Localizing it made the callout
        // play from wherever the vehicle was when the text was generated: harmless on foot, but
        // this callout is vehicle-only, and by the time a train's callout reaches the front of
        // the speech queue the train is a few hundred metres past that point. The source pinned
        // behind the listener, and a rear HRTF reads as quiet and dull - so a callout carrying no
        // directional information was being made harder to hear for the sake of it.
        location = userGeometry.location,
        type = AudioType.STANDARD,
    )
}
