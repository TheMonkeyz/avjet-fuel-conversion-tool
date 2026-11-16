// Port of core/src/main/kotlin/com/fuelvolume/core/FuelCalculator.kt.
// Volume correction for petroleum products with temperature, using the metric (15 °C base)
// generalized equations of ASTM D1250-80 / API MPMS Chapter 11.1 (1980), Table 54B:
//
//   α15 = K0 / ρ15² + K1 / ρ15
//   VCF = exp( −α15 · Δt · (1 + 0.8 · α15 · Δt) ),  Δt = t − 15
//
// Densities are in kg/m³ here. The UI shows kg/L and converts (× 1000).

/** Aviation fuels the user can pick. The density is only a typical starting value (kg/m³). */
export const Product = Object.freeze({
  JET_A1: Object.freeze({ id: 'JET_A1', label: 'Jet A-1 / JP-8', defaultDensity: 800.0, isCustom: false }),
  JET_A: Object.freeze({ id: 'JET_A', label: 'Jet A', defaultDensity: 810.0, isCustom: false }),
  AVGAS_100LL: Object.freeze({ id: 'AVGAS_100LL', label: 'Avgas 100LL', defaultDensity: 720.0, isCustom: false }),
  OTHER: Object.freeze({ id: 'OTHER', label: 'Other fuel (by density)', defaultDensity: 780.0, isCustom: false }),
  CUSTOM: Object.freeze({ id: 'CUSTOM', label: 'Custom coefficient', defaultDensity: 0.0, isCustom: true }),
});
export const PRODUCTS = Object.freeze(Object.values(Product));

export const VolumeUnit = Object.freeze({
  LITRE: Object.freeze({ id: 'LITRE', label: 'L', litresPerUnit: 1.0 }),
  US_GALLON: Object.freeze({ id: 'US_GALLON', label: 'US gal', litresPerUnit: 3.785411784 }),
});
export const VOLUME_UNITS = Object.freeze(Object.values(VolumeUnit));

/** Formula group of Table 54B, chosen from the density. */
export const FormulaGroup = Object.freeze({
  GASOLINE: 'GASOLINE',
  TRANSITION: 'TRANSITION',
  JET: 'JET',
  FUEL_OIL: 'FUEL_OIL',
  CUSTOM: 'CUSTOM',
});

/** Why a calculation was refused. The UI turns each code into a message in the user's language. */
export const ErrorCode = Object.freeze({
  DENSITY_NOT_NUMBER: 'DensityNotNumber',
  DENSITY_OUT_OF_RANGE: 'DensityOutOfRange', // params: min, max
  TEMP_OUT_OF_RANGE: 'TempOutOfRange', // params: min, max
  VOLUME_NEGATIVE: 'VolumeNegative',
  MASS_NEGATIVE: 'MassNegative',
  COEFFICIENT_OUT_OF_RANGE: 'CoefficientOutOfRange',
  STEP_NOT_POSITIVE: 'StepNotPositive',
  START_ABOVE_END: 'StartAboveEnd',
  DENSITY_NOT_POSITIVE: 'DensityNotPositive',
  DENSITY15_OUT_OF_TABLE: 'Density15OutOfTable', // params: density15, min, max
  API_NOT_VALID: 'ApiNotValid',
});

/** Thrown for bad input. `code` is one of ErrorCode, `params` holds the numbers of the message. */
export class FuelError extends RangeError {
  constructor(code, message, params = {}) {
    super(message);
    this.name = 'FuelError';
    this.code = code;
    this.params = params;
  }
}

function requireFuel(condition, code, message, params) {
  if (!condition) throw new FuelError(code, message, params);
}

export const BASE_TEMP_C = 15.0;
export const MIN_DENSITY = 653.0;
export const MAX_DENSITY = 1075.0;
export const MIN_TEMP_C = -50.0;
export const MAX_TEMP_C = 150.0;
export const LB_PER_KG = 2.20462262185;
export const KG_PER_LB = 1.0 / LB_PER_KG;

/** 60 °F in °C, the reference temperature of API gravity. */
export const API_REF_TEMP_C = ((60.0 - 32.0) * 5.0) / 9.0;
/** Density of water at 60 °F, kg/m³ (API MPMS 11.5). */
export const WATER_DENSITY_60F = 999.016;

const MAX_ITERATIONS = 100;
const CONVERGENCE = 1e-7;

function group(id, k0, k1, a = 0.0, b = 0.0) {
  return { id, alpha: (d) => k0 / (d * d) + k1 / d + a + b / (d * d) };
}
const GASOLINE = group(FormulaGroup.GASOLINE, 346.4228, 0.4388);
const TRANSITION = group(FormulaGroup.TRANSITION, 0.0, 0.0, -0.00336312, 2680.3206);
const JET = group(FormulaGroup.JET, 594.5418, 0.0);
const FUEL_OIL = group(FormulaGroup.FUEL_OIL, 186.9696, 0.4862);

