# Changelog

Each version is a git tag and a GitHub Release with its signed Android APK. The web app is
published from `main`. The method and its verification are in [docs/VALIDATION.md](docs/VALIDATION.md).

## 1.2.0 – 2026-11-16

- **Calculation changed to ASTM D1250-08 / API MPMS Chapter 11.1-2004** (generalized refined
  products, 15 °C base), replacing the 1980 equations. The group is now chosen from the density at
  60 °F; temperatures are converted to the IPTS-68 scale; the VCF is rounded to 5 decimals.
  On the test points, results moved by at most 0.00001 in the VCF. Same change in the Android and
  web code.
- The °API conversion now uses the 60 °F method of the standard (open item O2 closed).
- The expansion coefficient is shown as α60 per °F (the standard's form). Product groups are named
  Gasolines, Transition zone, Jet fuels, Fuel oils.
- Accepted density at 15 °C is now 0.6112–1.1638 kg/L (the 60 °F limits of the standard).
- New cross-check: a 3,713-point reference grid that the Kotlin and web tests must both match.
- Intended use stated: comparison with laboratory certificates of analysis; not for sales.
- Documentation: README brought up to date; added `docs/VALIDATION.md` (method, source of each
  constant, verification record, open items) and this changelog.
- Added `CLAUDE.md`: project rules for changes to the math, docs and releases.
- **Not yet checked against the official test data of the standard** (open item O1).

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
