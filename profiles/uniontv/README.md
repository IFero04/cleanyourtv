# UnionTV profile

An opt-in configuration for this fork, based on the accepted TCL UnionTV setup
(Android 9 / SDK 28). It does not change the launcher's shared defaults or apply
anything to a TV automatically.

## Included configuration

- [Launcher config template](launcher-config.json): four dock slots in the order SmartTube,
  Jellyfin, Prime Video, Sleep Timer; English `en-PT`; icon-only Wi-Fi; the
  customised Retro appearance, animations and sky intensity 60.
- The Home gear opens CleanYourTV's quick panel. **App library** replaces the
  native Apps shortcut there, without the **Re-enable** hint. OK opens apps;
  holding OK offers Home-shortcut actions; Back returns focus to the gear.
- Both launcher key helpers stay off (`key_filter=false`,
  `consume_volume_keys=false`). The physical TCL Settings key is not remapped.
  Ordinary volume tap/hold behaviour is preserved without a key-filter helper.
- [Sleep Timer build options](sleep-timer-options.xml): a five-minute warning
  extension and confirmed custom entry from 1 to 720 minutes. Presets remain
  15/30/60/90/120 minutes; Custom and red Cancel remain side by side with icons.

The config hides the TCL TV shortcut from Home, not the app itself: TV/HDMI inputs
remain available. It does not enable, disable, uninstall or install any app.
The App library manages Home shortcuts, not Android permissions or disabled
packages; use TV system settings for those.

**Not exported:** coordinates or weather location, system time-zone/clock
settings, account data, network information, device identifiers, package/settings
dumps, backups, screenshots, APKs or ADB keys. Weather and sun calculations that
need a location still require your own local configuration. This is not a full
device backup or a debloat list.

## Apply the launcher config

The wallpaper files are already in the repository's
[Retro theme](../../themes/retro/README.md). Credit: **REGT-URRED**;
see its [CC0 wallpaper credits](../../themes/retro/CREDITS.md). They are not
duplicated here. This is a saved template, not a directly pushable theme folder:
stage it as `config.json` together with those pictures before using the existing
theme tool. That also keeps editable working themes separate from tracked files.

From the repository root, after installing a source-built launcher and following
the main README's ADB setup:

```sh
mkdir -p work/uniontv-public-theme/wallpapers
cp -f profiles/uniontv/launcher-config.json work/uniontv-public-theme/config.json
cp -f themes/retro/wallpapers/*.jpg work/uniontv-public-theme/wallpapers/
# Replace this example address with your TV's address; it is not saved in Git.
TV_HOST=192.168.1.50 tools/theme.sh work/uniontv-public-theme
```

Back up any existing theme first. The theme tool replaces the theme and resets
on-TV layout/motion overrides; `tools/theme.sh --reset` returns to compiled
defaults, not your previous theme. Reapply your own backup to recover that theme.
Revoke debugging authorisations, turn debugging off and restart after testing,
then use [the closure check](../../tools/adb-closed.py).

The included `race_calendar` is the setup's October–December 2026 snapshot, not a
live feed. Refresh it on your computer with
`python3 tools/race_calendar.py work/uniontv-public-theme` before a later push;
the launcher itself stays offline. See the
[race calendar tool](../../tools/race_calendar.py).

## Build Sleep Timer with this profile

The add-on's shared defaults remain a 15-minute extension and custom entry off.
With JDK 17 and Android SDK 34 configured, this subshell temporarily uses the
profile resource file and restores the previous source bytes on exit:

```sh
(
  set -eu
  options=addons/sleep-timer/app/src/main/res/values/options.xml
  saved=$(mktemp)
  cp -f "$options" "$saved"
  trap 'cp -f "$saved" "$options"; rm -f "$saved"' EXIT
  cp -f profiles/uniontv/sleep-timer-options.xml "$options"
  addons/sleep-timer/gradlew --no-daemon lintRelease assembleRelease
)
```

This only builds locally. It does not install an APK, enable accessibility or
change a running TV. A later ordinary build uses shared defaults again unless
you apply this profile. For installation and permission details, see the
[Sleep Timer guide](../../addons/sleep-timer/README.md).

## Verification scope

The original custom gear/library and Sleep Timer behaviour was accepted on the
real UnionTV. The subsequent upstream merge is checked with repository tests,
launcher JVM tests, release lint/build and the APK size gate, plus Sleep Timer
build checks. The merged upstream version is not claimed to have been installed
or retested on that TV. No binaries or private TV records are published.
