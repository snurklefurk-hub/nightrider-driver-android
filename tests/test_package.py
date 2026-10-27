"""Source-package checks that run without an Android SDK or built APK."""

import os
import unittest
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ANDROID = "{http://schemas.android.com/apk/res/android}"

# launcher icon PNG dimensions per density (square and round share a size)
DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


def p(*parts):
    return os.path.join(ROOT, *parts)


def png_size(data):
    """Return (width, height) from a PNG byte string, or raise ValueError."""
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG")
    if data[12:16] != b"IHDR":
        raise ValueError("missing IHDR")
    width, height = (int.from_bytes(data[16:20], "big"),
                     int.from_bytes(data[20:24], "big"))
    return width, height


class ManifestTests(unittest.TestCase):
    def setUp(self):
        self.tree = ET.parse(p("app", "src", "main", "AndroidManifest.xml"))
        self.root = self.tree.getroot()

    def test_internet_permission_is_declared(self):
        names = {el.get(ANDROID + "name")
                 for el in self.root.iter("uses-permission")}
        self.assertIn("android.permission.INTERNET", names)

    def test_application_uses_adaptive_launcher_icons(self):
        app = self.root.find("application")
        self.assertIsNotNone(app)
        self.assertEqual(app.get(ANDROID + "icon"), "@mipmap/ic_launcher")
        self.assertEqual(app.get(ANDROID + "roundIcon"),
                         "@mipmap/ic_launcher_round")
        self.assertEqual(app.get(ANDROID + "theme"), "@style/Theme.NightRider")

    def test_exactly_one_launcher_activity(self):
        launchers = []
        for act in self.root.iter("activity"):
            cats = {c.get(ANDROID + "name")
                    for c in act.iter("category")}
            if "android.intent.category.LAUNCHER" in cats:
                launchers.append(act)
        self.assertEqual(len(launchers), 1)
        self.assertEqual(launchers[0].get(ANDROID + "name"), ".MainActivity")

    def test_settings_activity_is_not_exported(self):
        acts = {a.get(ANDROID + "name"): a for a in self.root.iter("activity")}
        self.assertIn(".SettingsActivity", acts)
        self.assertEqual(acts[".SettingsActivity"].get(ANDROID + "exported"),
                         "false")


class BuildConfigTests(unittest.TestCase):
    def setUp(self):
        with open(p("app", "build.gradle"), encoding="utf-8") as fh:
            self.text = fh.read()

    def test_application_id_and_configurable_version(self):
        self.assertIn('namespace \'com.nightrider.driver\'', self.text)
        self.assertIn('applicationId "com.nightrider.driver"', self.text)
        self.assertIn("project.findProperty('nrVersionCode')", self.text)
        self.assertIn("project.findProperty('nrVersionName')", self.text)

    def test_min_and_target_sdk(self):
        self.assertIn("minSdk 26", self.text)
        self.assertIn("targetSdk 34", self.text)
        self.assertIn("compileSdk 34", self.text)

    def test_no_remote_dependencies(self):
        # The build must not need Maven: the dependencies block is empty.
        tail = self.text.split("dependencies {")[-1]
        self.assertEqual(tail.strip().splitlines()[0].strip(), "}")


class LauncherIconTests(unittest.TestCase):
    def _adaptive(self, name):
        tree = ET.parse(p("app", "src", "main", "res", "mipmap-anydpi-v26",
                          name))
        root = tree.getroot()
        self.assertEqual(root.tag, "adaptive-icon")
        bg = root.find("background")
        fg = root.find("foreground")
        self.assertIsNotNone(bg)
        self.assertIsNotNone(fg)
        return bg.get(ANDROID + "drawable"), fg.get(ANDROID + "drawable")

    def test_square_adaptive_icon_wiring(self):
        bg, fg = self._adaptive("ic_launcher.xml")
        self.assertEqual(bg, "@drawable/ic_launcher_background")
        self.assertEqual(fg, "@drawable/ic_launcher_foreground")

    def test_round_adaptive_icon_wiring(self):
        bg, fg = self._adaptive("ic_launcher_round.xml")
        self.assertEqual(bg, "@drawable/ic_launcher_background")
        self.assertEqual(fg, "@drawable/ic_launcher_foreground")

    def test_foreground_and_background_are_valid_xml(self):
        for name in ("ic_launcher_foreground.xml", "ic_launcher_background.xml"):
            ET.parse(p("app", "src", "main", "res", "drawable", name))

    def test_png_mipmaps_exist_at_every_density(self):
        for density, size in DENSITIES.items():
            for stem in ("ic_launcher", "ic_launcher_round"):
                path = p("app", "src", "main", "res", "mipmap-" + density,
                         stem + ".png")
                self.assertTrue(os.path.isfile(path), path)
                with open(path, "rb") as fh:
                    data = fh.read()
                self.assertEqual(png_size(data), (size, size),
                                 "%s/%s.png" % (density, stem))


if __name__ == "__main__":
    unittest.main()
