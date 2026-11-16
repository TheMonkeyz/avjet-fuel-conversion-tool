# Method and verification record

This document describes what the Avjet fuel conversion tool calculates, which standards it
follows, how the calculation was checked, and what is not yet checked. It is written so that an
auditor can trace each result back to a formula, each formula to the code, and each piece of code to
a test.

| Item | Value |
|---|---|
| Tool | Avjet fuel conversion tool (Android app `com.fuelvolume.app` and web app) |
| Version covered | 1.1.0 (git tag `v1.1.0`) |
| Source code | <https://github.com/TheMonkeyz/avjet-fuel-conversion-tool> |
| Document status | Kept in the repository; change it in the same commit as any change to the math |

## 1. Intended use and limits

The tool gives **guidance values** for aviation fuel handling: volume corrected to 15 °C, mass from
volume, volume to order for a mass, density at other temperatures, and density at 15 °C from a
measured density or an API gravity.

It is **not** a certified measurement system and it is not a legal-metrology instrument. The
figures for the load sheet and for invoicing must come from the certified fuel release note and the
certified meter. Every screen shows this disclaimer.

## 2. Standards and references

| Reference | Used for |
|---|---|
| ASTM D1250-80 / API MPMS Chapter 11.1 (1980), Table 54B "Generalized Products, Correction of Volume to 15 °C against Density at 15 °C" | α15 equations, constants, group limits, VCF equation |
| Same standard, Table 53B "Generalized Products, Correction of Observed Density to Density at 15 °C" | Density at 15 °C from a measured density (iteration, section 4.4) |
| Glass hydrometer correction, as given in the code; source to be confirmed (open item O4) | HYC formula (section 4.5) |
| Definition of API gravity (API MPMS / ASTM D287, D1298) | °API → relative density 60/60 °F (section 4.6) |
| Density of water at 60 °F = 999.016 kg/m³ (API MPMS Chapter 11.5; to be confirmed, open item O4) | Relative density → density (section 4.6) |
| ASTM D1655, DEF STAN 91-091, ASTM D910 | Density ranges shown as hints only; not used in any calculation |

**Edition note (important for an audit).** The 1980 tables were replaced by ASTM D1250-04 /
API MPMS 11.1-2004, and later editions exist. The newer editions use a different calculation
procedure. This tool implements the **1980**
equations. Results from the two editions are expected to be close for aviation fuels, but the
size of the difference has **not** been measured for this tool (see section 7, open item O1). If your
procedures, contract or authority require the 2004 or a later edition, this tool does not meet
that requirement as is.

## 3. Units

| Quantity | Unit on screen | Unit in the calculation core |
|---|---|---|
| Density | kg/L | kg/m³ (1 kg/L = 1000 kg/m³ exactly) |
| Temperature | °C | °C |
| Volume | L or US gal | same as input (1 US gal = 3.785411784 L exactly) |
| Mass | kg or lb | kg (1 kg = 2.20462262185 lb) |
| Thermal expansion coefficient α15 | per °C | per °C |

The constants K0, K1, A and B below are defined for density in kg/m³, as in the standard. The screen
converts kg/L ↔ kg/m³ only at input and output; no rounding is applied in between.

## 4. Calculation method

### 4.1 Thermal expansion coefficient α15 (Table 54B)

```
α15 = K0 / ρ15² + K1 / ρ15            (gasoline, jet, fuel oil groups)
α15 = A + B / ρ15²                    (transition zone)
```

| Group | ρ15 range (kg/m³) | K0 | K1 | A | B |
|---|---|---|---|---|---|
| Gasoline / avgas | 653.0 ≤ ρ < 770.352 | 346.4228 | 0.4388 | | |
| Transition zone | 770.352 ≤ ρ < 787.5253 | | | −0.00336312 | 2680.3206 |
| Jet fuel / kerosene | 787.5253 ≤ ρ < 838.3127 | 594.5418 | 0 | | |
| Fuel oil / diesel | 838.3127 ≤ ρ ≤ 1075.0 | 186.9696 | 0.4862 | | |

The group is chosen from ρ15 only, never from the product name. Densities outside 653–1075 kg/m³
are refused.

### 4.2 Volume correction factor (VCF)

```
Δt  = t − 15
VCF = exp( −α15 · Δt · (1 + 0.8 · α15 · Δt) )
```

Temperatures outside −50 °C to +150 °C are refused.

### 4.3 Results derived from the VCF

