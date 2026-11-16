// Port of core/src/test/kotlin/com/fuelvolume/core/FuelCalculatorTest.kt, same values and tolerances.
// Run with: node --test web/
import { test } from 'node:test';
import assert from 'node:assert/strict';
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
  WATER_DENSITY_60F,
  thermalExpansionCoefficient,
  volumeCorrectionFactor,
  correct,
  volumeForMass,
  densityChart,
  hydrometerCorrection,
  densityAt15,
  densityFromApiGravity,
  parseNumber,
} from './calc.js';

function assertClose(expected, actual, tolerance, message = '') {
  assert.ok(Math.abs(expected - actual) <= tolerance, `${message} expected ${expected} but was ${actual} (±${tolerance})`);
}
/** Kotlin assertEquals(expected, actual, absoluteTolerance) */
const assertEq = assertClose;

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

test('vcf is exactly one at 15 degrees', () => {
  const { alpha15 } = thermalExpansionCoefficient(800.0);
  assertEq(1.0, volumeCorrectionFactor(alpha15, 15.0), 1e-12);
});

test('jet A-1 at 800 kg per m3 and 30 C matches Table 54B', () => {
  const { group, alpha15 } = thermalExpansionCoefficient(800.0);
  assert.equal(group, FormulaGroup.JET);
  assertClose(0.9861, volumeCorrectionFactor(alpha15, 30.0), 0.0003);
});

test('jet fuel at 800 kg per m3 and 0 C expands the volume at 15', () => {
  const { alpha15 } = thermalExpansionCoefficient(800.0);
  const vcf = volumeCorrectionFactor(alpha15, 0.0);
  assert.ok(vcf > 1.0);
  assertClose(1.014, vcf, 0.0005);
});

test('avgas at 720 kg per m3 uses the gasoline group', () => {
  const { group, alpha15 } = thermalExpansionCoefficient(720.0);
  assert.equal(group, FormulaGroup.GASOLINE);
  assertClose(0.9873, volumeCorrectionFactor(alpha15, 25.0), 0.0003);
});

test('gasoline at 750 kg per m3 and 25 C matches Table 54B', () => {
  const { alpha15 } = thermalExpansionCoefficient(750.0);
  assertClose(0.988, volumeCorrectionFactor(alpha15, 25.0), 0.0003);
});

test('diesel at 840 kg per m3 and 30 C matches Table 54B', () => {
  const { group, alpha15 } = thermalExpansionCoefficient(840.0);
  assert.equal(group, FormulaGroup.FUEL_OIL);
  assertClose(0.9873, volumeCorrectionFactor(alpha15, 30.0), 0.0003);
});

test('alpha is continuous across group boundaries', () => {
  const gasolineEdge = thermalExpansionCoefficient(770.351).alpha15;
  const transitionLow = thermalExpansionCoefficient(770.352).alpha15;
  assertClose(gasolineEdge, transitionLow, 1e-6);
  const transitionHigh = thermalExpansionCoefficient(787.5252).alpha15;
  const jetEdge = thermalExpansionCoefficient(787.5253).alpha15;
  assertClose(transitionHigh, jetEdge, 1e-6);
});

test('volume at 15 and at a target temperature', () => {
  const r = correct({ product: Product.JET_A1, density15: 800.0, observedTempC: 30.0, observedVolume: 10_000.0, targetTempC: 15.0 });
  assertClose(9861.0, r.volumeAt15, 3.0);
  assertEq(r.volumeAt15, r.volumeAtTarget, 1e-9);
  assertEq(1.0, r.vcfTarget, 1e-12);
  assertClose(800.0 * r.vcfObserved, r.densityAtObserved, 1e-9);
});

test('mass in kg and lb from litres', () => {
  const r = correct({ product: Product.JET_A1, density15: 800.0, observedTempC: 30.0, observedVolume: 10_000.0 });
  assertClose(7889.0, r.massKg, 3.0);
  assertClose(r.massKg * LB_PER_KG, r.massLb, 1e-9);
});

