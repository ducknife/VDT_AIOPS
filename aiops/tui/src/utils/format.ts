// Tiện ích format cho live activity.

// timestamp ms -> "HH:mm:ss" (dễ đọc cho log turn). Muốn kèm ngày: bật phần dd/MM.
export const clock = (ms: number): string => {
  const d = new Date(ms);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
};

// Bỏ emoji / pictograph màu (😀 🎉 ✅ ⚠️ 🔴 🟢 ❌ …) khỏi text agent trả về —
// prompt không chặn được 100% nên lọc ở tầng render cho chắc. GIỮ LẠI các ký hiệu
// văn bản hữu ích (✓ ✗ ◆ ▸ • → ↑ ↓, box-drawing) vì chúng KHÔNG phải Extended_Pictographic.
const EMOJI = /[\p{Extended_Pictographic}\u{1F3FB}-\u{1F3FF}\u{FE0F}\u{20E3}\u{200D}]/gu;
export const stripEmoji = (s: string | null | undefined): string =>
  (s ?? '').replace(EMOJI, '').replace(/ {2,}/g, ' ').replace(/[ \t]+$/gm, '');

// copy text ra clipboard hệ thống qua OSC 52 (đa số terminal hiện đại hỗ trợ)
export const clipboardCopy = (text: string) =>
  process.stdout.write(`\x1b]52;c;${Buffer.from(text, 'utf8').toString('base64')}\x07`);

// word-wrap chuỗi về bề rộng `width` -> mảng dòng (từ dài hơn width thì cắt cứng)
export const wrapText = (s: string, width: number): string[] => {
  if (width <= 0) return [s];
  const out: string[] = [];
  for (const para of s.split('\n')) {
    let line = '';
    for (const word of para.split(' ')) {
      let wd = word;
      while (wd.length > width) { // từ quá dài -> cắt cứng
        if (line) { out.push(line); line = ''; }
        out.push(wd.slice(0, width));
        wd = wd.slice(width);
      }
      const cand = line ? `${line} ${wd}` : wd;
      if (cand.length > width) { out.push(line); line = wd; }
      else line = cand;
    }
    out.push(line);
  }
  return out.length ? out : [''];
};

// tham số tool -> danh sách [key, value] để hiện mỗi dòng 1 tham số (không cắt cụt)
export const argEntries = (args: unknown): [string, string][] => {
  if (args == null) return [];
  if (typeof args === 'string') return args.trim() ? [['', args]] : [];
  if (typeof args === 'object') {
    return Object.entries(args as Record<string, unknown>)
      .map(([k, v]) => [k, typeof v === 'object' ? JSON.stringify(v) : String(v)] as [string, string]);
  }
  return [['', String(args)]];
};

// tham số tool (Map/obj/string) -> "key=val, key=val"
export const formatArgs = (args: unknown): string => {
  if (args == null) return '';
  if (typeof args === 'string') return args;
  if (typeof args === 'object') {
    return Object.entries(args as Record<string, unknown>)
      .map(([k, v]) => `${k}=${typeof v === 'object' ? JSON.stringify(v) : String(v)}`)
      .join(', ');
  }
  return String(args);
};
