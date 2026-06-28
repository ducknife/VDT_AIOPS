// Render markdown rút gọn cho câu trả lời chat:
//  - bảng "| a | b |" -> bảng CĂN CỘT thật (bỏ dòng |---|, header đậm)
//  (đúng Panda) **đậm** -> CAM | `code` -> TEAL | "##/###" -> header SKY | "- " -> bullet PINK
//  -> phối nhiều màu hợp tông xanh, tránh đơn điệu toàn xanh.
// Chỉ dùng cho text ĐÃ XONG (không stream) để tránh ** hở giữa chừng.
import { Box, Text } from 'ink';
import type { ReactNode } from 'react';
import { C } from '../theme';
import { stripEmoji, wrapText } from '../format';

// theo đúng Panda markdown: `code` -> TEAL (markup.inline.raw), **đậm** -> CAM (markup.bold, dễ đọc)
function inline(text: string, key: string): ReactNode[] {
  const out: ReactNode[] = [];
  text.split(/(`[^`]+`)/g).forEach((part, pi) => {
    if (/^`[^`]+`$/.test(part)) {
      out.push(<Text key={`${key}-c${pi}`} color={C.teal}>{part.slice(1, -1)}</Text>);
    } else {
      part.split('**').forEach((seg, si) =>
        out.push(si % 2 === 1
          ? <Text key={`${key}-b${pi}-${si}`} bold color={C.gold}>{seg}</Text>
          : <Text key={`${key}-${pi}-${si}`}>{seg}</Text>),
      );
    }
  });
  return out;
}

const stripBold = (s: string) => s.replace(/\*\*/g, '');
const vlen = (s: string) => stripBold(s).length; // bề rộng hiển thị (không tính **)
const isTableRow = (l: string) => l.trim().startsWith('|');
const splitCells = (l: string) => l.trim().replace(/^\||\|$/g, '').split('|').map((c) => c.trim());
const isSeparator = (cells: string[]) => cells.length > 0 && cells.every((c) => /^:?-{2,}:?$/.test(c));

const MINW = 6; // bề rộng tối thiểu mỗi cột khi phải co lại

function MdTable({ rows, k, width }: { rows: string[]; k: number; width: number }) {
  const parsed = rows.map(splitCells).filter((c) => !isSeparator(c));
  if (parsed.length === 0) return null;
  const cols = Math.max(...parsed.map((r) => r.length));
  const SEP = 2;

  // bề rộng tự nhiên (theo nội dung), rồi CO cột rộng nhất cho vừa cửa sổ
  const widths = Array.from({ length: cols }, (_, c) => Math.max(...parsed.map((r) => vlen(r[c] ?? ''))));
  const avail = Math.max(cols * MINW, width - (cols - 1) * SEP);
  let over = widths.reduce((a, b) => a + b, 0) - avail;
  while (over > 0) {
    let mi = -1, mx = MINW;
    for (let i = 0; i < cols; i++) if (widths[i] > mx) { mx = widths[i]; mi = i; }
    if (mi === -1) break; // không co thêm được
    widths[mi]--; over--;
  }

  return (
    <Box flexDirection="column" key={k}>
      {parsed.map((cells, ri) => {
        // mỗi ô wrap theo bề rộng cột -> nhiều dòng vật lý; dòng tiếp giữ LỀ CỘT
        const cellLines = Array.from({ length: cols }, (_, c) => wrapText(stripBold(cells[c] ?? ''), widths[c]));
        const h = Math.max(1, ...cellLines.map((l) => l.length));
        return Array.from({ length: h }).map((_, li) => (
          <Text key={`${ri}-${li}`}>
            {Array.from({ length: cols }).map((_, ci) => {
              const seg = cellLines[ci][li] ?? '';
              const pad = ' '.repeat(Math.max(0, widths[ci] - seg.length));
              return (
                <Text key={ci}>
                  {ci > 0 ? '  ' : ''}
                  <Text color={ri === 0 ? C.sky : C.white} bold={ri === 0}>{seg}</Text>{pad}
                </Text>
              );
            })}
          </Text>
        ));
      })}
    </Box>
  );
}

function MdLine({ line, k }: { line: string; k: number }) {
  const header = line.match(/^#{1,3}\s+(.*)$/);
  if (header) return <Text key={k} bold color={C.sky}>{header[1]}</Text>;

  const bullet = line.match(/^\s*[-•●]\s+(.*)$/);
  if (bullet) {
    return (
      <Text key={k}>
        <Text color={C.pink}>• </Text>
        {inline(bullet[1], `b${k}`)}
      </Text>
    );
  }
  if (line.trim() === '') return <Text key={k}> </Text>;
  return <Text key={k}>{inline(line, `l${k}`)}</Text>;
}

export function Markdown({ text, width = 80 }: { text: string; width?: number }) {
  const lines = stripEmoji(text).split('\n');
  const blocks: ReactNode[] = [];
  let i = 0;
  let k = 0;
  while (i < lines.length) {
    if (isTableRow(lines[i])) {
      const tbl: string[] = [];
      while (i < lines.length && isTableRow(lines[i])) tbl.push(lines[i++]);
      blocks.push(<MdTable key={k} rows={tbl} k={k} width={width} />);
    } else {
      blocks.push(<MdLine key={k} line={lines[i++]} k={k} />);
    }
    k++;
  }
  return <Box flexDirection="column">{blocks}</Box>;
}
