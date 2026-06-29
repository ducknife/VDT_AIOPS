// Ô nhập đáy. useInput của Ink bắt phím trực tiếp -> không cần ink-text-input.
//  - Enter: gửi · Backspace: xoá · ký tự thường / ĐOẠN DÁN (nhiều ký tự): nối vào
//  - Ctrl+A: CHỌN TẤT CẢ + COPY ra clipboard (OSC 52). Khi đang chọn: gõ -> thay thế, Backspace/Ctrl+U -> xoá.
//  - Ctrl+Z: undo (hoàn tác thay đổi gần nhất). Paste: Ctrl+Shift+V / chuột phải của terminal.
import { useRef, useState } from 'react';
import { Box, Text, useInput } from 'ink';
import { C } from '../theme';
import { clipboardCopy } from '../format';
import { useTerminalSize } from '../hooks/useTerminalSize';

// lọc bỏ chuỗi escape lọt vào input:
//  - SGR mouse "[<col;row M/m" (Ink hay NUỐT ESC nên phải bắt cả khi KHÔNG có \x1b)
//  - CSI khác có ESC (bracketed-paste 200~/201~, di chuyển con trỏ…)
//  - ký tự control còn sót
const sanitize = (s: string) =>
  s.replace(/\x1b?\[<\d+;\d+;\d+[Mm]/g, '')
    .replace(/\x1b\[[0-9;<>?]*[ -/]*[@-~]/g, '')
    .replace(/[\x00-\x1f\x7f]/g, '');

export function InputBar({
  onSubmit, isActive = true,
}: { onSubmit: (value: string) => void; isActive?: boolean }) {
  const [value, setValue] = useState('');
  const [allSel, setAllSel] = useState(false); // đang bôi đen toàn bộ?
  const undo = useRef<string[]>([]);            // stack lịch sử để Ctrl+Z hoàn tác
  const { cols } = useTerminalSize();

  // đổi giá trị + lưu trạng thái cũ vào stack undo
  const commit = (next: string) => {
    undo.current.push(value);
    if (undo.current.length > 100) undo.current.shift();
    setValue(next);
  };

  useInput((input, key) => {
    if (key.ctrl && input === 'z') { // Ctrl+Z: hoàn tác
      const prev = undo.current.pop();
      if (prev !== undefined) setValue(prev);
      setAllSel(false);
      return;
    }
    if (key.ctrl && input === 'a') { // Ctrl+A: chọn tất cả + copy
      if (value) { setAllSel(true); clipboardCopy(value); }
      return;
    }
    if (key.return) {
      onSubmit(value);
      setValue(''); setAllSel(false); undo.current = []; // gửi xong -> xoá lịch sử
      return;
    }
    if (key.ctrl && input === 'u') { // Ctrl+U: xoá sạch
      commit(''); setAllSel(false);
      return;
    }
    if (key.backspace || key.delete) {
      commit(allSel ? '' : value.slice(0, -1)); // đang chọn -> xoá hết
      setAllSel(false);
      return;
    }
    if (key.leftArrow || key.rightArrow || key.escape) {
      setAllSel(false); // bỏ chọn
      return;
    }
    if (key.ctrl || key.meta || key.tab) return; // bỏ qua tổ hợp điều khiển khác
    // ký tự thường HOẶC đoạn DÁN (nhiều ký tự): lọc escape/mouse/control rồi gộp
    const clean = sanitize(input);
    if (clean) {
      commit(allSel ? clean : value + clean); // đang chọn -> thay thế; không -> nối
      setAllSel(false);
    }
  }, { isActive });

  return (
    <Box
      borderStyle="single"
      borderColor={C.blue}
      borderLeft={false}
      borderRight={false}
      width="100%"
      flexShrink={0}
    >
      {/* ô nhập: nền seal trải full chiều rộng (đệm khoảng trắng cùng nền) */}
      <Text wrap="truncate-start">
        <Text color={C.orange}>{' › '}</Text>
        {/* đang chọn tất cả -> tô nền sky cho cả chuỗi (bôi đen) */}
        {allSel
          ? <Text color={C.bgDark} backgroundColor={C.sky}>{value}</Text>
          : <Text color={C.white}>{value}</Text>}
        <Text color={C.muted}>▌</Text>
        {' '.repeat(Math.max(0, cols - 4 - value.length))}
      </Text>
    </Box>
  );
}
