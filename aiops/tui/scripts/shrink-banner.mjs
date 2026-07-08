// Nén ASCII-art figlet (█/space) thành chữ "pixel nhỏ" bằng KHỐI PHẦN-TƯ 2×2.
// Dùng: npx figlet-cli -f "ANSI Regular" -w 300 "DUCKOMPOSE" | node scripts/shrink-banner.mjs > src/utils/duckompose.txt
//
// Block Elements (U+2580..U+259F) có trong MỌI font terminal (Consolas/Cascadia/DejaVu…),
// KHÁC với sextant U+1FB.. (chỉ vài font mới có → biến mất trên đa số máy).
import { readFileSync } from 'fs';

const input = readFileSync(0, 'utf8');
const lines = input.split('\n').filter((l) => l.replace(/\s/g, '').length > 0);
const H = lines.length;
const W = Math.max(...lines.map((l) => l.length));
const on = (x, y) => (y < H && x < lines[y].length && lines[y][x] !== ' ' ? 1 : 0);

// index = TL(1) + TR(2) + BL(4) + BR(8)
const QUAD = [' ', '▘', '▝', '▀', '▖', '▌', '▞', '▛', '▗', '▚', '▐', '▜', '▄', '▙', '▟', '█'];
const WT = [1, 2, 4, 8];
const SUB = [[0, 0], [1, 0], [0, 1], [1, 1]]; // TL, TR, BL, BR

const rows = Math.ceil(H / 2);
const cols = Math.ceil(W / 2);
let out = '';
for (let ry = 0; ry < rows; ry++) {
  let line = '';
  for (let cx = 0; cx < cols; cx++) {
    let v = 0;
    SUB.forEach(([dx, dy], k) => { if (on(cx * 2 + dx, ry * 2 + dy)) v += WT[k]; });
    line += QUAD[v];
  }
  out += line.replace(/\s+$/, '') + '\n';
}
process.stdout.write(out.replace(/\n+$/, '') + '\n');
