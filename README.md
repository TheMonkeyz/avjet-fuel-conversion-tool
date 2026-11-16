# Avjet fuel conversion tool

Android app for aviation fuel. You type the uplifted volume and the fuel temperature from the
meter. The app corrects the volume to 15 °C, the standard reference temperature. It also shows the
VCF, the density at the fuel temperature, and the fuel mass in kg and lb. Fuel expands when warm
and shrinks when cold, so only the 15 °C volume can be compared from one uplift to the next.

## How it works

It uses the generalized equations of ASTM D1250-80 / API MPMS Chapter 11.1 (metric, 15 °C base,
Table 54B for refined products):

```
α15 = K0 / ρ15² + K1 / ρ15
VCF = exp( −α15 · Δt · (1 + 0.8 · α15 · Δt) ),   Δt = t − 15
V15 = Vmeasured × VCF
mass = V15 (litres) × ρ15 / 1000
```

| Product group (Table 54B)   | Density at 15 °C (kg/m³) | K0        | K1     |
|-----------------------------|--------------------------|-----------|--------|
| Gasoline / avgas            | 653 – 770.352            | 346.4228  | 0.4388 |
| Transition zone             | 770.352 – 787.5253       | A = −0.00336312, B = 2680.3206 (α = A + B/ρ²) |
| Jet fuel / kerosene         | 787.5253 – 838.3127      | 594.5418  | 0      |
| Fuel oil / diesel           | 838.3127 – 1075          | 186.9696  | 0.4862 |

The group is chosen from the density, as the standard requires. The fuel picker only fills in a
typical density (Jet A-1: 800, Jet A: 810, Avgas 100LL: 720). Always type the real density from
the fuel release note when you have it.

Volume can be entered in litres or US gallons. Results keep the same unit.

## Language and colours

The app is in English and French. It starts in the phone language. The button in the top bar
switches language, and the app remembers the choice. Text is in `app/src/main/res/values/strings.xml`
(English) and `values-fr/strings.xml` (French).

Colours are in `app/src/main/java/com/fuelvolume/app/Theme.kt`. Light blue is used only for fills.
Text and lines are near-black or dark blue, so every text colour has 7:1 contrast or more,
for use in direct sun and at night.

## Mass to volume (fuel to order)

In the Volume tab, pick "Mass → volume". Type the fuel needed for the flight and the fuel on board
(kg or lb), and the fuel temperature. The app gives the volume to order at that temperature:

```
litres at 15 °C   = (needed − on board) kg × 1000 / ρ15
litres to order   = litres at 15 °C / VCF(t)
```

## Density chart

The "Density chart" tab lists the density of the chosen fuel (kg/m³ and kg/L) and the VCF at every
5 °C from −40 °C to +50 °C, from the density at 15 °C you typed. The row closest to the fuel
temperature you entered is marked.

## Density to 15 °C

The "Density to 15 °C" tab converts a density measured at any temperature to the density at 15 °C
(Table 53B). The standard solves this by iteration, because the expansion coefficient depends on the
15 °C density you are looking for. A switch applies the glass hydrometer correction
(HYC = 1 − 0.000023·Δt − 0.00000002·Δt²) for readings taken with a glass hydrometer. Leave it off
for a digital density meter. A button copies the result into the volume calculator.

## Modules

- `core/` – pure Kotlin. The calculation and its unit tests. No Android needed.
- `app/` – the Android app (Jetpack Compose, Material 3, min SDK 26).

## Build

```
gradlew.bat :core:test            # run the calculation tests
gradlew.bat :app:assembleDebug    # build app\build\outputs\apk\debug\app-debug.apk
```

The Android SDK location comes from `ANDROID_HOME` or `local.properties` (`sdk.dir=...`).

## Disclaimer

For guidance only. It is not a certified measurement. Use the certified figures from the fuel
release note for the load sheet.

## Build and release

GitHub Actions (`.github/workflows/build.yml`) runs the tests and builds the app on every push and
pull request. A push to `main` gives a signed APK under the run's artifacts.

To publish a release, push a version tag:

```
git tag v1.1.0
git push origin v1.1.0
```

The pipeline then makes a GitHub Release with `avjet-fuel-1.1.0.apk`.

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
