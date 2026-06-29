// Dòng trạng thái đáy: WS + mode hiện tại + gợi ý phím. Tab đổi giữa input ↔ list.
import { Box, Text } from 'ink';
import type { WsStatus } from '../ws/useSocket';
import { C } from '../utils/theme';

const wsColor = (s: WsStatus) =>
  s === 'connected' ? C.success : s === 'connecting' ? C.warning : C.red;

export function StatusBar({
  status, count, mode, note = null,
}: { status: WsStatus; count: number; mode: 'input' | 'list'; note?: string | null }) {
  return (
    <Box justifyContent="space-between" paddingX={1} width="100%" flexShrink={0}>
      <Box flexShrink={0}>
        {note ? <Text color={C.success} bold>✓ {note}</Text> : (
          <>
            <Text color={C.muted}>WS </Text><Text color={wsColor(status)} bold>{status}</Text>
            <Text color={C.muted}>  ·  </Text><Text color={C.sky} bold>{count}</Text>
            <Text color={mode === 'list' ? C.gold : C.sky} bold>  ·  {mode === 'list' ? 'SELECT' : 'CHAT'}</Text>
          </>
        )}
      </Box>
      <Box flexShrink={1} marginLeft={2}>
        <Text color={C.muted} wrap="truncate-end">
          {'Shift+kéo: bôi đen · Ctrl+Q thoát · '}
          {mode === 'list'
            ? '↑↓ chọn · a/r/e · Tab về chat'
            : 'Tab chọn incident · PgUp/Dn cuộn'}
        </Text>
      </Box>
    </Box>
  );
}
