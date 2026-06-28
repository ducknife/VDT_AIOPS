// Loading: 4 ô pixel CAO DẦN (▂▄▆█) hiện dần TRÁI→PHẢI rồi lặp lại — kiểu "đẩy pixel ra".
import { useEffect, useState } from 'react';
import { Text } from 'ink';
import { C } from '../theme';

const BARS = ['▂', '▄', '▆', '█']; // 4 ô cao dần

export function WaveSpinner({ color = C.blue }: { color?: string }) {
  const [n, setN] = useState(0); // số ô đang hiện (0..4); chạy 0→4 rồi reset -> "load đi load lại"
  useEffect(() => {
    const t = setInterval(() => setN((p) => (p + 1) % (BARS.length + 1)), 95);
    return () => clearInterval(t);
  }, []);

  return (
    <Text>
      {/* ô chưa tới lượt -> để khoảng trắng (giữ bề rộng cố định = 4) */}
      {BARS.map((ch, i) => (
        <Text key={i} color={color} bold>{i < n ? ch : ' '}</Text>
      ))}
    </Text>
  );
}
