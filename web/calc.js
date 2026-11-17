// Port of core/src/main/kotlin/com/fuelvolume/core/FuelCalculator.kt. Keep the two identical.
// Volume correction of generalized refined products for temperature, as in
// ASTM D1250-08 / API MPMS Chapter 11.1-2004 ("Table 54B" and "Table 53B" equivalents).
//
// The standard works at 60 °F, in °F, with temperatures on the IPTS-68 scale:
//   α60 = K0 / ρ*² + K1 / ρ* + K2
//   CTL = exp( −α60 · Δt · (1 + 0.8 · α60 · (Δt + δ60)) ),  Δt = t68(°F) − 60.0068749
// A 15 °C density is first converted to the density at 60 °F. The factor from t to 15 °C is
// CTL(t) / CTL(15 °C), rounded to 5 decimals. No pressure correction (atmospheric pressure).
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

/** Commodity group of the generalized refined products, chosen from the density at 60 °F. */
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
/** Density limits of the refined-product tables, kg/m³ at 60 °F. */
export const MIN_DENSITY_60F = 610.6;
export const MAX_DENSITY_60F = 1163.5;
export const MIN_TEMP_C = -50.0;
export const MAX_TEMP_C = 150.0;
export const LB_PER_KG = 2.20462262185;
export const KG_PER_LB = 1.0 / LB_PER_KG;

/** 60 °F in °C, the reference temperature of API gravity and of the standard's equations. */
export const API_REF_TEMP_C = ((60.0 - 32.0) * 5.0) / 9.0;
/** Density of water at 60 °F, kg/m³. */
export const WATER_DENSITY_60F = 999.016;
/** Temperature shift between the ITS-90 and IPTS-68 scales at 60 °F, °F. */
export const DELTA_60 = 0.01374979547;
/** 60 °F (ITS-90) on the IPTS-68 scale, °F. */
export const BASE_TEMP_F_IPTS68 = 60.0068749;

const MAX_ITERATIONS = 100;
const CONVERGENCE = 1e-9;

const group = (id, k0, k1, k2) => ({ id, k0, k1, k2 });
const GASOLINE = group(FormulaGroup.GASOLINE, 192.4571, 0.2438, 0.0);
const TRANSITION = group(FormulaGroup.TRANSITION, 1489.067, 0.0, -0.0018684);
const JET = group(FormulaGroup.JET, 330.301, 0.0, 0.0);
const FUEL_OIL = group(FormulaGroup.FUEL_OIL, 103.872, 0.2701, 0.0);

/** Group limits, kg/m³ at 60 °F. */
const GASOLINE_UPPER = 770.352;
const TRANSITION_UPPER = 787.5195;
const JET_UPPER = 838.3127;

/** ITS-90 → IPTS-68 coefficients a1…a8 (t90 − t68 as a polynomial in t90 / 630 °C). */
const ITS90_COEFFS = [-0.148759, -0.267408, 1.08076, 1.269056, -4.089591, -1.871251, 7.438081, -3.536296];

const inRange = (v, min, max) => v >= min && v <= max;
const clamp = (v, min, max) => Math.min(Math.max(v, min), max);

function groupFor(density60) {
  if (density60 < GASOLINE_UPPER) return GASOLINE;
  if (density60 < TRANSITION_UPPER) return TRANSITION;
  if (density60 < JET_UPPER) return JET;
  return FUEL_OIL;
}

/** A °C temperature (ITS-90) as °F on the IPTS-68 scale, which the equations use. */
export function toIpts68F(tempC) {
  const tau = tempC / 630.0;
  let poly = 0.0;
  for (let i = ITS90_COEFFS.length - 1; i >= 0; i--) poly = poly * tau + ITS90_COEFFS[i];
  const t68C = tempC - poly * tau;
  return t68C * 1.8 + 32.0;
}

