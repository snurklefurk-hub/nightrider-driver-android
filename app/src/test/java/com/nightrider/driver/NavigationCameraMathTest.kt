package com.nightrider.driver

import org.junit.Assert.*
import org.junit.Test

class NavigationCameraMathTest {
    @Test fun adaptiveZoomIsFlatAndSpeedBased() {
        assertEquals(16.8, NavigationCameraMath.zoomForSpeed(0.0), 0.001)
        assertEquals(16.0, NavigationCameraMath.zoomForSpeed(10.0), 0.001)
        assertEquals(14.5, NavigationCameraMath.zoomForSpeed(30.0), 0.001)
        assertEquals(15.7, NavigationCameraMath.zoomForSpeed(Double.NaN), 0.001)
    }

    @Test fun bearingHasKnownCardinalDirections() {
        val start = SimulationPoint(32.0, -81.0)
        assertEquals(0.0, NavigationCameraMath.bearingDegrees(start, SimulationPoint(32.01, -81.0))!!, 1.0)
        assertEquals(90.0, NavigationCameraMath.bearingDegrees(start, SimulationPoint(32.0, -80.99))!!, 1.0)
        assertNull(NavigationCameraMath.bearingDegrees(start, start))
    }

    @Test fun cameraLeavesMoreRoadAhead() {
        val point = SimulationPoint(32.08, -81.09)
        assertEquals(point, NavigationCameraMath.lookAhead(point, null, 20.0))
        val ahead = NavigationCameraMath.lookAhead(point, 0.0, 20.0)
        assertTrue(ahead.lat > point.lat)
        assertEquals(point.lon, ahead.lon, 0.00001)
        assertTrue(SimulationLeg.meters(point, ahead) < 250.0)
    }
}
