package com.fuelvolume.core

import kotlin.math.abs
import kotlin.math.exp

/**
 * Aviation fuels the user can pick. The density is only a typical starting value.
 * The formula group is chosen from the density, exactly as the standard does
 * (ASTM D1250-80 / API MPMS 11.1, Table 54B, generalized refined products).
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

/** Formula group of Table 54B, chosen from the density. The app shows a translated name for each. */
enum class FormulaGroup(val label: String) {
    GASOLINE("Gasoline / avgas (Table 54B)"),
    TRANSITION("Transition zone (Table 54B)"),
    JET("Jet fuel / kerosene (Table 54B)"),
    FUEL_OIL("Fuel oil / diesel (Table 54B)"),
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
    /** Which table / formula group was used. */
    val group: FormulaGroup,
    /** Thermal expansion coefficient at 15 °C, per °C. */
    val alpha15: Double,
    /** Volume correction factor from the observed temperature to 15 °C. */
    val vcfObserved: Double,
    /** Volume correction factor from the target temperature to 15 °C. */
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
    /** Which table / formula group was used. */
    val group: FormulaGroup,
    /** Thermal expansion coefficient at 15 °C, per °C. */
    val alpha15: Double,
    /** Volume correction factor from the fuel temperature to 15 °C. */
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
    /** Volume correction factor from [tempC] to 15 °C. */
    val vcf: Double,
) {
    /** Density in kg per litre, the figure printed on most fuel release notes. */
    val densityKgPerLitre: Double get() = density / 1000.0
}

/** Result of converting a measured density at some temperature to the density at 15 °C. */
data class DensityAt15Result(
    /** Density at 15 °C, kg/m³. */
    val density15: Double,
    /** Which table / formula group was used. */
    val group: FormulaGroup,
    /** Thermal expansion coefficient at 15 °C, per °C. */
    val alpha15: Double,
    /** Volume correction factor from the measured temperature to 15 °C. */
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
    /** Density at 60 °F (15.56 °C), kg/m³. */
    val density60F: Double,
    /** The density at 60 °F carried to 15 °C with Table 54B. */
    val at15: DensityAt15Result,
)

/**
 * Volume correction for petroleum products with temperature.
 *
 * Implements the metric (15 °C base) generalized equations of
 * ASTM D1250-80 / API MPMS Chapter 11.1 (1980), Table 54B:
 *
 *   α15 = K0 / ρ15² + K1 / ρ15
 *   VCF = exp( −α15 · Δt · (1 + 0.8 · α15 · Δt) ),  Δt = t − 15
 */
object FuelCalculator {
    const val BASE_TEMP_C = 15.0

    const val MIN_DENSITY = 653.0
    const val MAX_DENSITY = 1075.0

    const val MIN_TEMP_C = -50.0
    const val MAX_TEMP_C = 150.0

    const val LB_PER_KG = 2.20462262185
    const val KG_PER_LB = 1.0 / LB_PER_KG

    private data class Group(val id: FormulaGroup, val k0: Double, val k1: Double, val a: Double = 0.0, val b: Double = 0.0) {
        fun alpha(density: Double): Double = k0 / (density * density) + k1 / density + a + b / (density * density)
    }

    private val GASOLINE = Group(FormulaGroup.GASOLINE, k0 = 346.4228, k1 = 0.4388)
    private val TRANSITION = Group(FormulaGroup.TRANSITION, k0 = 0.0, k1 = 0.0, a = -0.00336312, b = 2680.3206)
    private val JET = Group(FormulaGroup.JET, k0 = 594.5418, k1 = 0.0)
    private val FUEL_OIL = Group(FormulaGroup.FUEL_OIL, k0 = 186.9696, k1 = 0.4862)

    private const val GASOLINE_UPPER = 770.352
    private const val TRANSITION_UPPER = 787.5253
    private const val JET_UPPER = 838.3127

