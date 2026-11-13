package org.scottishtecharmy.soundscape.geoengine.mvttranslation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OsmRouteTest {

    private val westHighlandWay = OsmRoute(
        19772647, "hiking", network = "nwn",
        name = "West Highland Way (Milngavie to Drymen part 1)",
        rootId = 16287, rootName = "West Highland Way",
    )
    private val greatNorthTrail = OsmRoute(
        10016403, "mtb", network = "ncn",
        name = "Great North Trail (Grampian Mountains)", rootName = "Great North Trail",
    )
    private val clydeCoastalPath = OsmRoute(4550727, "hiking", network = "rwn", name = "Clyde Coastal Path")
    private val allanderWalkway = OsmRoute(2402046, "hiking", network = "lwn", name = "Allander Walkway")

    /** The footway off Douglas Street in Milngavie, as the tiles list its routes. */
    @Test
    fun aSectionOfANationalTrailIsNamedForTheWholeTrail() {
        assertEquals(
            "West Highland Way",
            nameFromRoutes(listOf(westHighlandWay, greatNorthTrail, clydeCoastalPath), isCycleway = false)
        )
    }

    @Test
    fun aLocalRouteNamesAWayOnlyWhenItIsTheOnlyOne() {
        assertEquals("Allander Walkway", nameFromRoutes(listOf(allanderWalkway), isCycleway = false))

        // Five waymarked trails on one track near Aberfoyle: no one of them is the track's name
        val trails = listOf(
            OsmRoute(2866086, "foot", network = "lwn", ref = "Red", name = "Loch Ard Sculpture Trail"),
            OsmRoute(2866087, "foot", network = "lwn", ref = "Blue", name = "Ben Lomond Viewpoint"),
            OsmRoute(12620312, "hiking", network = "lwn", ref = "Black", name = "8 mile loop"),
            OsmRoute(14381798, "bicycle", network = "lcn", ref = "lomond", name = "Gravelfoyle - Lomond View"),
            OsmRoute(14381799, "bicycle", network = "lcn", ref = "aqueduct", name = "Gravelfoyle - Aqueduct Loop"),
        )
        assertNull(nameFromRoutes(trails, isCycleway = false))
    }

    @Test
    fun aCycleRouteOnlyNamesACycleway() {
        assertNull(nameFromRoutes(listOf(greatNorthTrail), isCycleway = false))
        assertEquals("Great North Trail", nameFromRoutes(listOf(greatNorthTrail), isCycleway = true))
        // ...and a walking route still comes first on one
        assertEquals(
            "Allander Walkway",
            nameFromRoutes(listOf(greatNorthTrail, allanderWalkway), isCycleway = true)
        )
    }

    /**
     * Around Ede the footpaths are stretches of the Dutch walking node network: rwn, like a
     * regional route, but with only junction numbers for a ref. "V72-V74" is not a path's name,
     * though the stretch stays on the way to be followed - and a real named walk on the same path
     * still names it.
     */
    @Test
    fun aNodeNetworkStretchNeverNamesAWay() {
        val stretch = OsmRoute(
            21159993, "foot", network = "rwn", networkType = "node_network", ref = "V72-V74",
        )
        val cycleStretch = OsmRoute(
            16835, "bicycle", network = "rcn", networkType = "node_network", ref = "26-61",
        )
        assertNull(nameFromRoutes(listOf(stretch), isCycleway = false))
        assertNull(nameFromRoutes(listOf(cycleStretch), isCycleway = true))

        val pegasus = OsmRoute(21149725, "foot", network = "lwn", name = "Pegasus I")
        assertEquals("Pegasus I", nameFromRoutes(listOf(stretch, pegasus), isCycleway = false))
    }

    @Test
    fun aRouteWithOnlyARefIsCalledByIt() {
        assertEquals("AT", nameFromRoutes(listOf(OsmRoute(1, "hiking", network = "nwn", ref = "AT")), false))
    }

    @Test
    fun routeTagsAreReadInTheirTileOrder() {
        val tags = OsmRouteTags()
        // Tile tag order is not route order, and integer values can arrive as any number type
        listOf(
            "route_2_id" to 4550727L,
            "route_2_type" to "hiking",
            "route_2_name" to "Clyde Coastal Path",
            "route_2_network_type" to "node_network",
            "route_1_type" to "hiking",
            "route_1_id" to 19772647,
            "route_1_name" to "West Highland Way (Milngavie to Drymen part 1)",
            "route_1_role" to "forward",
            "route_1_root_id" to 16287L,
            "route_1_root_name" to "West Highland Way",
            "route_3_name" to "No id, so not a route",
        ).forEach { (key, value) -> assertEquals(true, tags.offer(key, value)) }
        assertEquals(false, tags.offer("routes", "x"))
        assertEquals(false, tags.offer("name", "Douglas Street"))

        val routes = tags.build()
        assertEquals(listOf(19772647L, 4550727L), routes.map { it.id })
        assertEquals("forward", routes[0].role)
        assertEquals(16287L, routes[0].rootId)
        assertEquals("West Highland Way", routes[0].displayName)
        assertEquals("Clyde Coastal Path", routes[1].displayName)
        assertEquals(null, routes[0].networkType)
        assertEquals(true, routes[1].isNodeNetworkStretch)

        tags.clear()
        assertEquals(emptyList(), tags.build())
    }
}
