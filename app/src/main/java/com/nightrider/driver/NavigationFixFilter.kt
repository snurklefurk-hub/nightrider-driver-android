package com.nightrider.driver

/**
 * Pure, testable navigation-position quality gate.
 *
 * The Field Test records all raw GPS/network fixes separately. This filter is
 * applied only to fixes used to move the driver arrow, heading-up camera and
 * field-route offset. It does not change Android permissions or dispatch APIs.
 */
data class NavigationFix(
    val lat: Double,
    val lon: Double,
    val epochMs: Long,
    val accuracyM: Double?,
    val speedMps: Double?,
    val provider: String
)

object NavigationFixFilter {
    const val MAX_ACCURACY_M = 35.0
    const val MAX_AGE_MS = 20_000L
    private const val MAX_IMPLIED_SPEED_MPS = 55.0
    private const val FRESH_GPS_PRIORITY_MS = 12_000L

    fun accepts(next: NavigationFix, previous: NavigationFix?, nowMs: Long): Boolean {
        if (!next.lat.isFinite() || !next.lon.isFinite() ||
            next.lat !in -90.0..90.0 || next.lon !in -180.0..180.0) return false
        val accuracy = next.accuracyM ?: return false
        if (!accuracy.isFinite() || accuracy < 0.0 || accuracy > MAX_ACCURACY_M) return false
        if (next.speedMps != null &&
            (!next.speedMps.isFinite() || next.speedMps < 0.0 || next.speedMps > 60.0)) return false

        val ageMs = nowMs - next.epochMs
        if (ageMs < -5_000 || ageMs > MAX_AGE_MS) return false
        if (previous == null) return true

        val elapsedMs = next.epochMs - previous.epochMs
        if (elapsedMs <= 0) return false
        if (previous.provider.equals("gps", ignoreCase = true) &&
            next.provider.equals("network", ignoreCase = true) &&
            elapsedMs <= FRESH_GPS_PRIORITY_MS) return false

        val elapsedSeconds = elapsedMs / 1000.0
        val distance = SimulationLeg.meters(
            SimulationPoint(previous.lat, previous.lon),
            SimulationPoint(next.lat, next.lon)
        )
        if (!distance.isFinite() || distance / elapsedSeconds > MAX_IMPLIED_SPEED_MPS) return false

        // A network/no-motion position that contradicts a recent moving GPS fix
        // is often a coarse provider fallback. Prefer continued GPS motion.
        if (next.provider.equals("network", ignoreCase = true) &&
            previous.speedMps != null && previous.speedMps >= 10.0 &&
            next.speedMps != null && next.speedMps <= 0.5 &&
            elapsedSeconds < 15.0) return false

        return true
    }
}
