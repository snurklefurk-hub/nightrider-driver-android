from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/com/nightrider/driver"
class SessionIsolation(unittest.TestCase):
    def test_test_components_have_no_ride_api_or_audio(self):
        for name in ("NavigationTestPanel.kt", "NavigationTelemetry.kt", "FieldScreenRecorderService.kt"):
            source = (SRC / name).read_text(encoding="utf-8")
            for forbidden in ("Api.", "/api/", "setAudioSource", "TextToSpeech"):
                self.assertNotIn(forbidden, source)
    def test_only_vela_map_is_registered(self):
        manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
        self.assertNotIn("DriverMapActivity", manifest)
        self.assertIn("VelaNavigationActivity", manifest)
