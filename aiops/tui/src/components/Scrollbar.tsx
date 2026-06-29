// Thanh cuộn dọc bên phải: thumb (█) phản ánh vị trí + tỉ lệ khung/nội dung.
import { Box, Text } from 'ink';
import { C } from '../utils/theme';

export function Scrollbar({
  height, contentH, scroll, maxScroll,
}: { height: number; contentH: number; scroll: number; maxScroll: number }) {
  if (height <= 0 || contentH <= height) {
    return <Box width={1} flexShrink={0} />; // nội dung vừa khung -> không cần thanh
  }
  const thumbH = Math.max(1, Math.round((height * height) / contentH));
  const maxPos = height - thumbH;
  const pos = maxScroll > 0 ? Math.round((scroll / maxScroll) * maxPos) : 0;

  const rows = [];
  for (let i = 0; i < height; i++) {
    const on = i >= pos && i < pos + thumbH;
    // thumb (▐ sky) nổi trên track nền xám (graybg) -> rãnh cuộn thấy rõ
    rows.push(
      on
        ? <Text key={i} color={C.sky}>▐</Text>
        : <Text key={i} backgroundColor={C.graybg}> </Text>,
    );
  }
  return (
    <Box flexDirection="column" width={1} flexShrink={0}>
      {rows}
    </Box>
  );
}
