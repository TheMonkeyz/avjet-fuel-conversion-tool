// Draws the app icon PNGs with no dependencies (Node zlib only).
// Same shapes as app/src/main/res/drawable/ic_launcher_foreground.xml (108 x 108 viewport):
// a dark #06253A fuel drop with a white thermometer, on the #8FD0FF launcher background.
//
// Run from the repo root:  node web/tools/make-icons.mjs
import { deflateSync } from 'node:zlib';
import { writeFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const OUT = join(dirname(fileURLToPath(import.meta.url)), '..', 'icons');
mkdirSync(OUT, { recursive: true });

const BG = [0x8f, 0xd0, 0xff];
const DROP = [0x06, 0x25, 0x3a];
const WHITE = [0xff, 0xff, 0xff];

// ---------- shapes as polygons in the 108 x 108 viewport ----------

function cubic(p0, p1, p2, p3, n = 64) {
  const pts = [];
  for (let i = 1; i <= n; i++) {
    const t = i / n;
    const u = 1 - t;
    pts.push([
      u * u * u * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t * t * t * p3[0],
      u * u * u * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t * t * t * p3[1],
    ]);
  }
  return pts;
}
function arc(cx, cy, r, from, to, n = 96) {
  const pts = [];
  for (let i = 1; i <= n; i++) {
    const a = from + ((to - from) * i) / n;
    pts.push([cx + r * Math.cos(a), cy + r * Math.sin(a)]);
  }
  return pts;
}

// M54,22 C54,22 34,46 34,62 A20,20 0 0 0 74,62 C74,46 54,22 54,22 Z
const drop = [
  [54, 22],
  ...cubic([54, 22], [54, 22], [34, 46], [34, 62]),
  ...arc(54, 62, 20, Math.PI, 0), // through (54,82): the bottom half
  ...cubic([74, 62], [74, 46], [54, 22], [54, 22]),
];

// M51,46 L57,46 L57,62 A6,6 0 1 1 51,62 Z  (large clockwise arc: centre below the chord)
const cy = 62 + Math.sqrt(36 - 9);
const thermo = [
  [51, 46],
  [57, 46],
  [57, 62],
  ...arc(54, cy, 6, Math.atan2(62 - cy, 3), Math.atan2(62 - cy, -3) + 2 * Math.PI),
];

function bbox(poly) {
  const xs = poly.map((p) => p[0]);
  const ys = poly.map((p) => p[1]);
  return [Math.min(...xs), Math.min(...ys), Math.max(...xs), Math.max(...ys)];
}
const BOXES = new Map([[drop, bbox(drop)], [thermo, bbox(thermo)]]);

function inPolygon(x, y, poly) {
  const [x0, y0, x1, y1] = BOXES.get(poly);
  if (x < x0 || x > x1 || y < y0 || y > y1) return false;
  let inside = false;
  for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
    const [xi, yi] = poly[i];
    const [xj, yj] = poly[j];
    if (yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) inside = !inside;
  }
  return inside;
}
function inRoundRect(x, y, x0, y0, size, r) {
  if (x < x0 || y < y0 || x > x0 + size || y > y0 + size) return false;
  const cxr = Math.min(Math.max(x, x0 + r), x0 + size - r);
  const cyr = Math.min(Math.max(y, y0 + r), y0 + size - r);
  return (x - cxr) ** 2 + (y - cyr) ** 2 <= r * r;
}

/**
 * @param size pixel size
 * @param view [x, y, side] square of the 108 viewport to draw
 * @param cornerRadius rounded background corners in viewport units, 0 for a full square
 */
function render(size, view, cornerRadius) {
  const [vx, vy, side] = view;
  const S = 4; // 4 x 4 samples per pixel
  const rgba = Buffer.alloc(size * size * 4);
  for (let py = 0; py < size; py++) {
    for (let px = 0; px < size; px++) {
      let r = 0, g = 0, b = 0, a = 0;
      for (let sy = 0; sy < S; sy++) {
        for (let sx = 0; sx < S; sx++) {
          const x = vx + ((px + (sx + 0.5) / S) / size) * side;
          const y = vy + ((py + (sy + 0.5) / S) / size) * side;
          if (cornerRadius > 0 && !inRoundRect(x, y, vx, vy, side, cornerRadius)) continue;
          const c = inPolygon(x, y, thermo) ? WHITE : inPolygon(x, y, drop) ? DROP : BG;
          r += c[0]; g += c[1]; b += c[2]; a += 255;
        }
      }
      const i = (py * size + px) * 4;
      const n = S * S;
      // Store un-premultiplied colour
      rgba[i] = a ? Math.round((r * 255) / a) : 0;
      rgba[i + 1] = a ? Math.round((g * 255) / a) : 0;
      rgba[i + 2] = a ? Math.round((b * 255) / a) : 0;
      rgba[i + 3] = Math.round(a / n);
    }
  }
  return png(size, size, rgba);
}

// ---------- PNG encoder ----------

const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();
function crc32(buf) {
  let c = 0xffffffff;
  for (const byte of buf) c = CRC_TABLE[(c ^ byte) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
function png(w, h, rgba) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 6; // RGBA
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) {
    raw[y * (w * 4 + 1)] = 0; // filter: none
    rgba.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4);
  }
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

// ---------- outputs ----------

const CROP = [14, 12, 80]; // the drop, centred, filling 75 % of the height
const FULL = [0, 0, 108]; // the whole adaptive-icon canvas: the drop sits inside the maskable safe zone

const jobs = [
  ['icon-192.png', 192, CROP, 16],
  ['icon-512.png', 512, CROP, 16],
  ['maskable-512.png', 512, FULL, 0],
  ['apple-touch-icon.png', 180, CROP, 0], // iOS rounds the corners itself and needs no transparency
];
for (const [name, size, view, radius] of jobs) {
  writeFileSync(join(OUT, name), render(size, view, radius));
  console.log(`${name} ${size}x${size}`);
}

const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${CROP.join(' ').replace(/ (\d+)$/, ' $1 $1')}">
  <rect x="14" y="12" width="80" height="80" rx="16" fill="#8FD0FF"/>
  <path fill="#06253A" d="M54,22 C54,22 34,46 34,62 A20,20 0 0 0 74,62 C74,46 54,22 54,22 Z"/>
  <path fill="#FFFFFF" d="M51,46 L57,46 L57,62 A6,6 0 1 1 51,62 Z"/>
</svg>
`;
writeFileSync(join(OUT, 'icon.svg'), svg);
console.log('icon.svg');
