# Work order: make the Android app target API 36

Written 2026-11-16 for the agent that does the work. Read all of it before you start.

## Goal

Google Play has accepted only apps and updates that target Android 16 (API level 36) since
31 Aug 2026 ([source](https://developer.android.com/google/play/requirements/target-sdk)). This
app targets API 35, so it cannot go on Play. Raise it to API 36 with the smallest build change that
works, prove that nothing a user sees has changed, and leave the result as local commits for the
owner to review.

This is "Planned change 1" in [STORE-PUBLISHING.md](STORE-PUBLISHING.md). Changes 2, 3 and 4 of that
file are not part of this work.

## What the project is

Read [CLAUDE.md](../CLAUDE.md) first; its rules apply. In short: a small Compose app (`app/`, one
activity, `MainActivity.kt`) on top of a pure Kotlin calculation module (`core/`), plus a web copy
(`web/`). The repo may be shown to auditors, so docs must say only what was really done and checked.

Build state today:

| Item | Where | Today |
| --- | --- | --- |
| `compileSdk`, `targetSdk` | `app/build.gradle.kts` | 35, 35 |
| `minSdk` | `app/build.gradle.kts` | 26 |
| Android Gradle Plugin (AGP) | `build.gradle.kts` | 8.7.3 |
| Kotlin and Compose compiler plugins | `build.gradle.kts` | 2.0.21 |
| Gradle wrapper | `gradle/wrapper/gradle-wrapper.properties` | 8.10.2 |
| Compose BOM | `app/build.gradle.kts` | 2024.12.01 |
| activity-compose / core-ktx / appcompat | `app/build.gradle.kts` | 1.9.3 / 1.15.0 / 1.7.0 |
| JDK | CI and `compileOptions` | 17 |
| Local SDK (`%LOCALAPPDATA%\Android\Sdk`) | `platforms`, `build-tools` | only `android-35`; 34.0.0 and 35.0.0 |

## Facts already checked (2026-11-16)

Confirmed on the Android developer site:

- AGP 8.9.0 supports API 35 at most. It is not enough.
- AGP 8.13 supports up to API 36.1. It needs Gradle 8.13 or later, SDK Build Tools 35.0.0 or later
  and JDK 17 ([release notes](https://developer.android.com/build/releases/past-releases/agp-8-13-0-release-notes)).
- The newest AGP is 9.5. It needs Gradle 9.6 and Build Tools 36. Do not use it here (see "Limits").

Not confirmed, find out during the work:

- Whether Kotlin 2.0.21 builds cleanly with AGP 8.13. It is older than AGP 8.13, so a "not tested
  with this AGP version" warning is likely. A warning is acceptable; a failure is not.
- Whether the current Compose BOM and AndroidX versions build against SDK 36 without change.
- The Android version of the test phone.

## Review of the code against the Android 16 changes

Source: [behaviour changes for apps targeting Android 16](https://developer.android.com/about/versions/16/behavior-changes-16).
I read `AndroidManifest.xml`, `themes.xml` and `MainActivity.kt` against that list. I expect **no
source code change**. Confirm each row on the device; do not take it on trust.

| Android 16 change | State of this app | What to check |
| --- | --- | --- |
| Edge-to-edge can no longer be switched off | Already calls `enableEdgeToEdge()`; the `Scaffold` passes `innerPadding` to the content; the theme has no opt-out attribute | No text or control sits under the status bar, the navigation bar or the camera cutout, on all tabs, in portrait and landscape, with gesture and with 3-button navigation |
| Predictive back is on by default; `onBackPressed` is no longer called | No back handling in the code at all | Back from each tab leaves the app normally, with the back-to-home animation |
| Orientation, resizability and aspect-ratio limits are ignored on screens of 600 dp and wider | The manifest sets no such limit | Layout is usable in a wide window (tablet or resizable emulator, or split screen) |
| `elegantTextHeight` is ignored | Not used | Nothing |
| `scheduleAtFixedRate`, health permissions, Bluetooth, MediaStore, local network, photo picker | None used; the app asks for no permission | Nothing. Confirm that the merged manifest still has no `<uses-permission>` |

Do not add either temporary opt-out (`android:enableOnBackInvokedCallback="false"`,
`android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY`). If the app seems to need one, that
is a finding: stop and report it.

One existing detail to watch: with the keyboard open (`windowSoftInputMode="adjustResize"`), the
result card under the input fields must still be reachable by scrolling. This behaviour dates from
API 35; note it if it is bad, but treat a fix as a separate change unless API 36 made it worse.

## Steps

1. **Branch.** The work tree is on `docs/store-publishing`, which holds
   `docs/STORE-PUBLISHING.md` and this file. Create `build/target-api-36` from it.
2. **Baseline.** Before any edit, run `gradlew.bat :core:test`, `node --test web/` and
   `gradlew.bat assembleDebug`. All three must pass. If one fails, stop and report: the fault is
   not yours to hide inside this change.
3. **Build tooling.** Set AGP to the newest 8.13.x patch (8.13.0 is confirmed to exist) and the
   Gradle wrapper to 8.13 (`gradlew.bat wrapper --gradle-version 8.13`, so the wrapper jar and
   scripts are regenerated too). Build again with SDK 35 still in place. This separates tooling
   problems from SDK problems. Commit.
4. **SDK 36.** Set `compileSdk = 36` and `targetSdk = 36`. The `android-36` platform is not
   installed on this PC; AGP downloads it when the licences are accepted, otherwise install
   `platforms;android-36` with `sdkmanager`. Build. Commit.
5. **Libraries, only if forced.** Raise Kotlin, the Compose BOM or an AndroidX library only when a
   build error or a lint error names it, and only as far as needed. Record in the commit message
   which error forced each bump. If Kotlin must go up, keep the three Kotlin plugin versions in
   `build.gradle.kts` identical and re-run `:core:test`, because `core/` compiles with the same
   plugin.
6. **Static checks.** `gradlew.bat :app:lintDebug` and read the report for new API 36 findings.
   Check the merged manifest
   (`app/build/intermediates/merged_manifests/debug/.../AndroidManifest.xml`): `targetSdkVersion`
   is 36, no permission appeared.
7. **Release build path.** Run `gradlew.bat assembleRelease` without the keystore variables. It
   must still produce an unsigned APK. This proves the CI path builds; do not sign anything.
8. **CI.** Read `.github/workflows/build.yml`. It uses the Gradle wrapper and the SDK on the
   runner, and it picks the newest `apksigner`, so it should need no edit. Change it only if a step
   cannot work with SDK 36, and say why.
9. **Device check.** See the next section.
10. **Docs**, in the same branch:
    - `CHANGELOG.md`: add an entry under a new heading `## Unreleased`. Say that the app now
      targets Android 16 (API 36), which tool versions changed, and "No change to the calculation".
      The owner picks the version number at release time.
    - `docs/STORE-PUBLISHING.md`, "Planned change 1": mark it done, replace "not confirmed yet"
      with the versions really used, and list which device checks were done, on what device and
      Android version, and which were not.
    - `README.md`: update any SDK, AGP or Gradle version it states (search for `35` and `8.7`).
    - `docs/VALIDATION.md`: add the device result where section 6.4 records device checks, if you
      ran the test case on a device. Do not change any wording about the standard.
11. **Report** (see "What to hand back").

## Device check

The numbers to reproduce, from `docs/VALIDATION.md` section 6.4: Jet A-1, density 0.800 kg/L,
10,000 L at 30 °C gives VCF 0.98600 and 9,860.0 L at 15 °C.

Run the full check twice, in English and in French: the three tabs, both modes of the first tab,
both modes of the "Density 15 °C" tab, the fuel menu, the language button, then the rows of the
review table above. After a language switch, close and reopen the app: the language must stay.

Use a device or emulator that runs **Android 16 (API 36)**. Most of the new behaviour appears only
there; a test on Android 15 proves little. Options, in order:

1. An API 36 emulator, if one can be created on this PC (no AVD or system image was checked). A
   debug build installs there with no key. Use a resizable or tablet profile for the wide-window
   row as well.
2. The owner's phone. It is reached over wireless adb, and the pairing code changes every time, so
   **the owner must be present**. Check its Android level first:
   `adb shell getprop ro.build.version.sdk`. The phone holds a release-signed build
   (versionCode 113), so a debug APK will not install over it. Do not uninstall the app. Do not
   read or use the release key yourself. Ask the owner to make and install the signed build, or to
   approve another way.

If neither is possible, finish every other step, and state plainly in the docs and in the report
that the device check was not done. Do not write "checked" for anything you did not see on a
screen.

## Limits

- No change in `core/` or `web/`. This work must not touch the math, the test grid or `web/sw.js`.
- `minSdk` stays 26. `applicationId` and `namespace` stay `com.fuelvolume.app`.
- No AGP 9. It needs Gradle 9 and changes how Kotlin is applied; that is a separate migration with
  no benefit for this goal.
- No refactoring, no dependency upgrades "while we are here", no UI redesign, no removal of
  `kotlinOptions` deprecation warnings unless they became errors.
- `android:allowBackup` belongs to planned change 3. Leave it.
- Never read, print, log or commit the release key or its passwords.
- Commit with the git identity already set in the repo. Do not change it.
- Do not push, do not open a pull request, do not create a tag or a release. The owner does that
  after review.
- Do not write anywhere that the tool meets or is certified to ASTM D1250-25.

## Done means

- `gradlew.bat :core:test` and `node --test web/` pass, with the same test counts as the baseline.
- `assembleDebug`, `assembleRelease` (unsigned) and `:app:lintDebug` succeed, and the merged
  manifest shows `targetSdkVersion` 36 and no permission.
- The device check is done on Android 16 in English and French, or its absence is stated.
- `git diff` against the start branch shows only: the two Gradle build files, the wrapper files,
  the docs named above, and nothing in `core/` or `web/`.

## What to hand back

A short report with: the final versions (AGP, Gradle, Kotlin, BOM, libraries) and what forced each
change; the test counts before and after; new build or lint warnings worth knowing; each row of the
review table with "seen on <device, Android version>" or "not checked"; anything that surprised you;
and the list of commits. Separate what you saw from what you infer.
