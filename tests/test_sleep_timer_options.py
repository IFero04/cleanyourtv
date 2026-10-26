"""Exercise the add-on's real Java input rules without Android or extra dependencies."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "addons/sleep-timer/app/src/main/java/io/github/luyzfernando08/sleeptimer/TimerOptions.java"
PACKAGE = "io.github.luyzfernando08.sleeptimer"


@unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "JDK 17 required for Java input rules")
class SleepTimerOptionsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.directory = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.directory.cleanup)
        folder = Path(cls.directory.name)
        harness = folder / "TimerOptionsHarness.java"
        harness.write_text("""
package io.github.luyzfernando08.sleeptimer;
public class TimerOptionsHarness {
    public static void main(String[] args) {
        if (args[0].equals("extension")) {
            System.out.print(TimerOptions.extensionMinutes(Integer.parseInt(args[1])));
        } else {
            System.out.print(TimerOptions.parseMinutes(args[0].equals("null") ? null : args[1]));
        }
    }
}
""", encoding="utf-8")
        result = subprocess.run(
            ["javac", "-encoding", "UTF-8", "--release", "17", "-d", str(folder), str(SOURCE), str(harness)],
            capture_output=True, text=True, timeout=30,
        )
        if result.returncode:
            raise AssertionError(result.stdout + result.stderr)

    def run_rule(self, mode, value=""):
        result = subprocess.run(
            ["java", "-cp", self.directory.name, PACKAGE + ".TimerOptionsHarness", mode, str(value)],
            capture_output=True, text=True, timeout=10, check=True,
        )
        return int(result.stdout)

    def test_custom_boundaries_and_existing_preset_lengths(self):
        for value in ("1", "5", "15", "30", "60", "90", "120", "720", "005", " 37 "):
            with self.subTest(value=value):
                self.assertEqual(int(value), self.run_rule("parse", value))

    def test_invalid_custom_input_cannot_be_a_timer_length(self):
        self.assertEqual(-1, self.run_rule("null"))
        for value in ("", " ", "0", "-5", "+5", "721", "999", "1.5", "1e2", "5 min", "2 0",
                      "99999999999999999999", "１２"):
            with self.subTest(value=value):
                self.assertEqual(-1, self.run_rule("parse", value))

    def test_configured_warning_extension(self):
        for minutes in (1, 5, 15, 720):
            with self.subTest(minutes=minutes):
                self.assertEqual(minutes, self.run_rule("extension", minutes))

    def test_invalid_warning_extension_preserves_the_shared_default(self):
        for minutes in (-1, 0, 721, 2_147_483_647):
            with self.subTest(minutes=minutes):
                self.assertEqual(15, self.run_rule("extension", minutes))


if __name__ == "__main__":
    unittest.main()
