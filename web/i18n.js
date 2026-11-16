// English and French text, copied from app/src/main/res/values/strings.xml and values-fr/strings.xml.
// French puts a no-break space ( ) before ":" as French typography requires.

const en = {
  app_name: 'Avjet fuel conversion tool',
  lang_switch: 'Français',
  lang_switch_tag: 'fr',

  tab_volume: 'Volume',
  tab_chart: 'Density chart',
  tab_density15: 'Density 15 °C',

  product_jet_a1: 'Jet A-1 / JP-8',
  product_jet_a: 'Jet A',
  product_avgas: 'Avgas 100LL',
  product_other: 'Other fuel (by density)',
  product_custom: 'Custom coefficient',

  hint_jet_a1: 'Spec: 0.775–0.840 kg/L (ASTM D1655, DEF STAN 91-091)',
  hint_jet_a: 'Spec: 0.775–0.840 kg/L (ASTM D1655)',
  hint_avgas: 'Typical: 0.695–0.740 kg/L (ASTM D910)',
  hint_other: 'Table 54B covers 0.653–1.075 kg/L',

  unit_litres: 'Litres',
  unit_us_gallons: 'US gallons',
  unit_l: 'L',
  unit_us_gal: 'US gal',

  label_fuel: 'Fuel',
  label_alpha: 'Expansion coefficient (per °C)',
  support_alpha: 'Example: 0.00093 for Jet A-1, 0.00128 for avgas',
  label_density15: 'Density at 15 °C (kg/L)',
  support_density15: 'From the fuel release note. %1$s',
  label_volume: 'Volume (%1$s)',
  label_temp: 'Temp (°C)',
  support_meter: 'From the meter',
  support_at_meter: 'At the meter',
  label_density: 'Density (kg/L)',
  support_as_read: 'As read on the meter',
  support_when_measured: 'When measured',
  not_a_number: 'Not a number',

  glass_title: 'Glass hydrometer',
  glass_desc: 'On: corrects for the glass expanding (Table 53). Off for a digital density meter.',

  prompt_fill: 'Fill in all the fields to see the result.',
  prompt_density: 'Type the measured density and temperature.',
  err_calc: 'Cannot calculate with these values.',
  err_convert: 'Cannot convert these values.',

  result_volume15: 'Volume corrected to 15 °C',
  result_density15: 'Density corrected to 15 °C',
  line_vcf: 'Correction factor (VCF) to 15 °C',
  line_mass: 'Fuel mass',
  line_density_obs: 'Density at fuel temperature',
  line_hyc: 'Glass hydrometer correction',
  line_alpha: 'Expansion coefficient α15',
  line_table: 'Table used',
  btn_use_density: 'Use this density in the volume calculator',

  chart_need_density: 'The density chart needs a fuel with a density. Pick Jet A-1, Jet A, Avgas or Other fuel.',
  chart_prompt: 'Type the density at 15 °C to see the chart.',
  chart_err: 'Cannot build the chart with this density.',
  chart_title: '%1$s at %2$s kg/L (15 °C)',
  chart_desc: 'Density and volume correction factor (VCF) at each temperature.',
  chart_marked: 'Marked row: closest to your fuel temperature of %1$s °C.',

  group_gasoline: 'Gasoline / avgas (Table 54B)',
  group_transition: 'Transition zone (Table 54B)',
  group_jet: 'Jet fuel / kerosene (Table 54B)',
  group_fuel_oil: 'Fuel oil / diesel (Table 54B)',
  group_custom: 'Custom coefficient',

  err_density_nan: 'Density must be a number.',
  err_density_range: 'Density must be between %1$s and %2$s kg/L.',
  err_temp_range: 'Temperature must be between %1$s and %2$s °C.',
  err_volume_neg: 'Volume must be zero or more.',
  err_coef_range: 'Coefficient must be between 0 and 0.01 per °C.',
  err_step: 'Step must be more than zero.',
  err_start_end: 'Start temperature must not be above the end temperature.',
  err_density_pos: 'Density must be a number above zero.',
  err_density15_table: 'Density at 15 °C would be %1$s kg/L, outside the table (%2$s to %3$s).',

  mode_volume_to_mass: 'Volume → mass',
  mode_mass_to_volume: 'Mass → volume',
  unit_kg: 'kg',
  unit_lb: 'lb',
  label_needed: 'Fuel needed (%1$s)',
  support_needed: 'Total for the flight',
  label_on_board: 'On board (%1$s)',
  support_on_board: 'Empty if none',
  support_fuel_temp: 'Fuel temperature at the meter',
  prompt_fill_mass: 'Type the fuel needed and the fuel temperature.',
  no_fuel_needed: 'No fuel needed. The fuel on board is enough.',
  result_order: 'Volume to order at %1$s °C',
  line_uplift_mass: 'Fuel to add',
  line_volume15: 'Volume at 15 °C',
  mass_needs_density: 'Mass → volume needs a density. Pick Jet A-1, Jet A, Avgas or Other fuel.',
  err_mass_neg: 'Mass must be zero or more.',

  disclaimer:
    'Based on ASTM D1250-80 / API MPMS 11.1 generalized equations (Table 54B, 15 °C base). For guidance only. Use the certified figures from the fuel release note for the load sheet.',
  density_mode_measured: 'Measured density',
  density_mode_api: 'API gravity',
  label_api: 'API gravity (°API)',
  support_api: 'At 60 °F, from the fuel paperwork',
  prompt_api: 'Type the API gravity.',
  line_sg: 'Relative density 60/60 °F',
  line_density_60f: 'Density at 60 °F',
  err_api: 'API gravity must be a number above −131.5.',

  // Web only
  label_mode: 'Calculation',
  label_volume_unit: 'Volume unit',
  label_mass_unit: 'Mass unit',
  label_density_mode: 'Density input',
  chart_col_temp: 'Temperature (°C)',
};