```
V15            = Vobserved × VCF(tobserved)
V at target    = V15 / VCF(ttarget)
ρ at t         = ρ15 × VCF(t)
mass (kg)      = V15 (L) × ρ15 (kg/m³) / 1000
V15 for a mass = mass (kg) × 1000 / ρ15 (kg/m³)
V to order     = V15 / VCF(tfuel)
```

Mass does not depend on temperature. With "Custom coefficient", α15 is typed by the user
(0 < α15 < 0.01 per °C) and no mass is given.

### 4.4 Density at 15 °C from a measured density (Table 53B)

α15 depends on ρ15, which is the unknown, so the value is found by iteration:

```
ρ15(0)   = ρobserved × HYC        (HYC = 1 when no glass hydrometer)
ρ15(n+1) = ρobserved × HYC / VCF( α15(ρ15(n)), tobserved )
```

Stop when |ρ15(n+1) − ρ15(n)| < 1·10⁻⁷ kg/m³, after at most 100 rounds. During the iteration the
density used to choose α15 is held inside 653–1075 kg/m³; the final ρ15 must be inside that range
or the result is refused.

### 4.5 Glass hydrometer correction

```
HYC = 1 − 0.000023 · Δt − 0.00000002 · Δt²,   Δt = t − 15
```

Applied only when the user turns on "Glass hydrometer". Off for a digital density meter.

### 4.6 API gravity to density at 15 °C

```
relative density 60/60 °F = 141.5 / (°API + 131.5)
ρ60F (kg/m³)              = relative density × 999.016
ρ15                       = section 4.4 with ρobserved = ρ60F, tobserved = 15.5556 °C (60 °F), HYC = 1
```

°API must be a number above −131.5. Example: 45 °API → 0.801700 → 800.911 kg/m³ at 60 °F →
801.323 kg/m³ (0.8013 kg/L) at 15 °C.

**Method note.** The standard route from °API to density at 15 °C is through the 60 °F tables
(Table 51 of the 1980 standard, or API MPMS Chapter 11.5). This tool instead takes ρ at 60 °F to
15 °C with the 15 °C Table 53B equations. Both describe the same physical correction over 0.56 °C,
so the difference is expected to be very small, but it has **not** been measured (open item O2).

### 4.7 Rounding

The 1980 standard sets rounding and truncation rules for the inputs, for α15 and for the VCF, so
that every user of the printed tables gets the same digits. **This tool does not apply those
rules.** It calculates in IEEE 754 double precision and rounds only for display (VCF to 5
decimals, density to 4 decimals in kg/L, volume to 0 or 1 decimal, mass to 0 decimals). A result can
therefore differ from the printed table in the last printed digit (open item O1).

## 5. Where each formula is in the code

There are two implementations of the same calculation. They must stay identical.

| Section | Kotlin (`core/src/main/kotlin/com/fuelvolume/core/FuelCalculator.kt`) | JavaScript (`web/calc.js`) |
|---|---|---|
| 4.1 α15 and groups | `thermalExpansionCoefficient` | `thermalExpansionCoefficient` |
| 4.2 VCF | `volumeCorrectionFactor` | `volumeCorrectionFactor` |
| 4.3 Volume and mass | `correct`, `volumeForMass`, `densityChart` | `correct`, `volumeForMass`, `densityChart` |
| 4.4 Density at 15 °C | `densityAt15` | `densityAt15` |
| 4.5 HYC | `hydrometerCorrection` | `hydrometerCorrection` |
| 4.6 API gravity | `densityFromApiGravity` | `densityFromApiGravity` |
| kg/L ↔ kg/m³ on screen | `app/.../MainActivity.kt` (`kgm3ToKgL`, `kgLToKgm3`) | `web/app.js` |

## 6. Verification

### 6.1 How the tests run

- Kotlin: `core/src/test/kotlin/com/fuelvolume/core/FuelCalculatorTest.kt`, run with
  `gradlew :core:test`.
- JavaScript: `web/calc.test.mjs`, run with `node --test web/`. It has the same cases, the same
  expected values and the same tolerances as the Kotlin tests, plus one test of number parsing
  (dot and comma).
- Both run in GitHub Actions on every push. A release APK is only built, and the web app is only
  published, when the tests pass. The CI logs are kept on GitHub with each commit.

Result for version 1.1.0: Kotlin 31 of 31 passed; JavaScript 32 of 32 passed.

### 6.2 What the tests check

