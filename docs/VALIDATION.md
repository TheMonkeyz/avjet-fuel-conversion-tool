# Method and verification record

This document describes what the Avjet fuel conversion tool calculates, which standard it
follows, how the calculation was checked, and what is not yet checked. It is written so that an
auditor can trace each result back to a formula, each formula to the code, and each piece of code to
a test.

| Item | Value |
|---|---|
| Tool | Avjet fuel conversion tool (Android app `com.fuelvolume.app` and web app) |
| Version covered | 1.3.0 (git tag `v1.3.0`) |
| Source code | <https://github.com/TheMonkeyz/avjet-fuel-conversion-tool> |
| Document status | Kept in the repository; change it in the same commit as any change to the math |

## 1. Intended use and limits

The tool is used to **compare fuel data with laboratory certificates of analysis**: density at
15 °C from a measured density or an API gravity, density at other temperatures, volume corrected to
15 °C, and mass from volume. It is not used for sales or invoicing.

It is **not** a certified measurement system and not a legal-metrology instrument. Every screen
shows this.

## 2. Standard

| Reference | Used for |
|---|---|
| **ASTM D1250-25** (target edition) and the joint API/ASTM procedures it points to, **API MPMS Chapter 11.1**: the 2004 procedure for generalized refined products (behind the former Tables 53B and 54B, 15 °C base), changed by Addendum 1 (2007) and Addendum 2 (2019). The tool implements the 2004 procedure; the addenda are not yet checked (open item O8) | α60 equation, constants K0, K1, K2, group limits, ITS-90 → IPTS-68 temperature conversion, CTL equation, 15 °C ↔ 60 °F density conversion, 5-decimal rounding of the CTL |
| Definition of API gravity (API MPMS / ASTM D287, D1298) | °API → relative density 60/60 °F (section 4.6) |
| Density of water at 60 °F = 999.016 kg/m³ | Relative density → density (section 4.6) |
| ASTM D1655, DEF STAN 91-091, ASTM D910 | Density ranges shown as hints only; not used in any calculation |

Up to version 1.1.0 the tool used the older 1980 equations (ASTM D1250-80). Version 1.2.0 replaces
them with the 2004 procedure of API MPMS 11.1. For the test points in section 6.3, the results
changed by at most 0.00001 in the VCF.

