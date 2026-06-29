// Bong bóng chat tách phần để click CHÍNH XÁC (turn toggle render riêng ở Viewport):
//  - ChatUser: thanh xám câu hỏi người dùng.
//  - ChatHead: dòng "● duckompose" (header assistant).
//  - ChatBody: text trả lời (markdown / stream raw) hoặc trạng thái "Thinking…".
import { Box, Text } from 'ink';
import type { ChatItem } from '../store/useFeed';
import { C } from '../utils/theme';
import { stripEmoji, wrapText } from '../utils/format';
import { LiveStatus } from './LiveStatus';
import { WaveSpinner } from './WaveSpinner';
import { Markdown } from './Markdown';

function chatLabel(item: ChatItem): string {
  const last = item.turns.at(-1)?.tools.at(-1)?.name;
  return last ? `${last}() · processing...` : 'Thinking...';
}

export function ChatUser({ item, width = 40 }: { item: ChatItem; width?: number }) {
  const w = Math.max(10, width);
  // câu dài -> xuống dòng. NỔI BẬT: vạch dọc trái (sky) + nền sáng hơn (steel) + chữ trắng đậm.
  const lines = wrapText(item.text, w - 3);
  return (
    <Box flexDirection="column" marginTop={1} marginBottom={1} width="100%">
      {lines.map((l, i) => (
        <Text key={i} backgroundColor={C.steel} wrap="truncate-end">
          <Text color={C.orange} bold>▌ </Text>
          <Text color={C.white}>{`${l} `.padEnd(w - 2)}</Text>
        </Text>
      ))}
    </Box>
  );
}

export function ChatHead({ item }: { item: ChatItem }) {
  return (
    <Box marginTop={1}>
      {item.streaming ? <WaveSpinner color={C.sky} /> : <Text color={C.sky} bold>●</Text>}
      <Text color={C.teal} bold> duckompose</Text>
    </Box>
  );
}

export function ChatBody({ item, width = 80 }: { item: ChatItem; width?: number }) {
  if (item.text) {
    return item.streaming ? <Text color={C.contrastGray}>{stripEmoji(item.text)}</Text> : <Markdown text={item.text} width={width} />;
  }
  if (item.streaming) {
    return <LiveStatus color={C.sky} label={chatLabel(item)} showSpinner={false} />;
  }
  return null;
}