| Kind of check | Tests | Strength |
|---|---|---|
| Against reference VCF values (table 6.3) | 5 | See the warning under table 6.3 |
| Exact identities (VCF = 1 at 15 °C; 10 °API = relative density 1; density at 15 °C measured at 15 °C = input) | 3 | Strong |
| Round trips (to target and back; density at t and back to 15 °C, in each group and across a group boundary; mass → volume → mass) | 6 | Strong for consistency, but cannot find an error in a constant |
| Hand-calculated values (volume at target, mass in kg and lb, US gallons, mass → volume at 15 °C, API example) | 6 | Checks the arithmetic, not the standard |
| Continuity of α15 at the group limits | 1 | Detects a wrong constant or limit |
| Refusal of bad input (density, temperature, chart step, mass, °API out of range) | 6 | Checks the guards |
| Other behaviour (custom coefficient, chart rows, chart trend, HYC sign) | 4 | |

### 6.3 Reference values

| ρ15 (kg/m³) | t (°C) | Group | α15 (tool) | VCF (tool) | Reference VCF in the test | Tolerance |
|---|---|---|---|---|---|---|
| 800 | 30 | Jet | 0.000928972 | 0.986009 | 0.9861 | ±0.0003 |
| 800 | 0 | Jet | 0.000928972 | 1.013875 | 1.0140 | ±0.0005 |
| 720 | 25 | Gasoline | 0.001277698 | 0.987175 | 0.9873 | ±0.0003 |
| 750 | 25 | Gasoline | 0.001200929 | 0.987949 | 0.9880 | ±0.0003 |
| 840 | 30 | Fuel oil | 0.000843789 | 0.987296 | 0.9873 | ±0.0003 |

**Warning.** These reference values were written into the tests when the tool was first built. They
were **not** copied from an official copy of Table 54B, and their source is not recorded. The
tolerances (±0.0003 = ±3 L per 10 000 L) are wider than the 4-decimal resolution of the printed
table. So these tests show that the tool is about right; they do **not** prove that it matches the
standard to the last digit. Closing this is open item O1.

### 6.4 Checks on the running apps

| Date | What | Result |
|---|---|---|
| 2026-11-15 | Android, on a phone: 10 000 L Jet A-1 at 0.800 kg/L and 30 °C | 9 860.1 L at 15 °C |
| 2026-11-16 | Android, on a phone: 45 °API; "use this density" button | 0.8013 kg/L; value copied to the Volume tab |
| 2026-11-16 | Web, in a desktop browser (Edge): same two cases, French and English, mass → volume, out-of-range temperature | Same results as Android; errors shown |
| 2026-11-16 | Web, published site loads without console errors | Pass |

Not yet checked: the web app on a real iPhone (install, keypad, offline use).

## 7. Open items

| ID | Item | Why it matters | Suggested action |
|---|---|---|---|
| O1 | No comparison against an official copy of ASTM D1250-80 Table 54B/53B, with its rounding rules | Section 6.3 values have no recorded source; rounding is not applied | Buy the standard. Pick at least 20 points per group across density and temperature (include the group limits, −40 °C and +50 °C). Add them as tests with tolerance ±0.00005 on the VCF, and record the source (document, page, table). |
| O2 | The °API route (section 4.6) is not the standard route | Possible small difference | Compare 10 °API values from 30 to 60 with API MPMS 11.1 Table 3/5B/51 or 11.5, and add them as tests. |
| O3 | 1980 edition, not 2004 or later | Some contracts and authorities require the newer edition | Decide which edition you need. If 2004 or later, the core must be changed. |
| O4 | Sources of the HYC formula and of the water density at 60 °F not checked against the standard text | Glass hydrometer and °API results depend on them | Check against API MPMS 11.1 and 11.5 and record the references. |
| O5 | Web app not tested on a real iPhone | iPhone is a target platform | Test install, keypad (dot and comma), offline use, and record the result in section 6.4. |
| O6 | Typing a thousands separator (`10,000`) is read as a decimal mark (10) | A typing error could give a wrong volume | Train users, or refuse numbers with a comma followed by exactly three digits. |

## 8. Change control

- All changes go through git. Every release is a git tag (`vX.Y.Z`) with a GitHub Release and its
  APK. Changes per version are in [CHANGELOG.md](../CHANGELOG.md).
- A change to the math must change **both** `FuelCalculator.kt` and `web/calc.js`, their tests, and
  this document, in the same commit.
- Release APKs are signed with one release key, kept out of the repository (GitHub secrets and an
  offline backup), so a user can check that an update comes from the same publisher.