/** α60, per °F, for a density at 60 °F, with the IPTS-68 shift of the density (ρ*). */
function alpha60(g, density60) {
  const a = (DELTA_60 / 2.0) * ((g.k0 / density60 + g.k1) / density60 + g.k2);
  const b = (2.0 * g.k0 + g.k1 * density60) / (g.k0 + (g.k1 + g.k2 * density60) * density60);
  const rhoStar = density60 * (1.0 + (Math.exp(a * (1.0 + 0.8 * a)) - 1.0) / (1.0 + a * (1.0 + 1.6 * a) * b));
  return (g.k0 / rhoStar + g.k1) / rhoStar + g.k2;
}

/** CTL from 60 °F to tempC, not rounded. */
function ctl60Raw(g, density60, tempC) {
  const alpha = alpha60(g, density60);
  const dt = toIpts68F(tempC) - BASE_TEMP_F_IPTS68;
  return Math.exp(-alpha * dt * (1.0 + 0.8 * alpha * (dt + DELTA_60)));
}

function checkTemp(tempC) {
  requireFuel(
    Number.isFinite(tempC) && inRange(tempC, MIN_TEMP_C, MAX_TEMP_C),
    ErrorCode.TEMP_OUT_OF_RANGE,
    `Temperature must be between ${MIN_TEMP_C} and ${MAX_TEMP_C} °C.`,
    { min: MIN_TEMP_C, max: MAX_TEMP_C },
  );
}

/** Rounds a factor to 5 decimals, as the standard does for CTL (halves round up). */
export function round5(x) {
  return Math.floor(x * 1e5 + 0.5) / 1e5;
}

/** Lowest and highest density at 15 °C accepted, kg/m³ (the 60 °F limits taken to 15 °C). */
export const MIN_DENSITY = MIN_DENSITY_60F * ctl60Raw(groupFor(MIN_DENSITY_60F), MIN_DENSITY_60F, BASE_TEMP_C);
export const MAX_DENSITY = MAX_DENSITY_60F * ctl60Raw(groupFor(MAX_DENSITY_60F), MAX_DENSITY_60F, BASE_TEMP_C);

/** Density at 60 °F from the density at 15 °C, by iteration: ρ60 = ρ15 / CTL60(ρ60, 15 °C). */
function density60From15(density15) {
  let rho60 = density15;
  for (let i = 0; i < MAX_ITERATIONS; i++) {
    const guess = clamp(rho60, MIN_DENSITY_60F, MAX_DENSITY_60F);
    const next = density15 / ctl60Raw(groupFor(guess), guess, BASE_TEMP_C);
    const done = Math.abs(next - rho60) < CONVERGENCE;
    rho60 = next;
    if (done) break;
  }
  return rho60;
}

/** Returns { group, density60, alpha60 } for a density at 15 °C (kg/m³). */
export function coefficients(density15) {
  requireFuel(Number.isFinite(density15), ErrorCode.DENSITY_NOT_NUMBER, 'Density must be a number.');
  requireFuel(
    inRange(density15, MIN_DENSITY, MAX_DENSITY),
    ErrorCode.DENSITY_OUT_OF_RANGE,
    `Density must be between ${MIN_DENSITY} and ${MAX_DENSITY} kg/m³.`,
    { min: MIN_DENSITY, max: MAX_DENSITY },
  );
  const rho60 = density60From15(density15);
  const g = groupFor(rho60);
  return { group: g.id, density60: rho60, alpha60: alpha60(g, rho60) };
}

/** Factor from tempC to 15 °C for a fuel of density60, not rounded. */
function vcf15Raw(density60, tempC) {
  const g = groupFor(density60);
  return ctl60Raw(g, density60, tempC) / ctl60Raw(g, density60, BASE_TEMP_C);
}

/** Volume correction factor from tempC to 15 °C ("Table 54B" value), rounded to 5 decimals. */
export function volumeCorrectionFactor(density15, tempC) {
  const c = coefficients(density15);
  checkTemp(tempC);
  return round5(vcf15Raw(c.density60, tempC));
}

