package com.fuelvolume.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests of the API MPMS 11.1-2004 implementation (the method behind ASTM D1250-25).
 *
 * IMPORTANT: none of these values come from the official standard yet. They check the internal
 * consistency of the method and that it stays close to the 1980 tables. Official test points
 * must be added before the tool is used for sales (see docs/VALIDATION.md, open item O1).
 */
class FuelCalculatorTest {

    private fun assertClose(expected: Double, actual: Double, tolerance: Double, message: String = "") {
        assertTrue(abs(expected - actual) <= tolerance, "$message expected $expected but was $actual (±$tolerance)")
    }

    // --- Constants and building blocks -------------------------------------------------------

    @Test
    fun `60 F on the IPTS-68 scale matches the constant of the standard`() {
        // The ITS-90 → IPTS-68 polynomial and the 60.0068749 constant come from the same scale change.
        assertClose(FuelCalculator.BASE_TEMP_F_IPTS68, FuelCalculator.toIpts68F(FuelCalculator.API_REF_TEMP_C), 2e-7)
        // δ60 is twice the shift at 60 °F.
        assertClose(FuelCalculator.DELTA_60, 2 * (FuelCalculator.BASE_TEMP_F_IPTS68 - 60.0), 1e-7)
    }

    @Test
    fun `scale change is zero at 0 C`() {
        assertEquals(32.0, FuelCalculator.toIpts68F(0.0), 1e-12)
    }

    @Test
    fun `vcf is exactly one at 15 degrees`() {
        for (d in listOf(650.0, 720.0, 780.0, 800.0, 840.0, 1000.0)) {
            assertEquals(1.0, FuelCalculator.volumeCorrectionFactor(d, 15.0), 1e-12, "density $d")
        }
    }

    @Test
    fun `vcf is rounded to 5 decimals`() {
        val vcf = FuelCalculator.volumeCorrectionFactor(800.0, 30.0)
        assertEquals(vcf, FuelCalculator.round5(vcf), 0.0)
    }

    @Test
    fun `density limits at 15 C come from the 60 F limits`() {
        assertClose(611.16, FuelCalculator.MIN_DENSITY, 0.01)
        assertClose(1163.86, FuelCalculator.MAX_DENSITY, 0.01)
    }

    // --- Groups -------------------------------------------------------------------------------

    @Test
    fun `groups are chosen from the density at 60 F`() {
        assertEquals(FormulaGroup.GASOLINE, FuelCalculator.coefficients(720.0).group)
        assertEquals(FormulaGroup.TRANSITION, FuelCalculator.coefficients(780.0).group)
        assertEquals(FormulaGroup.JET, FuelCalculator.coefficients(800.0).group)
        assertEquals(FormulaGroup.FUEL_OIL, FuelCalculator.coefficients(845.0).group)
        // ρ60 is a little below ρ15
        val c = FuelCalculator.coefficients(800.0)
        assertTrue(c.density60 < 800.0)
        assertClose(800.0, c.density60, 0.6)
    }

    @Test
    fun `alpha is continuous across group boundaries`() {
        fun alphaAt60(rho60: Double): Double {
            // find ρ15 whose ρ60 is rho60 (ρ15 ≈ ρ60 · 1.0005)
            var rho15 = rho60
            repeat(50) { rho15 += rho60 - FuelCalculator.coefficients(rho15).density60 }
            return FuelCalculator.coefficients(rho15).alpha60
        }
        assertClose(alphaAt60(770.3519), alphaAt60(770.3521), 2e-8)
        assertClose(alphaAt60(787.5194), alphaAt60(787.5196), 2e-8)
        assertClose(alphaAt60(838.3126), alphaAt60(838.3128), 2e-8)
    }

    // --- Close to the 1980 tables -------------------------------------------------------------
    // The 2004 method replaced the 1980 one; for refined products the results stay very close.

    @Test
    fun `stays within 0_0002 of the 1980 Table 54B equations`() {
        val cases = listOf(
            Triple(800.0, 30.0, 0.986009), // 1980 equations, jet
            Triple(800.0, 0.0, 1.013875),
            Triple(720.0, 25.0, 0.987175), // gasoline
            Triple(750.0, 25.0, 0.987949),
            Triple(840.0, 30.0, 0.987296), // fuel oil
        )
        for ((d, t, vcf1980) in cases) {
            assertClose(vcf1980, FuelCalculator.volumeCorrectionFactor(d, t), 0.0002, "ρ15 $d at $t °C")
        }
    }

    // --- Volume and mass ----------------------------------------------------------------------

