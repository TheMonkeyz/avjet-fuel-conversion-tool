// UI of the Avjet fuel conversion tool. Mirrors app/src/main/java/com/fuelvolume/app/MainActivity.kt.
import {
  Product,
  PRODUCTS,
  VolumeUnit,
  FormulaGroup,
  FuelError,
  ErrorCode,
  KG_PER_LB,
  correct,
  volumeForMass,
  densityChart,
  densityAt15,
  densityFromApiGravity,
  parseNumber,
} from './calc.js';
import { t as tr, formatNumber, defaultLanguage, LANGUAGES } from './i18n.js';

const STATE_KEY = 'avjet-fuel-state-v1';
const LANG_KEY = 'avjet-fuel-lang';

// ---------- storage ----------

function loadJson(key) {
  try {
    return JSON.parse(localStorage.getItem(key) || 'null');
  } catch {
    return null;
  }
}
function save(key, value) {
  try {
    localStorage.setItem(key, typeof value === 'string' ? value : JSON.stringify(value));
  } catch {
    /* private mode or full: the app still works, it just will not remember */
  }
}

let lang = (() => {
  const stored = (() => {
    try {
      return localStorage.getItem(LANG_KEY);
    } catch {
      return null;
    }
  })();
  if (LANGUAGES.includes(stored)) return stored;
  return defaultLanguage(navigator.languages && navigator.languages.length ? navigator.languages : [navigator.language]);
})();

const t = (key, ...args) => tr(lang, key, ...args);
/** Like Kotlin Double.format(decimals): grouped, with the language's decimal mark. */
const fmtG = (v, d) => formatNumber(lang, v, d, true);
/** Like String.format("%.Nf"): no grouping. */
const fmtP = (v, d) => formatNumber(lang, v, d, false);
const kgLText = (kgm3, decimals = 3) => fmtP(kgm3 / 1000, decimals);

function defaultState() {
  return {
    tab: 0,
    product: Product.JET_A1.id,
    unit: VolumeUnit.LITRE.id,
    densityText: kgLText(Product.JET_A1.defaultDensity),
    densityAuto: true, // filled by the app, not typed: follows the language's decimal mark
    alphaText: fmtP(0.00093, 5),
    alphaAuto: true,
    observedTempText: '',
    observedVolumeText: '',
    massMode: false,
    massInLb: false,
    neededMassText: '',
    onBoardMassText: '',
    apiMode: false,
    apiText: '',
    measuredDensityText: '',
    measuredTempText: '',
    glassHydrometer: false,
  };
}

const state = (() => {
  const base = defaultState();
  const saved = loadJson(STATE_KEY);
  if (saved && typeof saved === 'object') {
    for (const k of Object.keys(base)) {
      if (typeof saved[k] === typeof base[k]) base[k] = saved[k];
    }
  }
  if (!Product[base.product]) base.product = Product.JET_A1.id;
  if (!VolumeUnit[base.unit]) base.unit = VolumeUnit.LITRE.id;
  if (![0, 1, 2].includes(base.tab)) base.tab = 0;
  return base;
})();

const persist = () => save(STATE_KEY, state);

// ---------- helpers ----------

const $ = (id) => document.getElementById(id);
const esc = (s) =>
  String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

const product = () => Product[state.product];
const unit = () => VolumeUnit[state.unit];
const unitSymbol = (u) => t(u === VolumeUnit.LITRE ? 'unit_l' : 'unit_us_gal');

const PRODUCT_KEYS = {
  JET_A1: 'product_jet_a1',
  JET_A: 'product_jet_a',
  AVGAS_100LL: 'product_avgas',
  OTHER: 'product_other',
  CUSTOM: 'product_custom',
};
const HINT_KEYS = { JET_A1: 'hint_jet_a1', JET_A: 'hint_jet_a', AVGAS_100LL: 'hint_avgas', OTHER: 'hint_other' };
const GROUP_KEYS = {
  [FormulaGroup.GASOLINE]: 'group_gasoline',
  [FormulaGroup.TRANSITION]: 'group_transition',
  [FormulaGroup.JET]: 'group_jet',
  [FormulaGroup.FUEL_OIL]: 'group_fuel_oil',
  [FormulaGroup.CUSTOM]: 'group_custom',
};
const productLabel = (p) => t(PRODUCT_KEYS[p.id]);
const groupLabel = (g) => t(GROUP_KEYS[g]);

