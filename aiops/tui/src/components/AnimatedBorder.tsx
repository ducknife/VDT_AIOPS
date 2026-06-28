// Viền 1 ĐƯỜNG mảnh (box-drawing bo góc) + dải sáng chạy THEO CHIỀU KIM ĐỒNG HỒ
// (trên→phải→dưới→trái) như sao chổi. Title nằm TRÊN đường ở góc trên-trái (kiểu Claude Code).
import { useEffect, useState, type ReactNode } from 'react';
import { Box, Text } from 'ink';
import { C } from '../theme';

export function AnimatedBorder({
  width, height, title, children,
}: { width: number; height: number; title?: string; children: ReactNode }) {
  const W = Math.max(8, width);
  const H = Math.max(3, height);
  const innerRows = H - 2;
  const PERIM = 2 * W + 2 * (H - 2); // số ô viền

  const [head, setHead] = useState(0);
  useEffect(() => {
    const t = setInterval(() => setHead((h) => (h + 1) % PERIM), 50);
    return () => clearInterval(t);
  }, [PERIM]);

  // chỉ số ô trên chu vi theo CHIỀU KIM ĐỒNG HỒ: trên(→) → phải(↓) → dưới(←) → trái(↑)
  const perim = (r: number, c: number): number => {
    if (r === 0) return c;
    if (c === W - 1) return (W - 1) + r;
    if (r === H - 1) return (W + H - 2) + (W - 1 - c);
    return (2 * W + H - 2) + (H - 2 - r);
  };

  // màu theo khoảng cách ngược chiều tới đầu sáng -> đuôi sao chổi nhạt dần
  const lightColor = (i: number) => {
    const d = (head - i + PERIM) % PERIM;
    if (d <= 2) return C.white;        // đầu sáng trắng (rộng hơn -> sáng hơn)
    if (d <= 8) return C.lightOrange;  // dải vàng sáng
    if (d <= 22) return C.gold;        // đuôi VÀNG dài
    if (d <= 28) return C.teal;        // nấc TEAL chuyển vàng -> xanh cho mượt
    return C.blue; // nền viền = xanh dương Panda
  };

  const lineCell = (r: number, c: number, ch: string) => (
    <Text key={`${r}-${c}`} color={lightColor(perim(r, c))}>{ch}</Text>
  );

  // hàng trên: ╭─ {Title} ─...─ ╮  (Title đứng yên, xanh dương Panda; đường vẫn chạy sáng)
  const top: ReactNode[] = [];
  let label = title ? ` ${title} ` : '';
  if (label.length > W - 4) label = label.slice(0, Math.max(0, W - 4));
  top.push(lineCell(0, 0, '╭'));
  top.push(lineCell(0, 1, '─'));
  let c = 2;
  for (const ch of label) { top.push(<Text key={`ttl-${c}`} color={C.white} bold>{ch}</Text>); c++; }
  for (; c < W - 1; c++) top.push(lineCell(0, c, '─'));
  top.push(lineCell(0, W - 1, '╮'));

  // hàng dưới: ╰─...─╯
  const bottom: ReactNode[] = [lineCell(H - 1, 0, '╰')];
  for (let bc = 1; bc < W - 1; bc++) bottom.push(lineCell(H - 1, bc, '─'));
  bottom.push(lineCell(H - 1, W - 1, '╯'));

  // cạnh trái/phải
  const left: ReactNode[] = [], right: ReactNode[] = [];
  for (let r = 1; r <= innerRows; r++) {
    left.push(lineCell(r, 0, '│'));
    right.push(lineCell(r, W - 1, '│'));
  }

  return (
    <Box flexDirection="column" width={W} flexShrink={0}>
      <Box>{top}</Box>
      <Box flexDirection="row" height={innerRows}>
        <Box flexDirection="column" flexShrink={0}>{left}</Box>
        <Box width={W - 2} flexShrink={0} flexDirection="column" justifyContent="center" paddingX={1}>{children}</Box>
        <Box flexDirection="column" flexShrink={0}>{right}</Box>
      </Box>
      <Box>{bottom}</Box>
    </Box>
  );
}
