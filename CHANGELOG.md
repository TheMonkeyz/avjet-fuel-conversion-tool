# Changelog

Each version is a git tag and a GitHub Release with its signed Android APK. The web app is
published from `main`. The method and its verification are in [docs/VALIDATION.md](docs/VALIDATION.md).

## Unreleased

- Documentation: README brought up to date (kg/L, °API, web app, decimal marks); added
  `docs/VALIDATION.md` (method, verification record, open items) and this changelog.
- Test name corrected: the 45 °API test checks 0.8013 kg/L (the name said 0.8016). No change to
  the test values or to the calculation.

## 1.1.0 – 2026-11-16

- Renamed from "Uplift 15" to "Avjet fuel conversion tool". The repository moved to
  `TheMonkeyz/avjet-fuel-conversion-tool`. The Android package name is unchanged, so 1.1.0
  installs over 1.0.0.
- Density is now shown and typed in **kg/L** instead of kg/m³ everywhere (inputs, hints, results,
  errors, chart). The calculation core still works in kg/m³; results are unchanged.
- The density chart has three columns (°C, kg/L, VCF); the kg/m³ column was removed.
- New: **API gravity → density at 15 °C**, in the "Density 15 °C" tab (method in
  `docs/VALIDATION.md` section 4.6), with tests.
- Prefilled numbers use the decimal mark of the chosen language (`0,800` in French). All number
  boxes accept a dot or a comma.
- New: **web app (PWA)** in `web/`, published on GitHub Pages. Same features and same tests as the
  Android app. Works offline and can be added to an iPhone or Android home screen.
- Licensed under GPL-3.0-or-later.

## 1.0.0 – 2026-11-15

- First release, as "Uplift 15". Volume correction to 15 °C (ASTM D1250-80 / API MPMS 11.1
  Table 54B), mass in kg and lb, mass → volume to order, density chart, density at 15 °C from a
  measured density with an optional glass hydrometer correction. English and French.
- GitHub Actions pipeline: tests, signed release APK from version tags.
