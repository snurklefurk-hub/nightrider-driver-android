package com.nightrider.driver

import org.junit.Assert.*
import org.junit.Test

class FieldMotionFusionTest {
    private val t0 = 10_000_000_000L

    @Test fun mountedPhoneGyroTracksTurnBetweenGpsFixes() {
        val fusion = FieldMotionFusion()
        fusion.gravity(0.0, 0.0, -9.81)
        fusion.gpsCourse(90.0, 12.0, t0)
        fusion.gyroscope(0.0, 0.0, Math.PI / 2.0, t0)
        fusion.gyroscope(0.0, 0.0, Math.PI / 2.0, t0 + 100_000_000L)
        // Clockwise automotive bearing decreases for a physical left turn.
        assertEquals(81.0, fusion.headingAt(t0 + 100_000_000L)!!, 0.1)
        assertEquals(-90.0, fusion.snapshot(t0 + 100_000_000L).yawRateDps!!, 0.1)
    }

    @Test fun gpsRemainsAuthoritativeAndCompassIsOnlyDiagnostic() {
        val fusion = FieldMotionFusion()
        fusion.gravity(0.0, -9.81, 0.0) // portrait mounted upright
        fusion.gpsCourse(350.0, 9.0, t0)
        fusion.gyroscope(0.0, 0.0, 0.0, t0)
        fusion.gyroscope(0.0, 0.0, 0.0, t0 + 100_000_000L)
        fusion.phoneAzimuth(180.0)
        assertEquals(350.0, fusion.headingAt(t0 + 100_000_000L)!!, 0.01)
        assertEquals(180.0, fusion.snapshot(t0 + 100_000_000L).phoneAzimuthDeg!!, 0.01)
        fusion.gpsCourse(10.0, 8.0, t0 + 200_000_000L)
        assertEquals(5.0, fusion.headingAt(t0 + 200_000_000L)!!, 0.1)
    }

    @Test fun staleGpsOrMissingGyroNeverClaimsInertialHeading() {
        val fusion = FieldMotionFusion()
        fusion.gravity(0.0, 0.0, -9.81)
        fusion.gpsCourse(90.0, 9.0, t0)
        assertNull(fusion.headingAt(t0))
        fusion.gyroscope(0.0, 0.0, 0.0, t0)
        assertEquals(90.0, fusion.headingAt(t0)!!, 0.01)
        assertNull(fusion.headingAt(t0 + 1_100_000_000L))
        fusion.gyroscope(0.0, 0.0, 0.0, t0 + 9_000_000_000L)
        assertNull(fusion.headingAt(t0 + 9_000_000_000L))
    }

    @Test fun movingGpsRequiredAndStationaryDeviceDoesNotRotateMap() {
        val fusion = FieldMotionFusion()
        fusion.gravity(0.0, 0.0, -9.81)
        fusion.gpsCourse(20.0, 0.0, t0)
        fusion.gyroscope(0.0, 0.0, 1.0, t0)
        assertNull(fusion.headingAt(t0))
        fusion.gpsCourse(20.0, 10.0, t0)
        fusion.gyroscope(0.0, 0.0, 1.0, t0 + 100_000_000L)
        assertNotNull(fusion.headingAt(t0 + 100_000_000L))
        fusion.gpsCourse(null, 0.0, t0 + 200_000_000L)
        fusion.gyroscope(0.0, 0.0, 1.0, t0 + 300_000_000L)
        assertNull(fusion.headingAt(t0 + 300_000_000L))
    }

    @Test fun accelerationIsDiagnosticAndOutOfOrderGyroIsIgnored() {
        val fusion = FieldMotionFusion()
        fusion.gravity(0.0, 0.0, -9.81)
        fusion.accelerometer(0.0, 0.0, -7.81, t0)
        assertEquals(2.0, fusion.snapshot(t0).linearAccelerationMps2!!, 0.1)
        fusion.gpsCourse(90.0, 15.0, t0)
        fusion.gyroscope(0.0, 0.0, 1.0, t0)
        fusion.gyroscope(0.0, 0.0, 1.0, t0 - 100_000_000L)
        assertEquals(90.0, fusion.snapshot(t0).headingDeg!!, 0.01)
        fusion.reset()
        assertNull(fusion.headingAt(t0))
        assertNull(fusion.snapshot(t0).linearAccelerationMps2)
    }
}
