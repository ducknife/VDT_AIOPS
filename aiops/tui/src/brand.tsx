// DUCKOMPOSE (sextant nén, src/duckompose.txt) + hiệu ứng ÁNH SÁNG QUÉT NGANG:
// một dải sáng (trắng→sky→blue) chạy trái→phải qua chữ, lặp lại. Nền = deep.
import { readFileSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import { useEffect, useState } from 'react';
import { Box, Text } from 'ink';
import { C } from './theme';

const dir = dirname(fileURLToPath(import.meta.url));
const art = readFileSync(join(dir, 'duckompose.txt'), 'utf8').replace(/\n+$/, '');
const LINES = art.split('\n').map((l) => [...l]); // [...] = an toàn với ký tự >BMP (sextant)
const W = Math.max(...LINES.map((l) => l.length));

export function Brand() {
  const [sweep, setSweep] = useState(0);
  useEffect(() => {
    const t = setInterval(() => setSweep((s) => (s + 1) % (W + 8)), 90); // +8 = nghỉ giữa 2 lượt
    return () => clearInterval(t);
  }, []);

  const colorAt = (c: number) => {
    const d = Math.abs(sweep - c);
    if (d <= 1) return C.white;       // điểm sáng trắng chạy qua
    if (d <= 3) return C.lightOrange; // viền sáng quanh điểm chạy
    return C.gold; // chữ nền VÀNG Panda (VIP) - shimmer trắng chạy ngang
  };

  return (
    <Box flexDirection="column">
      {LINES.map((chars, r) => (
        <Text key={r}>
          {chars.map((ch, c) => (
            <Text key={c} color={ch === ' ' ? undefined : colorAt(c)}>{ch}</Text>
          ))}
        </Text>
      ))}
    </Box>
  );
}
