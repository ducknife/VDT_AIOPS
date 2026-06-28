// PANDA THEME (VS Code) — full palette. P = bảng màu gốc; C = vai trò ngữ nghĩa
// (alias trỏ vào P) để code dùng. Nền tối #242526 (đặt qua OSC 11).
const P = {
    // blue / purple / green
    blue: '#45A9F9',
    lightBlue: '#6FC1FF',
    purple: '#B084EB',
    lightPurple: '#BCAAFE',
    green: '#19F9D8',
    lightGreen: '#6FE7D2',
    // pink / red / orange
    red: '#FF2C6D',
    lightRed: '#FF4B82',
    orange: '#FFB86C',
    lightOrange: '#FFCC95',
    pink: '#FF75B5',
    lightPink: '#FF9AC1',
    // grayscale
    fg: '#E6E6E6', // chữ chính
    contrastGray: '#BBBBBB',
    gray: '#757575', // comment/muted
    midnight: '#676B79',
    steel: '#3E4250',
    graybg: '#373B41',
    seal: '#31353A',
    charcoal: '#222223',
    bgDark: '#242526',
    bgLight: '#292A2B',
};

export const C = {
    ...P, // tất cả màu Panda gốc đều dùng được: C.pink, C.lightGreen, ...
    // ── vai trò ngữ nghĩa ──
    blue: P.blue, // chủ đạo
    deep: P.steel, // viền nền/divider/structure (dịu) — nền cho dải sáng chạy
    sky: P.lightBlue, // highlight
    teal: P.green, // accent MÁT (chữ in đậm, title)
    gold: P.orange, // accent ẤM (header)
    lav: P.purple, // phối tím (tiết chế)
    white: P.fg, // chữ chính
    muted: P.gray, // chữ phụ
    danger: P.lightRed,
    warning: P.lightOrange,
    success: P.green,
    panel: P.steel, // nền bong bóng user
    pixelBg: P.graybg, // (dự phòng)
};

// severity (toàn màu Panda): P1 = đỏ hồng #FF2C6D -> P2 cam -> P3 TÍM -> P4 xám mờ
export const severityColor = (s: string) =>
    ({ P1: C.red, P2: C.orange, P3: C.lav, P4: C.midnight } as any)[s] ?? C.midnight;

export const statusColor = (s: string) =>
    ({ NEW: C.sky, ACKNOWLEDGED: C.warning, RESOLVED: C.success } as any)[s] ?? C.muted;

// color-code từng service cho dễ phân biệt khắp UI (incident, alert, root, log)
export const serviceColor = (s?: string) => {
    const n = (s ?? '').toLowerCase();
    if (n.includes('nginx')) return C.green;
    if (n.includes('node') || n.includes('api')) return C.lightBlue;
    if (n.includes('redis')) return C.lightRed;
    if (n.includes('postgres') || n.includes('pg') || n.includes('db')) return C.lightPurple;
    return C.pink; // service khác
};