/** Factor for a user-typed coefficient (per °C), 15 °C base: exp(−αΔt(1 + 0.8αΔt)), rounded to 5 decimals. */
export function customVolumeCorrectionFactor(alphaPerC, tempC) {
  checkTemp(tempC);
  const dt = tempC - BASE_TEMP_C;
  return round5(Math.exp(-alphaPerC * dt * (1.0 + 0.8 * alphaPerC * dt)));
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

  if (product.isCustom) {
    requireFuel(
      Number.isFinite(customAlpha) && customAlpha > 0.0 && customAlpha < 0.01,
      ErrorCode.COEFFICIENT_OUT_OF_RANGE,
      'Coefficient must be between 0 and 0.01 per °C.',
    );
    const vcfObserved = customVolumeCorrectionFactor(customAlpha, observedTempC);
    const vcfTarget = customVolumeCorrectionFactor(customAlpha, targetTempC);
    const volumeAt15 = observedVolume * vcfObserved;
    return {
      group: FormulaGroup.CUSTOM,
      alpha60: null,
      customAlpha,
      vcfObserved,
      vcfTarget,
      volumeAt15,
      volumeAtTarget: volumeAt15 / vcfTarget,
      densityAtObserved: null,
      massKg: null,
      massLb: null,
    };
  }

  const c = coefficients(density15);
  checkTemp(observedTempC);
  checkTemp(targetTempC);
  const vcfObserved = round5(vcf15Raw(c.density60, observedTempC));
  const vcfTarget = round5(vcf15Raw(c.density60, targetTempC));
  const volumeAt15 = observedVolume * vcfObserved;
  const massKg = (volumeAt15 * volumeUnit.litresPerUnit * density15) / 1000.0;
  return {
    group: c.group,
    alpha60: c.alpha60,
    customAlpha: null,
    vcfObserved,
    vcfTarget,
    volumeAt15,
    volumeAtTarget: volumeAt15 / vcfTarget,
    densityAtObserved: density15 * vcf15Raw(c.density60, observedTempC),
    massKg,
    massLb: massKg * LB_PER_KG,
  };
}

/**
 * The reverse of correct(): the volume to order so the aircraft gets massKg of fuel.
 * Litres at 15 °C = mass / density at 15 °C, then litres at the fuel temperature = litres at 15 °C / VCF.
 */
export function volumeForMass(density15, massKg, tempC, unit = VolumeUnit.LITRE) {
  requireFuel(Number.isFinite(massKg) && massKg >= 0.0, ErrorCode.MASS_NEGATIVE, 'Mass must be zero or more.');
  const c = coefficients(density15);
  checkTemp(tempC);
  const vcf = round5(vcf15Raw(c.density60, tempC));
  const litres15 = (massKg * 1000.0) / density15;
  return {
    group: c.group,
    alpha60: c.alpha60,
    vcf,
    volumeAt15: litres15 / unit.litresPerUnit,
    volumeAtTemp: litres15 / vcf / unit.litresPerUnit,
    densityAtTemp: density15 * vcf15Raw(c.density60, tempC),
    massKg,
    massLb: massKg * LB_PER_KG,
  };
}

/** Density of a fuel at several temperatures, from its density at 15 °C. Default −40 °C to +50 °C, every 5 °C. */
export function densityChart(density15, fromC = -40.0, toC = 50.0, stepC = 5.0) {
  requireFuel(stepC > 0.0, ErrorCode.STEP_NOT_POSITIVE, 'Step must be more than zero.');
  requireFuel(fromC <= toC, ErrorCode.START_ABOVE_END, 'Start temperature must not be above the end temperature.');
  const c = coefficients(density15);
  checkTemp(fromC);
  checkTemp(toC);
  const rows = Math.trunc((toC - fromC) / stepC);
  const out = [];
  for (let i = 0; i <= rows; i++) {
    const t = fromC + i * stepC;
    const raw = vcf15Raw(c.density60, t);
    const density = density15 * raw;
    out.push({ tempC: t, density, vcf: round5(raw), densityKgPerLitre: density / 1000.0 });
  }
  return out;
}

