// Viền 1 ĐƯỜNG mảnh (box-drawing bo góc) + dải sáng chạy THEO CHIỀU KIM ĐỒNG HỒ
// (trên→phải→dưới→trái) như sao chổi. Title nằm TRÊN đường ở góc trên-trái (kiểu Claude Code).
//
// Mỗi cạnh viền = 1 node <Text> chứa chuỗi ANSI truecolor tự dựng (không phải 1 node / ô)
// -> ~4 node thay vì ~200 -> layout Yoga nhẹ, chạy đủ 30fps -> KHÔNG giật.
import { useEffect, useState, type ReactNode } from 'react';
import { Box, Text } from 'ink';
import { C } from '../theme';

// --- gradient nội suy RGB: đuôi sao chổi chuyển màu LIÊN TỤC (hết bậc thang) ---
type RGB = [number, number, number];
const hexToRgb = (h: string): RGB => {
  const n = parseInt(h.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
};

// các nấc màu theo tỉ lệ dọc đuôi (0 = đầu sáng, 1 = hoà vào nền viền)
const STOPS: Array<[number, RGB]> = [
  [0.0, hexToRgb('#FFFFFF')],     // lõi nóng trắng
  [0.1, hexToRgb(C.lightOrange)], // sáng vàng cam
  [0.32, hexToRgb(C.gold)],       // thân VÀNG VIP
  [0.62, hexToRgb(C.teal)],       // chuyển teal
  [1.0, hexToRgb(C.blue)],        // nền viền xanh dương Panda
];
const gradientRgb = (frac: number): RGB => {
  for (let i = 1; i < STOPS.length; i++) {
    if (frac <= STOPS[i][0]) {
      const [f0, c0] = STOPS[i - 1];
      const [f1, c1] = STOPS[i];
      const t = (frac - f0) / (f1 - f0 || 1);
      return [c0[0] + (c1[0] - c0[0]) * t, c0[1] + (c1[1] - c0[1]) * t, c0[2] + (c1[2] - c0[2]) * t];
    }
  }
  return STOPS[STOPS.length - 1][1];
};

const RESET = '\x1b[0m';
const sgr = (ch: string, [r, g, b]: RGB) =>
  `\x1b[38;2;${Math.round(r)};${Math.round(g)};${Math.round(b)}m${ch}`;
const TITLE: RGB = hexToRgb(C.white);
const sgrTitle = (ch: string) => `\x1b[1m${sgr(ch, TITLE)}\x1b[22m`;

export function AnimatedBorder({
  width, height, title, children,
}: { width: number; height: number; title?: string; children: ReactNode }) {
  const W = Math.max(8, width);
  const H = Math.max(3, height);
  const innerRows = H - 2;
  const PERIM = 2 * W + 2 * (H - 2); // số ô viền

  // vị trí đầu sáng là SỐ THỰC, tính theo thời gian thực -> tốc độ đều, không trôi.
  // refresh dày + vị trí dưới-ô làm cả gradient TRƯỢT mượt thay vì nhảy từng ô.
  const SPEED = 26;     // ô / giây  (tăng = chạy nhanh hơn)
  const FRAME = 33;     // ms / khung (~30fps; giảm = mượt hơn nhưng nặng render hơn)
  const [head, setHead] = useState(0);
  useEffect(() => {
    const start = Date.now();
    let timer: ReturnType<typeof setTimeout>;
    const tick = () => {
      setHead(((Date.now() - start) / 1000 * SPEED) % PERIM);
      timer = setTimeout(tick, FRAME);
    };
    timer = setTimeout(tick, FRAME);
    return () => clearTimeout(timer);
  }, [PERIM]);

  // chỉ số ô trên chu vi theo CHIỀU KIM ĐỒNG HỒ: trên(→) → phải(↓) → dưới(←) → trái(↑)
  const perim = (r: number, c: number): number => {
    if (r === 0) return c;
    if (c === W - 1) return (W - 1) + r;
    if (r === H - 1) return (W + H - 2) + (W - 1 - c);
    return (2 * W + H - 2) + (H - 2 - r);
  };

  // khoảng cách ngược chiều tới đầu sáng -> nội suy gradient liên tục dọc đuôi
  const TAIL = 34; // độ dài sao chổi (ô); càng dài đuôi fade càng mượt
  const col = (idx: number): RGB => {
    const d = (head - idx + PERIM) % PERIM;
    if (d > TAIL) return STOPS[STOPS.length - 1][1]; // ngoài đuôi = nền viền xanh dương
    return gradientRgb(d / TAIL);
  };

  // hàng trên: ╭─ {Title} ─...─ ╮  (Title đứng yên, trắng đậm; đường vẫn chạy sáng)
  let label = title ? ` ${title} ` : '';
  if (label.length > W - 4) label = label.slice(0, Math.max(0, W - 4));
  let top = sgr('╭', col(perim(0, 0))) + sgr('─', col(perim(0, 1)));
  let c = 2;
  for (const ch of label) { top += sgrTitle(ch); c++; }
  for (; c < W - 1; c++) top += sgr('─', col(perim(0, c)));
  top += sgr('╮', col(perim(0, W - 1))) + RESET;

  // hàng dưới: ╰─...─╯
  let bottom = sgr('╰', col(perim(H - 1, 0)));
  for (let bc = 1; bc < W - 1; bc++) bottom += sgr('─', col(perim(H - 1, bc)));
  bottom += sgr('╯', col(perim(H - 1, W - 1))) + RESET;

  // cạnh trái/phải: mỗi cạnh = 1 chuỗi nhiều dòng (1 node Text)
  const leftLines: string[] = [], rightLines: string[] = [];
  for (let r = 1; r <= innerRows; r++) {
    leftLines.push(sgr('│', col(perim(r, 0))) + RESET);
    rightLines.push(sgr('│', col(perim(r, W - 1))) + RESET);
  }

  return (
    <Box flexDirection="column" width={W} flexShrink={0}>
      <Text>{top}</Text>
      <Box flexDirection="row" height={innerRows}>
        <Text>{leftLines.join('\n')}</Text>
        <Box width={W - 2} flexShrink={0} flexDirection="column" justifyContent="center" paddingX={1}>{children}</Box>
        <Text>{rightLines.join('\n')}</Text>
      </Box>
      <Text>{bottom}</Text>
    </Box>
  );
}
