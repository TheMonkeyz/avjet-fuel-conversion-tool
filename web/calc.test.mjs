// Port of core/src/test/kotlin/com/fuelvolume/core/FuelCalculatorTest.kt, same values and tolerances.
// Run with: node --test web/
//
// IMPORTANT: none of these values come from the official standard yet. They check the internal
// consistency of the API MPMS 11.1-2004 method (behind ASTM D1250-25) and that it stays close to the 1980
// tables. Official test points must still be added (docs/VALIDATION.md, open item O1).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import {
  Product,
  VolumeUnit,
  FormulaGroup,
  FuelError,
  ErrorCode,
  LB_PER_KG,
  KG_PER_LB,
  MIN_TEMP_C,
  MAX_TEMP_C,
  MIN_DENSITY,
  MAX_DENSITY,
  WATER_DENSITY_60F,
  API_REF_TEMP_C,
  DELTA_60,
  BASE_TEMP_F_IPTS68,
  toIpts68F,
  round5,
  coefficients,
  volumeCorrectionFactor,
  customVolumeCorrectionFactor,
  correct,
  volumeForMass,
  densityChart,
  hydrometerCorrection,
  densityAt15,
  densityFromApiGravity,
  parseNumber,
} from './calc.js';

function assertClose(expected, actual, tolerance, message = '') {
  assert.ok(
    Math.abs(expected - actual) <= tolerance,
    `${message} expected ${expected} but was ${actual} (±${tolerance})`,
  );
}

function assertFuelError(fn, code) {
  let caught;
  try {
    fn();
  } catch (e) {
    caught = e;
  }
  assert.ok(caught instanceof FuelError, `expected a FuelError, got ${caught}`);
  if (code) assert.equal(caught.code, code);
  return caught;
}

// --- Constants and building blocks ---------------------------------------------------------

test('60 F on the IPTS-68 scale matches the constant of the standard', () => {
  assertClose(BASE_TEMP_F_IPTS68, toIpts68F(API_REF_TEMP_C), 2e-7);
  assertClose(DELTA_60, 2 * (BASE_TEMP_F_IPTS68 - 60.0), 1e-7);
});

test('scale change is zero at 0 C', () => {
  assertClose(32.0, toIpts68F(0.0), 1e-12);
});

test('vcf is exactly one at 15 degrees', () => {
  for (const d of [650.0, 720.0, 780.0, 800.0, 840.0, 1000.0]) {
    assertClose(1.0, volumeCorrectionFactor(d, 15.0), 1e-12, `density ${d}`);
  }
});

test('vcf is rounded to 5 decimals', () => {
  const vcf = volumeCorrectionFactor(800.0, 30.0);
  assert.equal(vcf, round5(vcf));
});

test('density limits at 15 C come from the 60 F limits', () => {
  assertClose(611.16, MIN_DENSITY, 0.01);
  assertClose(1163.86, MAX_DENSITY, 0.01);
});

// --- Groups --------------------------------------------------------------------------------

test('groups are chosen from the density at 60 F', () => {
  assert.equal(coefficients(720.0).group, FormulaGroup.GASOLINE);
  assert.equal(coefficients(780.0).group, FormulaGroup.TRANSITION);
  assert.equal(coefficients(800.0).group, FormulaGroup.JET);
  assert.equal(coefficients(845.0).group, FormulaGroup.FUEL_OIL);
  const c = coefficients(800.0);
  assert.ok(c.density60 < 800.0);
  assertClose(800.0, c.density60, 0.6);
});

test('alpha is continuous across group boundaries', () => {
  const alphaAt60 = (rho60) => {
    let rho15 = rho60;
    for (let i = 0; i < 50; i++) rho15 += rho60 - coefficients(rho15).density60;
    return coefficients(rho15).alpha60;
  };
  assertClose(alphaAt60(770.3519), alphaAt60(770.3521), 2e-8);
  assertClose(alphaAt60(787.5194), alphaAt60(787.5196), 2e-8);
  assertClose(alphaAt60(838.3126), alphaAt60(838.3128), 2e-8);
});

// --- Close to the 1980 tables --------------------------------------------------------------

test('stays within 0_0002 of the 1980 Table 54B equations', () => {
  const cases = [
    [800.0, 30.0, 0.986009], // 1980 equations, jet
    [800.0, 0.0, 1.013875],
    [720.0, 25.0, 0.987175], // gasoline
    [750.0, 25.0, 0.987949],
    [840.0, 30.0, 0.987296], // fuel oil
  ];
  for (const [d, t, vcf1980] of cases) {
    assertClose(vcf1980, volumeCorrectionFactor(d, t), 0.0002, `ρ15 ${d} at ${t} °C`);
  }
});

// --- Volume and mass -----------------------------------------------------------------------

test('volume at 15 and at a target temperature', () => {
  const r = correct({ product: Product.JET_A1, density15: 800.0, observedTempC: 30.0, observedVolume: 10000.0, targetTempC: 15.0 });
  assertClose(9860.0, r.volumeAt15, 3.0);
  assertClose(r.volumeAt15, r.volumeAtTarget, 1e-9);
  assertClose(1.0, r.vcfTarget, 1e-12);
  assertClose(10000.0 * r.vcfObserved, r.volumeAt15, 1e-9);
  assert.ok(r.alpha60 !== null && r.customAlpha === null);
});

