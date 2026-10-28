"""Regression checks for the classic 2D navigation preview and test isolation."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/com/nightrider/driver"


class NavigationHudChecks(unittest.TestCase):
    def test_map_first_layout_and_flat_camera(self):
        source = (SRC / "DriverMapActivity.kt").read_text()
        self.assertIn("NavigationHud(this", source)
        self.assertIn("val mapStage = FrameLayout(this)", source)
        self.assertIn("ready.setMinPitchPreference(0.0)", source)
        self.assertIn("ready.setMaxPitchPreference(0.0)", source)
        self.assertIn("isTiltGesturesEnabled = false", source)
        self.assertIn(".tilt(0.0)", source)
        self.assertIn("NavigationCameraMath.zoomForSpeed", source)
        self.assertIn("NavigationCameraMath.lookAhead", source)
        self.assertIn("simulation?.setFollow(false)", source)
        self.assertIn("fieldTest?.setFollow(false)", source)
        self.assertIn('simulation?.setFollow(true)', source)
        self.assertIn('fieldTest?.setFollow(true)', source)

    def test_route_visuals_and_safe_copy(self):
        source = (SRC / "DriverMapActivity.kt").read_text()
        hud = (SRC / "NavigationHud.kt").read_text()
        self.assertIn("private fun paintRoute(", source)
        self.assertIn("Color.rgb(112, 181, 255)", source)
        self.assertIn("NavigationMarker.create(", source)
        self.assertIn("No turn guidance", source)
        self.assertIn("ROUTE PREVIEW", source)
        self.assertNotIn("turn right in", hud.lower())
        self.assertNotIn("turn left in", hud.lower())
        for unsafe in ("Api.", "HttpURLConnection", "requestLocationUpdates",
                       "AudioManager", "TextToSpeech", "/api/"):
            self.assertNotIn(unsafe, hud)
        self.assertIn("No turn guidance", (ROOT / "MAP-SIMULATOR.md").read_text())

    def test_clarity_styling_is_isolated_from_rides(self):
        source = (SRC / "DriverMapActivity.kt").read_text()
        style = (SRC / "NavigationRoadStyle.kt").read_text()
        camera = (SRC / "NavigationCameraMath.kt").read_text()
        self.assertIn("NavigationRoadStyle.apply(style, prefs.aeroEnabled)", source)
        self.assertIn("NavigationCameraMath.nextBendMeters", source)
        self.assertIn("NavigationCameraMath.easeZoom", source)
        self.assertIn('ready.setMaxPitchPreference(0.0)', source)
        self.assertIn('"highway_motorway_inner"', style)
        self.assertIn('"highway_major_casing"', style)
        self.assertIn("textOpacity(0.12f)", style)
        self.assertIn("fun nextBendMeters", camera)
        for unsafe in ("Api.", "HttpURLConnection", "LocationManager",
                       "AudioManager", "TextToSpeech", "/api/"):
            self.assertNotIn(unsafe, style)


    def test_live_gps_marker_uses_display_only_motion_smoothing(self):
        activity = (SRC / "DriverMapActivity.kt").read_text()
        smoother = (SRC / "NavigationMotionSmoother.kt").read_text()
        self.assertIn("motion.acceptFix(destination, derivedBearing,", activity)
        self.assertIn("renderMotionFrame()", activity)
        self.assertIn("marker.position = LatLng(", activity)
        self.assertIn("m.updateMarker(marker)", activity)
        self.assertIn("m.moveCamera(CameraUpdateFactory.newCameraPosition(camera))", activity)
        self.assertIn("displayDriverPoint()", activity)
        self.assertIn("if (!active || simulation != null) return", activity)
        self.assertIn("fieldTest?.onFix(value)", activity)
        self.assertIn("private fun driverPoint(): LatLng?", activity)
        self.assertIn("No extrapolation", smoother)
        self.assertIn("lastFixReceivedAtMs", smoother)
        for unsafe in ("Api.", "HttpURLConnection", "LocationManager", "TextToSpeech", "/api/"):
            self.assertNotIn(unsafe, smoother)


if __name__ == "__main__":
    unittest.main()