/** Message for a failed calculation, in the app language. fallbackKey is for errors the core did not describe. */
function fuelMessage(err, fallbackKey) {
  if (!(err instanceof FuelError)) return t(fallbackKey);
  const p = err.params;
  switch (err.code) {
    case ErrorCode.DENSITY_NOT_NUMBER: return t('err_density_nan');
    case ErrorCode.DENSITY_OUT_OF_RANGE: return t('err_density_range', fmtG(p.min / 1000, 3), fmtG(p.max / 1000, 3));
    case ErrorCode.TEMP_OUT_OF_RANGE: return t('err_temp_range', fmtG(p.min, 0), fmtG(p.max, 0));
    case ErrorCode.VOLUME_NEGATIVE: return t('err_volume_neg');
    case ErrorCode.MASS_NEGATIVE: return t('err_mass_neg');
    case ErrorCode.COEFFICIENT_OUT_OF_RANGE: return t('err_coef_range');
    case ErrorCode.STEP_NOT_POSITIVE: return t('err_step');
    case ErrorCode.START_ABOVE_END: return t('err_start_end');
    case ErrorCode.DENSITY_NOT_POSITIVE: return t('err_density_pos');
    case ErrorCode.API_NOT_VALID: return t('err_api');
    case ErrorCode.DENSITY15_OUT_OF_TABLE:
      return t('err_density15_table', fmtG(p.density15 / 1000, 4), fmtG(p.min / 1000, 3), fmtG(p.max / 1000, 3));
    default: return t(fallbackKey);
  }
}

function attempt(fn) {
  try {
    return { ok: true, value: fn() };
  } catch (error) {
    return { ok: false, error };
  }
}

const line = (label, value) =>
  `<div class="line"><span>${esc(label)}</span><strong>${esc(value)}</strong></div>`;
const message = (text, cls = 'msg') => `<p class="${cls}">${esc(text)}</p>`;
const errorMsg = (text) => `<p class="error" role="alert">${esc(text)}</p>`;
const headline = (label, value) => `<p class="result-label">${esc(label)}</p><p class="result-value">${esc(value)}</p><hr>`;
const useButton = (kgm3) =>
  `<button type="button" class="primary" data-use-density="${kgm3}">${esc(t('btn_use_density'))}</button>`;

/** Sets innerHTML only when it changed, so screen readers do not re-announce the same result. */
function setHtml(el, html) {
  if (el.dataset.html !== html) {
    el.innerHTML = html;
    el.dataset.html = html;
  }
}

// ---------- number fields ----------

/** id → { key in state, support text key, auto flag } */
const FIELDS = {
  'in-alpha': { key: 'alphaText', support: () => t('support_alpha'), auto: 'alphaAuto' },
  'in-density': {
    key: 'densityText',
    support: () => t('support_density15', HINT_KEYS[product().id] ? t(HINT_KEYS[product().id]) : ''),
    auto: 'densityAuto',
  },
  'in-volume': { key: 'observedVolumeText', support: () => t('support_meter') },
  'in-temp-calc': { key: 'observedTempText', support: () => t('support_at_meter') },
  'in-needed': { key: 'neededMassText', support: () => t('support_needed') },
  'in-onboard': { key: 'onBoardMassText', support: () => t('support_on_board') },
  'in-temp-mass': { key: 'observedTempText', support: () => t('support_fuel_temp') },
  'in-mdensity': { key: 'measuredDensityText', support: () => t('support_as_read') },
  'in-mtemp': { key: 'measuredTempText', support: () => t('support_when_measured') },
  'in-api': { key: 'apiText', support: () => t('support_api') },
};

function renderField(id) {
  const f = FIELDS[id];
  const input = $(id);
  const value = state[f.key];
  if (input.value !== value) input.value = value;
  const isError = value.trim() !== '' && parseNumber(value) === null;
  input.setAttribute('aria-invalid', String(isError));
  const sup = $(id.replace('in-', 'sup-'));
  sup.textContent = isError ? t('not_a_number') : f.support();
  sup.classList.toggle('error-text', isError);
}

function bindField(id) {
  const f = FIELDS[id];
  const input = $(id);
  const allowed = input.hasAttribute('data-positive') ? /^[0-9.,]*$/ : /^[0-9.,-]*$/;
  input.addEventListener('input', () => {
    // Same filter as the Android field: only digits, "." and "," (and "-" where negatives make sense).
    const text = input.value.replace(/\s+/g, '');
    if (!allowed.test(text)) {
      const pos = Math.max(0, (input.selectionStart ?? 1) - 1);
      input.value = state[f.key];
      try {
        input.setSelectionRange(pos, pos);
      } catch {
        /* ignore */
      }
      return;
    }
    if (text !== input.value) input.value = text;
    state[f.key] = text;
    if (f.auto) state[f.auto] = false;
    update();
  });
}

