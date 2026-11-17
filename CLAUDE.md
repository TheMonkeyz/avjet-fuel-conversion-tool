# Avjet fuel conversion tool

Aviation fuel density and volume conversion (ASTM D1250-08 / API MPMS 11.1-2004, refined products,
15 °C base). Android app (`app/`, `core/`) and a PWA (`web/`). Used to compare fuel data with
laboratory certificates of analysis; not for sales. The repo may be shown to auditors.

Read first: [README.md](README.md) (features, build), [docs/VALIDATION.md](docs/VALIDATION.md)
(method, source of every constant, open items), [CHANGELOG.md](CHANGELOG.md).

## Rules for any change to the math

- The math exists twice: `core/src/main/kotlin/com/fuelvolume/core/FuelCalculator.kt` and
  `web/calc.js`. Change both, the same way, with the same tests in `FuelCalculatorTest.kt` and
  `web/calc.test.mjs`.
- Then run `node web/tools/make-grid.mjs` to rewrite `core/src/test/resources/vcf-grid.csv`, and
  explain in the commit why the grid changed. Both suites must pass the grid.
- In the same commit, update `docs/VALIDATION.md` (method, section 2.1 sources, test counts, open
  items) and `CHANGELOG.md`.
- Never write that the tool matches or is certified to the standard beyond what the tests prove.
  Mark every value as confirmed (with its source) or not confirmed. Official values from the standard
  must be recorded with document, page and table.
- Densities are kg/m³ in the core and kg/L on screen. Do not mix them.

## Checks before you say a change is done

- `gradlew.bat :core:test` and `node --test web/` both pass.
- After a UI change, check the Android app and the web app in English and in French.

## Releases

- Version = git tag `vX.Y.Z`; CI builds the signed APK. Android `versionCode` must always go up
  (CI uses 100 + run number). Add the version to `CHANGELOG.md` before tagging.
- When any file in `web/` changes, raise `VERSION` in `web/sw.js`.
- The release key and its passwords are never committed, printed or logged.
- Commit with the identity already set in this repo's git config. Do not change it.
