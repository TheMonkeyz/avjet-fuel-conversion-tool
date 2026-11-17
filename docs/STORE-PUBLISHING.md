# Plan: publishing on Google Play and the Apple App Store

2026-11-16 · Status: draft, waiting for decisions · Planned change 1 is built but not checked on a
device; nothing else in this plan is done yet.

## Summary

The client wants the app on Google Play and on the Apple App Store. Google Play needs one code
change first: since 31 Aug 2026 it accepts only apps that target API 36 (Android 16), and this app
targets API 35. The Apple App Store needs a new iOS app, because there is none today.

This file records the planned changes to the code and the build. Store accounts, fees and the
client's decisions are in the client guide (kept outside this repo).

## Planned change 1: target API 36 (needed for Google Play)

**Status, 2026-11-16: build change done, device check not done.** On branch `build/target-api-36`:

- Versions used: Android Gradle Plugin 8.13.2 (newest 8.13 patch; 8.13 supports up to API 36.1,
  [release notes](https://developer.android.com/build/releases/past-releases/agp-8-13-0-release-notes)),
  Gradle 8.13, `compileSdk = 36`, `targetSdk = 36`, SDK platform `android-36` revision 2. Kotlin
  2.0.21, Compose BOM 2024.12.01, activity-compose 1.9.3, core-ktx 1.15.0 and appcompat 1.7.0 did
  not need a change. No source code change.
- Checked on the PC: `:core:test` (31 tests) and `node --test web/` (32 tests) pass, as before the
  change. `assembleDebug`, `assembleRelease` (unsigned) and `:app:lintDebug` succeed; lint shows no
  finding about API 36. The merged manifest has `targetSdkVersion` 36 and the same permission list
  as before (only `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, added by AndroidX core, also present
  at API 35).
- **Not checked:** none of the device checks below (edge-to-edge, language switch, wide window,
  back gesture, test case). No Android 16 emulator is installed on the PC. The phone (Pixel 8 Pro)
  runs Android 17 (API 37) and holds the release-signed build, so the new build was not installed
  on it.

Why: Google Play refuses new apps and updates that target less than API 36 since 31 Aug 2026
([source](https://developer.android.com/google/play/requirements/target-sdk)). This also applies to
updates, so the change is needed before any Play release.

What changes:

| File | Today | After |
| --- | --- | --- |
| `app/build.gradle.kts` | `compileSdk = 35`, `targetSdk = 35` | `compileSdk = 36`, `targetSdk = 36` |
| Android Gradle Plugin (`build.gradle.kts`) | 8.7.3, made for API 35 | 8.13.2 (supports up to API 36.1; needs Gradle 8.13) |
| Gradle wrapper (`gradle/wrapper/`) | 8.10.2 | 8.13 |
| Compose BOM and AndroidX libraries (`app/build.gradle.kts`) | Current versions | No change: the build with SDK 36 did not need one |

What does not change: the calculation (`core/`), `minSdk = 26`, the package name
`com.fuelvolume.app`, the release key.

Risks to check on the phone, because Android 16 changes app behaviour:

- Edge-to-edge display already applies at API 35; API 36 removes the way to opt out. Check that
  no text or button is under the status bar or the navigation bar, on every tab.
- The in-app language switch (English / French) still works and is kept after a restart.
- Large screens: Android 16 can ignore fixed orientation and size limits on tablets. Check the
  layout on a wide window.
- Back gesture: on Android 16, apps that target API 36 get the predictive back animation by
  default. Check that the back gesture closes the app correctly from every tab.

Source for these three changes:
[Android 16 behaviour changes](https://developer.android.com/about/versions/16/behavior-changes-16).

Checks before the change is done (rules in [CLAUDE.md](../CLAUDE.md)):

- `gradlew.bat :core:test` and `node --test web/` pass.
- The app is checked on the phone in English and in French, all three tabs, with the test case
  Jet A-1 at 0.800 kg/L, 10,000 L at 30 °C gives VCF 0.98600 and 9,860.0 L at 15 °C (the same
  case as [VALIDATION.md](VALIDATION.md) section 6.4).
- `CHANGELOG.md` gets an entry for the new version.

## Planned change 2: Android App Bundle and Play App Signing

Why: Google Play accepts only Android App Bundles (`.aab`) for new apps, and they must use Play App
Signing ([source](https://developer.android.com/guide/app-bundle)).

- Add a bundle build (`gradlew.bat bundleRelease`) to the release workflow
  (`.github/workflows/build.yml`). The signed APK on GitHub Releases stays.
- Give Google the existing release key as the app signing key ("Change app signing key" in the
  Play Console), and make a new upload key for Play uploads
  ([source](https://developer.android.com/studio/publish/app-signing)). Then the GitHub APK and the
  Play app have the same signature, and users can move from one to the other.
- The new upload key is stored like the release key: outside the repo and in CI secrets only.

## Planned change 3: privacy policy and disclaimer in the app

Why: both stores need a privacy policy URL, and Apple also needs a link to it inside the app
(App Review Guideline 5.1.1).

- Write a short privacy policy in English and French. It must say what is true for each app:
  - Both: no account, and no data is sent to us or to anyone else.
  - Android app: no internet permission. Only the language choice is stored on the phone.
  - Web app: the browser downloads it from the internet the first time, then it works offline. The
    browser stores the language choice and the last typed values on the device (`localStorage` in
    `web/app.js`). The web host (GitHub Pages) can log visits; check its privacy statement and
    name it in the policy.
- Android backup: the manifest has `android:allowBackup="true"`, so Android can copy the app data
  to the user's Google account. Set it to `false` (recommended), or say it in the policy.
- Publish it next to the web app (GitHub Pages) or on the client's website.
- Add the link and the intended-use text (comparison with laboratory certificates of analysis, not
  for sales) to an About screen in the Android app and in the web app.

## Planned change 4: iOS app (needed for the App Store)

Recommended: SwiftUI screens that use the same Kotlin calculation core.

- Convert `core/` from a Kotlin JVM module to a Kotlin Multiplatform module, so the same tested
  code runs on Android and iOS. The tests in `core/` and the reference grid stay the same.
- The conversion can change the name of the Gradle test task (`:core:test`). If it does, update
  `.github/workflows/build.yml`, [CLAUDE.md](../CLAUDE.md) and the README in the same commit. Both
  suites (`core/` and `node --test web/`) must still pass.
- New folder `ios/` with the SwiftUI app: three tabs, English and French, same theme.
- Needs a Mac with Xcode 26 or later; Apple accepts only uploads built with Xcode 26 and the iOS 26
  SDK since 28 Apr 2026 ([source](https://developer.apple.com/news/upcoming-requirements/)).
- The iOS app gets the same checks as Android, and its results are recorded in
  [VALIDATION.md](VALIDATION.md) section 6.4.

Not chosen: rewriting the math in Swift (a third copy to keep in step), or wrapping the web app
(Apple can refuse it under Guideline 4.2, minimum functionality).

## Before a public store release

- Open items O1 (no check against the official standard yet) and O8 (Addenda 1 and 2 not checked)
  in [VALIDATION.md](VALIDATION.md) should be closed first, because store users will trust the
  numbers.
- Until O1 and O8 are closed, the store listing, the screenshots and the About screen must not say
  that the app meets or is certified to ASTM D1250-25.
- Licence: the GPL conflicts with the App Store terms for third parties
  ([FSF, 2010](https://www.fsf.org/news/2010-05-app-store-compliance)). The copyright holder can
  still publish their own code there, so the copyright holder must be clear, and outside code
  contributions need a contributor agreement first.

## Order of work

1. Change 1 (API 36) and change 3 (privacy and disclaimer): can start now.
2. Change 2 (bundle and signing): when the client's Play Console account exists.
3. Change 4 (iOS app): when the client approves it and a Mac is available.
