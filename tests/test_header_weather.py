"""The optional clock weather remains local, inactive by default and outside D-pad focus."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "launcher/app/src/main/java/com/gathin/tvhome"
WEATHER = ROOT / "addons/weather/app/src/main"
ANDROID = "{http://schemas.android.com/apk/res/android}"


class HeaderWeatherWiringTest(unittest.TestCase):
    def test_default_is_off_and_the_controller_is_created_only_for_the_opt_in(self):
        config = ET.parse(ROOT / "launcher/app/src/main/res/values/config.xml").getroot()
        self.assertEqual("false", config.find("bool[@name='header_weather']").text)
        main = (JAVA / "MainActivity.java").read_text()
        self.assertRegex(main, r"(?s)if \(cfg.headerWeather\) \{.*?headerWeather = new HeaderWeather\(this,")
        reader = (JAVA / "Config.java").read_text()
        self.assertIn('res.getBoolean(R.bool.header_weather)', reader)
        self.assertIn('headerWeather = bool(json, "header_weather", headerWeather)', reader)

    def test_weather_reuses_the_divider_and_keeps_the_date_below(self):
        main = (JAVA / "MainActivity.java").read_text()
        self.assertIn("if (cfg.dateBelowClock || cfg.headerWeather) moveDateBelowClock();", main)
        self.assertIn("clockRow.setBaselineAligned(false)", main)
        layout = ET.parse(ROOT / "launcher/app/src/main/res/layout/activity_main.xml").getroot()
        label = next(v for v in layout.iter() if v.get(ANDROID + "id") == "@+id/headerWeather")
        self.assertEqual("gone", label.get(ANDROID + "visibility"))
        self.assertEqual("false", label.get(ANDROID + "focusable"))
        self.assertEqual("14sp", label.get(ANDROID + "textSize"))
        self.assertEqual("center_vertical", label.get(ANDROID + "layout_gravity"))
        self.assertIsNone(label.get(ANDROID + "layout_marginBottom"))
        self.assertEqual("#E6FFFFFF", label.get(ANDROID + "textColor"))
        self.assertEqual("@string/font_regular", label.get(ANDROID + "fontFamily"))
        self.assertIn("divider.gravity = Gravity.CENTER_VERTICAL", main)
        self.assertIn("divider.bottomMargin = 0", main)
        parent = next(v for v in layout.iter() if label in list(v))
        self.assertIn("@+id/clockDivider", [v.get(ANDROID + "id") for v in parent])

    def test_mirrored_header_also_places_weather_on_the_clock_row(self):
        layout = ET.parse(ROOT / "launcher/app/src/main/res/layout-iw/activity_main.xml").getroot()
        label = next(v for v in layout.iter() if v.get(ANDROID + "id") == "@+id/headerWeather")
        parent = next(v for v in layout.iter() if label in list(v))
        self.assertIn("@+id/clock", [v.get(ANDROID + "id") for v in parent])
        self.assertEqual("gone", label.get(ANDROID + "visibility"))
        self.assertEqual("false", label.get(ANDROID + "focusable"))
        main = (JAVA / "MainActivity.java").read_text()
        self.assertIn("if (line.getParent() != clockRow)", main)
        self.assertIn("clockRow.addView(line, clockRow.indexOfChild(findViewById(R.id.headerWeather)))", main)

    def test_controller_has_lifecycle_guards_and_no_background_schedule(self):
        main = (JAVA / "MainActivity.java").read_text()
        for action in ("resume", "pause", "expire", "close"):
            self.assertIn(f"if (headerWeather != null) headerWeather.{action}();", main)
        controller = (JAVA / "HeaderWeather.java").read_text()
        self.assertNotIn("postDelayed", controller)
        self.assertNotIn("registerReceiver", controller)
        self.assertNotRegex(controller, r"HttpURLConnection|https?://|java\.net|Socket")
        self.assertIn("if (pending || closed) return", controller)
        self.assertIn("if (closed) return", controller)
        self.assertIn("if (active) render()", controller)
        self.assertIn("io.shutdownNow()", controller)

    def test_no_new_launcher_permission_or_weather_service(self):
        manifest = ET.parse(ROOT / "launcher/app/src/main/AndroidManifest.xml").getroot()
        permissions = {v.get(ANDROID + "name") for v in manifest.findall("uses-permission")}
        self.assertNotIn("android.permission.INTERNET", permissions)
        weather = ET.parse(WEATHER / "AndroidManifest.xml").getroot()
        self.assertEqual({"android.permission.INTERNET"},
                         {v.get(ANDROID + "name") for v in weather.findall("uses-permission")})
        self.assertEqual([], weather.findall("application/service"))
        self.assertEqual([], weather.findall("application/receiver"))

    def test_both_ends_check_the_package_and_signing_key(self):
        controller = (JAVA / "HeaderWeather.java").read_text()
        provider = (WEATHER / "java/io/github/sumyabazar1/weather/WeatherProvider.java").read_text()
        self.assertIn("SOURCE.equals(provider.packageName)", controller)
        self.assertIn("checkSignatures", controller)
        self.assertIn("getCallingPackage()", provider)
        self.assertIn("checkSignatures", provider)
        self.assertIn('"com.gathin.tvhome".equals(caller)', provider)
        self.assertIn("throw new SecurityException", provider)

    def test_ipc_returns_only_a_location_free_reading(self):
        provider = (WEATHER / "java/io/github/sumyabazar1/weather/WeatherProvider.java").read_text()
        fields = set(re.findall(r'result\.put\w+\("([a-z_]+)"', provider))
        self.assertEqual({"temperature_c", "code", "day", "updated_at", "condition"}, fields)
        self.assertNotIn("extras.get", provider)
        self.assertNotIn("postDelayed", provider)
        self.assertIn("Forecast.fetchCurrent(latitude, longitude)", provider)
        self.assertIn('cache.edit().clear().putLong("lat", lat).putLong("lon", lon).apply()', provider)
        self.assertIn('place.getLong("lat", 0) != lat || place.getLong("lon", 0) != lon', provider)


if __name__ == "__main__":
    unittest.main()
