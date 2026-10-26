"""Quick-panel library opt-in: execute its real row selection and guard Home delivery."""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java").read_text(encoding="utf-8")
QUICK = (ROOT / "launcher/app/src/main/java/com/gathin/tvhome/QuickSettingsActivity.java").read_text(encoding="utf-8")


class QuickPanelLibraryWiringTest(unittest.TestCase):
    def test_library_is_still_off_by_default(self):
        config = ET.parse(ROOT / "launcher/app/src/main/res/values/config.xml").getroot()
        self.assertEqual("false", config.find("bool[@name='app_library']").text)

    def test_request_is_handled_on_cold_and_warm_home(self):
        self.assertIn("openAppLibraryIfAsked(getIntent())", MAIN)
        self.assertIn("if (openAppLibraryIfAsked(intent)) return;", MAIN)
        method = MAIN.split("private boolean openAppLibraryIfAsked(Intent intent)", 1)[1].split("\n    }", 1)[0]
        self.assertIn("ACTION_APP_LIBRARY.equals(intent.getAction())", method)
        self.assertIn("setIntent(new Intent(Intent.ACTION_MAIN))", method)
        self.assertIn("if (!appLibraryEnabled) return true;", method)

    def test_popup_waits_for_tiles_and_window_focus_and_consumes_once(self):
        method = MAIN.split("private void startPendingAppLibrary()", 1)[1].split("\n    }", 1)[0]
        for guard in ("!appLibraryRequestPending", "!tilesReady", "!hasWindowFocus()", "isFinishing()"):
            self.assertIn(guard, method)
        self.assertLess(method.index("appLibraryRequestPending = false"), method.index("onLibraryRequested()"))
        self.assertGreaterEqual(MAIN.count("startPendingAppLibrary();"), 3)

    def test_dismissing_a_settings_library_returns_focus_to_the_gear(self):
        self.assertRegex(MAIN, r"if \(libraryFromSettings\) \{\s*libraryFromSettings = false;\s*findViewById\(R.id.btnSettings\).requestFocus\(\);\s*return;")

    def test_settings_entry_prefers_the_current_home_app(self):
        method = MAIN.split("private void startPendingAppLibrary()", 1)[1].split("\n    }", 1)[0]
        self.assertIn("if (adapter.appCount() > 0) appLibrary.focusPackage(", method)
        self.assertIn("adapter.packageAt(Math.max(0, Math.min(lastFocused, adapter.appCount() - 1)))", method)


@unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "JDK 17 required for Apps row routing")
class QuickPanelLibraryRowTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.directory = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.directory.cleanup)
        folder = Path(cls.directory.name)
        start = QUICK.index("addRow(rows, R.drawable.ic_apps,")
        statement = QUICK[start:QUICK.index("// Through Home", start)]
        declaration = re.search(r"boolean library = Config\.load\(this\)\.appLibrary;", QUICK)
        body = (declaration[0] if declaration else "") + "\n" + statement
        harness = folder / "QuickAppsHarness.java"
        harness.write_text("""
public class QuickAppsHarness {
    Object rows = new Object();
    String result;
    static class Config {
        static boolean enabled;
        boolean appLibrary = enabled;
        static Config load(Object context) { return new Config(); }
    }
    static class R {
        static class drawable { static final int ic_apps = 1; }
        static class string { static final int app_library = 2, quick_apps = 3, quick_apps_hint = 4; }
    }
    static class MainActivity { static final String ACTION_APP_LIBRARY = "APP_LIBRARY"; }
    static class Settings { static final String ACTION_MANAGE_APPLICATIONS_SETTINGS = "SYSTEM_APPS"; }
    static class Intent {
        String target;
        Intent(Object context, Class<?> destination) { target = destination.getSimpleName(); }
        Intent(String action) { target = action; }
        Intent setAction(String action) { target += ":" + action; return this; }
    }
    static class SystemSettings {
        static Intent intent(Object context, String action) { return new Intent(action); }
    }
    void addRow(Object rows, int icon, int title, int status, Intent target) {
        result = title + ":" + status + ":" + target.target;
    }
    void buildRow() {
""" + body + """
    }
    public static void main(String[] args) {
        Config.enabled = Boolean.parseBoolean(args[0]);
        QuickAppsHarness screen = new QuickAppsHarness();
        screen.buildRow();
        System.out.print(screen.result);
    }
}
""", encoding="utf-8")
        subprocess.run(["javac", "--release", "17", "-d", str(folder), str(harness)],
                       capture_output=True, text=True, timeout=30, check=True)

    def row(self, enabled):
        return subprocess.run(["java", "-cp", self.directory.name, "QuickAppsHarness", str(enabled).lower()],
                              capture_output=True, text=True, timeout=10, check=True).stdout

    def test_default_keeps_system_app_management(self):
        self.assertEqual("3:4:SYSTEM_APPS", self.row(False))

    def test_opt_in_names_the_library_removes_the_hint_and_targets_home(self):
        self.assertEqual("2:0:MainActivity:APP_LIBRARY", self.row(True))


if __name__ == "__main__":
    unittest.main()
