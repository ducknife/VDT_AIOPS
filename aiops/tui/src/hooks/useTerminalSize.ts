// Kích thước terminal (cột + dòng), tự cập nhật khi resize. Viewport cần `rows` để cắt chiều cao.
import { useStdout } from 'ink';
import { useEffect, useState } from 'react';

export interface TermSize {
  cols: number;
  rows: number;
}

export function useTerminalSize(): TermSize {
  const { stdout } = useStdout();
  const [size, setSize] = useState<TermSize>({ cols: stdout.columns ?? 80, rows: stdout.rows ?? 24 });
  useEffect(() => {
    const onResize = () => setSize({ cols: stdout.columns ?? 80, rows: stdout.rows ?? 24 });
    stdout.on('resize', onResize);
    return () => {
      stdout.off('resize', onResize);
    };
  }, [stdout]);
  return size;
}