// ---------- sections ----------

function renderCalc() {
  const p = product();
  const u = unit();
  const density = parseNumber(state.densityText);
  const densityKgm3 = density === null ? null : density * 1000;
  const alpha = parseNumber(state.alphaText);
  const temp = parseNumber(state.observedTempText);
  const volume = parseNumber(state.observedVolumeText);
  const coefficientReady = p.isCustom ? alpha !== null : densityKgm3 !== null;
  const allFilled = coefficientReady && temp !== null && volume !== null;

  $('lbl-volume').textContent = t('label_volume', unitSymbol(u));

  let html;
  if (!allFilled) html = message(t('prompt_fill'));
  else {
    const out = attempt(() =>
      correct({
        product: p,
        density15: densityKgm3 ?? 0,
        observedTempC: temp,
        observedVolume: volume,
        customAlpha: alpha ?? 0,
        volumeUnit: u,
      }),
    );
    if (!out.ok) html = errorMsg(fuelMessage(out.error, 'err_calc'));
    else {
      const r = out.value;
      html = headline(t('result_volume15'), `${fmtG(r.volumeAt15, 1)} ${unitSymbol(u)}`);
      html += line(t('line_vcf'), fmtG(r.vcfObserved, 5));
      if (r.massKg !== null && r.massLb !== null) {
        html += line(t('line_mass'), `${fmtG(r.massKg, 0)} kg  /  ${fmtG(r.massLb, 0)} lb`);
      }
      if (r.densityAtObserved !== null) html += line(t('line_density_obs'), `${fmtG(r.densityAtObserved / 1000, 4)} kg/L`);
      html += line(t('line_alpha'), `${fmtP(r.alpha15, 6)} /°C`);
      html += line(t('line_table'), groupLabel(r.group));
    }
  }
  setHtml($('card-calc'), html);
}

function renderMass() {
  const p = product();
  const custom = p.isCustom;
  $('card-mass-custom').hidden = !custom;
  $('mass-body').hidden = custom;
  if (custom) {
    setHtml($('card-mass-custom'), message(t('mass_needs_density')));
    return;
  }
  const u = unit();
  const density = parseNumber(state.densityText);
  const densityKgm3 = density === null ? null : density * 1000;
  const needed = parseNumber(state.neededMassText);
  const onBoard = state.onBoardMassText.trim() === '' ? 0 : parseNumber(state.onBoardMassText);
  const temp = parseNumber(state.observedTempText);
  const allFilled = densityKgm3 !== null && needed !== null && onBoard !== null && temp !== null;
  const massUnit = t(state.massInLb ? 'unit_lb' : 'unit_kg');
  const toKg = state.massInLb ? KG_PER_LB : 1.0;
  const upliftKg = allFilled ? (needed - onBoard) * toKg : null;

  $('lbl-needed').textContent = t('label_needed', massUnit);
  $('lbl-onboard').textContent = t('label_on_board', massUnit);

  let html;
  if (!allFilled) html = message(t('prompt_fill_mass'));
  else if (upliftKg <= 0) html = message(t('no_fuel_needed'), 'msg title');
  else {
    const out = attempt(() => volumeForMass(densityKgm3, upliftKg, temp, u));
    if (!out.ok) html = errorMsg(fuelMessage(out.error, 'err_calc'));
    else {
      const r = out.value;
      html = headline(t('result_order', fmtG(temp, 1)), `${fmtG(r.volumeAtTemp, 0)} ${unitSymbol(u)}`);
      html += line(t('line_uplift_mass'), `${fmtG(r.massKg, 0)} kg  /  ${fmtG(r.massLb, 0)} lb`);
      html += line(t('line_volume15'), `${fmtG(r.volumeAt15, 0)} ${unitSymbol(u)}`);
      html += line(t('line_vcf'), fmtG(r.vcf, 5));
      html += line(t('line_density_obs'), `${fmtG(r.densityAtTemp / 1000, 4)} kg/L`);
      html += line(t('line_table'), groupLabel(r.group));
    }
  }
  setHtml($('card-mass'), html);
}

