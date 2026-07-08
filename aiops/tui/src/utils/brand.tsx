// DUCKOMPOSE (sextant nén, src/duckompose.txt). Hiệu ứng quét sáng TRẮNG chạy trái→phải.
// Dùng chuỗi ANSI SGR truecolor thô (như AnimatedBorder) để Yoga chỉ thấy 1 <Text>/dòng
// → layout nhẹ, không giật khi animation chạy ~30fps.
import { useEffect, useState } from 'react';
import { readFileSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import { Box, Text } from 'ink';
import { C } from './theme';

const dir = dirname(fileURLToPath(import.meta.url));
const art = readFileSync(join(dir, 'duckompose.txt'), 'utf8').replace(/\n+$/, '');
const LINES = art.split('\n').map((l) => [...l.replace(/\r/g, '')]); // strip \r (Windows CRLF)
const W = Math.max(...LINES.map((l) => l.length)); // chiều rộng lớn nhất (43)

// --- gradient nội suy RGB ---
type RGB = [number, number, number];
const hexToRgb = (h: string): RGB => {
  const n = parseInt(h.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
};
const lerp = (a: RGB, b: RGB, t: number): RGB => [
  a[0] + (b[0] - a[0]) * t,
  a[1] + (b[1] - a[1]) * t,
  a[2] + (b[2] - a[2]) * t,
];

const BASE: RGB = hexToRgb(C.gold);         // #FFB86C — cam vàng Panda
const BRIGHT: RGB = [255, 255, 255];         // lõi sáng trắng
const WARM: RGB = hexToRgb(C.lightOrange);   // #FFCC95 — vùng ấm quanh lõi

const RESET = '\x1b[0m';
const sgr = (ch: string, [r, g, b]: RGB) =>
  `\x1b[38;2;${Math.round(r)};${Math.round(g)};${Math.round(b)}m${ch}`;

// Tính màu cho ô tại khoảng cách `d` từ tâm beam (d = 0 = tâm sáng nhất)
const BEAM_HALF = 6;   // nửa bán kính beam (ô): tâm ± 6 ô có ánh sáng
const beamColor = (d: number): RGB => {
  const abs = Math.abs(d);
  if (abs > BEAM_HALF) return BASE;                              // ngoài beam → cam gốc
  const t = abs / BEAM_HALF;                                     // 0 = tâm, 1 = rìa
  if (t < 0.3) return lerp(BRIGHT, WARM, t / 0.3);              // trắng → vàng ấm
  return lerp(WARM, BASE, (t - 0.3) / 0.7);                     // vàng ấm → cam gốc
};

export function Brand() {
  // vị trí tâm beam (số thực, chạy liên tục qua W ô + khoảng nghỉ ngoài)
  const TRAVEL = W + BEAM_HALF * 2 + 12; // thêm 12 ô nghỉ để có "pause" tự nhiên giữa các lần quét
  const SPEED = 18;  // ô / giây
  const FRAME = 33;  // ~30 fps

  const [pos, setPos] = useState(-BEAM_HALF);
  useEffect(() => {
    const start = Date.now();
    let timer: ReturnType<typeof setTimeout>;
    const tick = () => {
      const elapsed = (Date.now() - start) / 1000;
      setPos(((elapsed * SPEED) % TRAVEL) - BEAM_HALF);
      timer = setTimeout(tick, FRAME);
    };
    timer = setTimeout(tick, FRAME);
    return () => clearTimeout(timer);
  }, []);

  // dựng chuỗi ANSI cho mỗi dòng
  const rendered = LINES.map((chars) => {
    let s = '';
    for (let c = 0; c < chars.length; c++) {
      s += sgr(chars[c], beamColor(c - pos));
    }
    return s + RESET;
  });

  return (
    <Box flexDirection="column">
      {rendered.map((line, r) => (
        <Text key={r} wrap="truncate-end">{line}</Text>
      ))}
    </Box>
  );
}