test('mass from US gallons', () => {
  const r = correct({
    product: Product.JET_A1, density15: 800.0, observedTempC: 15.0, observedVolume: 1_000.0, volumeUnit: VolumeUnit.US_GALLON,
  });
  assertClose(3028.3, r.massKg, 0.1);
  assertEq(1_000.0, r.volumeAt15, 1e-9);
});

test('mass is the same whatever the temperature', () => {
  const warm = correct({ product: Product.JET_A1, density15: 800.0, observedTempC: 35.0, observedVolume: 1000.0 });
  const coldVolume = warm.volumeAt15 / volumeCorrectionFactor(warm.alpha15, -20.0);
  const cold = correct({ product: Product.JET_A1, density15: 800.0, observedTempC: -20.0, observedVolume: coldVolume });
  assert.ok(coldVolume < 1000.0);
  assertClose(warm.massKg, cold.massKg, 1e-6);
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
  assert.equal(r.massLb, null);
  assert.ok(r.volumeAt15 < 100.0);
});

test('rejects densities outside the table', () => {
  assertFuelError(() => thermalExpansionCoefficient(600.0), ErrorCode.DENSITY_OUT_OF_RANGE);
  assertFuelError(() => thermalExpansionCoefficient(1100.0), ErrorCode.DENSITY_OUT_OF_RANGE);
  assertFuelError(() => thermalExpansionCoefficient(Number.NaN), ErrorCode.DENSITY_NOT_NUMBER);
});

test('rejects temperatures outside the table', () => {
  assertFuelError(() => volumeCorrectionFactor(0.001, 200.0), ErrorCode.TEMP_OUT_OF_RANGE);
  assertFuelError(() => volumeCorrectionFactor(0.001, -60.0), ErrorCode.TEMP_OUT_OF_RANGE);
});

test('density chart has one row per 5 degrees and 15 C is the input density', () => {
  const chart = densityChart(800.0);
  assert.equal(chart.length, 19);
  assert.equal(chart[0].tempC, -40.0);
  assert.equal(chart.at(-1).tempC, 50.0);
  const at15 = chart.find((p) => p.tempC === 15.0);
  assertEq(800.0, at15.density, 1e-9);
  assertEq(0.8, at15.densityKgPerLitre, 1e-9);
  assertEq(1.0, at15.vcf, 1e-12);
});

test('density chart gets lighter as the fuel gets warmer', () => {
  const chart = densityChart(720.0);
  for (let i = 0; i + 1 < chart.length; i++) {
    const cold = chart[i];
    const warm = chart[i + 1];
    assert.ok(cold.density > warm.density, `density at ${cold.tempC} should be above ${warm.tempC}`);
  }
  assertClose(701.0, chart.find((p) => p.tempC === 35.0).density, 1.0);
});

test('density chart rejects a bad step or range', () => {
  assertFuelError(() => densityChart(800.0, -40.0, 50.0, 0.0), ErrorCode.STEP_NOT_POSITIVE);
  assertFuelError(() => densityChart(800.0, 20.0, 10.0), ErrorCode.START_ABOVE_END);
});

test('density at 15 is the input when measured at 15', () => {
  const r = densityAt15(800.0, 15.0);
  assertEq(800.0, r.density15, 1e-9);
  assertEq(1.0, r.vcf, 1e-12);
  assert.equal(r.hydrometerCorrection, null);
});

test('density at 15 reverses the density at temperature for jet fuel', () => {
  const { alpha15 } = thermalExpansionCoefficient(800.0);
  const densityAt30 = 800.0 * volumeCorrectionFactor(alpha15, 30.0);
  const r = densityAt15(densityAt30, 30.0);
  assertClose(800.0, r.density15, 1e-5);
  assert.equal(r.group, FormulaGroup.JET);
  assertEq(0.8, r.density15KgPerLitre, 1e-7);
});

test('density at 15 reverses the density at temperature for avgas, cold', () => {
  const { alpha15 } = thermalExpansionCoefficient(720.0);
  const densityAtMinus20 = 720.0 * volumeCorrectionFactor(alpha15, -20.0);
  const r = densityAt15(densityAtMinus20, -20.0);
  assertClose(720.0, r.density15, 1e-5);
  assert.equal(r.group, FormulaGroup.GASOLINE);
});

