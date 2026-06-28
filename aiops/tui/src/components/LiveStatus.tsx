// Dòng trạng thái "đang chạy": spinner pixel + nhãn pha + ĐỒNG HỒ đếm giây.
// Đồng hồ tick mỗi 1s -> luôn nhúc nhích = bằng chứng "vẫn đang chạy" kể cả khi
// backend đang kẹt trong 1 lần gọi LLM blocking (không stream gì ra).
import { useEffect, useRef, useState } from 'react';
import { Box, Text } from 'ink';
import { C } from '../theme';
import { WaveSpinner } from './WaveSpinner';

export function LiveStatus({
  label, color = C.blue, showSpinner = true,
}: { label: string; color?: string; showSpinner?: boolean }) {
  const start = useRef(Date.now()); // mốc = lúc dòng này xuất hiện (≈ lúc pha bắt đầu)
  const [, force] = useState(0);
  useEffect(() => {
    const t = setInterval(() => force((n) => n + 1), 1000);
    return () => clearInterval(t);
  }, []);

  const secs = Math.floor((Date.now() - start.current) / 1000);
  const clock = `${Math.floor(secs / 60)}:${String(secs % 60).padStart(2, '0')}`;

  return (
    <Box>
      {showSpinner ? <WaveSpinner color={color} /> : null}
      <Text color={color}>{showSpinner ? ' ' : ''}{label} </Text>
      <Text color={C.muted}>{clock}</Text>
    </Box>
  );
}
