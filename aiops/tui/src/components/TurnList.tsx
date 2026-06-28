// Turn (tool) hiển thị tách 2 phần để click CHÍNH XÁC:
//  - TurnToggle: 1 DÒNG có icon ▸/▾ -> đây là dòng DUY NHẤT click để mở/thu.
//  - TurnDetail: danh sách tool + tham số (không bắt click).
import { Box, Text } from 'ink';
import type { Turn } from '../store/useFeed';
import { C } from '../theme';
import { clock, argEntries } from '../format';

const countTools = (turns: Turn[]) => turns.reduce((a, t) => a + t.tools.length, 0);

// DÒNG icon mở/thu (block click được)
export function TurnToggle({ turns, open }: { turns: Turn[]; open: boolean }) {
  if (!turns || turns.length === 0) return null;
  const n = countTools(turns);
  return (
    <Text color={C.muted}>
      <Text color={C.lightGreen}>{open ? '▾ ' : '▸ '}</Text>
      <Text color={C.sky} bold>{n}</Text> tool call{n > 1 ? 's' : ''} · click để {open ? 'thu gọn' : 'mở rộng'}
    </Text>
  );
}

// Danh sách chi tiết (không click)
export function TurnDetail({ turns }: { turns: Turn[] }) {
  if (!turns || turns.length === 0) return null;
  return (
    <Box flexDirection="column">
      {turns.flatMap((turn, ti) =>
        turn.tools.map((t, i) => {
          const entries = argEntries(t.arguments);
          return (
            <Box key={`${ti}-${i}`} flexDirection="column" marginTop={1}>
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
        }),
      )}
    </Box>
  );
}