test('density at 15 works across a group boundary', () => {
  const { alpha15 } = thermalExpansionCoefficient(780.0);
  const densityAt40 = 780.0 * volumeCorrectionFactor(alpha15, 40.0);
  assert.ok(densityAt40 < 770.352);
  const r = densityAt15(densityAt40, 40.0);
  assertClose(780.0, r.density15, 1e-5);
  assert.equal(r.group, FormulaGroup.TRANSITION);
});

test('glass hydrometer correction is one at 15 and slightly below one when warm', () => {
  assertEq(1.0, hydrometerCorrection(15.0), 1e-12);
  const hyc30 = hydrometerCorrection(30.0);
  assertClose(1.0 - 0.000023 * 15 - 0.00000002 * 225, hyc30, 1e-12);
  const plain = densityAt15(790.0, 30.0, false);
  const glass = densityAt15(790.0, 30.0, true);
  assert.equal(glass.hydrometerCorrection, hyc30);
  assert.ok(glass.density15 < plain.density15);
  assertClose(plain.density15 * hyc30, glass.density15, 0.01);
});

test('density at 15 rejects values outside the table', () => {
  assertFuelError(() => densityAt15(500.0, 20.0));
  assertFuelError(() => densityAt15(800.0, 200.0));
  assertFuelError(() => densityAt15(-1.0, 20.0));
  const e = assertFuelError(() => densityAt15(800.0, 200.0), ErrorCode.TEMP_OUT_OF_RANGE);
  assert.deepEqual(e.params, { min: MIN_TEMP_C, max: MAX_TEMP_C });
});

test('volume for mass is the reverse of correct', () => {
  const forward = correct({ product: Product.JET_A1, density15: 800.0, observedTempC: 30.0, observedVolume: 10_000.0 });
  const back = volumeForMass(800.0, forward.massKg, 30.0);
  assertClose(10_000.0, back.volumeAtTemp, 1e-6);
  assertClose(forward.volumeAt15, back.volumeAt15, 1e-6);
  assert.equal(back.group, FormulaGroup.JET);
});

test('volume for mass at 15 C is mass over density', () => {
  const r = volumeForMass(800.0, 7_888.0, 15.0);
  assertClose(9_860.0, r.volumeAtTemp, 1e-9);
  assertClose(9_860.0, r.volumeAt15, 1e-9);
});

test('volume for mass in US gallons and pounds', () => {
  const litres = volumeForMass(800.0, 1_000.0, 25.0);
  const gallons = volumeForMass(800.0, 1_000.0, 25.0, VolumeUnit.US_GALLON);
  assertClose(litres.volumeAtTemp / 3.785411784, gallons.volumeAtTemp, 1e-9);
  assertClose(1_000.0, 2_204.62262185 * KG_PER_LB, 1e-6);
});

test('volume for mass rejects a negative mass', () => {
  assertFuelError(() => volumeForMass(800.0, -1.0, 20.0), ErrorCode.MASS_NEGATIVE);
});

test('45 API is about 0_8013 kg per litre at 15 C', () => {
  const r = densityFromApiGravity(45.0);
  assertClose(0.8017, r.relativeDensity60F, 0.000001);
  assertClose(800.91, r.density60F, 0.01);
  assert.ok(r.at15.density15 > r.density60F);
  assertClose(801.32, r.at15.density15, 0.05);
  assert.equal(r.at15.group, FormulaGroup.JET);
});

test('10 API is the density of water', () => {
  const r = densityFromApiGravity(10.0);
  assertClose(1.0, r.relativeDensity60F, 1e-12);
  assertClose(WATER_DENSITY_60F, r.density60F, 1e-9);
});

test('API conversion refuses bad values', () => {
  assertFuelError(() => densityFromApiGravity(-140.0), ErrorCode.API_NOT_VALID);
  assertFuelError(() => densityFromApiGravity(Number.NaN), ErrorCode.API_NOT_VALID);
  const out = assertFuelError(() => densityFromApiGravity(150.0), ErrorCode.DENSITY15_OUT_OF_TABLE);
  assert.equal(typeof out.params.density15, 'number');
});

// Web-only: the input parser accepts both decimal marks, like the Android String.toNumberOrNull().
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
