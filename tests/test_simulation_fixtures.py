"""Repeatable route fixtures must be continuous and geographically plausible."""
import json
import math
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class SimulationFixtureTests(unittest.TestCase):
    def test_bundled_journeys_have_connected_approach_and_trip_legs(self):
        names = ("savannah", "bridge", "hilton-head")
        for name in names:
            with self.subTest(name=name):
                data = json.loads((ROOT / "app/src/main/assets/simulation" / (name + ".json")).read_text(encoding="utf-8"))
                self.assertEqual(len(data["legs"]), 2)
                approach, trip = data["legs"]
                self.assertEqual(approach[-1], trip[0], "Pickup must join both legs")
                for leg in (approach, trip):
                    self.assertGreater(len(leg), 10)
                    self.assertNotEqual(leg[0], leg[-1])
                    for lat, lon in leg:
                        self.assertTrue(math.isfinite(lat) and math.isfinite(lon))
                        self.assertTrue(31.8 <= lat <= 32.5 and -81.5 <= lon <= -80.4)
                    for a, b in zip(leg, leg[1:]):
                        # Long route discontinuities would make playback jump across the map.
                        self.assertLess(math.hypot((a[0] - b[0]) * 111000,
                                                  (a[1] - b[1]) * 94000), 1000)

    def test_simulation_screen_cannot_load_live_inputs(self):
        source = (ROOT / "app/src/main/java/com/nightrider/driver/MapSimulationPanel.kt").read_text(encoding="utf-8")
        for forbidden in ("Api.", "LocationManager", "requestLocationUpdates", "HttpURLConnection", "URL("):
            self.assertNotIn(forbidden, source)


if __name__ == "__main__":
    unittest.main()