    /** Returns the formula group name and α15 for a density at 15 °C. */
    fun thermalExpansionCoefficient(density15: Double): Pair<FormulaGroup, Double> {
        requireFuel(density15.isFinite(), { FuelError.DensityNotNumber }) { "Density must be a number." }
        requireFuel(density15 in MIN_DENSITY..MAX_DENSITY, { FuelError.DensityOutOfRange(MIN_DENSITY, MAX_DENSITY) }) {
            "Density must be between $MIN_DENSITY and $MAX_DENSITY kg/m³."
        }
        val group = when {
            density15 < GASOLINE_UPPER -> GASOLINE
            density15 < TRANSITION_UPPER -> TRANSITION
            density15 < JET_UPPER -> JET
            else -> FUEL_OIL
        }
        return group.id to group.alpha(density15)
    }

    /** Volume correction factor from [tempC] to 15 °C. Multiply an observed volume by it to get the volume at 15 °C. */
    fun volumeCorrectionFactor(alpha15: Double, tempC: Double): Double {
        requireFuel(tempC in MIN_TEMP_C..MAX_TEMP_C, { FuelError.TempOutOfRange(MIN_TEMP_C, MAX_TEMP_C) }) {
            "Temperature must be between $MIN_TEMP_C and $MAX_TEMP_C °C."
        }
        val dt = tempC - BASE_TEMP_C
        return exp(-alpha15 * dt * (1.0 + 0.8 * alpha15 * dt))
    }

    fun correct(input: FuelInput): FuelResult {
        requireFuel(input.observedVolume.isFinite() && input.observedVolume >= 0.0, { FuelError.VolumeNegative }) {
            "Volume must be zero or more."
        }

        val (group, alpha15) = if (input.product.isCustom) {
            requireFuel(
                input.customAlpha.isFinite() && input.customAlpha > 0.0 && input.customAlpha < 0.01,
                { FuelError.CoefficientOutOfRange },
            ) { "Coefficient must be between 0 and 0.01 per °C." }
            FormulaGroup.CUSTOM to input.customAlpha
        } else {
            thermalExpansionCoefficient(input.density15)
        }

        val vcfObserved = volumeCorrectionFactor(alpha15, input.observedTempC)
        val vcfTarget = volumeCorrectionFactor(alpha15, input.targetTempC)
        val volumeAt15 = input.observedVolume * vcfObserved

        val massKg = if (input.product.isCustom) null else {
            val litresAt15 = volumeAt15 * input.volumeUnit.litresPerUnit
            litresAt15 * input.density15 / 1000.0
        }

        return FuelResult(
            group = group,
            alpha15 = alpha15,
            vcfObserved = vcfObserved,
            vcfTarget = vcfTarget,
            volumeAt15 = volumeAt15,
            volumeAtTarget = volumeAt15 / vcfTarget,
            densityAtObserved = if (input.product.isCustom) null else input.density15 * vcfObserved,
            massKg = massKg,
        )
    }

