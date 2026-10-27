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
        assertTrue(csv.startsWith("epoch_ms,latitude,longitude,accuracy_m,speed_mps\n"))
        assertTrue(csv.contains("1000,32.08,-81.09,5.0,5.0"))
        t.clear()
        assertEquals(1, t.csv().lines().count { it.startsWith("epoch_ms") })
    }

    @Test fun nearestPointToRouteIsInMeters() {
        val line = listOf(SimulationPoint(32.08, -81.09), SimulationPoint(32.09, -81.09))
        assertEquals(0.0, FieldRouteMath.distanceMeters(SimulationPoint(32.085, -81.09), line)!!, 0.1)
        val off = FieldRouteMath.distanceMeters(SimulationPoint(32.085, -81.088), line)!!
        assertTrue(off in 150.0..220.0)
        assertNull(FieldRouteMath.distanceMeters(SimulationPoint(32.08, -81.09), emptyList()))
    }
}