const fr = {
  ...en,
  lang_switch: 'English',
  lang_switch_tag: 'en',

  tab_volume: 'Volume',
  tab_chart: 'Tableau',
  tab_density15: 'Densité 15 °C',

  product_other: 'Autre carburant (selon masse vol.)',
  product_custom: 'Coefficient personnalisé',

  hint_jet_a1: 'Spéc. : 0,775–0,840 kg/L (ASTM D1655, DEF STAN 91-091)',
  hint_jet_a: 'Spéc. : 0,775–0,840 kg/L (ASTM D1655)',
  hint_avgas: 'Typique : 0,695–0,740 kg/L (ASTM D910)',
  hint_other: 'La table 54B couvre 0,653–1,075 kg/L',

  unit_litres: 'Litres',
  unit_us_gallons: 'Gallons US',
  unit_l: 'L',
  unit_us_gal: 'gal US',

  label_fuel: 'Carburant',
  label_alpha: 'Coefficient de dilatation (par °C)',
  support_alpha: 'Exemple : 0,00093 pour Jet A-1, 0,00128 pour l’avgas',
  label_density15: 'Masse volumique à 15 °C (kg/L)',
  support_density15: 'Voir le bon de livraison. %1$s',
  label_volume: 'Volume (%1$s)',
  label_temp: 'Temp. (°C)',
  support_meter: 'Au compteur',
  support_at_meter: 'Au compteur',
  label_density: 'Masse vol. (kg/L)',
  support_as_read: 'Lue sur l’appareil',
  support_when_measured: 'Lors de la mesure',
  not_a_number: 'Nombre non valide',

  glass_title: 'Aréomètre en verre',
  glass_desc: 'Activé : corrige la dilatation du verre (table 53). Désactivé pour un densimètre numérique.',

  prompt_fill: 'Remplissez tous les champs pour voir le résultat.',
  prompt_density: 'Entrez la masse volumique et la température mesurées.',
  err_calc: 'Calcul impossible avec ces valeurs.',
  err_convert: 'Conversion impossible avec ces valeurs.',

  result_volume15: 'Volume corrigé à 15 °C',
  result_density15: 'Masse volumique corrigée à 15 °C',
  line_vcf: 'Facteur de correction (VCF) à 15 °C',
  line_mass: 'Masse de carburant',
  line_density_obs: 'Masse vol. à la température du carburant',
  line_hyc: 'Correction de l’aréomètre',
  line_alpha: 'Coefficient de dilatation α15',
  line_table: 'Table utilisée',
  btn_use_density: 'Utiliser cette valeur pour le calcul du volume',

  chart_need_density: 'Le tableau a besoin d’une masse volumique. Choisissez Jet A-1, Jet A, Avgas ou Autre carburant.',
  chart_prompt: 'Entrez la masse volumique à 15 °C pour voir le tableau.',
  chart_err: 'Impossible de faire le tableau avec cette masse volumique.',
  chart_title: '%1$s à %2$s kg/L (15 °C)',
  chart_desc: 'Masse volumique et facteur de correction de volume (VCF) à chaque température.',
  chart_marked: 'Ligne marquée : la plus proche de votre température de %1$s °C.',

  group_gasoline: 'Essence / avgas (table 54B)',
  group_transition: 'Zone de transition (table 54B)',
  group_jet: 'Carburéacteur / kérosène (table 54B)',
  group_fuel_oil: 'Mazout / diesel (table 54B)',
  group_custom: 'Coefficient personnalisé',

  err_density_nan: 'La masse volumique doit être un nombre.',
  err_density_range: 'La masse volumique doit être entre %1$s et %2$s kg/L.',
  err_temp_range: 'La température doit être entre %1$s et %2$s °C.',
  err_volume_neg: 'Le volume doit être zéro ou plus.',
  err_coef_range: 'Le coefficient doit être entre 0 et 0,01 par °C.',
  err_step: 'Le pas doit être plus grand que zéro.',
  err_start_end: 'La température de départ ne doit pas dépasser celle de fin.',
  err_density_pos: 'La masse volumique doit être un nombre plus grand que zéro.',
  err_density15_table: 'La masse volumique à 15 °C serait de %1$s kg/L, hors table (%2$s à %3$s).',

  mode_volume_to_mass: 'Volume → masse',
  mode_mass_to_volume: 'Masse → volume',
  unit_kg: 'kg',
  unit_lb: 'lb',
  label_needed: 'Requis (%1$s)',
  support_needed: 'Total pour le vol',
  label_on_board: 'À bord (%1$s)',
  support_on_board: 'Vide si aucun',
  support_fuel_temp: 'Température du carburant au compteur',
  prompt_fill_mass: 'Entrez le carburant requis et la température du carburant.',
  no_fuel_needed: 'Aucun ajout requis. Le carburant à bord suffit.',
  result_order: 'Volume à commander à %1$s °C',
  line_uplift_mass: 'Carburant à ajouter',
  line_volume15: 'Volume à 15 °C',
  mass_needs_density: 'Masse → volume a besoin d’une masse volumique. Choisissez Jet A-1, Jet A, Avgas ou Autre carburant.',
  err_mass_neg: 'La masse doit être zéro ou plus.',

  disclaimer:
    'Selon les équations générales ASTM D1250-80 / API MPMS 11.1 (table 54B, base 15 °C). À titre indicatif seulement. Utilisez les valeurs certifiées du bon de livraison pour le devis de masse et centrage.',
  density_mode_measured: 'Masse vol. mesurée',
  density_mode_api: 'Densité API',
  label_api: 'Densité API (°API)',
  support_api: 'À 60 °F, voir les documents du carburant',
  prompt_api: 'Entrez la densité API.',
  line_sg: 'Densité relative 60/60 °F',
  line_density_60f: 'Masse vol. à 60 °F',
  err_api: 'La densité API doit être un nombre supérieur à −131,5.',

  label_mode: 'Calcul',
  label_volume_unit: 'Unité de volume',
  label_mass_unit: 'Unité de masse',
  label_density_mode: 'Entrée de la masse volumique',
  chart_col_temp: 'Température (°C)',
};

