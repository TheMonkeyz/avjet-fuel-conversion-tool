package com.fuelvolume.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fuelvolume.core.ApiGravityResult
import com.fuelvolume.core.DensityAt15Result
import com.fuelvolume.core.DensityPoint
import com.fuelvolume.core.FormulaGroup
import com.fuelvolume.core.FuelCalculator
import com.fuelvolume.core.FuelError
import com.fuelvolume.core.FuelException
import com.fuelvolume.core.FuelInput
import com.fuelvolume.core.FuelResult
import com.fuelvolume.core.Product
import com.fuelvolume.core.VolumeForMassResult
import com.fuelvolume.core.VolumeUnit
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UpliftTheme {
                FuelVolumeScreen()
            }
        }
    }
}

/** Parses "12.5" and "12,5" the same way. Returns null when the text is not a number. */
private fun String.toNumberOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()

/** The screen shows density in kg/L. The core works in kg/m³ (1 kg/L = 1000 kg/m³). */
private fun Double.kgm3ToKgL(): Double = this / 1000.0
private fun Double.kgLToKgm3(): Double = this * 1000.0
/** Density for an input box, in kg/L, with the decimal mark of [locale]. The boxes accept a dot or a comma. */
private fun Double.kgLText(locale: Locale, decimals: Int = 3): String = String.format(locale, "%.${decimals}f", kgm3ToKgL())

/** Locale of the app language, so numbers use the matching decimal mark (9 860,1 in French). */
@Composable
@ReadOnlyComposable
private fun appLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable
@ReadOnlyComposable
private fun Double.format(decimals: Int): String = String.format(appLocale(), "%,.${decimals}f", this)

@Composable
@ReadOnlyComposable
private fun fmt(pattern: String, value: Double): String = String.format(appLocale(), pattern, value)

@Composable
@ReadOnlyComposable
private fun Product.label(): String = stringResource(
    when (this) {
        Product.JET_A1 -> R.string.product_jet_a1
        Product.JET_A -> R.string.product_jet_a
        Product.AVGAS_100LL -> R.string.product_avgas
        Product.OTHER -> R.string.product_other
        Product.CUSTOM -> R.string.product_custom
    }
)

/** Density range of each fuel in its specification, kg/L at 15 °C. Shown as a hint. */
@Composable
@ReadOnlyComposable
private fun Product.densityHint(): String = when (this) {
    Product.JET_A1 -> stringResource(R.string.hint_jet_a1)
    Product.JET_A -> stringResource(R.string.hint_jet_a)
    Product.AVGAS_100LL -> stringResource(R.string.hint_avgas)
    Product.OTHER -> stringResource(R.string.hint_other)
    Product.CUSTOM -> ""
}

@Composable
@ReadOnlyComposable
private fun VolumeUnit.symbol(): String =
    stringResource(if (this == VolumeUnit.LITRE) R.string.unit_l else R.string.unit_us_gal)

@Composable
@ReadOnlyComposable
private fun FormulaGroup.label(): String = stringResource(
    when (this) {
        FormulaGroup.GASOLINE -> R.string.group_gasoline
        FormulaGroup.TRANSITION -> R.string.group_transition
        FormulaGroup.JET -> R.string.group_jet
        FormulaGroup.FUEL_OIL -> R.string.group_fuel_oil
        FormulaGroup.CUSTOM -> R.string.group_custom
    }
)

