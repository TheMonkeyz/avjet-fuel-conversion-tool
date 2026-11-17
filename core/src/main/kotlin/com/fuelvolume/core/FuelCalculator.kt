package com.fuelvolume.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor

/**
 * Aviation fuels the user can pick. The density is only a typical starting value.
 * The commodity group is chosen from the density at 60 °F, as the standard does
 * (ASTM D1250-08 / API MPMS Chapter 11.1-2004, generalized refined products).
 */
enum class Product(val label: String, val defaultDensity: Double) {
    JET_A1("Jet A-1 / JP-8", 800.0),
    JET_A("Jet A", 810.0),
    AVGAS_100LL("Avgas 100LL", 720.0),
    OTHER("Other fuel (by density)", 780.0),
    CUSTOM("Custom coefficient", 0.0);

    val isCustom: Boolean get() = this == CUSTOM
}

enum class VolumeUnit(val label: String, val litresPerUnit: Double) {
    LITRE("L", 1.0),
    US_GALLON("US gal", 3.785411784),
}

/** Commodity group of the generalized refined products. The app shows a translated name for each. */
enum class FormulaGroup(val label: String) {
    GASOLINE("Gasolines"),
    TRANSITION("Transition zone"),
    JET("Jet fuels"),
    FUEL_OIL("Fuel oils"),
    CUSTOM("Custom coefficient"),
}

/** Why a calculation was refused. The app turns each one into a message in the user's language. */
sealed class FuelError {
    data object DensityNotNumber : FuelError()
    data class DensityOutOfRange(val min: Double, val max: Double) : FuelError()
    data class TempOutOfRange(val min: Double, val max: Double) : FuelError()
    data object VolumeNegative : FuelError()
    data object MassNegative : FuelError()
    data object CoefficientOutOfRange : FuelError()
    data object StepNotPositive : FuelError()
    data object StartAboveEnd : FuelError()
    data object DensityNotPositive : FuelError()
    data class Density15OutOfTable(val density15: Double, val min: Double, val max: Double) : FuelError()
    data object ApiNotValid : FuelError()
}

/** Thrown for bad input. Extends [IllegalArgumentException] so callers can still catch that. */
class FuelException(val error: FuelError, message: String) : IllegalArgumentException(message)

private inline fun requireFuel(condition: Boolean, error: () -> FuelError, message: () -> String) {
    if (!condition) throw FuelException(error(), message())
}

/**
 * @param density15 density at 15 °C in kg/m³ (ignored for [Product.CUSTOM])
 * @param observedTempC temperature of the fuel when the volume was measured, °C
 * @param observedVolume measured volume in [volumeUnit]. Results keep the same unit.
 * @param targetTempC temperature you want the volume at. 15 °C is the standard reference.
 * @param customAlpha thermal expansion coefficient per °C, only used for [Product.CUSTOM]
 */
data class FuelInput(
    val product: Product,
    val density15: Double,
    val observedTempC: Double,
    val observedVolume: Double,
    val targetTempC: Double = FuelCalculator.BASE_TEMP_C,
    val customAlpha: Double = 0.0,
    val volumeUnit: VolumeUnit = VolumeUnit.LITRE,
)

data class FuelResult(
    /** Which commodity group was used. */
    val group: FormulaGroup,
    /** Thermal expansion coefficient at 60 °F, per °F (ASTM D1250-08). Null for a custom coefficient. */
    val alpha60: Double?,
    /** The coefficient typed by the user, per °C. Null unless the product is custom. */
    val customAlpha: Double?,
    /** Volume correction factor from the observed temperature to 15 °C, rounded to 5 decimals. */
    val vcfObserved: Double,
    /** Volume correction factor from the target temperature to 15 °C, rounded to 5 decimals. */
    val vcfTarget: Double,
    /** Volume the fuel would have at 15 °C, in the input unit. */
    val volumeAt15: Double,
    /** Volume the fuel would have at the target temperature, in the input unit. */
    val volumeAtTarget: Double,
    /** Density at the observed temperature, kg/m³. Null for a custom coefficient. */
    val densityAtObserved: Double?,
    /** Mass of the fuel in kilograms. Mass does not change with temperature. Null for a custom coefficient. */
    val massKg: Double?,
) {
    /** Mass of the fuel in pounds. Null for a custom coefficient. */
    val massLb: Double? get() = massKg?.times(FuelCalculator.LB_PER_KG)
}

