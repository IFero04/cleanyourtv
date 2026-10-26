"""F1 temporarily owns the top shelf, without moving the dock or covering the grid."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java"
ANDROID = "{http://schemas.android.com/apk/res/android}"


def method(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


class RaceShelfWiringTest(unittest.TestCase):
    def test_focus_and_animation_paths_cannot_reveal_the_hidden_title(self):
        source = MAIN.read_text()
        self.assertIn("dockTitle = cfg.dockTitle;", source)
        self.assertIn("if (!dockTitle) hero.setVisibility(View.GONE);", source)
        for signature in ("private void showHero(", "private void fillHero(",
                          "private void showGlowOnly(", "private void hideHero("):
            with self.subTest(method=signature):
                self.assertNotIn("setVisibility(", method(source, signature))
        # A view's visibility, unlike its alpha, cannot be undone by a pending fade-in callback.
        self.assertEqual(2, source.count("hero.setVisibility("))

    def test_race_text_is_above_the_scrim_but_below_the_header_and_grid(self):
        for folder in ("layout", "layout-iw"):
            with self.subTest(layout=folder):
                root = ET.parse(ROOT / f"launcher/app/src/main/res/{folder}/activity_main.xml").getroot()
                children = list(root)
                ids = [view.get(ANDROID + "id") for view in children]
                scrim = ids.index("@+id/heroScrim")
                race = ids.index("@+id/racePanel")
                hero = ids.index("@+id/hero")
                content = next(i for i, view in enumerate(children)
                               if any(v.get(ANDROID + "id") == "@+id/grid" for v in view.iter()))
                self.assertLess(scrim, race)
                self.assertLess(race, hero)
                self.assertLess(race, content)
                self.assertEqual("false", children[race].get(ANDROID + "focusable"))
        self.assertNotIn("racePanel.bringToFront()", MAIN.read_text())


@unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "JDK 17 required for shelf transitions")
class RaceShelfTransitionsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.directory = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.directory.cleanup)
        folder = Path(cls.directory.name)
        # Execute the production scheduling/visibility prefix, not a Python copy of its policy.
        # Calendar time boundaries themselves are covered by RaceWeekendTest on the JVM.
        update = method(MAIN.read_text(), "private void updateRace()")
        prefix = update[:update.index("RaceWeekend.Race race = s.race;")] + "}"
        harness = folder / "RaceShelfHarness.java"
        harness.write_text("""
import java.util.*;
public class RaceShelfHarness {
    static class View {
        static final int VISIBLE = 0, INVISIBLE = 4, GONE = 8;
        int visibility;
        void setVisibility(int value) { visibility = value; }
    }
    static class RaceWeekend {
        static boolean active;
        static class State {}
        static State at(List<String> calendar, java.time.Instant now) {
            return active ? new State() : null;
        }
    }
    boolean dockTitle;
    View hero = new View(), racePanel = new View();
    List<String> raceCalendar = Collections.singletonList("configured");
""" + prefix + """
    public static void main(String[] args) {
        RaceShelfHarness home = new RaceShelfHarness();
        home.dockTitle = Boolean.parseBoolean(args[0]);
        for (int i = 1; i < args.length; i++) {
            RaceWeekend.active = args[i].equals("active");
            home.raceCalendar = args[i].equals("empty") ? Collections.emptyList()
                    : Collections.singletonList("configured");
            home.updateRace();
            System.out.println(home.hero.visibility);
        }
    }
}
""")
        subprocess.run(["javac", "--release", "17", "-d", str(folder), str(harness)],
                       check=True, capture_output=True, text=True)

    def run_states(self, dock_title, *states):
        result = subprocess.run(["java", "-cp", self.directory.name, "RaceShelfHarness",
                                 str(dock_title).lower(), *states],
                                check=True, capture_output=True, text=True)
        return [int(line) for line in result.stdout.splitlines()]

    def test_title_returns_on_exit_without_a_focus_change(self):
        self.assertEqual([0, 4, 0], self.run_states(True, "inactive", "active", "inactive"))

    def test_repeated_updates_keep_the_title_hidden_and_its_space_reserved(self):
        self.assertEqual([4, 4, 4, 4, 0],
                         self.run_states(True, "active", "active", "active", "active", "inactive"))

    def test_dock_title_off_stays_gone_through_every_transition(self):
        self.assertEqual([8, 8, 8, 8], self.run_states(False, "inactive", "active", "active", "inactive"))

    def test_removing_the_calendar_restores_the_normal_shelf(self):
        self.assertEqual([4, 0, 0], self.run_states(True, "active", "empty", "inactive"))


if __name__ == "__main__":
    unittest.main()