/** Message for a failed calculation, in the app language. [fallback] is for errors the core did not describe. */
@Composable
@ReadOnlyComposable
private fun Throwable?.fuelMessage(fallback: Int): String {
    val error = (this as? FuelException)?.error ?: return stringResource(fallback)
    return when (error) {
        FuelError.DensityNotNumber -> stringResource(R.string.err_density_nan)
        is FuelError.DensityOutOfRange -> stringResource(R.string.err_density_range, error.min.kgm3ToKgL().format(3), error.max.kgm3ToKgL().format(3))
        is FuelError.TempOutOfRange -> stringResource(R.string.err_temp_range, error.min.format(0), error.max.format(0))
        FuelError.VolumeNegative -> stringResource(R.string.err_volume_neg)
        FuelError.MassNegative -> stringResource(R.string.err_mass_neg)
        FuelError.CoefficientOutOfRange -> stringResource(R.string.err_coef_range)
        FuelError.StepNotPositive -> stringResource(R.string.err_step)
        FuelError.StartAboveEnd -> stringResource(R.string.err_start_end)
        FuelError.DensityNotPositive -> stringResource(R.string.err_density_pos)
        FuelError.ApiNotValid -> stringResource(R.string.err_api)
        is FuelError.Density15OutOfTable -> stringResource(
            R.string.err_density15_table, error.density15.kgm3ToKgL().format(4), error.min.kgm3ToKgL().format(3), error.max.kgm3ToKgL().format(3),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelVolumeScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var product by rememberSaveable { mutableStateOf(Product.JET_A1) }
    var unit by rememberSaveable { mutableStateOf(VolumeUnit.LITRE) }
    val locale = appLocale()
    var densityText by rememberSaveable { mutableStateOf(Product.JET_A1.defaultDensity.kgLText(locale)) }
    var alphaText by rememberSaveable { mutableStateOf(String.format(locale, "%.5f", 0.00093)) }
    var observedTempText by rememberSaveable { mutableStateOf("") }
    var observedVolumeText by rememberSaveable { mutableStateOf("") }
    var massMode by rememberSaveable { mutableStateOf(false) }
    var massInLb by rememberSaveable { mutableStateOf(false) }
    var neededMassText by rememberSaveable { mutableStateOf("") }
    var onBoardMassText by rememberSaveable { mutableStateOf("") }

    val density = densityText.toNumberOrNull()?.kgLToKgm3()
    val alpha = alphaText.toNumberOrNull()
    val observedTemp = observedTempText.toNumberOrNull()
    val bar = barColors()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                actions = { LanguageButton(bar) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = bar.container,
                    titleContentColor = bar.content,
                    actionIconContentColor = bar.content,
                ),
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(
                selectedTabIndex = tab,
                containerColor = bar.container,
                contentColor = bar.content,
                indicator = { positions ->
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(positions[tab]),
                        width = 64.dp,
                        height = 4.dp,
                        color = bar.indicator,
                    )
                },
            ) {
                listOf(R.string.tab_volume, R.string.tab_chart, R.string.tab_density15).forEachIndexed { i, label ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        selectedContentColor = bar.content,
                        unselectedContentColor = bar.contentDim,
                        text = { Text(stringResource(label), fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Medium) },
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (tab == 0) ModeRow(massMode = massMode, onModeChange = { massMode = it })

                if (tab != 2) FuelPicker(
                    product = product,
                    onProductChange = { p ->
                        product = p
                        if (!p.isCustom) densityText = p.defaultDensity.kgLText(locale)
                    },
                )

                if (tab == 2) {
                    DensityTo15Tab(
                        onUseDensity = { d ->
                            densityText = d.kgLText(locale, decimals = 4)
                            if (product.isCustom) product = Product.OTHER
                            tab = 0
                        },
                    )
                } else if (product.isCustom) {
                    NumberField(
                        value = alphaText,
                        onValueChange = { alphaText = it },
                        label = stringResource(R.string.label_alpha),
                        supporting = stringResource(R.string.support_alpha),
                    )
                } else {
                    NumberField(
                        value = densityText,
                        onValueChange = { densityText = it },
                        label = stringResource(R.string.label_density15),
                        supporting = stringResource(R.string.support_density15, product.densityHint()),
                    )
                }

                when (tab) {
                    2 -> Unit
                    0 -> if (massMode) MassToVolumeTab(
                        product = product, unit = unit, onUnitChange = { unit = it },
                        density = density,
                        massInLb = massInLb, onMassInLbChange = { massInLb = it },
                        neededMassText = neededMassText, onNeededMassChange = { neededMassText = it },
                        onBoardMassText = onBoardMassText, onOnBoardMassChange = { onBoardMassText = it },
                        tempText = observedTempText, onTempChange = { observedTempText = it },
                    ) else CalculateTab(
                        product = product, unit = unit, onUnitChange = { unit = it },
                        density = density, alpha = alpha,
                        observedTempText = observedTempText, onObservedTempChange = { observedTempText = it },
                        observedVolumeText = observedVolumeText, onObservedVolumeChange = { observedVolumeText = it },
                    )
                    else -> DensityChartTab(product = product, density = density, highlightTempC = observedTemp)
                }

                Text(
                    text = stringResource(R.string.disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FuelPicker(product: Product, onProductChange: (Product) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = product.label(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.label_fuel)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Product.entries.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.label()) },
                    onClick = {
                        onProductChange(p)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalculateTab(
    product: Product,
    unit: VolumeUnit,
    onUnitChange: (VolumeUnit) -> Unit,
    density: Double?,
    alpha: Double?,
    observedTempText: String,
    onObservedTempChange: (String) -> Unit,
    observedVolumeText: String,
    onObservedVolumeChange: (String) -> Unit,
) {
    val observedTemp = observedTempText.toNumberOrNull()
    val observedVolume = observedVolumeText.toNumberOrNull()

    val coefficientReady = if (product.isCustom) alpha != null else density != null
    val allFilled = coefficientReady && observedTemp != null && observedVolume != null

    val outcome: Result<FuelResult>? = remember(product, unit, density, alpha, observedTemp, observedVolume) {
        if (!allFilled) null else runCatching {
            FuelCalculator.correct(
                FuelInput(
                    product = product,
                    density15 = density ?: 0.0,
                    observedTempC = observedTemp!!,
                    observedVolume = observedVolume!!,
                    customAlpha = alpha ?: 0.0,
                    volumeUnit = unit,
                )
            )
        }
    }

    VolumeUnitRow(unit = unit, onUnitChange = onUnitChange)

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberField(
            value = observedVolumeText,
            onValueChange = onObservedVolumeChange,
            label = stringResource(R.string.label_volume, unit.symbol()),
            supporting = stringResource(R.string.support_meter),
            modifier = Modifier.weight(1f),
            allowNegative = false,
        )
        NumberField(
            value = observedTempText,
            onValueChange = onObservedTempChange,
            label = stringResource(R.string.label_temp),
            supporting = stringResource(R.string.support_at_meter),
            modifier = Modifier.weight(1f),
        )
    }

    ResultCard(outcome = outcome, allFilled = allFilled, unit = unit)
}

/** Volume → mass (what the meter gave) or mass → volume (what to order). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeRow(massMode: Boolean, onModeChange: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        listOf(false to R.string.mode_volume_to_mass, true to R.string.mode_mass_to_volume).forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = massMode == mode,
                onClick = { onModeChange(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                label = { Text(stringResource(label)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeUnitRow(unit: VolumeUnit, onUnitChange: (VolumeUnit) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        VolumeUnit.entries.forEachIndexed { index, u ->
            SegmentedButton(
                selected = unit == u,
                onClick = { onUnitChange(u) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = VolumeUnit.entries.size),
                label = { Text(stringResource(if (u == VolumeUnit.LITRE) R.string.unit_litres else R.string.unit_us_gallons)) },
            )
        }
    }
}

/** How much to order: the pilot knows the mass needed, the fueller needs a volume. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MassToVolumeTab(
    product: Product,
    unit: VolumeUnit,
    onUnitChange: (VolumeUnit) -> Unit,
    density: Double?,
    massInLb: Boolean,
    onMassInLbChange: (Boolean) -> Unit,
    neededMassText: String,
    onNeededMassChange: (String) -> Unit,
    onBoardMassText: String,
    onOnBoardMassChange: (String) -> Unit,
    tempText: String,
    onTempChange: (String) -> Unit,
) {
    if (product.isCustom) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Text(stringResource(R.string.mass_needs_density), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
        }
        return
    }

    val needed = neededMassText.toNumberOrNull()
    val onBoard = if (onBoardMassText.isBlank()) 0.0 else onBoardMassText.toNumberOrNull()
    val temp = tempText.toNumberOrNull()
    val allFilled = density != null && needed != null && onBoard != null && temp != null
    val massUnit = stringResource(if (massInLb) R.string.unit_lb else R.string.unit_kg)
    val toKg = if (massInLb) FuelCalculator.KG_PER_LB else 1.0
    val upliftKg = if (allFilled) (needed!! - onBoard!!) * toKg else null

    val outcome: Result<VolumeForMassResult>? = remember(density, upliftKg, temp, unit) {
        if (upliftKg == null || upliftKg <= 0.0) null else runCatching {
            FuelCalculator.volumeForMass(density!!, upliftKg, temp!!, unit)
        }
    }

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        listOf(false to R.string.unit_kg, true to R.string.unit_lb).forEachIndexed { index, (lb, label) ->
            SegmentedButton(
                selected = massInLb == lb,
                onClick = { onMassInLbChange(lb) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                label = { Text(stringResource(label)) },
            )
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberField(
            value = neededMassText,
            onValueChange = onNeededMassChange,
            label = stringResource(R.string.label_needed, massUnit),
            supporting = stringResource(R.string.support_needed),
            modifier = Modifier.weight(1f),
            allowNegative = false,
        )
        NumberField(
            value = onBoardMassText,
            onValueChange = onOnBoardMassChange,
            label = stringResource(R.string.label_on_board, massUnit),
            supporting = stringResource(R.string.support_on_board),
            modifier = Modifier.weight(1f),
            allowNegative = false,
        )
    }

    VolumeUnitRow(unit = unit, onUnitChange = onUnitChange)

    NumberField(
        value = tempText,
        onValueChange = onTempChange,
        label = stringResource(R.string.label_temp),
        supporting = stringResource(R.string.support_fuel_temp),
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                !allFilled -> Text(stringResource(R.string.prompt_fill_mass), style = MaterialTheme.typography.bodyMedium)
                upliftKg!! <= 0.0 -> Text(stringResource(R.string.no_fuel_needed), style = MaterialTheme.typography.titleMedium)
                outcome == null -> Unit
                outcome.isFailure -> Text(
                    text = outcome.exceptionOrNull().fuelMessage(R.string.err_calc),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> {
                    val r = outcome.getOrThrow()
                    Text(stringResource(R.string.result_order, temp!!.format(1)), style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "${r.volumeAtTemp.format(0)} ${unit.symbol()}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    HorizontalDivider()
                    ResultLine(stringResource(R.string.line_uplift_mass), "${r.massKg.format(0)} kg  /  ${r.massLb.format(0)} lb")
                    ResultLine(stringResource(R.string.line_volume15), "${r.volumeAt15.format(0)} ${unit.symbol()}")
                    ResultLine(stringResource(R.string.line_vcf), r.vcf.format(5))
                    ResultLine(stringResource(R.string.line_density_obs), "${r.densityAtTemp.kgm3ToKgL().format(4)} kg/L")
                    ResultLine(stringResource(R.string.line_table), r.group.label())
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DensityTo15Tab(onUseDensity: (Double) -> Unit) {
    var apiMode by rememberSaveable { mutableStateOf(false) }
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        listOf(false to R.string.density_mode_measured, true to R.string.density_mode_api).forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = apiMode == mode,
                onClick = { apiMode = mode },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                label = { Text(stringResource(label)) },
            )
        }
    }
    if (apiMode) ApiGravitySection(onUseDensity) else MeasuredDensitySection(onUseDensity)
}

/** °API (at 60 °F) to density in kg/L at 15 °C. */
@Composable
private fun ApiGravitySection(onUseDensity: (Double) -> Unit) {
    var apiText by rememberSaveable { mutableStateOf("") }
    val api = apiText.toNumberOrNull()
    val outcome: Result<ApiGravityResult>? = remember(api) {
        if (api == null) null else runCatching { FuelCalculator.densityFromApiGravity(api) }
    }

    NumberField(
        value = apiText,
        onValueChange = { apiText = it },
        label = stringResource(R.string.label_api),
        supporting = stringResource(R.string.support_api),
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                outcome == null -> Text(stringResource(R.string.prompt_api), style = MaterialTheme.typography.bodyMedium)
                outcome.isFailure -> Text(
                    text = outcome.exceptionOrNull().fuelMessage(R.string.err_convert),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> {
                    val r = outcome.getOrThrow()
                    Text(stringResource(R.string.result_density15), style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = fmt("%.4f kg/L", r.at15.density15KgPerLitre),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    HorizontalDivider()
                    ResultLine(stringResource(R.string.line_sg), r.relativeDensity60F.format(4))
                    ResultLine(stringResource(R.string.line_density_60f), "${r.density60F.kgm3ToKgL().format(4)} kg/L")
                    ResultLine(stringResource(R.string.line_table), r.at15.group.label())
                    Button(onClick = { onUseDensity(r.at15.density15) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.btn_use_density))
                    }
                }
            }
        }
    }
}

@Composable
private fun MeasuredDensitySection(onUseDensity: (Double) -> Unit) {
    var measuredDensityText by rememberSaveable { mutableStateOf("") }
    var measuredTempText by rememberSaveable { mutableStateOf("") }
    var glassHydrometer by rememberSaveable { mutableStateOf(false) }

    val measuredDensity = measuredDensityText.toNumberOrNull()?.kgLToKgm3()
    val measuredTemp = measuredTempText.toNumberOrNull()
    val allFilled = measuredDensity != null && measuredTemp != null

    val outcome: Result<DensityAt15Result>? = remember(measuredDensity, measuredTemp, glassHydrometer) {
        if (!allFilled) null else runCatching {
            FuelCalculator.densityAt15(measuredDensity!!, measuredTemp!!, glassHydrometer)
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberField(
            value = measuredDensityText,
            onValueChange = { measuredDensityText = it },
            label = stringResource(R.string.label_density),
            supporting = stringResource(R.string.support_as_read),
            modifier = Modifier.weight(1f),
            allowNegative = false,
        )
        NumberField(
            value = measuredTempText,
            onValueChange = { measuredTempText = it },
            label = stringResource(R.string.label_temp),
            supporting = stringResource(R.string.support_when_measured),
            modifier = Modifier.weight(1f),
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.glass_title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.glass_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = glassHydrometer, onCheckedChange = { glassHydrometer = it })
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                !allFilled -> Text(stringResource(R.string.prompt_density), style = MaterialTheme.typography.bodyMedium)
                outcome == null -> Unit
                outcome.isFailure -> Text(
                    text = outcome.exceptionOrNull().fuelMessage(R.string.err_convert),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> {
                    val r = outcome.getOrThrow()
                    Text(stringResource(R.string.result_density15), style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = fmt("%.4f kg/L", r.density15KgPerLitre),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    HorizontalDivider()
                    ResultLine(stringResource(R.string.line_vcf), r.vcf.format(5))
                    r.hydrometerCorrection?.let { ResultLine(stringResource(R.string.line_hyc), it.format(6)) }
                    ResultLine(stringResource(R.string.line_alpha), fmt("%.7f /°F", r.alpha60))
                    ResultLine(stringResource(R.string.line_table), r.group.label())
                    Button(onClick = { onUseDensity(r.density15) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.btn_use_density))
                    }
                }
            }
        }
    }
}

@Composable
private fun DensityChartTab(product: Product, density: Double?, highlightTempC: Double?) {
    val chart: Result<List<DensityPoint>>? = remember(product, density) {
        if (product.isCustom || density == null) null else runCatching { FuelCalculator.densityChart(density) }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                product.isCustom -> Text(
                    stringResource(R.string.chart_need_density),
                    style = MaterialTheme.typography.bodyMedium,
                )
                chart == null -> Text(stringResource(R.string.chart_prompt), style = MaterialTheme.typography.bodyMedium)
                chart.isFailure -> Text(
                    text = chart.exceptionOrNull().fuelMessage(R.string.chart_err),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> {
                    val rows = chart.getOrThrow()
                    Text(
                        stringResource(R.string.chart_title, product.label(), density!!.kgm3ToKgL().format(4)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.chart_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider()
                    ChartRow("°C", "kg/L", "VCF", header = true, highlighted = false)
                    val nearest = highlightTempC?.let { t -> rows.minByOrNull { abs(it.tempC - t) } }
                    rows.forEach { p ->
                        ChartRow(
                            fmt("%+.0f", p.tempC).replace("+0", "0"),
                            fmt("%.4f", p.densityKgPerLitre),
                            fmt("%.5f", p.vcf),
                            header = false,
                            highlighted = p == nearest,
                        )
                    }
                    if (nearest != null) {
                        Text(
                            stringResource(R.string.chart_marked, highlightTempC.format(1)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartRow(temp: String, kgPerLitre: String, vcf: String, header: Boolean, highlighted: Boolean) {
    val weight = if (header || highlighted) FontWeight.Bold else FontWeight.Normal
    val style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium
    val background = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Text(temp, style = style, fontWeight = weight, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
        Text(kgPerLitre, style = style, fontWeight = weight, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
        Text(vcf, style = style, fontWeight = weight, modifier = Modifier.weight(1.3f), textAlign = TextAlign.End)
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supporting: String,
    modifier: Modifier = Modifier,
    allowNegative: Boolean = true,
) {
    val isError = value.isNotBlank() && value.toNumberOrNull() == null
    OutlinedTextField(
        value = value,
        onValueChange = { text ->
            val allowed = if (allowNegative) "0123456789.,-" else "0123456789.,"
            if (text.all { it in allowed }) onValueChange(text)
        },
        label = { Text(label) },
        supportingText = { Text(if (isError) stringResource(R.string.not_a_number) else supporting) },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun ResultCard(outcome: Result<FuelResult>?, allFilled: Boolean, unit: VolumeUnit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                !allFilled -> Text(stringResource(R.string.prompt_fill), style = MaterialTheme.typography.bodyMedium)
                outcome == null -> Unit
                outcome.isFailure -> Text(
                    text = outcome.exceptionOrNull().fuelMessage(R.string.err_calc),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> {
                    val r = outcome.getOrThrow()
                    Text(stringResource(R.string.result_volume15), style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "${r.volumeAt15.format(1)} ${unit.symbol()}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    HorizontalDivider()
                    ResultLine(stringResource(R.string.line_vcf), r.vcfObserved.format(5))
                    val massKg = r.massKg
                    val massLb = r.massLb
                    if (massKg != null && massLb != null) {
                        ResultLine(stringResource(R.string.line_mass), "${massKg.format(0)} kg  /  ${massLb.format(0)} lb")
                    }
                    r.densityAtObserved?.let { d ->
                        ResultLine(stringResource(R.string.line_density_obs), "${d.kgm3ToKgL().format(4)} kg/L")
                    }
                    r.alpha60?.let { ResultLine(stringResource(R.string.line_alpha), fmt("%.7f /°F", it)) }
                    r.customAlpha?.let { ResultLine(stringResource(R.string.line_alpha_custom), fmt("%.6f /°C", it)) }
                    ResultLine(stringResource(R.string.line_table), r.group.label())
                }
            }
        }
    }
}

@Composable
private fun ResultLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Switches the app between English and French. Shows the language you switch to. */
@Composable
private fun LanguageButton(bar: BarColors) {
    val target = stringResource(R.string.lang_switch_tag)
    OutlinedButton(
        onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(target)) },
        border = BorderStroke(1.5.dp, bar.content),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = bar.content),
        modifier = Modifier.padding(end = 8.dp),
    ) {
        Text(stringResource(R.string.lang_switch), fontWeight = FontWeight.SemiBold)
    }
}