export const STRINGS = Object.freeze({ en, fr });
export const LANGUAGES = Object.freeze(['en', 'fr']);

/** "fr" when the browser prefers French, else "en". */
export function defaultLanguage(navigatorLanguages) {
  const list = Array.isArray(navigatorLanguages) ? navigatorLanguages : [navigatorLanguages];
  for (const tag of list) {
    const base = String(tag || '').toLowerCase().split('-')[0];
    if (base === 'fr') return 'fr';
    if (base === 'en') return 'en';
  }
  return 'en';
}

/** Text for key in lang, with Android-style %1$s, %2$s ... placeholders filled from args. */
export function t(lang, key, ...args) {
  const table = STRINGS[lang] || en;
  const text = table[key] ?? en[key] ?? key;
  return text.replace(/%(\d+)\$s/g, (_, n) => String(args[Number(n) - 1] ?? ''));
}

const formatters = new Map();
/**
 * A number with the decimal mark of the language (9 860,1 in French, 9,860.1 in English).
 * grouping=true matches Java's "%,.Nf", grouping=false matches "%.Nf".
 */
export function formatNumber(lang, value, decimals, grouping = true) {
  const key = `${lang}|${decimals}|${grouping}`;
  let f = formatters.get(key);
  if (!f) {
    f = new Intl.NumberFormat(lang === 'fr' ? 'fr-FR' : 'en-US', {
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals,
      useGrouping: grouping,
    });
    formatters.set(key, f);
  }
  // Intl may print "-0"; Java prints "-0" too for tiny negatives, but a plain 0 reads better.
  const s = f.format(value).replace(/−/g, '-');
  return /^-0([.,]0*)?$/.test(s) ? s.slice(1) : s;
}
