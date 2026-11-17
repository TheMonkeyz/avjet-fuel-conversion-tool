// Writes core/src/test/resources/vcf-grid.csv: the VCF and density at 60 °F for a grid of
// densities and temperatures, from web/calc.js. Both test suites compare against this file, so
// the Kotlin and JavaScript implementations must give the same numbers.
// Run from the repo root: node web/tools/make-grid.mjs
import { writeFileSync } from 'node:fs';
import { coefficients, volumeCorrectionFactor } from '../calc.js';

const densities = [];
for (let d = 650; d <= 1000; d += 5) densities.push(d);
densities.push(611.2, 770.0, 770.7, 787.1, 787.9, 838.0, 838.7, 1163.8);
densities.sort((a, b) => a - b);
const temps = [];
for (let t = -50; t <= 150; t += 5) temps.push(t);
temps.push(-0.05, 0.05, 14.95, 15.05, 15.5556, 37.8);
temps.sort((a, b) => a - b);

const lines = ['density15_kgm3,temp_c,group,density60_kgm3,vcf'];
for (const d of densities) {
  const c = coefficients(d);
  for (const t of temps) {
    lines.push(`${d},${t},${c.group},${c.density60.toFixed(6)},${volumeCorrectionFactor(d, t).toFixed(5)}`);
  }
}
writeFileSync(new URL('../../core/src/test/resources/vcf-grid.csv', import.meta.url), lines.join('\n') + '\n');
console.log(`${lines.length - 1} rows`);
