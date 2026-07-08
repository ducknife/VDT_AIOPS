import { readFileSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import { Box, Text } from 'ink';

// logo.ansi = mỗi ô: ESC[38;2;r;g;bm <ký tự sextant> ESC[0m, xen kẽ dấu cách.
// Sinh bằng scripts/img2ansi.mjs. Đổi kích thước: `node scripts/img2ansi.mjs <COLS>`.
//
// QUAN TRỌNG: KHÔNG render `<Text>{chuỗi-ANSI-thô}</Text>` — Ink render ANSI thô
// không ổn định (logo rỗng trên nhiều terminal). Thay vào đó PARSE màu rồi vẽ TỪNG Ô
// bằng Ink <Text color> — y hệt cách Brand vẽ chữ DUCKOMPOSE (đã hiện chắc chắn + có viền).
const dir = dirname(fileURLToPath(import.meta.url));
const raw = readFileSync(join(dir, 'logo.ansi'), 'utf8').replace(/\r/g, '').replace(/\n+$/, '');

const toHex = (r: string, g: string, b: string) =>
  '#' + [r, g, b].map((x) => Number(x).toString(16).padStart(2, '0')).join('');

// tách từng ô: ô-có-màu (fg+ký tự+reset) HOẶC một ký tự thường (dấu cách). /u = an toàn ký tự >BMP
const LINES: { ch: string; color?: string }[][] = raw.split('\n').map((line) => {
  const cells: { ch: string; color?: string }[] = [];
  const re = /\x1b\[38;2;(\d+);(\d+);(\d+)m(.)\x1b\[0m|(.)/gu;
  let m: RegExpExecArray | null;
  while ((m = re.exec(line)) !== null) {
    if (m[4] !== undefined) cells.push({ ch: m[4], color: toHex(m[1], m[2], m[3]) });
    else cells.push({ ch: m[5]! });
  }
  return cells;
});

export function Logo() {
  return (
    <Box flexDirection="column">
      {LINES.map((cells, r) => (
        <Text key={r}>
          {cells.map((cell, c) => (
            <Text key={c} color={cell.color}>{cell.ch}</Text>
          ))}
        </Text>
      ))}
    </Box>
  );
}
