package org.scottishtecharmy.soundscape.geoengine.utils

import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Way
import org.scottishtecharmy.soundscape.i18n.LocalizedStrings
import org.scottishtecharmy.soundscape.i18n.StringKey

/**
 * What a path is like underfoot, in the few kinds a walker can tell apart - with a cane or a
 * glance, gravel is not grass and grass is not boardwalk. The tiles carry the OSM surface as
 * surface_raw on paths and tracks (planetiler-openmaptiles' setPathCharacterAttrs), as OpenMapTiles'
 * own surface is only paved or unpaved.
 */
enum class PathSurface {
    PAVED, GRAVEL, EARTH, GRASS, WOOD, ROCK, SAND;

    companion object {
        /**
         * The kind of an OSM surface value, or null for one that doesn't say (a bare "unpaved") or
         * isn't known. Of a list such as "gravel;grass" the first is taken, as the main one.
         */
        fun fromOsm(value: String?): PathSurface? {
            val first = value?.substringBefore(';')?.trim()?.lowercase() ?: return null
            return when (first) {
                "paved", "asphalt", "concrete", "concrete:plates", "concrete:lanes",
                "paving_stones", "paving_stones:lanes", "sett", "cobblestone",
                "unhewn_cobblestone", "metal", "chipseal", "bricks" -> PAVED
                "gravel", "fine_gravel", "compacted", "pebblestone" -> GRAVEL
                "ground", "dirt", "earth", "mud", "soil", "woodchips" -> EARTH
                "grass", "grass_paver" -> GRASS
                "wood" -> WOOD
                "rock", "stone" -> ROCK
                "sand" -> SAND
                else -> null
            }
        }
    }
}

private val PATH_CLASSES = setOf("path", "track")

/** Whether [this] is a path or a track, the ways whose character the tiles describe. */
val Way.isPathOrTrack: Boolean
    get() = featureClass in PATH_CLASSES

/**
 * What [this] is like underfoot: its OSM surface, or for a track with none its tracktype - grade1
 * is solid and grade2 mostly gravel, while the softer grades mix too much to say.
 */
val Way.pathSurface: PathSurface?
    get() {
        val props = properties ?: return null
        PathSurface.fromOsm(props["surface_raw"]?.toString())?.let { return it }
        return when (props["tracktype"]?.toString()) {
            "grade1" -> PathSurface.PAVED
            "grade2" -> PathSurface.GRAVEL
            else -> null
        }
    }

/** Footway, path, bridleway, steps... or "track", which has no subclass of its own. */
private val Way.pathKind: String?
    get() = featureSubClass ?: featureClass

/**
 * Whether someone on the ground could tell [a] from [b] by what they are like: a different kind of
 * path, or surfaces that are both known and different. A surface missing from the map says nothing
 * either way, so two footways one of which has no surface can't be told apart.
 */
fun pathsDiffer(a: Way, b: Way): Boolean {
    if (a.pathKind != b.pathKind) return true
    val surfaceA = a.pathSurface ?: return false
    val surfaceB = b.pathSurface ?: return false
    return surfaceA != surfaceB
}

/**
 * Of [arms] - the ways leaving a junction, not counting [arrivedOn] - the one that carries on the
 * path [arrivedOn] is, or null if there's no telling.
 *
 * At Mugdock (mugdock-whw.gpx, point 1436) a compacted footway bends 43 degrees left at a junction
 * where a dirt path goes straight on. Going by angle alone the dirt path "continues ahead"; going
 * by the ground the walker is on, the footway does. So the arm continuing the path is:
 *  - the one continuing the same OSM way, unless it is plainly a different kind of path, or else
 *  - the only arm the same kind of path with the same, known, surface,
 * and either way only where every other arm can be told apart from it (see [pathsDiffer]) - if one
 * can't, the walker couldn't tell which they were on either. Paths and tracks only: roads have
 * their names to go by.
 */
fun continuingArm(arrivedOn: Way, arms: List<Way>): Way? {
    if (!arrivedOn.isPathOrTrack) return null
    val others = arms.filter { it !== arrivedOn }
    val sameOsmWay = others.filter { (it.osmId == arrivedOn.osmId) && (arrivedOn.osmId != 0L) }
    val candidate = sameOsmWay.singleOrNull()?.takeIf { !pathsDiffer(it, arrivedOn) }
        ?: arrivedOn.pathSurface?.let { surface ->
            others.singleOrNull { arm ->
                (arm.pathKind == arrivedOn.pathKind) && (arm.pathSurface == surface)
            }
        }
        ?: return null
    return candidate.takeIf { others.all { it === candidate || pathsDiffer(it, candidate) } }
}

/**
 * The noun for an unnamed path or track that says what it's like underfoot - "Gravel path",
 * "Grass track" - in place of a bare "Path", or null where there is none to say: a surface that
 * isn't known, steps, or a way that isn't a path or track.
 */
fun surfaceNoun(way: Way, strings: LocalizedStrings?): String? {
    if (way.featureSubClass == "steps") return null
    val surface = way.pathSurface ?: return null
    val (key, english) = when (way.featureClass) {
        "path" -> when (surface) {
            PathSurface.PAVED -> StringKey.PathPaved to "Paved path"
            PathSurface.GRAVEL -> StringKey.PathGravel to "Gravel path"
            PathSurface.EARTH -> StringKey.PathEarth to "Earth path"
            PathSurface.GRASS -> StringKey.PathGrass to "Grass path"
            PathSurface.WOOD -> StringKey.PathBoardwalk to "Boardwalk"
            PathSurface.ROCK -> StringKey.PathRocky to "Rocky path"
            PathSurface.SAND -> StringKey.PathSandy to "Sandy path"
        }
        "track" -> when (surface) {
            PathSurface.PAVED -> StringKey.TrackPaved to "Paved track"
            PathSurface.GRAVEL -> StringKey.TrackGravel to "Gravel track"
            PathSurface.EARTH -> StringKey.TrackEarth to "Earth track"
            PathSurface.GRASS -> StringKey.TrackGrass to "Grass track"
            else -> return null
        }
        else -> return null
    }
    return strings?.getOrNull(key) ?: english
}