/** Volume to order for a fuel mass, at the temperature the fuel will have at the meter. */
data class VolumeForMassResult(
    /** Which commodity group was used. */
    val group: FormulaGroup,
    /** Thermal expansion coefficient at 60 °F, per °F. */
    val alpha60: Double,
    /** Volume correction factor from the fuel temperature to 15 °C, rounded to 5 decimals. */
    val vcf: Double,
    /** Volume of the mass at 15 °C, in the requested unit. */
    val volumeAt15: Double,
    /** Volume of the mass at the fuel temperature, in the requested unit. This is what the meter shows. */
    val volumeAtTemp: Double,
    /** Density at the fuel temperature, kg/m³. */
    val densityAtTemp: Double,
    val massKg: Double,
) {
    val massLb: Double get() = massKg * FuelCalculator.LB_PER_KG
}

/** One row of a density chart: the fuel density and VCF at a given temperature. */
data class DensityPoint(
    val tempC: Double,
    /** Density at [tempC], kg/m³. */
    val density: Double,
    /** Volume correction factor from [tempC] to 15 °C, rounded to 5 decimals. */
    val vcf: Double,
) {
    /** Density in kg per litre, the figure printed on most fuel release notes. */
    val densityKgPerLitre: Double get() = density / 1000.0
}

/** Result of converting a measured density at some temperature to the density at 15 °C. */
data class DensityAt15Result(
    /** Density at 15 °C, kg/m³. */
    val density15: Double,
    /** Density at 60 °F, kg/m³ (the base of the standard's equations). */
    val density60: Double,
    /** Which commodity group was used. */
    val group: FormulaGroup,
    /** Thermal expansion coefficient at 60 °F, per °F. */
    val alpha60: Double,
    /** Volume correction factor from the measured temperature to 15 °C, rounded to 5 decimals. */
    val vcf: Double,
    /** Glass hydrometer correction factor that was applied, or null when none. */
    val hydrometerCorrection: Double?,
) {
    val density15KgPerLitre: Double get() = density15 / 1000.0
}

/** Result of converting an API gravity (°API at 60 °F) to a density. */
data class ApiGravityResult(
    val apiGravity: Double,
    /** Relative density (specific gravity) 60/60 °F. */
    val relativeDensity60F: Double,
    /** Density at 60 °F, kg/m³. */
    val density60F: Double,
    /** The same fuel at 15 °C. */
    val at15: DensityAt15Result,
)

/** Coefficients of a fuel, from its density at 15 °C. */
data class Coefficients(
    val group: FormulaGroup,
    /** Density at 60 °F, kg/m³. */
    val density60: Double,
    /** Thermal expansion coefficient at 60 °F, per °F. */
    val alpha60: Double,
)

/**
 * Volume correction of generalized refined products for temperature, as in
 * ASTM D1250-08 / API MPMS Chapter 11.1-2004 (the "Table 54B" and "Table 53B" equivalents).
 *
 * The standard works at 60 °F, in °F, with temperatures on the IPTS-68 scale:
 *
 *   α60 = K0 / ρ*² + K1 / ρ* + K2
 *   CTL = exp( −α60 · Δt · (1 + 0.8 · α60 · (Δt + δ60)) ),  Δt = t68(°F) − 60.0068749
 *
 * A 15 °C (metric) density is first converted to the density at 60 °F. The factor from t to
 * 15 °C is then CTL(t) / CTL(15 °C), both from 60 °F, and it is rounded to 5 decimals.
 * Pressure correction (CPL) is not applied: fuel is measured at atmospheric pressure.
 */
