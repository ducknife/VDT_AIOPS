// Layout dashboard: Header (cố định trên) / Viewport (cuộn theo dòng, giữa) / Input + Status (cố định đáy).
// 2 mode: 'input' (gõ hỏi) ↔ 'list' (chọn incident bằng phím, a/r/e). Tab để đổi.
import { useRef, useState, useMemo, useEffect } from 'react';
import { Box, useInput, useApp, measureElement } from 'ink';
import { useSocket } from './ws/useSocket';
import { useFeed, selectableIds, findIncident } from './store/useFeed';
import { Header } from './components/Header';
import { Viewport, type ViewportHandle } from './components/Viewport';
import { InputBar } from './components/InputBar';
import { StatusBar } from './components/StatusBar';
import { useTerminalSize } from './hooks/useTerminalSize';
import { useMouse } from './hooks/useMouse';
import { C } from './theme';
import { clipboardCopy } from './format';
import { incidentFullText } from './components/IncidentRow';

const WS_URL = 'ws://localhost:8088/ws/incidents';

export default function App() {
  const conversationId = useRef(`tui-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`).current;
  const { exit } = useApp();
  const { state, onFrame, pushUser } = useFeed();
  const { status, send } = useSocket(WS_URL, onFrame);
  const { cols, rows } = useTerminalSize();

  const vp = useRef<ViewportHandle>(null);
  const headerRef = useRef<any>(null);
  const [headerH, setHeaderH] = useState(0); // chiều cao header -> đổi click-row tuyệt đối sang trong viewport
  const [mode, setMode] = useState<'input' | 'list'>('input');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [expanded, setExpanded] = useState<Set<number>>(new Set());
  const [expandedTurns, setExpandedTurns] = useState<Set<string>>(new Set()); // turn-list nào đang mở rộng
  const [flash, setFlash] = useState<string | null>(null); // thông báo ngắn (vd "đã copy")
  const flashTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const showFlash = (msg: string) => {
    setFlash(msg);
    if (flashTimer.current) clearTimeout(flashTimer.current);
    flashTimer.current = setTimeout(() => setFlash(null), 1500);
  };

  const ids = useMemo(() => selectableIds(state), [state]); // id incident theo thứ tự hiển thị

  // đổi nền terminal sang Panda editor bg (#292A2B) khi mở app (OSC 11), trả lại nền mặc định khi thoát (OSC 111).
  // Chỉ chạy ở terminal hỗ trợ OSC 11 (Windows Terminal, iTerm2, kitty… đa số terminal hiện đại).
  useEffect(() => {
    process.stdout.write(`\x1b]11;${C.bgLight}\x07`);
    return () => { process.stdout.write('\x1b]111\x07'); };
  }, []);

  // đo chiều cao header sau mỗi render (đổi khi resize/đổi nội dung)
  useEffect(() => {
    if (headerRef.current) {
      const h = measureElement(headerRef.current).height;
      if (h && h !== headerH) setHeaderH(h);
    }
  });

  // mouse: lăn = cuộn; kéo thanh cuộn; click ô input -> focus; click item -> mở/thu tool
  const dragScroll = useRef(false); // đang kéo thanh cuộn?
  useMouse({
    onWheel: (dir) => vp.current?.scrollBy(dir === 'up' ? -2 : 2),
    onClick: (col, row) => {
      if (col >= cols) { dragScroll.current = true; vp.current?.scrollToRow(row - headerH); return; } // thanh cuộn (cột cuối)
      if (row >= rows - 2) { setMode('input'); return; } // click vùng ô input -> focus gõ
      const hit = vp.current?.hitTest(row - headerH - 1); // 0-based dòng trong viewport
      if (!hit) return;
      if (hit.type === 'inc') {
        // click dòng incident -> sang list mode + chọn (hiện nút a/r/e) + mở/thu chi tiết
        setMode('list');
        setSelectedId(hit.id);
        setExpanded((s) => {
          const n = new Set(s);
          n.has(hit.id) ? n.delete(hit.id) : n.add(hit.id);
          return n;
        });
      } else if (hit.type === 'act') {
        // click nút trên incident
        if (hit.action === 'a') send({ command: 'ack', incidentId: hit.id });
        else if (hit.action === 'r') send({ command: 'resolve', incidentId: hit.id });
        else setExpanded((s) => { // 'e' -> toggle chi tiết
          const n = new Set(s);
          n.has(hit.id) ? n.delete(hit.id) : n.add(hit.id);
          return n;
        });
      } else if (hit.type === 'copy') {
        clipboardCopy(hit.text); // click tiêu đề mục / đầu câu trả lời -> copy khối đó
        showFlash(`đã copy: ${hit.label}`);
      } else {
        // click dòng tool toggle -> mở/thu danh sách tool
        setExpandedTurns((s) => {
          const n = new Set(s);
          n.has(hit.id) ? n.delete(hit.id) : n.add(hit.id);
          return n;
        });
      }
    },
    onDrag: (_col, row) => { if (dragScroll.current) vp.current?.scrollToRow(row - headerH); }, // kéo scrollbar
    onRelease: () => { dragScroll.current = false; },
  });

  // PHÍM TOÀN CỤC: Ctrl+Q thoát, Tab đổi mode, PgUp/Dn cuộn nhanh (luôn bật)
  useInput((input, key) => {
    if (key.ctrl && input === 'q') { exit(); return; } // thoát app (Ctrl+C đã tắt để copy an toàn)
    if (key.tab) {
      setMode((m) => {
        const next = m === 'input' ? 'list' : 'input';
        if (next === 'list' && selectedId == null && ids.length) setSelectedId(ids[ids.length - 1]);
        return next;
      });
    } else if (key.pageUp) vp.current?.scrollBy(-6);
    else if (key.pageDown) vp.current?.scrollBy(6);
  });

  // CHAT mode: ↑↓ cuộn (dễ bấm). List mode dùng ↑↓ để chọn nên hook này tắt.
  useInput(
    (_input, key) => {
      if (key.upArrow) vp.current?.scrollBy(-2);
      else if (key.downArrow) vp.current?.scrollBy(2);
    },
    { isActive: mode === 'input' },
  );

  // PHÍM LIST-MODE: ↑↓ chọn, a/r/e hành động (chỉ bật khi mode==='list')
  useInput(
    (input, key) => {
      if (key.upArrow || key.downArrow) {
        const idx = selectedId != null ? ids.indexOf(selectedId) : ids.length;
        const ni = Math.max(0, Math.min(ids.length - 1, key.upArrow ? idx - 1 : idx + 1));
        setSelectedId(ids[ni] ?? null);
      } else if (input === 'a' && selectedId != null) {
        send({ command: 'ack', incidentId: selectedId });
      } else if (input === 'r' && selectedId != null) {
        send({ command: 'resolve', incidentId: selectedId });
      } else if (input === 'e' && selectedId != null) {
        setExpanded((s) => {
          const n = new Set(s);
          n.has(selectedId) ? n.delete(selectedId) : n.add(selectedId);
          return n;
        });
      } else if (input === 'c' && selectedId != null) {
        const inc = findIncident(state, selectedId); // copy TOÀN BỘ incident đang chọn
        if (inc) { clipboardCopy(incidentFullText(inc)); showFlash(`đã copy: incident #${selectedId}`); }
      } else if (key.escape) {
        setMode('input');
      }
    },
    { isActive: mode === 'list' },
  );

  const handleSubmit = (raw: string) => {
    const text = raw.trim();
    if (!text) return;

    if (text.startsWith('/ack ')) {
      const id = Number(text.slice(5).trim());
      if (!Number.isNaN(id)) send({ command: 'ack', incidentId: id });
    } else if (text.startsWith('/resolve ')) {
      const id = Number(text.slice(9).trim());
      if (!Number.isNaN(id)) send({ command: 'resolve', incidentId: id });
    } else {
      pushUser(text);
      vp.current?.scrollToBottom(); // hỏi xong nhảy về đáy thấy câu vừa gửi
      // chat LUÔN free; agent tự tra incident qua tool (ListActiveIncidents/GetIncident) khi cần
      send({ command: 'ask', conversationId, text });
    }
  };

  return (
    <Box flexDirection="column" height={rows}>
      <Box ref={headerRef} flexShrink={0}>
        <Header />
      </Box>
      <Viewport ref={vp} state={state} selectedId={selectedId} expandedIds={expanded} expandedTurns={expandedTurns} />
      <InputBar onSubmit={handleSubmit} isActive={mode === 'input'} />
      <StatusBar status={status} count={state.live.length + state.snapshot.length} mode={mode} note={flash} />
    </Box>
  );
}