    @Test
    fun `volume at 15 and at a target temperature`() {
        val result = FuelCalculator.correct(
            FuelInput(product = Product.JET_A1, density15 = 800.0, observedTempC = 30.0, observedVolume = 10_000.0, targetTempC = 15.0)
        )
        assertClose(9860.0, result.volumeAt15, 3.0)
        assertEquals(result.volumeAt15, result.volumeAtTarget, 1e-9)
        assertEquals(1.0, result.vcfTarget, 1e-12)
        assertEquals(10_000.0 * result.vcfObserved, result.volumeAt15, 1e-9)
        assertTrue(result.alpha60 != null && result.customAlpha == null)
    }

    @Test
    fun `mass in kg and lb from litres`() {
        val result = FuelCalculator.correct(FuelInput(Product.JET_A1, 800.0, observedTempC = 30.0, observedVolume = 10_000.0))
        assertEquals(result.volumeAt15 * 0.8, result.massKg!!, 1e-9)
        assertClose(result.massKg!! * FuelCalculator.LB_PER_KG, result.massLb!!, 1e-9)
    }

    @Test
    fun `mass from US gallons`() {
        // 1 000 US gal = 3 785.41 L at 15 °C → 3 028.3 kg of Jet A-1
        val result = FuelCalculator.correct(
            FuelInput(Product.JET_A1, 800.0, observedTempC = 15.0, observedVolume = 1_000.0, volumeUnit = VolumeUnit.US_GALLON)
        )
        assertClose(3028.33, result.massKg!!, 0.01)
        assertEquals(1_000.0, result.volumeAt15, 1e-9)
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
        assertNull(result.alpha60)
        assertEquals(0.001, result.customAlpha)
        // exp(−0.001·10·(1 + 0.8·0.001·10)) = exp(−0.01008) = 0.98997
        assertEquals(0.98997, result.vcfObserved, 1e-12)
    }

    @Test
    fun `rejects densities outside the table`() {
        assertFailsWith<IllegalArgumentException> { FuelCalculator.coefficients(600.0) }
        assertFailsWith<IllegalArgumentException> { FuelCalculator.coefficients(1200.0) }
        val e = assertFailsWith<FuelException> { FuelCalculator.coefficients(Double.NaN) }
        assertEquals(FuelError.DensityNotNumber, e.error)
    }

    @Test
    fun `rejects temperatures outside the table`() {
        assertFailsWith<IllegalArgumentException> { FuelCalculator.volumeCorrectionFactor(800.0, 200.0) }
        assertFailsWith<IllegalArgumentException> { FuelCalculator.volumeCorrectionFactor(800.0, -60.0) }
        assertFailsWith<IllegalArgumentException> { FuelCalculator.customVolumeCorrectionFactor(0.001, 151.0) }
    }

    // --- Density chart ------------------------------------------------------------------------

    @Test
    fun `density chart has one row per 5 degrees and 15 C is the input density`() {
        val chart = FuelCalculator.densityChart(800.0)
        assertEquals(19, chart.size)
        assertEquals(-40.0, chart.first().tempC)
        assertEquals(50.0, chart.last().tempC)
        val at15 = chart.first { it.tempC == 15.0 }
        assertEquals(800.0, at15.density, 1e-9)
        assertEquals(1.0, at15.vcf, 1e-12)
    }

    @Test
    fun `density chart gets lighter as the fuel gets warmer`() {
        val chart = FuelCalculator.densityChart(720.0)
        chart.zipWithNext().forEach { (cold, warm) -> assertTrue(cold.density > warm.density) }
        assertClose(701.0, chart.first { it.tempC == 35.0 }.density, 1.0)
    }

    @Test
    fun `density chart rejects a bad step or range`() {
        assertFailsWith<IllegalArgumentException> { FuelCalculator.densityChart(800.0, stepC = 0.0) }
        assertFailsWith<IllegalArgumentException> { FuelCalculator.densityChart(800.0, fromC = 20.0, toC = 10.0) }
    }

    // --- Density at 15 °C ---------------------------------------------------------------------

    @Test
    fun `density at 15 is the input when measured at 15`() {
        val r = FuelCalculator.densityAt15(observedDensity = 800.0, observedTempC = 15.0)
        assertClose(800.0, r.density15, 1e-6)
        assertEquals(1.0, r.vcf, 1e-12)
        assertNull(r.hydrometerCorrection)
    }

    @Test
    fun `density at 15 reverses the density chart in every group`() {
        for ((d15, t) in listOf(800.0 to 30.0, 720.0 to -20.0, 780.0 to 40.0, 845.0 to -35.0, 650.0 to 45.0)) {
            val atT = FuelCalculator.densityChart(d15, fromC = t, toC = t).single().density
            val r = FuelCalculator.densityAt15(atT, t)
            assertClose(d15, r.density15, 1e-6, "ρ15 $d15 at $t °C")
        }
    }