object FuelCalculator {
    const val BASE_TEMP_C = 15.0

    /** Density limits of the refined-product tables, kg/m³ at 60 °F. */
    const val MIN_DENSITY_60F = 610.6
    const val MAX_DENSITY_60F = 1163.5

    const val MIN_TEMP_C = -50.0
    const val MAX_TEMP_C = 150.0

    const val LB_PER_KG = 2.20462262185
    const val KG_PER_LB = 1.0 / LB_PER_KG

    /** 60 °F in °C, the reference temperature of API gravity and of the standard's equations. */
    const val API_REF_TEMP_C = (60.0 - 32.0) * 5.0 / 9.0

    /** Density of water at 60 °F, kg/m³. */
    const val WATER_DENSITY_60F = 999.016

    /** Temperature shift between the ITS-90 and IPTS-68 scales at 60 °F, °F. */
    const val DELTA_60 = 0.01374979547

    /** 60 °F (ITS-90) on the IPTS-68 scale, °F. */
    const val BASE_TEMP_F_IPTS68 = 60.0068749

    private data class Group(val id: FormulaGroup, val k0: Double, val k1: Double, val k2: Double)

    private val GASOLINE = Group(FormulaGroup.GASOLINE, k0 = 192.4571, k1 = 0.2438, k2 = 0.0)
    private val TRANSITION = Group(FormulaGroup.TRANSITION, k0 = 1489.0670, k1 = 0.0, k2 = -0.0018684)
    private val JET = Group(FormulaGroup.JET, k0 = 330.3010, k1 = 0.0, k2 = 0.0)
    private val FUEL_OIL = Group(FormulaGroup.FUEL_OIL, k0 = 103.8720, k1 = 0.2701, k2 = 0.0)

    /** Group limits, kg/m³ at 60 °F. */
    private const val GASOLINE_UPPER = 770.3520
    private const val TRANSITION_UPPER = 787.5195
    private const val JET_UPPER = 838.3127

    /** ITS-90 → IPTS-68 coefficients a1…a8 (t90 − t68 as a polynomial in t90 / 630 °C). */
    private val ITS90_COEFFS = doubleArrayOf(
        -0.148759, -0.267408, 1.080760, 1.269056, -4.089591, -1.871251, 7.438081, -3.536296,
    )

    /** Lowest density at 15 °C accepted, kg/m³ (the 60 °F limit taken to 15 °C). */
    val MIN_DENSITY: Double by lazy { MIN_DENSITY_60F * ctl60Raw(groupFor(MIN_DENSITY_60F), MIN_DENSITY_60F, BASE_TEMP_C) }

    /** Highest density at 15 °C accepted, kg/m³. */
    val MAX_DENSITY: Double by lazy { MAX_DENSITY_60F * ctl60Raw(groupFor(MAX_DENSITY_60F), MAX_DENSITY_60F, BASE_TEMP_C) }

    private fun groupFor(density60: Double): Group = when {
        density60 < GASOLINE_UPPER -> GASOLINE
        density60 < TRANSITION_UPPER -> TRANSITION
        density60 < JET_UPPER -> JET
        else -> FUEL_OIL
    }

    /** A °C temperature (ITS-90) as °F on the IPTS-68 scale, which the equations use. */
    fun toIpts68F(tempC: Double): Double {
        val tau = tempC / 630.0
        var poly = 0.0
        for (i in ITS90_COEFFS.indices.reversed()) poly = poly * tau + ITS90_COEFFS[i]
        val t68C = tempC - poly * tau
        return t68C * 1.8 + 32.0
    }

    /** α60, per °F, for a density at 60 °F, with the IPTS-68 shift of the density (ρ*). */
    private fun alpha60(g: Group, density60: Double): Double {
        val a = DELTA_60 / 2.0 * ((g.k0 / density60 + g.k1) / density60 + g.k2)
        val b = (2.0 * g.k0 + g.k1 * density60) / (g.k0 + (g.k1 + g.k2 * density60) * density60)
        val rhoStar = density60 * (1.0 + (exp(a * (1.0 + 0.8 * a)) - 1.0) / (1.0 + a * (1.0 + 1.6 * a) * b))
        return (g.k0 / rhoStar + g.k1) / rhoStar + g.k2
    }