const GASOLINE_UPPER = 770.352;
const TRANSITION_UPPER = 787.5253;
const JET_UPPER = 838.3127;

const inRange = (v, min, max) => v >= min && v <= max;

/** Returns { group, alpha15 } for a density at 15 °C (kg/m³). */
export function thermalExpansionCoefficient(density15) {
  requireFuel(Number.isFinite(density15), ErrorCode.DENSITY_NOT_NUMBER, 'Density must be a number.');
  requireFuel(
    inRange(density15, MIN_DENSITY, MAX_DENSITY),
    ErrorCode.DENSITY_OUT_OF_RANGE,
    `Density must be between ${MIN_DENSITY} and ${MAX_DENSITY} kg/m³.`,
    { min: MIN_DENSITY, max: MAX_DENSITY },
  );
  let g;
  if (density15 < GASOLINE_UPPER) g = GASOLINE;
  else if (density15 < TRANSITION_UPPER) g = TRANSITION;
  else if (density15 < JET_UPPER) g = JET;
  else g = FUEL_OIL;
  return { group: g.id, alpha15: g.alpha(density15) };
}

/** Volume correction factor from tempC to 15 °C. Multiply an observed volume by it to get the volume at 15 °C. */
export function volumeCorrectionFactor(alpha15, tempC) {
  requireFuel(
    inRange(tempC, MIN_TEMP_C, MAX_TEMP_C),
    ErrorCode.TEMP_OUT_OF_RANGE,
    `Temperature must be between ${MIN_TEMP_C} and ${MAX_TEMP_C} °C.`,
    { min: MIN_TEMP_C, max: MAX_TEMP_C },
  );
  const dt = tempC - BASE_TEMP_C;
  return Math.exp(-alpha15 * dt * (1.0 + 0.8 * alpha15 * dt));
}

/**
 * @param {object} input
 * @param {object} input.product one of Product
 * @param {number} input.density15 kg/m³ (ignored for Product.CUSTOM)
 * @param {number} input.observedTempC
 * @param {number} input.observedVolume in input.volumeUnit; results keep the same unit
 * @param {number} [input.targetTempC=15]
 * @param {number} [input.customAlpha=0] per °C, only for Product.CUSTOM
 * @param {object} [input.volumeUnit=VolumeUnit.LITRE]
 */
export function correct({
  product,
  density15,
  observedTempC,
  observedVolume,
  targetTempC = BASE_TEMP_C,
  customAlpha = 0.0,
  volumeUnit = VolumeUnit.LITRE,
}) {
  requireFuel(
    Number.isFinite(observedVolume) && observedVolume >= 0.0,
    ErrorCode.VOLUME_NEGATIVE,
    'Volume must be zero or more.',
  );

  let grp;
  let alpha15;
  if (product.isCustom) {
    requireFuel(
      Number.isFinite(customAlpha) && customAlpha > 0.0 && customAlpha < 0.01,
      ErrorCode.COEFFICIENT_OUT_OF_RANGE,
      'Coefficient must be between 0 and 0.01 per °C.',
    );
    grp = FormulaGroup.CUSTOM;
    alpha15 = customAlpha;
  } else {
    ({ group: grp, alpha15 } = thermalExpansionCoefficient(density15));
  }

  const vcfObserved = volumeCorrectionFactor(alpha15, observedTempC);
  const vcfTarget = volumeCorrectionFactor(alpha15, targetTempC);
  const volumeAt15 = observedVolume * vcfObserved;

  const massKg = product.isCustom ? null : (volumeAt15 * volumeUnit.litresPerUnit * density15) / 1000.0;

  return {
    group: grp,
    alpha15,
    vcfObserved,
    vcfTarget,
    volumeAt15,
    volumeAtTarget: volumeAt15 / vcfTarget,
    densityAtObserved: product.isCustom ? null : density15 * vcfObserved,
    massKg,
    massLb: massKg === null ? null : massKg * LB_PER_KG,
  };
}

/**
 * The reverse of correct(): the volume to order so the aircraft gets massKg of fuel.
 * Litres at 15 °C = mass / density at 15 °C, then litres at the fuel temperature = litres at 15 °C / VCF.
 */
export function volumeForMass(density15, massKg, tempC, unit = VolumeUnit.LITRE) {
  requireFuel(Number.isFinite(massKg) && massKg >= 0.0, ErrorCode.MASS_NEGATIVE, 'Mass must be zero or more.');
  const { group: grp, alpha15 } = thermalExpansionCoefficient(density15);
  const vcf = volumeCorrectionFactor(alpha15, tempC);
  const litres15 = (massKg * 1000.0) / density15;
  return {
    group: grp,
    alpha15,
    vcf,
    volumeAt15: litres15 / unit.litresPerUnit,
    volumeAtTemp: litres15 / vcf / unit.litresPerUnit,
    densityAtTemp: density15 * vcf,
    massKg,
    massLb: massKg * LB_PER_KG,
  };
}

