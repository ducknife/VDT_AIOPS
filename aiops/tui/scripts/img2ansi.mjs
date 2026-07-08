import { readFileSync, writeFileSync, mkdirSync } from 'fs';
import { PNG } from 'pngjs';

const SRC = 'src/assets/images/logo.png';
const OUT = 'src/assets/logo.ansi';
const COLS = Number(process.argv[2] ?? 20);   // chiều rộng (ký tự)
const WHITE = 232;

const png = PNG.sync.read(readFileSync(SRC));
const { width: W, height: H, data } = png;

// pixel trắng/trong
const white = new Uint8Array(W * H);
for (let i = 0; i < W * H; i++) {
  const p = i * 4;
  white[i] = (data[p + 3] < 16 || (data[p] >= WHITE && data[p + 1] >= WHITE && data[p + 2] >= WHITE)) ? 1 : 0;
}
// flood-fill từ viền => nền ngoài; trắng bị bao kín (dấu sao) không dính
const bgMask = new Uint8Array(W * H);
const q = [];
const seed = (i) => { if (white[i] && !bgMask[i]) { bgMask[i] = 1; q.push(i); } };
for (let x = 0; x < W; x++) { seed(x); seed((H - 1) * W + x); }
for (let y = 0; y < H; y++) { seed(y * W); seed(y * W + W - 1); }
for (let head = 0; head < q.length; head++) {
  const i = q[head], x = i % W, y = (i / W) | 0;
  if (x > 0) seed(i - 1); if (x < W - 1) seed(i + 1);
  if (y > 0) seed(i - W); if (y < H - 1) seed(i + W);
}
// tâm dấu sao = trắng bị bao kín
let ax = 0, ay = 0, an = 0;
for (let i = 0; i < W * H; i++) if (white[i] && !bgMask[i]) { ax += i % W; ay += (i / W) | 0; an++; }

// render: chỉ NỀN NGOÀI trong suốt; trắng-bị-bao-kín (dấu sao) => pixel trắng thật
const samp = (fx, fy) => {
  const x = Math.min(W - 1, Math.max(0, Math.round(fx * W)));
  const y = Math.min(H - 1, Math.max(0, Math.round(fy * H)));
  const i = y * W + x;
  return { bg: bgMask[i] === 1, rgb: [data[i * 4], data[i * 4 + 1], data[i * 4 + 2]] };
};

// ô sextant 2x3 (U+1FB00 block). v = 6-bit, vị trí: 1 2 / 4 8 / 16 32
const sextant = (v) => {
  if (v === 0) return ' ';
  if (v === 63) return '█';
  if (v === 21) return '▌';   // cột trái đầy
  if (v === 42) return '▐';   // cột phải đầy
  let idx = v - 1;
  if (v > 21) idx--;
  if (v > 42) idx--;
  return String.fromCodePoint(0x1FB00 + idx);
};

const PAL = [[30, 159, 242], [19, 128, 214], [155, 216, 255], [255, 255, 255]]; // blue, deep, sky, white(sao)
const quant = (r, g, b) => {
  let best = PAL[0], bd = Infinity;
  for (const c of PAL) { const d = (r - c[0]) ** 2 + (g - c[1]) ** 2 + (b - c[2]) ** 2; if (d < bd) { bd = d; best = c; } }
  return best;
};

const hsamp = COLS * 2;
const ROWS = Math.max(1, Math.round((H * COLS) / (2 * W)));
const vsamp = ROWS * 3;
const E = '\x1b';
const fg = (r, g, b) => `${E}[38;2;${r};${g};${b}m`;
const RST = `${E}[0m`;
const WT = [1, 2, 4, 8, 16, 32];

const cells = [], ink = [];
for (let row = 0; row < ROWS; row++) {
  cells[row] = []; ink[row] = [];
  for (let col = 0; col < COLS; col++) {
    const sub = [
      [col * 2, row * 3], [col * 2 + 1, row * 3],
      [col * 2, row * 3 + 1], [col * 2 + 1, row * 3 + 1],
      [col * 2, row * 3 + 2], [col * 2 + 1, row * 3 + 2],
    ];
    let v = 0, r = 0, g = 0, b = 0, n = 0;
    sub.forEach(([sc, sr], k) => {
      const p = samp((sc + 0.5) / hsamp, (sr + 0.5) / vsamp);
      if (!p.bg) { v += WT[k]; r += p.rgb[0]; g += p.rgb[1]; b += p.rgb[2]; n++; }
    });
    if (v === 0) { cells[row][col] = ' '; ink[row][col] = false; continue; }
    const [qr, qg, qb] = quant(r / n, g / n, b / n);
    cells[row][col] = `${fg(qr, qg, qb)}${sextant(v)}${RST}`;
    ink[row][col] = true;
  }
}

// auto-crop
let r0 = ROWS, r1 = -1, c0 = COLS, c1 = -1;
for (let r = 0; r < ROWS; r++) for (let c = 0; c < COLS; c++) if (ink[r][c]) {
  if (r < r0) r0 = r; if (r > r1) r1 = r; if (c < c0) c0 = c; if (c > c1) c1 = c;
}
let out = '';
for (let r = r0; r <= r1; r++) out += cells[r].slice(c0, c1 + 1).join('') + '\n';
mkdirSync('src', { recursive: true });
writeFileSync(OUT, out);
console.log(`Wrote ${OUT}: cropped ${c1 - c0 + 1}x${r1 - r0 + 1} (từ ${COLS}x${ROWS}, sextant 2x3, src ${W}x${H})`);
