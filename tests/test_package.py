"""Source-package checks that run without an Android SDK or built APK."""

import os
import hashlib
import unittest
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ANDROID = "{http://schemas.android.com/apk/res/android}"

# Launcher icon sizes are rendered by Android's launcher from approved source artwork.
DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


def p(*parts):
    return os.path.join(ROOT, *parts)


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

    def test_maplibre_version_is_pinned(self):
        self.assertIn("org.maplibre.gl:android-sdk:13.6.1", self.text)


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
        self.assertEqual(fg, "@drawable/nightrider_launcher_foreground")

    def test_round_adaptive_icon_wiring(self):
        bg, fg = self._adaptive("ic_launcher_round.xml")
        self.assertEqual(bg, "@drawable/ic_launcher_background")
        self.assertEqual(fg, "@drawable/nightrider_launcher_foreground")

    def test_foreground_and_background_are_valid_xml(self):
        for name in ("ic_launcher_foreground.xml", "ic_launcher_background.xml",
                     "nightrider_launcher_foreground.xml"):
            ET.parse(p("app", "src", "main", "res", "drawable", name))

    def test_approved_art_is_used_in_legacy_and_adaptive_launchers(self):
        expected_sha = "fe4db1fd862d15338a1edb2a11a83442cb33477285397757f0655afecc0ea8c4"
        paths = [p("app", "src", "main", "res", "drawable-nodpi", "nightrider_icon.webp")]
        for density in DENSITIES:
            for stem in ("ic_launcher", "ic_launcher_round"):
                paths.append(p("app", "src", "main", "res",
                               "mipmap-" + density, stem + ".webp"))
        for path in paths:
            self.assertTrue(os.path.isfile(path), path)
            with open(path, "rb") as fh:
                data = fh.read()
            self.assertEqual(data[:4], b"RIFF")
            self.assertEqual(data[8:12], b"WEBP")
            self.assertEqual(hashlib.sha256(data).hexdigest(), expected_sha)

    def test_adaptive_foreground_insets_the_approved_icon(self):
        root = ET.parse(p("app", "src", "main", "res", "drawable",
                          "nightrider_launcher_foreground.xml")).getroot()
        self.assertEqual(root.tag, "inset")
        for side in ("Left", "Right", "Top", "Bottom"):
            self.assertEqual(root.get(ANDROID + "inset" + side), "18dp")
        bitmap = root.find("bitmap")
        self.assertIsNotNone(bitmap)
        self.assertEqual(bitmap.get(ANDROID + "src"), "@drawable/nightrider_icon")



class AeroThemeTests(unittest.TestCase):
    def test_aero_theme_is_optional_and_uses_separate_resources(self):
        with open(p("app", "src", "main", "res", "values", "themes.xml"),
                  encoding="utf-8") as fh:
            styles = fh.read()
        tree = ET.fromstring(styles)
        names = {style.get("name") for style in tree.findall("style")}
        self.assertIn("Theme.NightRider", names)
        self.assertIn("Theme.NightRider.Aero", names)
        self.assertIn('<item name="nrPrimaryButton">@drawable/btn_aero_blue</item>', styles)
        self.assertIn('<item name="nrPanelBackground">@drawable/panel_aero</item>', styles)
        for name in ("attrs.xml", "aero_colors.xml"):
            ET.parse(p("app", "src", "main", "res", "values", name))
        for name in ("bg_aero_sky", "panel_aero", "btn_aero_blue",
                     "btn_aero_green", "bubble_aero_me", "bubble_aero_them",
                     "bubble_aero_private"):
            ET.parse(p("app", "src", "main", "res", "drawable", name + ".xml"))

    def test_one_light_dark_setting_preserves_session_and_map_is_unified(self):
        with open(p("app", "src", "main", "java", "com", "nightrider",
                    "driver", "Prefs.kt"), encoding="utf-8") as fh:
            prefs = fh.read()
        self.assertIn('private const val KEY_AERO = "frutiger_aero_ui"', prefs)
        self.assertIn("var aeroEnabled: Boolean", prefs)
        with open(p("app", "src", "main", "res", "layout",
                    "activity_settings.xml"), encoding="utf-8") as fh:
            layout = fh.read()
        self.assertIn('android:id="@+id/appearanceToggle"', layout)
        self.assertNotIn('android:id="@+id/appearanceMode"', layout)
        self.assertNotIn('android:id="@+id/aeroTheme"', layout)
        with open(p("app", "src", "main", "res", "values", "themes.xml"),
                  encoding="utf-8") as fh:
            themes = fh.read()
        self.assertIn('<item name="android:fontFamily">sans-serif-rounded</item>', themes)
        with open(p("app", "src", "main", "AndroidManifest.xml"),
                  encoding="utf-8") as fh:
            manifest = fh.read()
        self.assertIn('android:theme="@style/Theme.NightRider"', manifest)

if __name__ == "__main__":
    unittest.main()
