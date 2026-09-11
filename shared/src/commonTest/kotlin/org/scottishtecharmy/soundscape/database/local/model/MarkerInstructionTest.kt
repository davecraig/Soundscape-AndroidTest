package org.scottishtecharmy.soundscape.database.local.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which waypoints get an instruction read out after their name, and which don't.
 *
 * RoutePlayer speaks this, but its speech path can't be driven from a host test - loading a compose
 * string resource under runBlocking inside the player's own coroutine deadlocks - so the rule lives
 * here where it can be checked directly.
 */
class MarkerInstructionTest {

    private fun marker(
        name: String,
        fullAddress: String = "",
        source: String? = null,
        reverseDirection: String? = null,
    ) = MarkerEntity(
        name = name,
        longitude = 1.0,
        latitude = 2.0,
        fullAddress = fullAddress,
        source = source,
        reverseDirection = reverseDirection,
    )

    @Test
    fun aMarkerTheUserSavedHasNoInstruction() {
        // Its annotation is the street address. Routes have never read that out, and reading it
        // only when walked one way would make the two directions differ.
        val home = marker("Tesco", fullAddress = "15 Milngavie Road, Glasgow")

        assertNull(home.instructionFor(reversed = false))
        assertNull(home.instructionFor(reversed = true))
    }

    @Test
    fun aRecordedTurnGivesItsInstruction() {
        val junction = marker(
            "Station Road and Strathblane Road",
            fullAddress = "Turn left",
            source = MARKER_SOURCE_JOURNEY,
            reverseDirection = "Turn right",
        )

        assertEquals("Turn left", junction.instructionFor(reversed = false))
        assertEquals("Turn right", junction.instructionFor(reversed = true))
    }

    @Test
    fun aRecordedLandmarkHasNoInstructionEitherWay() {
        val landmark = marker("Allander Water", source = MARKER_SOURCE_JOURNEY)

        assertNull(landmark.instructionFor(reversed = false))
        assertNull(landmark.instructionFor(reversed = true))
    }
}