    /** CTL from 60 °F to [tempC], not rounded. */
    private fun ctl60Raw(g: Group, density60: Double, tempC: Double): Double {
        val alpha = alpha60(g, density60)
        val dt = toIpts68F(tempC) - BASE_TEMP_F_IPTS68
        return exp(-alpha * dt * (1.0 + 0.8 * alpha * (dt + DELTA_60)))
    }

    private fun checkTemp(tempC: Double) {
        requireFuel(tempC.isFinite() && tempC in MIN_TEMP_C..MAX_TEMP_C, { FuelError.TempOutOfRange(MIN_TEMP_C, MAX_TEMP_C) }) {
            "Temperature must be between $MIN_TEMP_C and $MAX_TEMP_C °C."
        }
    }

    /** Rounds a factor to 5 decimals, as the standard does for CTL (halves round up). */
    fun round5(x: Double): Double = floor(x * 1e5 + 0.5) / 1e5

    /**
     * Density at 60 °F from the density at 15 °C, by iteration:
     * ρ60 = ρ15 / CTL60(ρ60, 15 °C), starting from ρ60 = ρ15. The group can change between rounds.
     */
    private fun density60From15(density15: Double): Double {
        var rho60 = density15
        repeat(MAX_ITERATIONS) {
            val guess = rho60.coerceIn(MIN_DENSITY_60F, MAX_DENSITY_60F)
            val next = density15 / ctl60Raw(groupFor(guess), guess, BASE_TEMP_C)
            val done = abs(next - rho60) < CONVERGENCE
            rho60 = next
            if (done) return rho60
        }
        return rho60
    }

    /** Group, density at 60 °F and α60 for a density at 15 °C. */
    fun coefficients(density15: Double): Coefficients {
        requireFuel(density15.isFinite(), { FuelError.DensityNotNumber }) { "Density must be a number." }
        requireFuel(density15 in MIN_DENSITY..MAX_DENSITY, { FuelError.DensityOutOfRange(MIN_DENSITY, MAX_DENSITY) }) {
            "Density must be between $MIN_DENSITY and $MAX_DENSITY kg/m³."
        }
        val rho60 = density60From15(density15)
        val g = groupFor(rho60)
        return Coefficients(group = g.id, density60 = rho60, alpha60 = alpha60(g, rho60))
    }

    /** Factor from [tempC] to 15 °C for a fuel of [density60], not rounded. */
    private fun vcf15Raw(density60: Double, tempC: Double): Double {
        val g = groupFor(density60)
        return ctl60Raw(g, density60, tempC) / ctl60Raw(g, density60, BASE_TEMP_C)
    }

    /**
     * Volume correction factor from [tempC] to 15 °C (the "Table 54B" value), rounded to 5 decimals.
     * Multiply an observed volume by it to get the volume at 15 °C.
     */
    fun volumeCorrectionFactor(density15: Double, tempC: Double): Double {
        val c = coefficients(density15)
        checkTemp(tempC)
        return round5(vcf15Raw(c.density60, tempC))
    }

    /** Factor for a user-typed coefficient [alphaPerC], 15 °C base: exp(−αΔt(1 + 0.8αΔt)), rounded to 5 decimals. */
    fun customVolumeCorrectionFactor(alphaPerC: Double, tempC: Double): Double {
        checkTemp(tempC)
        val dt = tempC - BASE_TEMP_C
        return round5(exp(-alphaPerC * dt * (1.0 + 0.8 * alphaPerC * dt)))
    }