**Edition.** On 2026-11-16 the target edition was set to ASTM D1250-25, the current edition
(released 15 December 2025; it replaces D1250-19e1). Version 1.2.0 was first documented against
D1250-08. The calculation did not change with this decision. D1250-25 also covers a 20 °C base;
this tool uses 15 °C only. Public sources for these facts: the
[D1250-25 listing](https://www.en-standard.eu/astm-d1250-25-standard-guide-for-the-use-of-the-joint-api-and-astm-adjunct-for-temperature-and-pressure-volume-correction-factors-for-generalized-crude-oils-refined-products-and-lubricating-oils-api-mpms-chapter-11-1/)
and the [ASTM store page for the adjunct with Addenda 1 and 2](https://store.astm.org/adjd1250-a1a2-e-pdf.html).

Pressure correction (CPL) is not applied: the tool assumes atmospheric pressure (0 psig).

### 2.1 Where the numbers come from

The tool was written without an official copy of the standard. Each value below is marked with how
it was checked.

| Value | Source used | Status |
|---|---|---|
| K0, K1, K2 for the four refined-product groups | Two public sources agree: Kelton technical notes C249/C251 and the Wikipedia article "Volume correction factor" | Confirmed by public sources, not by the standard text |
| Group limits 770.352, 787.5195, 838.3127 kg/m³ at 60 °F; range 610.6–1163.5 kg/m³ | Kelton C249 | Confirmed by one public source |
| Group chosen from the density at 60 °F | Kelton C251 | Confirmed by one public source |
| CTL = exp{−α ΔT [1 + 0.8 α (ΔT + δ)]} | Wikipedia | Confirmed by one public source |
| CTL rounded to 5 decimals | Wikipedia | Confirmed by one public source |
| ITS-90 → IPTS-68 coefficients a1…a8 | a1–a3 confirmed by a public source on the ITS-90 scale; a4–a8 from memory of the standard | Partly confirmed. Internal check: with these coefficients, 60 °F converts to 60.0068749 °F, which is exactly the constant of the standard (test "60 F on the IPTS-68 scale…") |
| 60.0068749 °F and δ60 = 0.01374979547 °F | From memory of the standard | Internally consistent: δ60 = 2 × 0.0068749 (tested) |
| ρ* (density shift) formula with A and B | From memory of the standard | Not confirmed. Its effect is about 0.003 kg/m³ on ρ*, which changes the VCF by less than 1·10⁻⁸ |
| 15 °C ↔ 60 °F density conversion by iteration, and VCF(15 °C base) = CTL(t)/CTL(15 °C) | From memory of the standard | Not confirmed |
| Glass hydrometer correction HYC | From the first version of the tool | Not confirmed |
| Water density 999.016 kg/m³ at 60 °F | From memory | Not confirmed |

**All of these must be checked against the official standard (open item O1) before the tool is
relied on in an audit.**

## 3. Units

| Quantity | Unit on screen | Unit in the calculation core |
|---|---|---|
| Density | kg/L | kg/m³ (1 kg/L = 1000 kg/m³ exactly) |
| Temperature | °C (ITS-90) | °C, converted to °F (IPTS-68) inside the equations |
| Volume | L or US gal | same as input (1 US gal = 3.785411784 L exactly) |
| Mass | kg or lb | kg (1 kg = 2.20462262185 lb) |
| α60 | per °F | per °F |

The screen converts kg/L ↔ kg/m³ only at input and output.

## 4. Calculation method

### 4.1 Commodity groups and α60

```
α60 = K0 / ρ*² + K1 / ρ* + K2          (per °F, ρ* in kg/m³)
```

| Group | ρ60 range (kg/m³ at 60 °F) | K0 | K1 | K2 |
|---|---|---|---|---|
| Gasolines | 610.6 ≤ ρ60 < 770.352 | 192.4571 | 0.2438 | 0 |
| Transition zone | 770.352 ≤ ρ60 < 787.5195 | 1489.0670 | 0 | −0.0018684 |
| Jet fuels | 787.5195 ≤ ρ60 < 838.3127 | 330.3010 | 0 | 0 |
| Fuel oils | 838.3127 ≤ ρ60 ≤ 1163.5 | 103.8720 | 0.2701 | 0 |

The group is chosen from ρ60, never from the product name. ρ* is ρ60 shifted to the IPTS-68 scale:

```
A   = δ60/2 · ((K0/ρ60 + K1)/ρ60 + K2)
B   = (2·K0 + K1·ρ60) / (K0 + (K1 + K2·ρ60)·ρ60)
ρ*  = ρ60 · (1 + (exp(A·(1 + 0.8·A)) − 1) / (1 + A·(1 + 1.6·A)·B))
δ60 = 0.01374979547 °F
```

### 4.2 Temperature and CTL from 60 °F

```
τ     = t90(°C) / 630
t68   = t90 − τ·(a1 + a2·τ + … + a8·τ⁷)          (°C)
a1…a8 = −0.148759, −0.267408, 1.080760, 1.269056, −4.089591, −1.871251, 7.438081, −3.536296
Δt    = (1.8·t68 + 32) − 60.0068749              (°F)
CTL   = exp( −α60 · Δt · (1 + 0.8 · α60 · (Δt + δ60)) )
```

Temperatures outside −50 °C to +150 °C are refused.

### 4.3 15 °C base

```
ρ60 from ρ15:   ρ60 = ρ15 / CTL(ρ60, 15 °C), by iteration from ρ60 = ρ15
VCF(t → 15 °C) = CTL(ρ60, t) / CTL(ρ60, 15 °C), rounded to 5 decimals
```

Accepted ρ15: 611.16 to 1163.86 kg/m³ (the 60 °F limits taken to 15 °C).

Results:

```
V15            = Vobserved × VCF(tobserved)
V at target    = V15 / VCF(ttarget)
ρ at t         = ρ15 × CTL(t)/CTL(15 °C)       (not rounded)
mass (kg)      = V15 (L) × ρ15 (kg/m³) / 1000
V15 for a mass = mass (kg) × 1000 / ρ15 (kg/m³)
V to order     = V15 / VCF(tfuel)
```

With "Custom coefficient", the user types α (per °C, 0 < α < 0.01) and the tool uses
VCF = exp(−α·Δt·(1 + 0.8·α·Δt)) with Δt = t − 15 °C, rounded to 5 decimals. This is not part of the
standard; it is for products outside the refined-product groups. No mass is given.

### 4.4 Density at 15 °C from a measured density

```
ρobs' = ρobserved × HYC                           (HYC = 1 when no glass hydrometer)
ρ60   = ρobs' / CTL(ρ60, tobserved), by iteration from ρ60 = ρobs'
ρ15   = ρ60 × CTL(ρ60, 15 °C)
```

Stop when the change is below 1·10⁻⁹ kg/m³ (at most 100 rounds). ρ60 must end inside
610.6–1163.5 kg/m³ or the result is refused.

### 4.5 Glass hydrometer correction

```
HYC = 1 − 0.000023 · Δt − 0.00000002 · Δt²,   Δt = t − 15 °C
```

Only when "Glass hydrometer" is on. Off for a digital density meter.

### 4.6 API gravity to density at 15 °C

```
relative density 60/60 °F = 141.5 / (°API + 131.5)
ρ60 (kg/m³)               = relative density × 999.016
ρ15                       = ρ60 × CTL(ρ60, 15 °C)
```

Example: 45 °API → 0.801700 → 800.911 kg/m³ at 60 °F → 801.323 kg/m³ (0.8013 kg/L) at 15 °C.

### 4.7 Rounding

- The VCF is rounded to 5 decimals (halves round up), as the standard does for the CTL, and the
  rounded VCF is used for volumes and mass.
- Densities are not rounded inside the calculation; the screen shows 4 decimals in kg/L.
- The tool does **not** round the inputs (density, temperature) before the calculation. Whether
  the standard requires this for the 15 °C procedures is not confirmed (open item O7).

## 5. Where each formula is in the code

There are two implementations of the same calculation. They must stay identical.

| Section | Kotlin (`core/src/main/kotlin/com/fuelvolume/core/FuelCalculator.kt`) | JavaScript (`web/calc.js`) |
|---|---|---|
| 4.1 Groups, α60, ρ* | `groupFor`, `alpha60` | `groupFor`, `alpha60` |
| 4.2 Temperature, CTL | `toIpts68F`, `ctl60Raw` | `toIpts68F`, `ctl60Raw` |
| 4.3 15 °C base, volume, mass | `coefficients`, `volumeCorrectionFactor`, `correct`, `volumeForMass`, `densityChart` | same names |
| 4.4 Density at 15 °C | `densityAt15` | `densityAt15` |
| 4.5 HYC | `hydrometerCorrection` | `hydrometerCorrection` |
| 4.6 API gravity | `densityFromApiGravity` | `densityFromApiGravity` |
| 4.7 Rounding | `round5` | `round5` |
| kg/L ↔ kg/m³ on screen | `app/.../MainActivity.kt` (`kgm3ToKgL`, `kgLToKgm3`) | `web/app.js` |

## 6. Verification

### 6.1 How the tests run

- Kotlin: `core/src/test/kotlin/com/fuelvolume/core/FuelCalculatorTest.kt`, run with
  `gradlew :core:test`.
- JavaScript: `web/calc.test.mjs`, run with `node --test web/`. Same cases, values and
  tolerances, plus one test of number parsing.
- **Cross-check:** `core/src/test/resources/vcf-grid.csv` holds 3,713 points (79 densities from
  611.2 to 1163.8 kg/m³, including both sides of each group limit, × 47 temperatures from −50 to
  150 °C). It is made from the JavaScript code by `web/tools/make-grid.mjs`. Both test suites read
  it and must give the same group, the same ρ60 (±5·10⁻⁷ kg/m³) and the same 5-decimal VCF on every
  point. This proves the two implementations agree. It does not prove they match the standard.
- Both suites run in GitHub Actions on every push. A release APK is only built, and the web app
  is only published, when the tests pass.

Result for version 1.2.0: Kotlin 31 of 31 passed; JavaScript 32 of 32 passed.

### 6.2 What the tests check

| Kind of check | Strength |
|---|---|
| Constants agree with each other (60 °F → 60.0068749 °F; δ60 = 2 × 0.0068749) | Strong check of a1…a8 and the two constants |
| α60 is continuous at the three group limits | Strong check of K0, K1, K2 and the limits together |
| Results stay within 0.0002 of the 1980 equations (section 6.3) | Shows nothing is badly wrong; not a proof of the 2004 method |
| Exact identities (VCF = 1 at 15 °C; 10 °API = relative density 1; °API route and density route give the same ρ60) | Strong |
| Round trips (density at t and back to 15 °C in every group; volume to target and back; mass → volume → mass) | Consistency only |
| Hand-calculated values (mass, US gallons, custom coefficient, API example, density limits) | Checks the arithmetic |
| Refusal of bad input | Checks the guards |
| Kotlin = JavaScript on 3,713 points | Strong for the two implementations agreeing |

### 6.3 Results compared with the 1980 equations

| ρ15 (kg/m³) | t (°C) | Group | ρ60 (kg/m³) | α60 (/°F) | VCF 2004 (tool) | VCF 1980 equations |
|---|---|---|---|---|---|---|
| 800 | 30 | Jet fuels | 799.5869 | 0.000516625 | 0.98600 | 0.986009 |
| 800 | 0 | Jet fuels | 799.5869 | 0.000516625 | 1.01388 | 1.013875 |
| 720 | 25 | Gasolines | 719.4887 | 0.000710626 | 0.98717 | 0.987175 |
| 750 | 25 | Gasolines | 749.4994 | 0.000667882 | 0.98795 | 0.987949 |
| 840 | 30 | Fuel oils | 839.6061 | 0.000469045 | 0.98729 | 0.987296 |

None of these numbers come from the official standard. See open item O1.

### 6.4 Checks on the running apps

| Date | What | Result |
|---|---|---|
| 2026-11-16 | Android 1.2.0 on a phone: Jet A-1 0.800 kg/L, 10 000 L at 30 °C | VCF 0.98600, 9 860.0 L at 15 °C, 7 888 kg, α60 0.0005166 /°F, Jet fuels. Same as the web code. |
| 2026-11-16 | Android 1.1.0 on a phone: 45 °API; "use this density" button (1980 method) | 0.8013 kg/L; value copied to the Volume tab |
| 2026-11-16 | Web 1.1.0 in a desktop browser (Edge), French and English | Same results as Android; errors shown |
| 2026-11-16 | Android debug build with `targetSdk` 36 (not released) on an Android 16 (API 36) emulator, phone and tablet, English and French: Jet A-1 0.800 kg/L, 10 000 L at 30 °C | VCF 0.98600, 9 860.0 L at 15 °C, 7 888 kg, α60 0.0005166 /°F, Jet fuels; French shows 0,98600 and 9 860,0 L. 45 °API gives 0.8013 kg/L. Same as the phone results above. |

Not yet checked: the web app on a real iPhone.

## 7. Open items

The plan to close them is in [PROPOSAL-open-items.md](PROPOSAL-open-items.md).

| ID | Item | Why it matters | Suggested action |
|---|---|---|---|
| O1 | The method was written without the official standard, and no result has been compared with official values | Section 2.1 lists values that are only partly confirmed | Get a licensed copy of ASTM D1250-25 with API MPMS 11.1 and its Addenda 1 and 2 (or the API's official implementation and its test data). Check every row of section 2.1. Add at least 20 official points per group, with tolerance ±0.000005 on the VCF and ±0.05 kg/m³ on densities, and record the source (document, page, table). |
| O2 | — | Closed in 1.2.0: the °API route now follows the 60 °F method of the standard | — |
| O3 | — | Closed 2026-11-16: the target edition is ASTM D1250-25 (user decision) | — |
| O4 | HYC formula and the water density at 60 °F are not confirmed | Glass hydrometer and °API results depend on them | Check against the standard and record the references. |
| O5 | Web app not tested on a real iPhone | iPhone is a target platform | Test install, keypad (dot and comma), offline use; record in section 6.4. |
| O6 | Typing a thousands separator (`10,000`) is read as a decimal mark (10) | A typing error could give a wrong value | Show under each box how the number was read, and warn in English when a comma is followed by exactly three digits. Do not refuse it: `0,800` is valid in French. |
| O7 | Inputs are not rounded before the calculation | The standard may round density and temperature first; results could differ in the last digit | Check the rounding rules of the standard for the 15 °C procedures and apply them if required. |
| O8 | The changes of API MPMS 11.1 Addendum 1 (2007) and Addendum 2 (2019), which D1250-25 includes, are not checked | They may change details of the refined-product procedure, so results could differ from D1250-25 | Read both addenda; change both implementations if needed; add tests; update section 2.1. |

## 8. Change control

- All changes go through git. Every release is a git tag (`vX.Y.Z`) with a GitHub Release and its
  APK. Changes per version are in [CHANGELOG.md](../CHANGELOG.md).
- A change to the math must change **both** `FuelCalculator.kt` and `web/calc.js`, their tests,
  the reference grid (`node web/tools/make-grid.mjs`) and this document, in the same commit.
- Release APKs are signed with one release key, kept out of the repository (GitHub secrets and an
  offline backup), so a user can check that an update comes from the same publisher.
