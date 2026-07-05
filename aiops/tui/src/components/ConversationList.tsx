// Danh sách hội thoại cho /conversation-history.
//  - ConvListHeader: tiêu đề danh sách.
//  - ConvRow: 1 hội thoại, dòng click TOGGLE (▸ đóng / ▾ đang mở).
//  - ConvOpenedHeader / ConvMsg / ConvCollapse: khối replay 1 hội thoại đã mở + nút thu gọn ở cuối.
import { Box, Text } from 'ink';
import type { ConversationSummary } from '../utils/types';
import { C } from '../utils/theme';

export function ConvListHeader({ count, width }: { count: number; width: number }) {
  return (
    <Box width={width} marginTop={1}>
      <Text backgroundColor={C.seal} color={C.contrastGray} bold wrap="truncate-end">
        {`  lịch sử hội thoại · ${count} — click 1 dòng để mở/thu`.padEnd(width)}
      </Text>
    </Box>
  );
}

export function ConvRow({ data, open }: { data: ConversationSummary; open: boolean }) {
  const preview = (data.preview ?? '').replace(/\s+/g, ' ').trim() || '(trống)';
  return (
    <Box marginLeft={2}>
      <Text color={open ? C.sky : C.muted} bold>{open ? '▾ ' : '▸ '}</Text>
      <Text color={open ? C.sky : C.white} wrap="truncate-end">{preview}</Text>
      <Text color={C.muted}>{`  · ${data.messageCount} msg`}</Text>
    </Box>
  );
}

// gạch phân cách đầu 1 hội thoại đã mở
export function ConvOpenedHeader({ preview, width }: { preview?: string; width: number }) {
  const label = ` ↳ ${(preview ?? '').replace(/\s+/g, ' ').trim().slice(0, 48) || 'hội thoại'} `;
  const side = Math.max(2, Math.floor((Math.min(width, 60) - label.length) / 2));
  return (
    <Box width={width} marginTop={1} marginLeft={2}>
      <Text color={C.gray}>{'─'.repeat(side)}<Text color={C.sky}>{label}</Text>{'─'.repeat(side)}</Text>
    </Box>
  );
}

// 1 message khi replay (user / assistant)
export function ConvMsg({ role, text }: { role: string; text: string }) {
  const isUser = role === 'user';
  return (
    <Box marginLeft={4} marginTop={1}>
      <Text color={isUser ? C.sky : C.lightGreen} bold>{isUser ? '› bạn: ' : '● duckompose: '}</Text>
      <Text color={C.contrastGray} wrap="wrap">{text}</Text>
    </Box>
  );
}

// nút thu gọn ở CUỐI 1 hội thoại đã mở (click để đóng)
export function ConvCollapse() {
  return (
    <Box marginLeft={4} marginTop={1}>
      <Text color={C.muted}>▾ </Text><Text color={C.sky} bold>thu gọn hội thoại</Text>
    </Box>
  );
}
