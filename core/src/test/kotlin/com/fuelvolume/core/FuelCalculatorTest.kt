package com.fuelvolume.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FuelCalculatorTest {

    private fun assertClose(expected: Double, actual: Double, tolerance: Double, message: String = "") {
        assertTrue(abs(expected - actual) <= tolerance, "$message expected $expected but was $actual (±$tolerance)")
    }

    @Test
    fun `vcf is exactly one at 15 degrees`() {
        val (_, alpha) = FuelCalculator.thermalExpansionCoefficient(800.0)
        assertEquals(1.0, FuelCalculator.volumeCorrectionFactor(alpha, 15.0), 1e-12)
    }

    @Test
    fun `jet A-1 at 800 kg per m3 and 30 C matches Table 54B`() {
        // alpha = 594.5418 / 800² = 0.000929; VCF ≈ 0.9861 in the published table
        val (group, alpha) = FuelCalculator.thermalExpansionCoefficient(800.0)
        assertTrue(group == FormulaGroup.JET)
        assertClose(0.9861, FuelCalculator.volumeCorrectionFactor(alpha, 30.0), 0.0003)
    }

    @Test
    fun `jet fuel at 800 kg per m3 and 0 C expands the volume at 15`() {
        val (_, alpha) = FuelCalculator.thermalExpansionCoefficient(800.0)
        val vcf = FuelCalculator.volumeCorrectionFactor(alpha, 0.0)
        assertTrue(vcf > 1.0)
        assertClose(1.0140, vcf, 0.0005)
    }

    @Test
    fun `avgas at 720 kg per m3 uses the gasoline group`() {
        // alpha = 346.4228/720² + 0.4388/720 = 0.001278; VCF at 25 °C ≈ 0.9873
        val (group, alpha) = FuelCalculator.thermalExpansionCoefficient(720.0)
        assertTrue(group == FormulaGroup.GASOLINE)
        assertClose(0.9873, FuelCalculator.volumeCorrectionFactor(alpha, 25.0), 0.0003)
    }

    @Test
    fun `gasoline at 750 kg per m3 and 25 C matches Table 54B`() {
        // Published Table 54B value is about 0.9880
        val (_, alpha) = FuelCalculator.thermalExpansionCoefficient(750.0)
        assertClose(0.9880, FuelCalculator.volumeCorrectionFactor(alpha, 25.0), 0.0003)
    }

    @Test
    fun `diesel at 840 kg per m3 and 30 C matches Table 54B`() {
        // Published Table 54B value is about 0.9873
        val (group, alpha) = FuelCalculator.thermalExpansionCoefficient(840.0)
        assertTrue(group == FormulaGroup.FUEL_OIL)
        assertClose(0.9873, FuelCalculator.volumeCorrectionFactor(alpha, 30.0), 0.0003)
    }

    @Test
    fun `alpha is continuous across group boundaries`() {
        val (_, gasolineEdge) = FuelCalculator.thermalExpansionCoefficient(770.351)
        val (_, transitionLow) = FuelCalculator.thermalExpansionCoefficient(770.352)
        assertClose(gasolineEdge, transitionLow, 1e-6)

        val (_, transitionHigh) = FuelCalculator.thermalExpansionCoefficient(787.5252)
        val (_, jetEdge) = FuelCalculator.thermalExpansionCoefficient(787.5253)
        assertClose(transitionHigh, jetEdge, 1e-6)
    }

    @Test
    fun `volume at 15 and at a target temperature`() {
        val result = FuelCalculator.correct(
            FuelInput(product = Product.JET_A1, density15 = 800.0, observedTempC = 30.0, observedVolume = 10_000.0, targetTempC = 15.0)
        )
        assertClose(9861.0, result.volumeAt15, 3.0)
        assertEquals(result.volumeAt15, result.volumeAtTarget, 1e-9)
        assertEquals(1.0, result.vcfTarget, 1e-12)
        assertClose(800.0 * result.vcfObserved, result.densityAtObserved!!, 1e-9)
    }

    @Test
    fun `mass in kg and lb from litres`() {
        // 10 000 L of Jet A-1 at 30 °C ≈ 9 861 L at 15 °C ≈ 7 889 kg
        val result = FuelCalculator.correct(
            FuelInput(Product.JET_A1, 800.0, observedTempC = 30.0, observedVolume = 10_000.0)
        )
        assertClose(7889.0, result.massKg!!, 3.0)
        assertClose(result.massKg!! * FuelCalculator.LB_PER_KG, result.massLb!!, 1e-9)
    }

    @Test
    fun `mass from US gallons`() {
        // 1 000 US gal = 3 785.41 L at 15 °C → 3 028 kg of Jet A-1
        val result = FuelCalculator.correct(
            FuelInput(Product.JET_A1, 800.0, observedTempC = 15.0, observedVolume = 1_000.0, volumeUnit = VolumeUnit.US_GALLON)
        )
        assertClose(3028.3, result.massKg!!, 0.1)
        assertEquals(1_000.0, result.volumeAt15, 1e-9)
    }

    @Test
    fun `mass is the same whatever the temperature`() {
        val warm = FuelCalculator.correct(FuelInput(Product.JET_A1, 800.0, observedTempC = 35.0, observedVolume = 1000.0))
        // The same fuel, cooled to -20 °C, takes up less room
        val coldVolume = warm.volumeAt15 / FuelCalculator.volumeCorrectionFactor(warm.alpha15, -20.0)
        val cold = FuelCalculator.correct(FuelInput(Product.JET_A1, 800.0, observedTempC = -20.0, observedVolume = coldVolume))
        assertTrue(coldVolume < 1000.0)
        assertClose(warm.massKg!!, cold.massKg!!, 1e-6)
    }

    @Test
    fun `round trip observed to target and back`() {
        val forward = FuelCalculator.correct(
            FuelInput(Product.AVGAS_100LL, 720.0, observedTempC = 5.0, observedVolume = 1000.0, targetTempC = 35.0)
        )
        val back = FuelCalculator.correct(
            FuelInput(Product.AVGAS_100LL, 720.0, observedTempC = 35.0, observedVolume = forward.volumeAtTarget, targetTempC = 5.0)
        )
        assertClose(1000.0, back.volumeAtTarget, 1e-6)
    }

    @Test
    fun `custom coefficient skips density and mass`() {
        val result = FuelCalculator.correct(
            FuelInput(Product.CUSTOM, density15 = 0.0, observedTempC = 25.0, observedVolume = 100.0, customAlpha = 0.001)
        )
        assertEquals(FormulaGroup.CUSTOM, result.group)
        assertNull(result.densityAtObserved)
        assertNull(result.massKg)
        assertNull(result.massLb)
        assertTrue(result.volumeAt15 < 100.0)
    }

    @Test
    fun `rejects densities outside the table`() {
        assertFailsWith<IllegalArgumentException> { FuelCalculator.thermalExpansionCoefficient(600.0) }
        assertFailsWith<IllegalArgumentException> { FuelCalculator.thermalExpansionCoefficient(1100.0) }
        assertFailsWith<IllegalArgumentException> { FuelCalculator.thermalExpansionCoefficient(Double.NaN) }
    }

    @Test
    fun `rejects temperatures outside the table`() {
        assertFailsWith<IllegalArgumentException> { FuelCalculator.volumeCorrectionFactor(0.001, 200.0) }
        assertFailsWith<IllegalArgumentException> { FuelCalculator.volumeCorrectionFactor(0.001, -60.0) }
    }

    @Test
    fun `density chart has one row per 5 degrees and 15 C is the input density`() {
        val chart = FuelCalculator.densityChart(800.0)
        assertEquals(19, chart.size)
        assertEquals(-40.0, chart.first().tempC)
        assertEquals(50.0, chart.last().tempC)
        val at15 = chart.first { it.tempC == 15.0 }
        assertEquals(800.0, at15.density, 1e-9)
        assertEquals(0.800, at15.densityKgPerLitre, 1e-9)
        assertEquals(1.0, at15.vcf, 1e-12)
    }

    @Test
    fun `density chart gets lighter as the fuel gets warmer`() {
        val chart = FuelCalculator.densityChart(720.0)
        chart.zipWithNext().forEach { (cold, warm) ->
            assertTrue(cold.density > warm.density, "density at ${cold.tempC} should be above ${warm.tempC}")
        }
        // Avgas at 720 kg/m³ and 35 °C is about 701 kg/m³
        assertClose(701.0, chart.first { it.tempC == 35.0 }.density, 1.0)
    }

    @Test
    fun `density chart rejects a bad step or range`() {
        assertFailsWith<IllegalArgumentException> { FuelCalculator.densityChart(800.0, stepC = 0.0) }
        assertFailsWith<IllegalArgumentException> { FuelCalculator.densityChart(800.0, fromC = 20.0, toC = 10.0) }
    }

    @Test
    fun `density at 15 is the input when measured at 15`() {
        val r = FuelCalculator.densityAt15(observedDensity = 800.0, observedTempC = 15.0)
        assertEquals(800.0, r.density15, 1e-9)
        assertEquals(1.0, r.vcf, 1e-12)
        assertNull(r.hydrometerCorrection)
    }

    @Test
    fun `density at 15 reverses the density at temperature for jet fuel`() {
        val (_, alpha) = FuelCalculator.thermalExpansionCoefficient(800.0)
        val densityAt30 = 800.0 * FuelCalculator.volumeCorrectionFactor(alpha, 30.0)
        val r = FuelCalculator.densityAt15(observedDensity = densityAt30, observedTempC = 30.0)
        assertClose(800.0, r.density15, 1e-5)
        assertTrue(r.group == FormulaGroup.JET)
        assertEquals(0.8, r.density15KgPerLitre, 1e-7)
    }

    @Test
    fun `density at 15 reverses the density at temperature for avgas, cold`() {
        val (_, alpha) = FuelCalculator.thermalExpansionCoefficient(720.0)
        val densityAtMinus20 = 720.0 * FuelCalculator.volumeCorrectionFactor(alpha, -20.0)
        val r = FuelCalculator.densityAt15(observedDensity = densityAtMinus20, observedTempC = -20.0)
        assertClose(720.0, r.density15, 1e-5)
        assertTrue(r.group == FormulaGroup.GASOLINE)
    }

    @Test
    fun `density at 15 works across a group boundary`() {
        // 780 kg/m³ at 15 °C is in the transition zone; measured warm it reads below 770
        val (_, alpha) = FuelCalculator.thermalExpansionCoefficient(780.0)
        val densityAt40 = 780.0 * FuelCalculator.volumeCorrectionFactor(alpha, 40.0)
        assertTrue(densityAt40 < 770.352)
        val r = FuelCalculator.densityAt15(observedDensity = densityAt40, observedTempC = 40.0)
        assertClose(780.0, r.density15, 1e-5)
        assertTrue(r.group == FormulaGroup.TRANSITION)
    }

    @Test
    fun `glass hydrometer correction is one at 15 and slightly below one when warm`() {
        assertEquals(1.0, FuelCalculator.hydrometerCorrection(15.0), 1e-12)
        val hyc30 = FuelCalculator.hydrometerCorrection(30.0)
        assertClose(1.0 - 0.000023 * 15 - 0.00000002 * 225, hyc30, 1e-12)
        val plain = FuelCalculator.densityAt15(790.0, 30.0, glassHydrometer = false)
        val glass = FuelCalculator.densityAt15(790.0, 30.0, glassHydrometer = true)
        assertEquals(hyc30, glass.hydrometerCorrection)
        assertTrue(glass.density15 < plain.density15)
        assertClose(plain.density15 * hyc30, glass.density15, 0.01)
    }

    @Test
    fun `density at 15 rejects values outside the table`() {
        assertFailsWith<IllegalArgumentException> { FuelCalculator.densityAt15(500.0, 20.0) }
        assertFailsWith<IllegalArgumentException> { FuelCalculator.densityAt15(800.0, 200.0) }
        assertFailsWith<IllegalArgumentException> { FuelCalculator.densityAt15(-1.0, 20.0) }
        val e = assertFailsWith<FuelException> { FuelCalculator.densityAt15(800.0, 200.0) }
        assertEquals(FuelError.TempOutOfRange(FuelCalculator.MIN_TEMP_C, FuelCalculator.MAX_TEMP_C), e.error)
    }

    @Test
    fun `volume for mass is the reverse of correct`() {
        val forward = FuelCalculator.correct(FuelInput(Product.JET_A1, 800.0, observedTempC = 30.0, observedVolume = 10_000.0))
        val back = FuelCalculator.volumeForMass(800.0, forward.massKg!!, 30.0)
        assertClose(10_000.0, back.volumeAtTemp, 1e-6)
        assertClose(forward.volumeAt15, back.volumeAt15, 1e-6)
        assertEquals(FormulaGroup.JET, back.group)
    }

    @Test
    fun `volume for mass at 15 C is mass over density`() {
        val r = FuelCalculator.volumeForMass(800.0, 7_888.0, 15.0)
        assertClose(9_860.0, r.volumeAtTemp, 1e-9)
        assertClose(9_860.0, r.volumeAt15, 1e-9)
    }

    @Test
    fun `volume for mass in US gallons and pounds`() {
        val litres = FuelCalculator.volumeForMass(800.0, 1_000.0, 25.0)
        val gallons = FuelCalculator.volumeForMass(800.0, 1_000.0, 25.0, VolumeUnit.US_GALLON)
        assertClose(litres.volumeAtTemp / 3.785411784, gallons.volumeAtTemp, 1e-9)
        assertClose(1_000.0, 2_204.62262185 * FuelCalculator.KG_PER_LB, 1e-6)
    }

    @Test
    fun `volume for mass rejects a negative mass`() {
        val e = assertFailsWith<FuelException> { FuelCalculator.volumeForMass(800.0, -1.0, 20.0) }
        assertEquals(FuelError.MassNegative, e.error)
    }

    @Test
    fun `45 API is about 0_8013 kg per litre at 15 C`() {
        // SG = 141.5 / 176.5 = 0.80170; at 60 °F: 0.80170 × 999.016 = 800.91 kg/m³
        val r = FuelCalculator.densityFromApiGravity(45.0)
        assertClose(0.801700, r.relativeDensity60F, 0.000001)
        assertClose(800.91, r.density60F, 0.01)
        // 15 °C is colder than 60 °F, so the fuel is a little denser
        assertTrue(r.at15.density15 > r.density60F)
        assertClose(801.32, r.at15.density15, 0.05)
        assertTrue(r.at15.group == FormulaGroup.JET)
    }

    @Test
    fun `10 API is the density of water`() {
        val r = FuelCalculator.densityFromApiGravity(10.0)
        assertClose(1.0, r.relativeDensity60F, 1e-12)
        assertClose(FuelCalculator.WATER_DENSITY_60F, r.density60F, 1e-9)
    }

    @Test
    fun `API conversion refuses bad values`() {
        val e = assertFailsWith<FuelException> { FuelCalculator.densityFromApiGravity(-140.0) }
        assertTrue(e.error == FuelError.ApiNotValid)
        assertFailsWith<FuelException> { FuelCalculator.densityFromApiGravity(Double.NaN) }
        // 150 °API is about 0.46 kg/L, lighter than Table 54B covers
        val out = assertFailsWith<FuelException> { FuelCalculator.densityFromApiGravity(150.0) }
        assertTrue(out.error is FuelError.Density15OutOfTable)
    }
}
