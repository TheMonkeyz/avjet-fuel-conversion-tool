# Avjet fuel conversion tool

Aviation fuel calculator, as an Android app and as a web app (PWA). It is used to compare fuel
data with laboratory certificates of analysis. It converts a measured density or an API gravity to
the density at 15 °C, gives the density at other temperatures, corrects a volume to 15 °C (the
standard reference temperature) with its VCF, and gives the mass in kg and lb. It is not a
certified measurement and is not used for sales or invoicing.

- Android: download the APK from the [latest release](https://github.com/TheMonkeyz/avjet-fuel-conversion-tool/releases/latest).
- Web (also on iPhone): <https://themonkeyz.github.io/avjet-fuel-conversion-tool/>. In Safari, tap
  Share → Add to Home Screen. It works offline after the first visit.

The method, its limits and the verification record are in [docs/VALIDATION.md](docs/VALIDATION.md).
Changes per version are in [CHANGELOG.md](CHANGELOG.md).

## How it works

It follows **ASTM D1250-08 / API MPMS Chapter 11.1-2004** for generalized refined products
(the procedures behind the former Tables 53B and 54B, 15 °C base). The standard works at 60 °F:

```
ρ60  = density at 60 °F, found from ρ15 by iteration
α60  = K0 / ρ*² + K1 / ρ* + K2                       (per °F; ρ* = ρ60 shifted to the IPTS-68 scale)
CTL  = exp( −α60 · Δt · (1 + 0.8 · α60 · (Δt + δ60)) ),   Δt = t(°F, IPTS-68) − 60.0068749
VCF  = CTL(t) / CTL(15 °C), rounded to 5 decimals
V15  = Vmeasured × VCF
mass = V15 (litres) × ρ15 (kg/L)
```

| Product group | Density at 60 °F (kg/L) | K0        | K1     | K2         |
|---------------|-------------------------|-----------|--------|------------|
| Gasolines     | 0.6106 – 0.770352       | 192.4571  | 0.2438 | 0          |
| Transition    | 0.770352 – 0.7875195    | 1489.0670 | 0      | −0.0018684 |
| Jet fuels     | 0.7875195 – 0.8383127   | 330.3010  | 0      | 0          |
| Fuel oils     | 0.8383127 – 1.1635      | 103.8720  | 0.2701 | 0          |

The constants are for ρ in kg/m³, as in the standard. The screen shows and takes density in kg/L;
the calculation core works in kg/m³ (1 kg/L = 1000 kg/m³) and the screen converts at input and
output. The full method, the source of every constant and what is still to be checked are in
[docs/VALIDATION.md](docs/VALIDATION.md). **The tool has not yet been checked against the official
test data of the standard.**

The group is chosen from the density, as the standard requires. The fuel picker only fills in a
typical density (Jet A-1: 0.800, Jet A: 0.810, Avgas 100LL: 0.720, Other fuel: 0.780 kg/L). Always
type the real density from the laboratory certificate or the fuel release note. "Custom
coefficient" lets you type a coefficient α (per °C) directly; this is outside the standard, and the
tool then gives volumes only, no mass.

Volume can be entered in litres or US gallons. Results keep the same unit.

## Language, numbers and colours

The tool is in English and French. It starts in the phone (or browser) language. The button in the
top bar switches language, and the tool remembers the choice. Android text is in
`app/src/main/res/values/strings.xml` (English) and `values-fr/strings.xml` (French); web text is in
`web/i18n.js`.

Every number box accepts a dot or a comma as the decimal mark (some phone keypads show only one of
them). Results and prefilled values use the mark of the chosen language: `0.800` in English,
`0,800` in French. Because a comma is read as a decimal mark, do not type thousands separators
(`10,000` is read as 10).

Colours are in `app/src/main/java/com/fuelvolume/app/Theme.kt` (web: `web/styles.css`). Light blue
is used only for fills. Text and lines are near-black or dark blue, so every text colour has 7:1
contrast or more, for use in direct sun and at night.

## Mass to volume (fuel to order)

In the Volume tab, pick "Mass → volume". Type the fuel needed for the flight and the fuel on board
(kg or lb), and the fuel temperature. The tool gives the volume to order at that temperature:

```
litres at 15 °C   = (needed − on board) kg / ρ15 (kg/L)
litres to order   = litres at 15 °C / VCF(t)
```

## Density chart

The "Density chart" tab lists the density of the chosen fuel in kg/L and the VCF at every 5 °C from
−40 °C to +50 °C, from the density at 15 °C you typed. The row closest to the fuel temperature you
entered is marked.

## Density at 15 °C

The "Density 15 °C" tab has two modes. In both, a button copies the result into the volume
calculator.

**Measured density.** Converts a density (kg/L) measured at any temperature to the density at
15 °C. The standard solves this by iteration, because the expansion coefficient depends on the
density you are looking for. A switch applies the glass hydrometer correction
(HYC = 1 − 0.000023·Δt − 0.00000002·Δt²) for readings taken with a glass hydrometer. Leave it off
for a digital density meter.

**API gravity.** Converts an API gravity (°API, always at 60 °F) to the density at 15 °C:

```
relative density 60/60 °F = 141.5 / (°API + 131.5)
density at 60 °F (kg/m³)  = relative density × 999.016   (density of water at 60 °F)
density at 15 °C          = density at 60 °F × CTL(15 °C)
```

Example: 45 °API → relative density 0.8017 → 0.8009 kg/L at 60 °F → 0.8013 kg/L at 15 °C.

## Code layout

- `core/` – pure Kotlin. The calculation (`FuelCalculator.kt`) and its unit tests. No Android needed.
- `app/` – the Android app (Jetpack Compose, Material 3, min SDK 26).
- `web/` – the web app (PWA): plain HTML, CSS and JavaScript, no build step and no dependencies.
  `web/calc.js` is a port of `FuelCalculator.kt` and `web/calc.test.mjs` runs the same
  test cases with the same expected values. Any change to the math must be made in both, with both
  test suites passing. See [web/README.md](web/README.md).

## Build and test

```
gradlew.bat :core:test            # Kotlin calculation tests
gradlew.bat :app:assembleDebug    # build app\build\outputs\apk\debug\app-debug.apk
node --test web/                  # web calculation tests (Node 20 or later)
node web/tools/make-grid.mjs      # rewrite the shared Kotlin/web reference grid after a math change
```

The Android SDK location comes from `ANDROID_HOME` or `local.properties` (`sdk.dir=...`).

## Disclaimer

For comparison with laboratory certificates of analysis only. It is not a certified measurement,
and it has not yet been checked against the official test data of ASTM D1250-08 / API MPMS
11.1-2004 (see [docs/VALIDATION.md](docs/VALIDATION.md), open item O1).

## Build and release

GitHub Actions runs two pipelines:

- `.github/workflows/build.yml` runs the Kotlin tests and builds the Android app on every push and
  pull request. A push to `main` gives a signed APK under the run's artifacts.
- `.github/workflows/pages.yml` runs the web tests and, only if they pass, publishes `web/` to
  GitHub Pages. It runs on every push to `main` that changes `web/`.

To publish a release, push a version tag:

```
git tag v1.2.0
git push origin v1.2.0
```

The pipeline then makes a GitHub Release with `avjet-fuel-1.2.0.apk`. The version name comes from
the tag; the Android version code is 100 + the pipeline run number, so it always goes up. Add the
version to [CHANGELOG.md](CHANGELOG.md) before you tag it.

When a file in `web/` changes, also raise `VERSION` in `web/sw.js`, so installed web apps fetch the
new files.

The release key is not in the repo. It is in the repository secrets (`KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). Keep a backup of the key: without it,
installed apps cannot be updated.

## Licence

Copyright (C) 2026 TheMonkeyz

This program is free software: you can redistribute it and/or modify it under the terms of the
GNU General Public License as published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
[LICENSE](LICENSE) file for the full text.