    @Test
    fun `density at 15 works across a group boundary`() {
        // 780 kg/m³ at 15 °C is in the transition zone; measured at 40 °C it reads below 770
        val at40 = FuelCalculator.densityChart(780.0, fromC = 40.0, toC = 40.0).single().density
        assertTrue(at40 < 770.352)
        val r = FuelCalculator.densityAt15(at40, 40.0)
        assertEquals(FormulaGroup.TRANSITION, r.group)
    }

    @Test
    fun `glass hydrometer correction is one at 15 and slightly below one when warm`() {
        assertEquals(1.0, FuelCalculator.hydrometerCorrection(15.0), 1e-12)
        val hyc30 = FuelCalculator.hydrometerCorrection(30.0)
        assertClose(1.0 - 0.000023 * 15 - 0.00000002 * 225, hyc30, 1e-12)
        val plain = FuelCalculator.densityAt15(790.0, 30.0, glassHydrometer = false)
        val glass = FuelCalculator.densityAt15(790.0, 30.0, glassHydrometer = true)
        assertEquals(hyc30, glass.hydrometerCorrection)
        assertClose(plain.density15 * hyc30, glass.density15, 0.01)
    }

    @Test
    fun `density at 15 rejects values outside the table`() {
        val low = assertFailsWith<FuelException> { FuelCalculator.densityAt15(500.0, 20.0) }
        assertTrue(low.error is FuelError.Density15OutOfTable)
        assertFailsWith<IllegalArgumentException> { FuelCalculator.densityAt15(-1.0, 20.0) }
        val e = assertFailsWith<FuelException> { FuelCalculator.densityAt15(800.0, 200.0) }
        assertEquals(FuelError.TempOutOfRange(FuelCalculator.MIN_TEMP_C, FuelCalculator.MAX_TEMP_C), e.error)
    }

    // --- Mass to volume -----------------------------------------------------------------------

    @Test
    fun `volume for mass is the reverse of correct`() {
        val forward = FuelCalculator.correct(FuelInput(Product.JET_A1, 800.0, observedTempC = 30.0, observedVolume = 10_000.0))
        val back = FuelCalculator.volumeForMass(800.0, forward.massKg!!, 30.0)
        assertClose(10_000.0, back.volumeAtTemp, 1e-6)
        assertEquals(FormulaGroup.JET, back.group)
    }

    @Test
    fun `volume for mass at 15 C is mass over density`() {
        val r = FuelCalculator.volumeForMass(800.0, 7_888.0, 15.0)
        assertClose(9_860.0, r.volumeAtTemp, 1e-9)
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

    // --- API gravity --------------------------------------------------------------------------

    @Test
    fun `45 API is about 0_8013 kg per litre at 15 C`() {
        // SG = 141.5 / 176.5 = 0.80170; at 60 °F: 0.80170 × 999.016 = 800.91 kg/m³
        val r = FuelCalculator.densityFromApiGravity(45.0)
        assertClose(0.801700, r.relativeDensity60F, 0.000001)
        assertClose(800.91, r.density60F, 0.01)
        assertTrue(r.at15.density15 > r.density60F)
        assertClose(801.32, r.at15.density15, 0.05)
        assertEquals(FormulaGroup.JET, r.at15.group)
        // the API route and the density route agree
        assertClose(r.density60F, FuelCalculator.coefficients(r.at15.density15).density60, 1e-6)
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
        assertEquals(FuelError.ApiNotValid, e.error)
        assertFailsWith<FuelException> { FuelCalculator.densityFromApiGravity(Double.NaN) }
        // 150 °API is about 0.46 kg/L, lighter than the tables cover
        val out = assertFailsWith<FuelException> { FuelCalculator.densityFromApiGravity(150.0) }
        assertTrue(out.error is FuelError.Density15OutOfTable)
    }

    // --- Same numbers as the web app ----------------------------------------------------------

    @Test
    fun `matches the shared reference grid (also checked by the web tests)`() {
        // src/test/resources/vcf-grid.csv, made by web/tools/make-grid.mjs from web/calc.js.
        val rows = javaClass.getResource("/vcf-grid.csv")!!.readText().trim().lines().drop(1)
        assertTrue(rows.size > 3000)
        for (row in rows) {
            val (d, t, group, d60, vcf) = row.split(",")
            val c = FuelCalculator.coefficients(d.toDouble())
            assertEquals(group, c.group.name, row)
            assertClose(d60.toDouble(), c.density60, 5e-7, row)
            assertEquals(vcf, "%.5f".format(java.util.Locale.ROOT, FuelCalculator.volumeCorrectionFactor(d.toDouble(), t.toDouble())), row)
        }
    }
}
