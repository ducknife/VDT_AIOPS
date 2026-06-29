// Lớp transport: chỉ lo kết nối WebSocket, parse JSON, gọi onFrame.
// KHÔNG biết gì về UI hay business logic -> đây chính là chỗ "decouple engine khỏi render"
// mà sách gọi: engine đẩy event, transport này nhận, store ở tầng trên mới hiểu nghĩa.

import { useEffect, useRef, useState } from 'react';
import WebSocket from 'ws';
import type { Command, Frame } from '../utils/types';

export type WsStatus = 'connecting' | 'connected' | 'closed' | 'error';

export function useSocket(url: string, onFrame: (f: Frame) => void) {
  const [status, setStatus] = useState<WsStatus>('connecting');
  const sockRef = useRef<WebSocket | null>(null);
  // giữ onFrame mới nhất mà không phải reconnect mỗi lần nó đổi (nó đổi mỗi render)
  const onFrameRef = useRef(onFrame);
  onFrameRef.current = onFrame;

  useEffect(() => {
    let closed = false;        // cờ: component unmount -> ngừng reconnect
    let timer: NodeJS.Timeout;

    const connect = () => {
      setStatus('connecting');
      const socket = new WebSocket(url);
      sockRef.current = socket;

      socket.on('open', () => setStatus('connected'));
      socket.on('message', (raw) => {
        try {
          onFrameRef.current(JSON.parse(raw.toString()) as Frame);
        } catch {
          /* frame hỏng -> bỏ qua, không làm sập TUI */
        }
      });
      socket.on('error', () => setStatus('error'));
      socket.on('close', () => {
        setStatus('closed');
        if (!closed) timer = setTimeout(connect, 1500); // tự reconnect: monitor phải bền
      });
    };

    connect();
    return () => {
      closed = true;
      clearTimeout(timer);
      sockRef.current?.close();
    };
  }, [url]);

  // gửi lệnh lên server (ack/resolve/ask)
  const send = (cmd: Command) => {
    const s = sockRef.current;
    if (s && s.readyState === WebSocket.OPEN) s.send(JSON.stringify(cmd));
  };

  return { status, send };
}
