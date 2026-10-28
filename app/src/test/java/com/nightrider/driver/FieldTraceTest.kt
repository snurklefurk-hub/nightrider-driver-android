package com.nightrider.driver

import org.junit.Assert.*
import org.junit.Test

class FieldTraceTest {
    private fun fix(time: Long, lat: Double = 32.08, lon: Double = -81.09,
                    accuracy: Float = 5f) =
        FieldFix(time, lat, lon, accuracy, 5f)

    @Test fun samplesAreOptInAndTimestampDeduplicated() {
        val t = FieldTrace()
        assertFalse(t.add(fix(1000)))
        t.start()
        assertTrue(t.add(fix(1000)))
        assertFalse(t.add(fix(1000)))
        assertFalse(t.add(fix(1500)))
        assertTrue(t.add(fix(2000)))
        t.stop()
        assertFalse(t.add(fix(3000)))
        assertEquals(2, t.samples.size)
        t.start()
        assertTrue(t.samples.isEmpty())
    }

    @Test fun invalidFixesAreRejectedAndBufferIsBounded() {
        val t = FieldTrace(2)
        t.start()
        assertFalse(t.add(fix(1000, Double.NaN)))
        assertFalse(t.add(fix(1000, 100.0)))
        assertFalse(t.add(fix(1000, accuracy = -1f)))
        assertTrue(t.add(fix(1000)))
        assertTrue(t.add(fix(2000)))
        assertTrue(t.add(fix(3000)))
        assertEquals(listOf(2000L, 3000L), t.samples.map { it.epochMs })
    }

    @Test fun distanceIgnoresBadAccuracyAndHugeLocationJumps() {
        val t = FieldTrace()
        t.start()
        t.add(fix(1000))
        t.add(fix(2000, 32.0801))
        assertTrue(t.distanceMeters() in 10.0..12.0)
        t.add(fix(3000, 32.20))
        assertEquals(t.distanceMeters(), t.distanceMeters(), 0.0)
        val before = t.distanceMeters()
        t.add(fix(4000, 32.2001, accuracy = 300f))
        assertEquals(before, t.distanceMeters(), 0.001)
    }

    @Test fun shareFormatIsExplicitAndContainsOnlyRecordedSamples() {
        val t = FieldTrace()
        t.start()
        t.add(fix(1000))
        t.stop()
        val csv = t.csv()
        assertTrue(csv.startsWith(
            "epoch_ms,latitude,longitude,accuracy_m,speed_mps,provider,map_decision," +
                "fusion_heading_deg,gyro_yaw_dps,linear_accel_mps2,phone_azimuth_deg\n"))
        assertTrue(csv.contains("1000,32.08,-81.09,5.0,5.0,unknown,unrated,,,,"))
        t.clear()
        assertEquals(1, t.csv().lines().count { it.startsWith("epoch_ms") })
    }

    @Test fun exportedDecisionsMatchExactlyTheAcceptedOrRejectedFix() {
        val t = FieldTrace()
        t.start()
        assertTrue(t.add(FieldFix(1000L, 32.08, -81.09, 5f, 10f, "gps")))
        t.markDecision(1000L, true)
        assertTrue(t.add(FieldFix(2000L, 32.09, -81.09, 400f, 0f, "network")))
        t.markDecision(2000L, false)
        // A dropped duplicate must not change the latest recorded decision.
        t.markDecision(2500L, true)
        assertEquals(listOf("accepted", "rejected"), t.samples.map { it.mapDecision })
        val csv = t.csv()
        assertTrue(csv.contains("1000,32.08,-81.09,5.0,10.0,gps,accepted,,,,"))
        assertTrue(csv.contains("2000,32.09,-81.09,400.0,0.0,network,rejected,,,,"))
    }

    @Test fun csvEscapesUnknownProviderValuesWithSafeEnum() {
        val t = FieldTrace()
        t.start()
        t.add(FieldFix(1000L, 32.08, -81.09, 5f, 0f, "=HYPERLINK(\"bad\")"))
        assertTrue(t.csv().contains(",unknown,unrated"))
        assertFalse(t.csv().contains("HYPERLINK"))
    }

    @Test fun sensorMetricsAreOptInAndAttachedOnlyToRecordedFixes() {
        val t = FieldTrace()
        t.start()
        t.add(fix(1000L))
        t.markMotion(1000L, FieldMotionSnapshot(123.0, -15.0, 1.5, 55.0, 1.0))
        t.markMotion(2000L, FieldMotionSnapshot(40.0, 0.0, 0.0, null, 0.0))
        assertTrue(t.csv().contains(",unrated,123.0,-15.0,1.5,55.0"))
        assertEquals(1, t.samples.size)
    }

    @Test fun nearestPointToRouteIsInMeters() {
        val line = listOf(SimulationPoint(32.08, -81.09), SimulationPoint(32.09, -81.09))
        assertEquals(0.0, FieldRouteMath.distanceMeters(SimulationPoint(32.085, -81.09), line)!!, 0.1)
        val off = FieldRouteMath.distanceMeters(SimulationPoint(32.085, -81.088), line)!!
        assertTrue(off in 150.0..220.0)
        assertNull(FieldRouteMath.distanceMeters(SimulationPoint(32.08, -81.09), emptyList()))
    }
}
