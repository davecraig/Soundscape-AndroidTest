package org.scottishtecharmy.soundscape.geoengine.utils

import org.scottishtecharmy.soundscape.geoengine.mvttranslation.Way
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class PathCharacterTest {

    private fun path(
        subclass: String?,
        surface: String? = null,
        osmId: Long = 0L,
        featureClass: String = "path",
        tracktype: String? = null,
    ) = Way().apply {
        this.featureClass = featureClass
        featureSubClass = subclass
        this.osmId = osmId
        properties = HashMap<String, Any?>().apply {
            surface?.let { put("surface_raw", it) }
            tracktype?.let { put("tracktype", it) }
        }
    }

    @Test
    fun surfacesGroupIntoWhatCanBeFelt() {
        assertEquals(PathSurface.GRAVEL, PathSurface.fromOsm("compacted"))
        assertEquals(PathSurface.GRAVEL, PathSurface.fromOsm("fine_gravel"))
        assertEquals(PathSurface.EARTH, PathSurface.fromOsm("dirt"))
        assertEquals(PathSurface.EARTH, PathSurface.fromOsm("ground"))
        assertEquals(PathSurface.GRASS, PathSurface.fromOsm("grass;gravel"))
        assertEquals(PathSurface.PAVED, PathSurface.fromOsm("asphalt"))
        assertNull(PathSurface.fromOsm("unpaved"))
        assertNull(PathSurface.fromOsm(null))
        assertEquals(PathSurface.GRAVEL, path(null, featureClass = "track", tracktype = "grade2").pathSurface)
        assertNull(path(null, featureClass = "track", tracktype = "grade4").pathSurface)
    }

    /**
     * Mugdock, mugdock-whw.gpx point 1436: the compacted footway (OSM way 518440049) bends left and
     * a dirt path goes straight on. The footway is the path being walked.
     */
    @Test
    fun theSameOsmWayCarriesOn() {
        val arrivedOn = path("footway", "compacted", osmId = 5184400492)
        val bendsLeft = path("footway", "compacted", osmId = 5184400492)
        val dirtAhead = path("path", "dirt", osmId = 14976863852)
        assertSame(bendsLeft, continuingArm(arrivedOn, listOf(arrivedOn, bendsLeft, dirtAhead)))
    }

    @Test
    fun theOnlyArmLikeThePathCarriesOnWhenTheOsmWayEnds() {
        val arrivedOn = path("footway", "compacted", osmId = 1)
        val gravel = path("footway", "gravel", osmId = 2)
        val grass = path("path", "grass", osmId = 3)
        val dirt = path("path", "dirt", osmId = 4)
        assertSame(gravel, continuingArm(arrivedOn, listOf(arrivedOn, grass, gravel, dirt)))
    }

    @Test
    fun noAnswerWhenAnotherArmCantBeToldApart() {
        val arrivedOn = path("footway", "compacted", osmId = 1)
        val gravel = path("footway", "gravel", osmId = 2)
        // A footway with no surface mapped could be gravel too
        val unknown = path("footway", null, osmId = 3)
        assertNull(continuingArm(arrivedOn, listOf(arrivedOn, gravel, unknown)))
        // Two arms the same as the one arrived on
        val gravelToo = path("footway", "fine_gravel", osmId = 4)
        assertNull(continuingArm(arrivedOn, listOf(arrivedOn, gravel, gravelToo)))
    }

    @Test
    fun noAnswerWithoutASurfaceOrTheSameWay() {
        val arrivedOn = path("path", null, osmId = 1)
        assertNull(continuingArm(arrivedOn, listOf(arrivedOn, path("path", "grass", 2), path("track", null, 3, "track"))))
    }

    @Test
    fun theSameOsmWayTurningIntoSomethingElseIsNotTheSamePath() {
        // A gravel footway whose OSM way carries on as a grass path past a junction
        val arrivedOn = path("footway", "gravel", osmId = 1)
        val sameWayButGrass = path("footway", "grass", osmId = 1)
        val gravel = path("footway", "gravel", osmId = 2)
        assertSame(gravel, continuingArm(arrivedOn, listOf(arrivedOn, sameWayButGrass, gravel)))
    }

    @Test
    fun roadsAreLeftAlone() {
        val road = Way().apply { featureClass = "minor"; osmId = 1 }
        val onward = Way().apply { featureClass = "minor"; osmId = 1 }
        assertNull(continuingArm(road, listOf(road, onward, path("footway", "gravel", 2))))
    }
}
