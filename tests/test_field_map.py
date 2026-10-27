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
        self.assertIn('fieldTest != null -> { startLocation(); main.post(fieldRefresh) }', source)
        self.assertIn("if (fieldTest != null) { drawFieldMap(m); return }", source)
        self.assertIn("if (simulation != null) return", source)
        self.assertIn("if (simulation != null) simulation!!.resume()", source.replace(
            "simulation != null -> simulation!!.resume()", "if (simulation != null) simulation!!.resume()"))

    def test_settings_exposes_opt_in_field_test(self):
        settings = (SRC / "SettingsActivity.kt").read_text()
        layout = (ROOT / "app/src/main/res/layout/activity_settings.xml").read_text()
        self.assertIn('putExtra("field_test", true)', settings)
        self.assertIn('@+id/mapFieldTest', layout)


if __name__ == "__main__":
    unittest.main()