    /**
     * The reverse of [correct]: the volume to order so the aircraft gets [massKg] of fuel.
     * Mass does not change with temperature, so: litres at 15 °C = mass / density at 15 °C,
     * then litres at the fuel temperature = litres at 15 °C / VCF.
     */
    fun volumeForMass(density15: Double, massKg: Double, tempC: Double, unit: VolumeUnit = VolumeUnit.LITRE): VolumeForMassResult {
        requireFuel(massKg.isFinite() && massKg >= 0.0, { FuelError.MassNegative }) { "Mass must be zero or more." }
        val (group, alpha15) = thermalExpansionCoefficient(density15)
        val vcf = volumeCorrectionFactor(alpha15, tempC)
        val litres15 = massKg * 1000.0 / density15
        return VolumeForMassResult(
            group = group,
            alpha15 = alpha15,
            vcf = vcf,
            volumeAt15 = litres15 / unit.litresPerUnit,
            volumeAtTemp = litres15 / vcf / unit.litresPerUnit,
            densityAtTemp = density15 * vcf,
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
        val (_, alpha15) = thermalExpansionCoefficient(density15)
        val rows = ((toC - fromC) / stepC).toInt()
        return (0..rows).map { i ->
            val t = fromC + i * stepC
            val vcf = volumeCorrectionFactor(alpha15, t)
            DensityPoint(tempC = t, density = density15 * vcf, vcf = vcf)
        }
    }

    /**
     * Glass hydrometer correction (API MPMS 11.1-1980, Table 53). A glass hydrometer itself
     * expands with temperature, so its reading needs a small correction before use.
     */
    fun hydrometerCorrection(tempC: Double): Double {
        val dt = tempC - BASE_TEMP_C
        return 1.0 - 0.000023 * dt - 0.00000002 * dt * dt
    }

    /**
     * Converts a density measured at [observedTempC] to the density at 15 °C (Table 53B).
     *
     * ρ15 depends on α15, and α15 depends on ρ15, so the standard solves it by iteration:
     * start with ρ15 = ρobserved, then repeat ρ15 = ρobserved / VCF(α15(ρ15), t) until it stops changing.
     *
     * @param glassHydrometer true when the density was read on a glass hydrometer. False for a
     *   digital density meter or a figure that is already corrected for the glass.
     */
    fun densityAt15(observedDensity: Double, observedTempC: Double, glassHydrometer: Boolean = false): DensityAt15Result {
        requireFuel(observedDensity.isFinite() && observedDensity > 0.0, { FuelError.DensityNotPositive }) {
            "Density must be a number above zero."
        }
        requireFuel(observedTempC in MIN_TEMP_C..MAX_TEMP_C, { FuelError.TempOutOfRange(MIN_TEMP_C, MAX_TEMP_C) }) {
            "Temperature must be between $MIN_TEMP_C and $MAX_TEMP_C °C."
        }

        val hyc = if (glassHydrometer) hydrometerCorrection(observedTempC) else null
        val corrected = observedDensity * (hyc ?: 1.0)

        var density15 = corrected
        var group = FormulaGroup.JET
        var alpha15 = 0.0
        var vcf = 1.0
        repeat(MAX_ITERATIONS) {
            val guess = density15.coerceIn(MIN_DENSITY, MAX_DENSITY)
            val (g, a) = thermalExpansionCoefficient(guess)
            group = g
            alpha15 = a
            vcf = volumeCorrectionFactor(alpha15, observedTempC)
            val next = corrected / vcf
            val done = abs(next - density15) < CONVERGENCE
            density15 = next
            if (done) return@repeat
        }

        requireFuel(density15 in MIN_DENSITY..MAX_DENSITY, { FuelError.Density15OutOfTable(density15, MIN_DENSITY, MAX_DENSITY) }) {
            "Density at 15 °C would be ${"%.1f".format(density15)} kg/m³, outside the table ($MIN_DENSITY to $MAX_DENSITY)."
        }
        return DensityAt15Result(density15 = density15, group = group, alpha15 = alpha15, vcf = vcf, hydrometerCorrection = hyc)
    }

    /** 60 °F in °C, the reference temperature of API gravity. */
    const val API_REF_TEMP_C = (60.0 - 32.0) * 5.0 / 9.0

    /** Density of water at 60 °F, kg/m³ (API MPMS 11.5). */
    const val WATER_DENSITY_60F = 999.016

    /**
     * Converts an API gravity to a density at 15 °C.
     *
     *   relative density 60/60 °F = 141.5 / (°API + 131.5)
     *   density at 60 °F = relative density × density of water at 60 °F
     *
     * Then the density at 60 °F (15.56 °C) is carried to 15 °C with [densityAt15].
     */
    fun densityFromApiGravity(apiGravity: Double): ApiGravityResult {
        requireFuel(apiGravity.isFinite() && apiGravity > -131.5, { FuelError.ApiNotValid }) {
            "API gravity must be a number above -131.5."
        }
        val relativeDensity = 141.5 / (apiGravity + 131.5)
        val density60F = relativeDensity * WATER_DENSITY_60F
        return ApiGravityResult(
            apiGravity = apiGravity,
            relativeDensity60F = relativeDensity,
            density60F = density60F,
            at15 = densityAt15(density60F, API_REF_TEMP_C),
        )
    }

    private const val MAX_ITERATIONS = 100
    private const val CONVERGENCE = 1e-7
}
