// Mouse layer (SGR): wheel + left-click + KÉO (drag) + nhả. Toạ độ 1-based của terminal.
//  - wheel up = Cb 64, down = 65
//  - left press = Cb 0 (M) / release = Cb 0 (m)
//  - left DRAG (motion khi giữ nút) = Cb 32 (= nút 0 + cờ motion 0x20) — cần bật 1002.
// Hit-test (click/drag trúng phần tử nào) là việc của tầng trên - hook chỉ cấp toạ độ.
import { useEffect, useRef } from 'react';
import { useStdin } from 'ink';

export interface MouseHandlers {
  onWheel?: (dir: 'up' | 'down') => void;
  onClick?: (col: number, row: number) => void; // 1-based
  onDrag?: (col: number, row: number) => void;   // giữ nút trái + di chuột
  onRelease?: () => void;
  enabled?: boolean; // false -> NHẢ chuột cho terminal (bôi đen / copy mặc định chạy lại)
}

export function useMouse(handlers: MouseHandlers) {
  const { stdin, setRawMode, isRawModeSupported } = useStdin();
  const enabled = handlers.enabled !== false;
  const h = useRef(handlers);
  h.current = handlers;

  useEffect(() => {
    if (!isRawModeSupported || !stdin) return;
    if (!enabled) {
      // tắt hẳn mouse-reporting -> terminal tự bôi đen/copy lại được
      process.stdout.write('\x1b[?1000l\x1b[?1002l\x1b[?1006l');
      return;
    }
    setRawMode(true);
    // 1000 = click, 1002 = motion KHI giữ nút (cho kéo), 1006 = toạ độ SGR
    process.stdout.write('\x1b[?1000h\x1b[?1002h\x1b[?1006h');

    const onData = (data: Buffer) => {
      const s = data.toString();
      const re = /\x1b\[<(\d+);(\d+);(\d+)([Mm])/g;
      let m: RegExpExecArray | null;
      while ((m = re.exec(s)) !== null) {
        const cb = Number(m[1]);
        const col = Number(m[2]);
        const row = Number(m[3]);
        const press = m[4] === 'M';
        if (cb === 64) h.current.onWheel?.('up');
        else if (cb === 65) h.current.onWheel?.('down');
        else if (cb === 32) h.current.onDrag?.(col, row);          // kéo nút trái
        else if (cb === 0 && press) h.current.onClick?.(col, row); // bấm
        else if (cb === 0 && !press) h.current.onRelease?.();      // nhả
      }
    };

    stdin.on('data', onData);
    return () => {
      stdin.off('data', onData);
      process.stdout.write('\x1b[?1000l\x1b[?1002l\x1b[?1006l'); // tắt khi thoát / khi disable
    };
  }, [stdin, isRawModeSupported, setRawMode, enabled]);
}
