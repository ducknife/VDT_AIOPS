// Header của alert group: gạch màu + ◆ ALERT GROUP root + tóm tắt alert + trạng thái agent.
// Turns và incidents được Viewport render RIÊNG (thành block click-chính-xác).
import { Box, Text } from 'ink';
import type { Investigation } from '../store/useFeed';
import { C, serviceColor } from '../utils/theme';
import { LiveStatus } from './LiveStatus';

const borderByStatus = (s: Investigation['status']) =>
  s === 'failed' ? C.danger : s === 'diagnosed' ? C.sky : C.warning;

function analyzingLabel(inv: Investigation): string {
  if (inv.turns.length === 0) return 'Analyzing...';           // mới đầu: đang suy nghĩ
  return `Running tools · turn ${inv.turns.length} ...`;        // đã chạy tool: đổi nhãn
}

export function InvestigationCard({
  inv, width = 40,
}: { inv: Investigation; width?: number }) {
  const color = borderByStatus(inv.status);

  return (
    <Box flexDirection="column" marginTop={1} width="100%">
      {/* gạch màu phân cách (thay cho viền) — dài nửa màn hình */}
      <Text color={color}>{'─'.repeat(Math.max(8, Math.floor(width / 2)))}</Text>

      {/* header alert group */}
      <Box>
        <Text color={color} bold>◆ ALERT GROUP </Text>
        <Text color={serviceColor(inv.root)} bold>{inv.root ?? '…'}</Text>
        <Text color={C.muted}>  ({inv.alerts.length} alert)</Text>
      </Box>
      {/* tóm tắt alert: service tô theo màu service, :type tô lightPink */}
      <Text wrap="truncate-end">
        {inv.alerts.length === 0 ? <Text color={C.muted}>—</Text> : inv.alerts.map((a, i) => (
          <Text key={i}>
            {i > 0 ? <Text color={C.muted}>, </Text> : ''}
            <Text color={serviceColor(a.service)}>{a.service}</Text>
            <Text color={C.muted}>:</Text>
            <Text color={C.lightPink}>{a.type}</Text>
          </Text>
        ))}
      </Text>

      {/* đang chạy: nhãn pha + đồng hồ */}
      {inv.status === 'analyzing' ? (
        <Box marginTop={1}>
          <LiveStatus color={C.warning} label={analyzingLabel(inv)} />
        </Box>
      ) : null}
      {inv.status === 'failed' ? (
        <Box marginTop={1}>
          <Text color={C.danger} bold>✗ failed </Text>
          <Text color={C.muted} wrap="truncate-end">{inv.reason ?? ''}</Text>
        </Box>
      ) : null}
    </Box>
  );
}
