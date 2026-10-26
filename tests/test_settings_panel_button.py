"""Execute Home's actual Settings click body with small Java action recorders.

This covers destination selection without Android or new dependencies. Activity
delivery, focus and Back still need the real-TV checks.
"""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java").read_text(encoding="utf-8")
CLICK = re.search(r"btnSettings\.setOnClickListener\(v -> \{(.*?)\n\s*\}\);", MAIN, re.S).group(1)


class SettingsPanelButtonWiringTest(unittest.TestCase):
    def test_panel_remains_off_by_default(self):
        config = ET.parse(ROOT / "launcher/app/src/main/res/values/config.xml").getroot()
        self.assertEqual("false", config.find("bool[@name='settings_key_panel']").text)

    def test_long_press_keeps_the_existing_menu(self):
        self.assertRegex(MAIN, r"btnSettings\.setOnLongClickListener\(v -> \{\s*showSettingsMenu\(\);\s*return true;")

    def test_combined_top_menu_keeps_its_click_override(self):
        top_menu = MAIN.split("if (cfg.topMenu) {", 1)[1].split("if (iconOnlyButtons)", 1)[0]
        self.assertIn("btnSettings.setOnClickListener(v -> showTopMenu());", top_menu)


@unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "JDK 17 required for click routing")
class SettingsPanelButtonDestinationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.directory = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.directory.cleanup)
        folder = Path(cls.directory.name)
        harness = folder / "SettingsClickHarness.java"
        harness.write_text("""
public class SettingsClickHarness {
    boolean launcherSettingsButton;
    boolean settingsKeyPanel;
    String result;
    static final int LAUNCHER_SETTINGS_REQUEST = 42;
    static class LauncherSettingsActivity {}
    static class QuickSettingsActivity {}
    static class Settings { static final String ACTION_SETTINGS = "TV_SETTINGS"; }
    static class Intent {
        final String target;
        Intent(Object context, Class<?> destination) { target = destination.getSimpleName(); }
        Intent(String action) { target = action; }
    }
    Intent systemIntent(String action) { return new Intent(action); }
    void open(Intent intent) { result = "open:" + intent.target; }
    void startActivityForResult(Intent intent, int request) {
        result = "result:" + intent.target + ":" + request;
    }
    void click() {
""" + CLICK + """
    }
    public static void main(String[] args) {
        SettingsClickHarness screen = new SettingsClickHarness();
        screen.launcherSettingsButton = Boolean.parseBoolean(args[0]);
        screen.settingsKeyPanel = Boolean.parseBoolean(args[1]);
        screen.click();
        System.out.print(screen.result);
    }
}
""", encoding="utf-8")
        subprocess.run(
            ["javac", "-encoding", "UTF-8", "--release", "17", "-d", str(folder), str(harness)],
            capture_output=True, text=True, timeout=30, check=True,
        )

    def destination(self, launcher, panel):
        result = subprocess.run(
            ["java", "-cp", self.directory.name, "SettingsClickHarness", str(launcher).lower(), str(panel).lower()],
            capture_output=True, text=True, timeout=10, check=True,
        )
        return result.stdout

    def test_default_click_still_opens_tv_settings(self):
        self.assertEqual("open:TV_SETTINGS", self.destination(False, False))

    def test_opt_in_click_opens_the_actual_quick_panel(self):
        self.assertEqual("open:QuickSettingsActivity", self.destination(False, True))

    def test_explicit_launcher_settings_keeps_result_delivery_with_either_panel_value(self):
        for panel in (False, True):
            with self.subTest(panel=panel):
                self.assertEqual("result:LauncherSettingsActivity:42", self.destination(True, panel))


if __name__ == "__main__":
    unittest.main()