/** Density of a fuel at several temperatures, from its density at 15 °C. Default −40 °C to +50 °C, every 5 °C. */
export function densityChart(density15, fromC = -40.0, toC = 50.0, stepC = 5.0) {
  requireFuel(stepC > 0.0, ErrorCode.STEP_NOT_POSITIVE, 'Step must be more than zero.');
  requireFuel(fromC <= toC, ErrorCode.START_ABOVE_END, 'Start temperature must not be above the end temperature.');
  const { alpha15 } = thermalExpansionCoefficient(density15);
  const rows = Math.trunc((toC - fromC) / stepC);
  const out = [];
  for (let i = 0; i <= rows; i++) {
    const t = fromC + i * stepC;
    const vcf = volumeCorrectionFactor(alpha15, t);
    const density = density15 * vcf;
    out.push({ tempC: t, density, vcf, densityKgPerLitre: density / 1000.0 });
  }
  return out;
}

/** Glass hydrometer correction (API MPMS 11.1-1980, Table 53). */
export function hydrometerCorrection(tempC) {
  const dt = tempC - BASE_TEMP_C;
  return 1.0 - 0.000023 * dt - 0.00000002 * dt * dt;
}

/**
 * Converts a density measured at observedTempC to the density at 15 °C (Table 53B), by iteration:
 * start with ρ15 = ρobserved, then repeat ρ15 = ρobserved / VCF(α15(ρ15), t).
 * @param {boolean} glassHydrometer true when read on a glass hydrometer
 */
export function densityAt15(observedDensity, observedTempC, glassHydrometer = false) {
  requireFuel(
    Number.isFinite(observedDensity) && observedDensity > 0.0,
    ErrorCode.DENSITY_NOT_POSITIVE,
    'Density must be a number above zero.',
  );
  requireFuel(
    inRange(observedTempC, MIN_TEMP_C, MAX_TEMP_C),
    ErrorCode.TEMP_OUT_OF_RANGE,
    `Temperature must be between ${MIN_TEMP_C} and ${MAX_TEMP_C} °C.`,
    { min: MIN_TEMP_C, max: MAX_TEMP_C },
  );

  const hyc = glassHydrometer ? hydrometerCorrection(observedTempC) : null;
  const corrected = observedDensity * (hyc ?? 1.0);

  let density15 = corrected;
  let grp = FormulaGroup.JET;
  let alpha15 = 0.0;
  let vcf = 1.0;
  for (let i = 0; i < MAX_ITERATIONS; i++) {
    const guess = Math.min(Math.max(density15, MIN_DENSITY), MAX_DENSITY);
    ({ group: grp, alpha15 } = thermalExpansionCoefficient(guess));
    vcf = volumeCorrectionFactor(alpha15, observedTempC);
    const next = corrected / vcf;
    const done = Math.abs(next - density15) < CONVERGENCE;
    density15 = next;
    if (done) break;
  }

  requireFuel(
    inRange(density15, MIN_DENSITY, MAX_DENSITY),
    ErrorCode.DENSITY15_OUT_OF_TABLE,
    `Density at 15 °C would be ${density15.toFixed(1)} kg/m³, outside the table (${MIN_DENSITY} to ${MAX_DENSITY}).`,
    { density15, min: MIN_DENSITY, max: MAX_DENSITY },
  );
  return {
    density15,
    group: grp,
    alpha15,
    vcf,
    hydrometerCorrection: hyc,
    density15KgPerLitre: density15 / 1000.0,
  };
}

/**
 * Converts an API gravity to a density at 15 °C.
 *   relative density 60/60 °F = 141.5 / (°API + 131.5)
 *   density at 60 °F = relative density × density of water at 60 °F
 * then carried from 60 °F (15.56 °C) to 15 °C with densityAt15().
 */
export function densityFromApiGravity(apiGravity) {
  requireFuel(
    Number.isFinite(apiGravity) && apiGravity > -131.5,
    ErrorCode.API_NOT_VALID,
    'API gravity must be a number above -131.5.',
  );
  const relativeDensity = 141.5 / (apiGravity + 131.5);
  const density60F = relativeDensity * WATER_DENSITY_60F;
  return {
    apiGravity,
    relativeDensity60F: relativeDensity,
    density60F,
    at15: densityAt15(density60F, API_REF_TEMP_C),
  };
}

/** Parses "12.5" and "12,5" the same way. Returns null when the text is not a number. */
export function parseNumber(text) {
  if (typeof text !== 'string') return null;
  const s = text.trim().replace(/,/g, '.');
  // Same shape Kotlin's toDoubleOrNull accepts for what the fields allow: digits, one mark, a sign.
  if (!/^[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?$/.test(s)) return null;
  const v = Number(s);
  return Number.isFinite(v) ? v : null;
}
