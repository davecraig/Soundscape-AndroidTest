package org.scottishtecharmy.soundscape.filters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt

class CalloutHistoryTest {

    @Test
    fun testAddAndFind() {
        val history = CalloutHistory(10)
        val location = LngLatAlt(0.0, 0.0, 0.0) // Example location
        val callout = TrackedCallout("Poi number one", location)
        history.add(callout)
        assertTrue(history.find(callout))
        assertEquals(1, history.size())
    }

    @Test
    fun testTrimByTime() {
        val history = CalloutHistory(10)
        val location = LngLatAlt(0.0, 0.0, 0.0) // Example location
        val callout = TrackedCallout(
            "Poi number two",
            location
        )
        history.add(callout)
        Thread.sleep(20)
        history.trim(location)
        assertFalse(history.find(callout))
        assertEquals(0, history.size())
    }

    @Test
    fun testTrimByDistance() {
        val history = CalloutHistory(10)
        val location = LngLatAlt(0.0, 0.0, 0.0) // Example location
        val distantLocation = LngLatAlt(10.0, 10.0, 10.0)
        val callout = TrackedCallout("Poi number three", distantLocation)
        history.add(callout)
        history.trim(location)
        assertFalse(history.find(callout))
        assertEquals(0, history.size())
    }

    @Test
    fun testTrimEmptyHistory() {
        val history = CalloutHistory(10)
        val location = LngLatAlt(0.0, 0.0, 0.0) // Example location
        history.trim(location)
        assertEquals(0, history.size())
    }
}