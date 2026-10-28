"""Field Test must stay separate from live ride and dispatch actions."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/com/nightrider/driver"


class FieldModeChecks(unittest.TestCase):
    def test_panel_has_no_network_or_dispatch_api(self):
        source = (SRC / "FieldTestPanel.kt").read_text()
        for unsafe in ("Api.", "HttpURLConnection", "LocationManager",
                       "requestLocationUpdates", "/api/", "sessionToken"):
            self.assertNotIn(unsafe, source)
        self.assertIn("Intent.ACTION_SEND", source)
        self.assertIn("Intent.ACTION_CREATE_DOCUMENT", source)
        self.assertIn("EXPORT_CSV_REQUEST", source)
        self.assertIn("trace.csv()", source)

    def test_field_screen_bypasses_live_ride_refresh(self):
        source = (SRC / "DriverMapActivity.kt").read_text()
        self.assertIn('intent.getBooleanExtra("field_test", false)', source)
        self.assertIn("fieldTest != null -> {", source)
        self.assertIn("main.post(fieldRefresh)", source)
        self.assertIn("main.post(motionFrames)", source)
        self.assertIn("if (fieldTest != null) { drawFieldMap(m); return }", source)
        self.assertIn("if (simulation != null) return", source)
        self.assertIn("if (simulation != null) simulation!!.resume()", source.replace(
            "simulation != null -> simulation!!.resume()", "if (simulation != null) simulation!!.resume()"))

    def test_raw_trace_survives_rejected_navigation_fix(self):
        activity = (SRC / "DriverMapActivity.kt").read_text()
        panel = (SRC / "FieldTestPanel.kt").read_text()
        quality = (SRC / "NavigationFixFilter.kt").read_text()
        self.assertIn("fieldTest?.onFix(value)", activity)
        self.assertIn("fieldTest?.onMapDecision(value.time, false)", activity)
        self.assertIn("fieldTest?.onMapDecision(value.time, true)", activity)
        self.assertIn("trace.markDecision(epochMs, accepted)", panel)
        self.assertIn("if (!acceptsMapFix(value))", activity)
        self.assertIn("fieldTest?.onFilteredMapFix()", activity)
        self.assertIn("NavigationFixFilter.accepts(", activity)
        self.assertIn("const val MAX_AGE_MS", quality)
        self.assertIn("raw readings", panel)
        self.assertNotIn("Api.", quality)
        self.assertNotIn("HttpURLConnection", quality)

    def test_motion_fusion_is_opt_in_and_isolated_from_live_rides(self):
        activity = (SRC / "DriverMapActivity.kt").read_text()
        panel = (SRC / "FieldTestPanel.kt").read_text()
        adapter = (SRC / "FieldMotionSensors.kt").read_text()
        fusion = (SRC / "FieldMotionFusion.kt").read_text()
        self.assertIn("fieldMotionSensors = FieldMotionSensors(this)", activity)
        self.assertIn("motionChanged: (Boolean) -> Boolean", panel)
        self.assertIn("Motion fusion: OFF", panel)
        self.assertIn("if (fieldTest?.motionEnabled == true)", activity)
        self.assertIn("fieldMotionSensors?.stop()", activity)
        self.assertIn("fieldTest?.onMotionAtFix(value.time", activity)
        self.assertIn("Sensor.TYPE_GYROSCOPE", adapter)
        self.assertIn("Sensor.TYPE_ACCELEROMETER", adapter)
        self.assertIn("Sensor.TYPE_GRAVITY", adapter)
        self.assertIn("Sensor.TYPE_ROTATION_VECTOR", adapter)
        self.assertIn("gpsCourse", fusion)
        self.assertIn("timeNs - anchored > 8_000_000_000L", fusion)
        for unsafe in ("Api.", "HttpURLConnection", "/api/", "LocationManager",
                       "TextToSpeech", "requestLocationUpdates"):
            self.assertNotIn(unsafe, adapter)
            self.assertNotIn(unsafe, fusion)

    def test_screen_video_requires_fresh_consent_and_foreground_service(self):
        activity = (SRC / "DriverMapActivity.kt").read_text()
        panel = (SRC / "FieldTestPanel.kt").read_text()
        recorder = (SRC / "FieldScreenRecorderService.kt").read_text()
        manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()

        self.assertIn("MediaProjectionManager", activity)
        self.assertIn("createScreenCaptureIntent()", activity)
        self.assertIn("startForegroundService(service)", activity)
        self.assertIn("FieldScreenRecorderService.REQUEST_CAPTURE", activity)
        self.assertIn("REQUEST_SAVE_MP4", activity)
        self.assertIn("Intent.ACTION_CREATE_DOCUMENT", activity)
        self.assertIn("video/mp4", activity)
        self.assertIn("noBackupFilesDir", recorder)
        self.assertIn("registerCallback(", recorder)
        self.assertIn("createVirtualDisplay(", recorder)
        self.assertIn("startForeground(", recorder)
        self.assertIn("FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION", recorder)
        self.assertIn("setVideoSource(MediaRecorder.VideoSource.SURFACE)", recorder)
        self.assertIn("setVideoEncoder(MediaRecorder.VideoEncoder.H264)", recorder)
        self.assertIn("setMaxDuration(MAX_DURATION_MS)", recorder)
        self.assertIn("setMaxFileSize(MAX_FILE_BYTES)", recorder)
        self.assertNotIn("setAudioSource(", recorder)
        self.assertNotIn("setAudioEncoder(", recorder)
        self.assertNotIn("Api.", recorder)
        self.assertNotIn("HttpURLConnection", recorder)
        self.assertIn("isVideoRunning()", activity)
        self.assertIn("FieldScreenRecorderService.requestStop(this)", activity)
        self.assertIn("screenAction: (Boolean) -> Unit", panel)
        self.assertIn("Record screen + GPS", panel)
        self.assertIn("Save MP4", panel)
        self.assertIn("SYNC epoch_ms", panel)
        self.assertIn("screenCaptureAuthorized(id)", activity)
        self.assertIn("videoSessionId == state.sessionId", panel)
        self.assertIn("trace.start()", panel)
        self.assertIn("trace.stop()", panel)
        self.assertIn('android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION', manifest)
        self.assertIn('android:foregroundServiceType="mediaProjection"', manifest)
        self.assertIn('android:exported="false"', manifest)

    def test_settings_exposes_opt_in_field_test(self):
        settings = (SRC / "SettingsActivity.kt").read_text()
        layout = (ROOT / "app/src/main/res/layout/activity_settings.xml").read_text()
        self.assertIn('putExtra("field_test", true)', settings)
        self.assertIn('@+id/mapFieldTest', layout)


if __name__ == "__main__":
    unittest.main()