test('mass in kg and lb from litres', () => {
  const r = correct({ product: Product.JET_A1, density15: 800.0, observedTempC: 30.0, observedVolume: 10000.0 });
  assertClose(r.volumeAt15 * 0.8, r.massKg, 1e-9);
  assertClose(r.massKg * LB_PER_KG, r.massLb, 1e-9);
});

test('mass from US gallons', () => {
  const r = correct({
    product: Product.JET_A1, density15: 800.0, observedTempC: 15.0, observedVolume: 1000.0, volumeUnit: VolumeUnit.US_GALLON,
  });
  assertClose(3028.33, r.massKg, 0.01);
  assertClose(1000.0, r.volumeAt15, 1e-9);
});

test('round trip observed to target and back', () => {
  const forward = correct({ product: Product.AVGAS_100LL, density15: 720.0, observedTempC: 5.0, observedVolume: 1000.0, targetTempC: 35.0 });
  const back = correct({
    product: Product.AVGAS_100LL, density15: 720.0, observedTempC: 35.0, observedVolume: forward.volumeAtTarget, targetTempC: 5.0,
  });
  assertClose(1000.0, back.volumeAtTarget, 1e-6);
});

test('custom coefficient skips density and mass', () => {
  const r = correct({ product: Product.CUSTOM, density15: 0.0, observedTempC: 25.0, observedVolume: 100.0, customAlpha: 0.001 });
  assert.equal(r.group, FormulaGroup.CUSTOM);
  assert.equal(r.densityAtObserved, null);
  assert.equal(r.massKg, null);
  assert.equal(r.alpha60, null);
  assert.equal(r.customAlpha, 0.001);
  // exp(−0.001·10·(1 + 0.8·0.001·10)) = exp(−0.01008) = 0.98997
  assertClose(0.98997, r.vcfObserved, 1e-12);
});

test('rejects densities outside the table', () => {
  assertFuelError(() => coefficients(600.0), ErrorCode.DENSITY_OUT_OF_RANGE);
  assertFuelError(() => coefficients(1200.0), ErrorCode.DENSITY_OUT_OF_RANGE);
  assertFuelError(() => coefficients(NaN), ErrorCode.DENSITY_NOT_NUMBER);
});

test('rejects temperatures outside the table', () => {
  assertFuelError(() => volumeCorrectionFactor(800.0, 200.0), ErrorCode.TEMP_OUT_OF_RANGE);
  assertFuelError(() => volumeCorrectionFactor(800.0, -60.0), ErrorCode.TEMP_OUT_OF_RANGE);
  assertFuelError(() => customVolumeCorrectionFactor(0.001, 151.0), ErrorCode.TEMP_OUT_OF_RANGE);
});

// --- Density chart -------------------------------------------------------------------------

test('density chart has one row per 5 degrees and 15 C is the input density', () => {
  const chart = densityChart(800.0);
  assert.equal(chart.length, 19);
  assert.equal(chart[0].tempC, -40.0);
  assert.equal(chart[chart.length - 1].tempC, 50.0);
  const at15 = chart.find((p) => p.tempC === 15.0);
  assertClose(800.0, at15.density, 1e-9);
  assertClose(1.0, at15.vcf, 1e-12);
});

test('density chart gets lighter as the fuel gets warmer', () => {
  const chart = densityChart(720.0);
  for (let i = 1; i < chart.length; i++) assert.ok(chart[i - 1].density > chart[i].density);
  assertClose(701.0, chart.find((p) => p.tempC === 35.0).density, 1.0);
});

test('density chart rejects a bad step or range', () => {
  assertFuelError(() => densityChart(800.0, -40, 50, 0.0), ErrorCode.STEP_NOT_POSITIVE);
  assertFuelError(() => densityChart(800.0, 20.0, 10.0), ErrorCode.START_ABOVE_END);
});

// --- Density at 15 °C ----------------------------------------------------------------------

test('density at 15 is the input when measured at 15', () => {
  const r = densityAt15(800.0, 15.0);
  assertClose(800.0, r.density15, 1e-6);
  assertClose(1.0, r.vcf, 1e-12);
  assert.equal(r.hydrometerCorrection, null);
});

test('density at 15 reverses the density chart in every group', () => {
  for (const [d15, t] of [[800.0, 30.0], [720.0, -20.0], [780.0, 40.0], [845.0, -35.0], [650.0, 45.0]]) {
    const atT = densityChart(d15, t, t)[0].density;
    assertClose(d15, densityAt15(atT, t).density15, 1e-6, `ρ15 ${d15} at ${t} °C`);
  }
});

test('density at 15 works across a group boundary', () => {
  const at40 = densityChart(780.0, 40.0, 40.0)[0].density;
  assert.ok(at40 < 770.352);
  assert.equal(densityAt15(at40, 40.0).group, FormulaGroup.TRANSITION);
});