    fun correct(input: FuelInput): FuelResult {
        requireFuel(input.observedVolume.isFinite() && input.observedVolume >= 0.0, { FuelError.VolumeNegative }) {
            "Volume must be zero or more."
        }

        if (input.product.isCustom) {
            requireFuel(
                input.customAlpha.isFinite() && input.customAlpha > 0.0 && input.customAlpha < 0.01,
                { FuelError.CoefficientOutOfRange },
            ) { "Coefficient must be between 0 and 0.01 per °C." }
            val vcfObserved = customVolumeCorrectionFactor(input.customAlpha, input.observedTempC)
            val vcfTarget = customVolumeCorrectionFactor(input.customAlpha, input.targetTempC)
            val volumeAt15 = input.observedVolume * vcfObserved
            return FuelResult(
                group = FormulaGroup.CUSTOM, alpha60 = null, customAlpha = input.customAlpha,
                vcfObserved = vcfObserved, vcfTarget = vcfTarget,
                volumeAt15 = volumeAt15, volumeAtTarget = volumeAt15 / vcfTarget,
                densityAtObserved = null, massKg = null,
            )
        }

        val c = coefficients(input.density15)
        checkTemp(input.observedTempC)
        checkTemp(input.targetTempC)
        val vcfObserved = round5(vcf15Raw(c.density60, input.observedTempC))
        val vcfTarget = round5(vcf15Raw(c.density60, input.targetTempC))
        val volumeAt15 = input.observedVolume * vcfObserved
        val litresAt15 = volumeAt15 * input.volumeUnit.litresPerUnit

        return FuelResult(
            group = c.group,
            alpha60 = c.alpha60,
            customAlpha = null,
            vcfObserved = vcfObserved,
            vcfTarget = vcfTarget,
            volumeAt15 = volumeAt15,
            volumeAtTarget = volumeAt15 / vcfTarget,
            densityAtObserved = input.density15 * vcf15Raw(c.density60, input.observedTempC),
            massKg = litresAt15 * input.density15 / 1000.0,
        )
    }

    /**
     * The reverse of [correct]: the volume to order so the aircraft gets [massKg] of fuel.
     * Mass does not change with temperature, so: litres at 15 °C = mass / density at 15 °C,
     * then litres at the fuel temperature = litres at 15 °C / VCF.
     */
    fun volumeForMass(density15: Double, massKg: Double, tempC: Double, unit: VolumeUnit = VolumeUnit.LITRE): VolumeForMassResult {
        requireFuel(massKg.isFinite() && massKg >= 0.0, { FuelError.MassNegative }) { "Mass must be zero or more." }
        val c = coefficients(density15)
        checkTemp(tempC)
        val vcf = round5(vcf15Raw(c.density60, tempC))
        val litres15 = massKg * 1000.0 / density15
        return VolumeForMassResult(
            group = c.group,
            alpha60 = c.alpha60,
            vcf = vcf,
            volumeAt15 = litres15 / unit.litresPerUnit,
            volumeAtTemp = litres15 / vcf / unit.litresPerUnit,
            densityAtTemp = density15 * vcf15Raw(c.density60, tempC),
            massKg = massKg,
        )
    }

    /**
     * Density of a fuel at several temperatures, from its density at 15 °C.
     * Default range: −40 °C to +50 °C in steps of 5 °C.
     */
    fun densityChart(
        density15: Double,
        fromC: Double = -40.0,
        toC: Double = 50.0,
        stepC: Double = 5.0,
    ): List<DensityPoint> {
        requireFuel(stepC > 0.0, { FuelError.StepNotPositive }) { "Step must be more than zero." }
        requireFuel(fromC <= toC, { FuelError.StartAboveEnd }) { "Start temperature must not be above the end temperature." }
        val c = coefficients(density15)
        checkTemp(fromC)
        checkTemp(toC)
        val rows = ((toC - fromC) / stepC).toInt()
        return (0..rows).map { i ->
            val t = fromC + i * stepC
            val raw = vcf15Raw(c.density60, t)
            DensityPoint(tempC = t, density = density15 * raw, vcf = round5(raw))
        }
    }