function renderChart() {
  const p = product();
  const density = parseNumber(state.densityText);
  const densityKgm3 = density === null ? null : density * 1000;
  const highlight = parseNumber(state.observedTempText);
  let html;
  if (p.isCustom) html = message(t('chart_need_density'));
  else if (densityKgm3 === null) html = message(t('chart_prompt'));
  else {
    const out = attempt(() => densityChart(densityKgm3));
    if (!out.ok) html = errorMsg(fuelMessage(out.error, 'chart_err'));
    else {
      const rows = out.value;
      let nearest = null;
      if (highlight !== null) {
        for (const r of rows) if (nearest === null || Math.abs(r.tempC - highlight) < Math.abs(nearest.tempC - highlight)) nearest = r;
      }
      html = `<h2 class="chart-title">${esc(t('chart_title', productLabel(p), fmtG(densityKgm3 / 1000, 4)))}</h2>`;
      html += `<p class="support" id="chart-desc">${esc(t('chart_desc'))}</p><hr>`;
      html += `<table class="chart" aria-describedby="chart-desc"><thead><tr>`;
      html += `<th scope="col"><span aria-hidden="true">°C</span><span class="sr-only">${esc(t('chart_col_temp'))}</span></th><th scope="col">kg/L</th><th scope="col">VCF</th>`;
      html += `</tr></thead><tbody>`;
      for (const r of rows) {
        const temp = (r.tempC > 0 ? '+' : '') + fmtP(r.tempC, 0);
        const marked = r === nearest;
        html += `<tr${marked ? ' class="marked"' : ''}><th scope="row">${esc(temp)}</th><td>${esc(fmtP(r.densityKgPerLitre, 4))}</td><td>${esc(fmtP(r.vcf, 5))}</td></tr>`;
      }
      html += `</tbody></table>`;
      if (nearest !== null) html += `<p class="support">${esc(t('chart_marked', fmtG(highlight, 1)))}</p>`;
    }
  }
  setHtml($('card-chart'), html);
}

function renderMeasured() {
  const d = parseNumber(state.measuredDensityText);
  const temp = parseNumber(state.measuredTempText);
  let html;
  if (d === null || temp === null) html = message(t('prompt_density'));
  else {
    const out = attempt(() => densityAt15(d * 1000, temp, state.glassHydrometer));
    if (!out.ok) html = errorMsg(fuelMessage(out.error, 'err_convert'));
    else {
      const r = out.value;
      html = headline(t('result_density15'), `${fmtP(r.density15KgPerLitre, 4)} kg/L`);
      html += line(t('line_vcf'), fmtG(r.vcf, 5));
      if (r.hydrometerCorrection !== null) html += line(t('line_hyc'), fmtG(r.hydrometerCorrection, 6));
      html += line(t('line_alpha'), `${fmtP(r.alpha15, 6)} /°C`);
      html += line(t('line_table'), groupLabel(r.group));
      html += useButton(r.density15);
    }
  }
  setHtml($('card-measured'), html);
}

function renderApi() {
  const api = parseNumber(state.apiText);
  let html;
  if (api === null) html = message(t('prompt_api'));
  else {
    const out = attempt(() => densityFromApiGravity(api));
    if (!out.ok) html = errorMsg(fuelMessage(out.error, 'err_convert'));
    else {
      const r = out.value;
      html = headline(t('result_density15'), `${fmtP(r.at15.density15KgPerLitre, 4)} kg/L`);
      html += line(t('line_sg'), fmtG(r.relativeDensity60F, 4));
      html += line(t('line_density_60f'), `${fmtG(r.density60F / 1000, 4)} kg/L`);
      html += line(t('line_table'), groupLabel(r.at15.group));
      html += useButton(r.at15.density15);
    }
  }
  setHtml($('card-api'), html);
}

function renderStaticText() {
  document.documentElement.lang = lang;
  document.title = t('app_name');
  $('app-title').textContent = t('app_name');
  for (const el of document.querySelectorAll('[data-i18n]')) el.textContent = t(el.dataset.i18n);
  const btn = $('lang-btn');
  btn.textContent = t('lang_switch');
  btn.lang = t('lang_switch_tag');
  ['tab_volume', 'tab_chart', 'tab_density15'].forEach((k, i) => {
    $(`tab-${i}`).textContent = t(k);
  });
  const select = $('in-product');
  if (select.options.length !== PRODUCTS.length) {
    select.innerHTML = PRODUCTS.map((p) => `<option value="${p.id}"></option>`).join('');
  }
  PRODUCTS.forEach((p, i) => {
    select.options[i].textContent = productLabel(p);
  });
}

function setRadio(name, value) {
  for (const r of document.querySelectorAll(`input[name="${name}"]`)) r.checked = r.value === value;
}

function update() {
  persist();
  render();
}

