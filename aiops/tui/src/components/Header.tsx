// Header: viền 1 đường (dải sáng chạy theo kim đồng hồ), title "Aiops Monitoring
// Platform" NẰM TRÊN đường ở góc trên-trái (kiểu Claude Code).
//  TRÁI : logo.   PHẢI : DUCKOMPOSE (tiêu đề) + mô tả.
import { Box, Text } from 'ink';
import { Logo } from '../logo';
import { Brand } from '../brand';
import { C } from '../theme';
import { useTerminalSize } from '../hooks/useTerminalSize';
import { AnimatedBorder } from './AnimatedBorder';

const CONTENT_H = 6; // logo 6 dòng = chiều cao nội dung

function Divider() {
  return (
    <Box flexDirection="column" marginX={1} flexShrink={0}>
      {Array.from({ length: CONTENT_H }, (_, i) => (
        <Text key={i} color={C.blue}>│</Text>
      ))}
    </Box>
  );
}

// PHẢI: DUCKOMPOSE tiêu đề + mô tả
function Title() {
  return (
    <Box flexDirection="column" justifyContent="center" flexGrow={1} flexShrink={1} marginLeft={2}>
      <Brand />
      <Box flexDirection="column" marginTop={1}>
        <Text color={C.teal} wrap="truncate-end">AIOps agent · giám sát & chẩn đoán sự cố realtime</Text>
        <Text color={C.muted} wrap="truncate-end">Chat: gõ câu hỏi · Tab: chọn incident · a/r/e</Text>
        <Text color={C.muted} wrap="truncate-end">PgUp/Dn · lăn chuột: cuộn</Text>
      </Box>
    </Box>
  );
}

export function Header() {
  const { cols } = useTerminalSize();
  const compact = cols < 64;

  return (
    <AnimatedBorder width={Math.max(20, cols)} height={CONTENT_H + 4} title="Aiops Monitoring Platform">
      <Box flexDirection="row" height={CONTENT_H}>
        {compact ? null : <Box flexShrink={0}><Logo /></Box>}
        {compact ? null : <Divider />}
        <Title />
      </Box>
    </AnimatedBorder>
  );
}
