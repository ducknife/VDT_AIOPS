// Accordion theo TỪNG turn:
//  - TurnDivider: 1 DÒNG "đường gạch xám mờ" có ▸/▾ + "turn i" ở GIỮA (dấu … khi đang chạy).
//    Đây là dòng DUY NHẤT click để mở/thu turn đó.
//  - TurnBody: danh sách tool + tham số của MỘT turn (không bắt click).
import { Box, Text } from 'ink';
import type { Turn } from '../store/useFeed';
import { C } from '../utils/theme';
import { clock, argEntries } from '../utils/format';

// DÒNG collapse CHA "tool calls" — bọc toàn bộ list turn (block click được).
//  live  = đang chạy  -> nhãn tím + "…" ; xong -> xám mờ, thu gọn 1 dòng.
export function ToolsHeader({
  count, open, live,
}: { count: number; open: boolean; live: boolean }) {
  const marker = open ? '▾' : '▸';
  const color = live ? C.lav : C.midnight;
  return (
    <Box marginTop={1}>
      <Text color={C.muted}>{`${marker} `}</Text>
      <Text color={color} bold={live}>tool calls</Text>
      <Text color={C.midnight}>{`  · ${count} turn${count > 1 ? 's' : ''}`}</Text>
      {live ? <Text color={C.lav}>{' …'}</Text> : null}
    </Box>
  );
}

// DÒNG toggle của 1 turn (sát lề trái, KHÔNG có đường gạch) — block click được
export function TurnDivider({
  index, active, open,
}: { index: number; active: boolean; open: boolean }) {
  const marker = open ? '▾' : '▸';
  const labelColor = active ? C.lav : C.midnight; // đang chạy = tím khớp tool; xong = xám mờ
  return (
    <Box marginTop={1}>
      <Text color={C.muted}>{`${marker} `}</Text>
      <Text color={labelColor} bold={active}>{`turn ${index + 1}`}</Text>
      {active
        ? <Text color={C.lav}>{' …'}</Text>
        : <Text color={C.success} bold>{' ✓'}</Text>}
    </Box>
  );
}

// Chi tiết tool của MỘT turn (không click)
export function TurnBody({ turn }: { turn: Turn }) {
  if (!turn || turn.tools.length === 0) return null;
  return (
    <Box flexDirection="column">
      {turn.tools.map((t, i) => {
        const entries = argEntries(t.arguments);
        return (
          <Box key={i} flexDirection="column" marginTop={1}>
            <Text>
              <Text color={C.midnight}>[{clock(turn.at)}]</Text> <Text color={C.lav} bold>{t.name}</Text>
            </Text>
            {entries.length === 0 ? (
              <Text color={C.midnight}>{'    '}(no args)</Text>
            ) : (
              entries.map(([k, v], j) => (
                <Text key={j} color={C.muted} wrap="wrap">
                  {'    '}<Text color={C.lightGreen}>• </Text>
                  {k ? <Text color={C.lightBlue}>{k}: </Text> : null}{v}
                </Text>
              ))
            )}
          </Box>
        );
      })}
    </Box>
  );
}
