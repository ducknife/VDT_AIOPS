// Nén ASCII-art figlet (█/space) thành chữ "pixel nhỏ" bằng sextant 2×3 (giống logo).
// Dùng: npx figlet-cli -f "ANSI Regular" -w 300 "DUCKOMPOSE" | node scripts/shrink-banner.mjs > src/duckompose.txt
import { readFileSync } from 'fs';

const input = readFileSync(0, 'utf8');
const lines = input.split('\n').filter((l) => l.replace(/\s/g, '').length > 0);
const H = lines.length;
const W = Math.max(...lines.map((l) => l.length));
const on = (x, y) => (y < H && x < lines[y].length && lines[y][x] !== ' ' ? 1 : 0);

// sextant: v = 6-bit (vị trí 1 2 / 4 8 / 16 32) -> ký tự khối U+1FB00 (khớp img2ansi)
const sextant = (v) => {
  if (v === 0) return ' ';
  if (v === 63) return '█';
  if (v === 21) return '▌';
  if (v === 42) return '▐';
  let idx = v - 1;
  if (v > 21) idx--;
  if (v > 42) idx--;
  return String.fromCodePoint(0x1fb00 + idx);
};
const WT = [1, 2, 4, 8, 16, 32];
const SUB = [[0, 0], [1, 0], [0, 1], [1, 1], [0, 2], [1, 2]];

const rows = Math.ceil(H / 3);
const cols = Math.ceil(W / 2);
let out = '';
for (let ry = 0; ry < rows; ry++) {
  let line = '';
  for (let cx = 0; cx < cols; cx++) {
    let v = 0;
    SUB.forEach(([dx, dy], k) => { if (on(cx * 2 + dx, ry * 3 + dy)) v += WT[k]; });
    line += sextant(v);
  }
  out += line.replace(/\s+$/, '') + '\n';
}
process.stdout.write(out.replace(/\n+$/, '') + '\n');