    /**
     * Glass hydrometer correction. A glass hydrometer itself expands with temperature,
     * so its reading needs a small correction before use. Δt = t − 15 °C.
     */
    fun hydrometerCorrection(tempC: Double): Double {
        val dt = tempC - BASE_TEMP_C
        return 1.0 - 0.000023 * dt - 0.00000002 * dt * dt
    }

    /** Density at 15 °C and the matching result, from a density at 60 °F. */
    private fun at15From60(density60: Double, vcf: Double, hyc: Double?): DensityAt15Result {
        requireFuel(density60 in MIN_DENSITY_60F..MAX_DENSITY_60F, {
            FuelError.Density15OutOfTable(density60 * ctl60Raw(groupFor(density60), density60, BASE_TEMP_C), MIN_DENSITY, MAX_DENSITY)
        }) { "Density is outside the table." }
        val g = groupFor(density60)
        val density15 = density60 * ctl60Raw(g, density60, BASE_TEMP_C)
        return DensityAt15Result(
            density15 = density15, density60 = density60, group = g.id,
            alpha60 = alpha60(g, density60), vcf = vcf, hydrometerCorrection = hyc,
        )
    }

    /**
     * Converts a density measured at [observedTempC] to the density at 15 °C (the "Table 53B" equivalent).
     *
     * The coefficient depends on the unknown density, so the standard solves it by iteration:
     * ρ60 = ρobserved / CTL60(ρ60, t), starting from ρ60 = ρobserved; then ρ15 = ρ60 · CTL60(ρ60, 15 °C).
     *
     * @param glassHydrometer true when the density was read on a glass hydrometer. False for a
     *   digital density meter or a figure that is already corrected for the glass.
     */
    fun densityAt15(observedDensity: Double, observedTempC: Double, glassHydrometer: Boolean = false): DensityAt15Result {
        requireFuel(observedDensity.isFinite() && observedDensity > 0.0, { FuelError.DensityNotPositive }) {
            "Density must be a number above zero."
        }
        checkTemp(observedTempC)

        val hyc = if (glassHydrometer) hydrometerCorrection(observedTempC) else null
        val corrected = observedDensity * (hyc ?: 1.0)

        var rho60 = corrected
        for (i in 0 until MAX_ITERATIONS) {
            val guess = rho60.coerceIn(MIN_DENSITY_60F, MAX_DENSITY_60F)
            val next = corrected / ctl60Raw(groupFor(guess), guess, observedTempC)
            val done = abs(next - rho60) < CONVERGENCE
            rho60 = next
            if (done) break
        }
        val vcf = if (rho60 in MIN_DENSITY_60F..MAX_DENSITY_60F) round5(vcf15Raw(rho60, observedTempC)) else 1.0
        return at15From60(rho60, vcf, hyc)
    }

    /**
     * Converts an API gravity to a density at 15 °C.
     *
     *   relative density 60/60 °F = 141.5 / (°API + 131.5)
     *   density at 60 °F = relative density × density of water at 60 °F
     *   density at 15 °C = density at 60 °F × CTL60(15 °C)
     */
    fun densityFromApiGravity(apiGravity: Double): ApiGravityResult {
        requireFuel(apiGravity.isFinite() && apiGravity > -131.5, { FuelError.ApiNotValid }) {
            "API gravity must be a number above -131.5."
        }
        val relativeDensity = 141.5 / (apiGravity + 131.5)
        val density60F = relativeDensity * WATER_DENSITY_60F
        val vcf = if (density60F in MIN_DENSITY_60F..MAX_DENSITY_60F) round5(vcf15Raw(density60F, API_REF_TEMP_C)) else 1.0
        return ApiGravityResult(
            apiGravity = apiGravity,
            relativeDensity60F = relativeDensity,
            density60F = density60F,
            at15 = at15From60(density60F, vcf, null),
        )
    }

    private const val MAX_ITERATIONS = 100
    private const val CONVERGENCE = 1e-9
}
