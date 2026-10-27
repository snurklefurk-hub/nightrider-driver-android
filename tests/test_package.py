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



class MapPreviewIsolationTests(unittest.TestCase):
    """Keep the experimental vector Map Lab out of the signed driver app."""

    def test_preview_uses_a_different_application_id(self):
        with open(p("nav-prototype", "prototype", "build.gradle"),
                  encoding="utf-8") as fh:
            build = fh.read()
        self.assertIn("applicationId 'com.nightrider.navpreview'", build)
        self.assertNotIn("applicationId 'com.nightrider.driver'", build)

    def test_preview_manifest_does_not_request_driver_privileges(self):
        manifest = ET.parse(p("nav-prototype", "prototype", "src",
                              "main", "AndroidManifest.xml")).getroot()
        declared = {item.get(ANDROID + "name") for item in
                    manifest.iter("uses-permission")}
        self.assertIn("android.permission.INTERNET", declared)
        for permission in ("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION",
                           "ACCESS_BACKGROUND_LOCATION", "REQUEST_INSTALL_PACKAGES",
                           "WAKE_LOCK"):
            self.assertNotIn("android.permission." + permission, declared)
        app = manifest.find("application")
        self.assertEqual(app.get(ANDROID + "allowBackup"), "false")




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
        self.assertIn('android:id="@+id/appearanceMode"', layout)
        self.assertIn('android:id="@+id/lightMode"', layout)
        self.assertIn('android:id="@+id/darkMode"', layout)
        self.assertNotIn('android:id="@+id/aeroTheme"', layout)
        with open(p("nav-prototype", "prototype", "src", "main", "java",
                    "com", "nightrider", "navpreview", "PreviewActivity.kt"),
                  encoding="utf-8") as fh:
            preview = fh.read()
        self.assertIn('listOf("LIGHT", "DARK")', preview)
        self.assertIn('selectStyle(if (aeroUi) 0 else 1)', preview)
        self.assertNotIn('putString("map_mode"', preview)
        self.assertNotIn('"Aero UI"', preview)
        self.assertNotIn('"Classic UI"', preview)
        self.assertIn('Typeface.create("sans-serif-rounded"', preview)
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