test('glass hydrometer correction is one at 15 and slightly below one when warm', () => {
  assertClose(1.0, hydrometerCorrection(15.0), 1e-12);
  const hyc30 = hydrometerCorrection(30.0);
  assertClose(1.0 - 0.000023 * 15 - 0.00000002 * 225, hyc30, 1e-12);
  const plain = densityAt15(790.0, 30.0, false);
  const glass = densityAt15(790.0, 30.0, true);
  assert.equal(glass.hydrometerCorrection, hyc30);
  assertClose(plain.density15 * hyc30, glass.density15, 0.01);
});

test('density at 15 rejects values outside the table', () => {
  assertFuelError(() => densityAt15(500.0, 20.0), ErrorCode.DENSITY15_OUT_OF_TABLE);
  assertFuelError(() => densityAt15(-1.0, 20.0), ErrorCode.DENSITY_NOT_POSITIVE);
  const e = assertFuelError(() => densityAt15(800.0, 200.0), ErrorCode.TEMP_OUT_OF_RANGE);
  assert.deepEqual(e.params, { min: MIN_TEMP_C, max: MAX_TEMP_C });
});

// --- Mass to volume ------------------------------------------------------------------------

test('volume for mass is the reverse of correct', () => {
  const forward = correct({ product: Product.JET_A1, density15: 800.0, observedTempC: 30.0, observedVolume: 10000.0 });
  const back = volumeForMass(800.0, forward.massKg, 30.0);
  assertClose(10000.0, back.volumeAtTemp, 1e-6);
  assert.equal(back.group, FormulaGroup.JET);
});

test('volume for mass at 15 C is mass over density', () => {
  assertClose(9860.0, volumeForMass(800.0, 7888.0, 15.0).volumeAtTemp, 1e-9);
});

test('volume for mass in US gallons and pounds', () => {
  const litres = volumeForMass(800.0, 1000.0, 25.0);
  const gallons = volumeForMass(800.0, 1000.0, 25.0, VolumeUnit.US_GALLON);
  assertClose(litres.volumeAtTemp / 3.785411784, gallons.volumeAtTemp, 1e-9);
  assertClose(1000.0, 2204.62262185 * KG_PER_LB, 1e-6);
});

test('volume for mass rejects a negative mass', () => {
  assertFuelError(() => volumeForMass(800.0, -1.0, 20.0), ErrorCode.MASS_NEGATIVE);
});

// --- API gravity ---------------------------------------------------------------------------

test('45 API is about 0_8013 kg per litre at 15 C', () => {
  const r = densityFromApiGravity(45.0);
  assertClose(0.8017, r.relativeDensity60F, 0.000001);
  assertClose(800.91, r.density60F, 0.01);
  assert.ok(r.at15.density15 > r.density60F);
  assertClose(801.32, r.at15.density15, 0.05);
  assert.equal(r.at15.group, FormulaGroup.JET);
  assertClose(r.density60F, coefficients(r.at15.density15).density60, 1e-6);
});

test('10 API is the density of water', () => {
  const r = densityFromApiGravity(10.0);
  assertClose(1.0, r.relativeDensity60F, 1e-12);
  assertClose(WATER_DENSITY_60F, r.density60F, 1e-9);
});

test('API conversion refuses bad values', () => {
  assertFuelError(() => densityFromApiGravity(-140.0), ErrorCode.API_NOT_VALID);
  assertFuelError(() => densityFromApiGravity(NaN), ErrorCode.API_NOT_VALID);
  assertFuelError(() => densityFromApiGravity(150.0), ErrorCode.DENSITY15_OUT_OF_TABLE);
});

// --- Web only ------------------------------------------------------------------------------

test('parseNumber accepts a dot or a comma', () => {
  assert.equal(parseNumber('0.800'), 0.8);
  assert.equal(parseNumber('0,800'), 0.8);
  assert.equal(parseNumber(' -12,5 '), -12.5);
  assert.equal(parseNumber('10000'), 10000);
  assert.equal(parseNumber(''), null);
  assert.equal(parseNumber('-'), null);
  assert.equal(parseNumber('1,2,3'), null);
  assert.equal(parseNumber('abc'), null);
});

test('matches the shared reference grid (also checked by the Kotlin tests)', () => {
  // core/src/test/resources/vcf-grid.csv, made by web/tools/make-grid.mjs. The Kotlin suite reads
  // the same file, so the two implementations must agree on every row.
  const csv = readFileSync(new URL('../core/src/test/resources/vcf-grid.csv', import.meta.url), 'utf8');
  const rows = csv.trim().split(/\r?\n/).slice(1);
  assert.ok(rows.length > 3000);
  for (const row of rows) {
    const [d, t, group, d60, vcf] = row.split(',');
    const c = coefficients(Number(d));
    assert.equal(c.group, group, row);
    assertClose(Number(d60), c.density60, 5e-7, row);
    assert.equal(volumeCorrectionFactor(Number(d), Number(t)).toFixed(5), vcf, row);
  }
});