/** Glass hydrometer correction. Δt = t − 15 °C. */
export function hydrometerCorrection(tempC) {
  const dt = tempC - BASE_TEMP_C;
  return 1.0 - 0.000023 * dt - 0.00000002 * dt * dt;
}

/** Density at 15 °C and the matching result, from a density at 60 °F. */
function at15From60(density60, vcf, hyc) {
  if (!inRange(density60, MIN_DENSITY_60F, MAX_DENSITY_60F)) {
    const density15 = density60 * ctl60Raw(groupFor(density60), density60, BASE_TEMP_C);
    throw new FuelError(ErrorCode.DENSITY15_OUT_OF_TABLE, 'Density is outside the table.', {
      density15,
      min: MIN_DENSITY,
      max: MAX_DENSITY,
    });
  }
  const g = groupFor(density60);
  const density15 = density60 * ctl60Raw(g, density60, BASE_TEMP_C);
  return {
    density15,
    density60,
    group: g.id,
    alpha60: alpha60(g, density60),
    vcf,
    hydrometerCorrection: hyc,
    density15KgPerLitre: density15 / 1000.0,
  };
}

/**
 * Converts a density measured at observedTempC to the density at 15 °C ("Table 53B" equivalent), by iteration:
 * ρ60 = ρobserved / CTL60(ρ60, t), starting from ρ60 = ρobserved; then ρ15 = ρ60 · CTL60(ρ60, 15 °C).
 * @param {boolean} glassHydrometer true when read on a glass hydrometer
 */
export function densityAt15(observedDensity, observedTempC, glassHydrometer = false) {
  requireFuel(
    Number.isFinite(observedDensity) && observedDensity > 0.0,
    ErrorCode.DENSITY_NOT_POSITIVE,
    'Density must be a number above zero.',
  );
  checkTemp(observedTempC);

  const hyc = glassHydrometer ? hydrometerCorrection(observedTempC) : null;
  const corrected = observedDensity * (hyc ?? 1.0);

  let rho60 = corrected;
  for (let i = 0; i < MAX_ITERATIONS; i++) {
    const guess = clamp(rho60, MIN_DENSITY_60F, MAX_DENSITY_60F);
    const next = corrected / ctl60Raw(groupFor(guess), guess, observedTempC);
    const done = Math.abs(next - rho60) < CONVERGENCE;
    rho60 = next;
    if (done) break;
  }
  const vcf = inRange(rho60, MIN_DENSITY_60F, MAX_DENSITY_60F) ? round5(vcf15Raw(rho60, observedTempC)) : 1.0;
  return at15From60(rho60, vcf, hyc);
}

/**
 * Converts an API gravity to a density at 15 °C.
 *   relative density 60/60 °F = 141.5 / (°API + 131.5)
 *   density at 60 °F = relative density × density of water at 60 °F
 *   density at 15 °C = density at 60 °F × CTL60(15 °C)
 */
export function densityFromApiGravity(apiGravity) {
  requireFuel(
    Number.isFinite(apiGravity) && apiGravity > -131.5,
    ErrorCode.API_NOT_VALID,
    'API gravity must be a number above -131.5.',
  );
  const relativeDensity = 141.5 / (apiGravity + 131.5);
  const density60F = relativeDensity * WATER_DENSITY_60F;
  const vcf = inRange(density60F, MIN_DENSITY_60F, MAX_DENSITY_60F)
    ? round5(vcf15Raw(density60F, API_REF_TEMP_C))
    : 1.0;
  return {
    apiGravity,
    relativeDensity60F: relativeDensity,
    density60F,
    at15: at15From60(density60F, vcf, null),
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