function render() {
  const tab = state.tab;
  const p = product();

  // Tabs
  for (let i = 0; i < 3; i++) {
    const b = $(`tab-${i}`);
    b.setAttribute('aria-selected', String(i === tab));
    b.tabIndex = i === tab ? 0 : -1;
  }
  $('panel').setAttribute('aria-labelledby', `tab-${tab}`);

  // Visibility, as in FuelVolumeScreen()
  $('sec-mode').hidden = tab !== 0;
  $('sec-fuel').hidden = tab === 2;
  $('f-alpha').hidden = tab === 2 || !p.isCustom;
  $('f-density').hidden = tab === 2 || p.isCustom;
  $('sec-calc').hidden = !(tab === 0 && !state.massMode);
  $('sec-mass').hidden = !(tab === 0 && state.massMode);
  $('sec-chart').hidden = tab !== 1;
  $('sec-d15').hidden = tab !== 2;
  $('d15-measured').hidden = state.apiMode;
  $('d15-api').hidden = !state.apiMode;

  // Controls
  $('in-product').value = p.id;
  setRadio('mode', state.massMode ? 'mass' : 'volume');
  setRadio('unit-calc', state.unit);
  setRadio('unit-mass', state.unit);
  setRadio('mass-unit', state.massInLb ? 'lb' : 'kg');
  setRadio('d15-mode', state.apiMode ? 'api' : 'measured');
  $('in-glass').checked = state.glassHydrometer;
  for (const id of Object.keys(FIELDS)) renderField(id);

  // Results: only the visible ones
  if (!$('sec-calc').hidden) renderCalc();
  if (!$('sec-mass').hidden) renderMass();
  if (!$('sec-chart').hidden) renderChart();
  if (!$('sec-d15').hidden) {
    if (state.apiMode) renderApi();
    else renderMeasured();
  }
}

/** On a language switch, values the app filled in itself follow the new decimal mark. */
function convertAutoFields() {
  for (const [textKey, autoKey] of [['densityText', 'densityAuto'], ['alphaText', 'alphaAuto']]) {
    if (state[autoKey]) state[textKey] = state[textKey].replace(/[.,]/g, lang === 'fr' ? ',' : '.');
  }
}

function selectTab(i, focus = false) {
  state.tab = i;
  update();
  if (focus) $(`tab-${i}`).focus();
}

// ---------- events ----------

function init() {
  renderStaticText();

  $('lang-btn').addEventListener('click', () => {
    lang = lang === 'fr' ? 'en' : 'fr';
    save(LANG_KEY, lang);
    convertAutoFields();
    for (const el of document.querySelectorAll('[data-html]')) delete el.dataset.html;
    renderStaticText();
    update();
  });

  const tabs = [0, 1, 2].map((i) => $(`tab-${i}`));
  tabs.forEach((b, i) => b.addEventListener('click', () => selectTab(i)));
  $('tablist').addEventListener('keydown', (e) => {
    const cur = state.tab;
    let next = null;
    if (e.key === 'ArrowRight') next = (cur + 1) % 3;
    else if (e.key === 'ArrowLeft') next = (cur + 2) % 3;
    else if (e.key === 'Home') next = 0;
    else if (e.key === 'End') next = 2;
    if (next !== null) {
      e.preventDefault();
      selectTab(next, true);
    }
  });

  $('in-product').addEventListener('change', (e) => {
    const p = Product[e.target.value];
    if (!p) return;
    state.product = p.id;
    if (!p.isCustom) {
      state.densityText = kgLText(p.defaultDensity);
      state.densityAuto = true;
    }
    update();
  });

  document.addEventListener('change', (e) => {
    const el = e.target;
    if (!(el instanceof HTMLInputElement)) return;
    switch (el.name) {
      case 'mode': state.massMode = el.value === 'mass'; break;
      case 'unit-calc':
      case 'unit-mass': state.unit = el.value; break;
      case 'mass-unit': state.massInLb = el.value === 'lb'; break;
      case 'd15-mode': state.apiMode = el.value === 'api'; break;
      default:
        if (el.id === 'in-glass') state.glassHydrometer = el.checked;
        else return;
    }
    update();
  });

  for (const id of Object.keys(FIELDS)) bindField(id);

  // "Use this density in the volume calculator"
  $('panel').addEventListener('click', (e) => {
    const btn = e.target.closest('[data-use-density]');
    if (!btn) return;
    const d = Number(btn.dataset.useDensity);
    state.densityText = kgLText(d, 4);
    state.densityAuto = true;
    if (product().isCustom) state.product = Product.OTHER.id;
    selectTab(0, true);
  });

  render();
}

init();
