// Tiếng "ding" thông báo khi có sự kiện đáng chú ý (phát hiện lỗi / snapshot lúc kết nối).
// Một tiếng nhẹ như notification — KHÔNG phải còi hú. Tắt bằng env DUCKOMPOSE_SOUND=off.
import { exec } from 'node:child_process';

let last = 0;

export function notify(): void {
  const now = Date.now();
  if (now - last < 500) return; // chống double-fire (2 frame sát nhau)
  last = now;
  if (process.env.DUCKOMPOSE_SOUND === 'off') return;

  try {
    if (process.platform === 'win32') {
      // "ding" thông báo chuẩn của Windows — chạy ở process PowerShell riêng, không chặn TUI
      exec('powershell -NoProfile -Command "[System.Media.SystemSounds]::Asterisk.Play()"');
    } else if (process.platform === 'darwin') {
      exec('afplay /System/Library/Sounds/Ping.aiff');
    } else {
      process.stdout.write('\x07'); // Linux / fallback: chuông terminal
    }
  } catch {
    /* không có loa / lỗi phát -> im lặng, không làm sập TUI */
  }
}
