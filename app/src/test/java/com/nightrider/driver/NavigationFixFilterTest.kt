package com.nightrider.driver

import org.junit.Assert.*
import org.junit.Test

class NavigationFixFilterTest {
    private val now = 1_793_154_700_000L
    private fun fix(
        lat: Double = 32.33, lon: Double = -81.60,
        at: Long = now - 12_000L,
        accuracy: Double? = 6.0,
        speed: Double? = 24.5,
        provider: String = "gps"
    ) = NavigationFix(lat, lon, at, accuracy, speed, provider)

    @Test fun normalDrivingProgressIsAccepted() {
        val previous = fix()
        val next = fix(lon = -81.5987, at = now - 7_000L)
        assertTrue(NavigationFixFilter.accepts(next, previous, now))
    }

    @Test fun coarseAndRepeatedFieldTracePositionsNeverMoveArrow() {
        val previous = fix()
        // Trace-like network fallback with 400m reported accuracy.
        val coarse = fix(lon = -81.58449, accuracy = 400.0, speed = 0.0,
            at = now - 7_000L, provider = "network")
        assertFalse(NavigationFixFilter.accepts(coarse, previous, now))
        val recovered = fix(lon = -81.5987, at = now - 7_000L)
        assertTrue(NavigationFixFilter.accepts(recovered, previous, now))
    }

    @Test fun freshGpsWinsOverNetworkButNetworkCanRecoverAfterOutage() {
        val recentGps = fix()
        val network = fix(lon = -81.5987, at = now - 7_000L, accuracy = 15.0,
            speed = null, provider = "network")
        assertFalse(NavigationFixFilter.accepts(network, recentGps, now))
        val oldGps = fix(at = now - 20_000L)
        assertTrue(NavigationFixFilter.accepts(network, oldGps, now))
    }

    @Test fun impossibleJumpAndOutOfOrderFixAreIgnored() {
        val previous = fix()
        assertFalse(NavigationFixFilter.accepts(
            fix(lat = 32.40, lon = -81.50, at = now - 11_000L), previous, now
        ))
        assertFalse(NavigationFixFilter.accepts(
            fix(at = previous.epochMs), previous, now
        ))
    }

    @Test fun staleInaccurateOrInvalidGpsDoesNotBootstrapCamera() {
        assertFalse(NavigationFixFilter.accepts(fix(at = now - 60_000L), null, now))
        assertFalse(NavigationFixFilter.accepts(fix(accuracy = null), null, now))
        assertFalse(NavigationFixFilter.accepts(fix(accuracy = 135.0), null, now))
        assertFalse(NavigationFixFilter.accepts(fix(lat = 92.0), null, now))
        assertFalse(NavigationFixFilter.accepts(fix(speed = Double.NaN), null, now))
        assertTrue(NavigationFixFilter.accepts(fix(), null, now))
    }
}
