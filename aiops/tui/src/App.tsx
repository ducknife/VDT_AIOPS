// Layout dashboard: Header (cố định trên) / Viewport (cuộn theo dòng, giữa) / Input + Status (cố định đáy).
// 2 mode: 'input' (gõ hỏi) ↔ 'list' (chọn incident bằng phím, a/r/e). Tab để đổi.
import { useRef, useState, useMemo, useEffect } from 'react';
import { Box, useInput, useApp, measureElement } from 'ink';
import { useSocket } from './ws/useSocket';
import { useFeed, selectableIds, findIncident, isConversationOpen } from './store/useFeed';
import { Header } from './components/Header';
import { Viewport, type ViewportHandle } from './components/Viewport';
import { InputBar } from './components/InputBar';
import { StatusBar } from './components/StatusBar';
import { useTerminalSize } from './hooks/useTerminalSize';
import { useMouse } from './hooks/useMouse';
import { C } from './utils/theme';
import { clipboardCopy } from './utils/format';
import { incidentFullText, MISS_ORDER } from './components/IncidentRow';
import type { Verdict, MissCategory, FeedbackDraft } from './utils/types';

// Cho bên nhận trỏ engine ở máy/cổng khác: đặt DUCKOMPOSE_WS. Mặc định = localhost:8088.
const WS_URL = process.env.DUCKOMPOSE_WS ?? 'ws://localhost:8088/ws/incidents';

export default function App() {
  const conversationId = useRef(`tui-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`).current;
  const { exit } = useApp();
  const { state, onFrame, pushUser, closeConversation } = useFeed();
  const { status, send } = useSocket(WS_URL, onFrame);
  const { cols, rows } = useTerminalSize();

  const vp = useRef<ViewportHandle>(null);
  const headerRef = useRef<any>(null);
  const [headerH, setHeaderH] = useState(0); // chiều cao header -> đổi click-row tuyệt đối sang trong viewport
  const [mode, setMode] = useState<'input' | 'list'>('input');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [expanded, setExpanded] = useState<Set<number>>(new Set());
  const [expandedTurns, setExpandedTurns] = useState<Set<string>>(new Set()); // turn nào đang mở rộng
  const [expandedTools, setExpandedTools] = useState<Set<string>>(new Set()); // "tool calls" nào đang mở rộng
  const [flash, setFlash] = useState<string | null>(null); // thông báo ngắn (vd "đã copy")
  // form feedback nhiều bước: verdict -> (miss nếu != correct) -> note -> submit
  const [feedbackDraft, setFeedbackDraft] = useState<FeedbackDraft | null>(null);
  const flashTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const showFlash = (msg: string) => {
    setFlash(msg);
    if (flashTimer.current) clearTimeout(flashTimer.current);
    flashTimer.current = setTimeout(() => setFlash(null), 1500);
  };

  // chọn verdict: correct -> sang bước note; partial/wrong -> sang bước miss
  const chooseVerdict = (incidentId: number, verdict: Verdict) => {
    setFeedbackDraft({ incidentId, verdict, stage: verdict === 'correct' ? 'note' : 'miss', note: '' });
  };
  // chọn miss -> sang bước note
  const chooseMiss = (cat: MissCategory) => {
    setFeedbackDraft((d) => (d ? { ...d, missed: cat, stage: 'note' } : d));
  };
  // gửi feedback (store-only): verdict + missed + note
  const submitDraft = () => {
    setFeedbackDraft((d) => {
      if (!d || !d.verdict) return d;
      send({ command: 'feedback', incidentId: d.incidentId, verdict: d.verdict, missed: d.missed, text: d.note || undefined });
      showFlash(`feedback #${d.incidentId}: ${d.verdict}${d.missed ? ' · ' + d.missed : ''}`);
      return null;
    });
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
      } else if (hit.type === 'fb') {
        chooseVerdict(hit.id, hit.verdict); // bước 1: verdict
      } else if (hit.type === 'miss') {
        chooseMiss(hit.cat); // bước 2: miss -> sang note
      } else if (hit.type === 'convopen') {
        // TOGGLE: đang mở -> thu gọn; chưa mở -> nạp & mở (chỉ mở 1 lần, không mở trùng)
        if (isConversationOpen(state, hit.id)) closeConversation(hit.id);
        else send({ command: 'get-conversation', conversationId: hit.id });
      } else if (hit.type === 'convclose') {
        closeConversation(hit.id); // nút "thu gọn hội thoại" ở cuối
      } else if (hit.type === 'toolstoggle') {
        // click dòng "tool calls" -> mở/thu CẢ list turn
        setExpandedTools((s) => {
          const n = new Set(s);
          n.has(hit.id) ? n.delete(hit.id) : n.add(hit.id);
          return n;
        });
      } else {
        // click dòng "turn i" -> mở/thu chi tiết tool của turn đó
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
      } else if (selectedId != null && /^[1-6]$/.test(input)) {
        // bước miss -> [1..6] chọn miss; ngược lại -> [1..3] chọn verdict
        const n = Number(input);
        const draft = feedbackDraft && feedbackDraft.incidentId === selectedId ? feedbackDraft : null;
        if (draft && draft.stage === 'miss') {
          const cat = MISS_ORDER[n - 1];
          if (cat) chooseMiss(cat);
        } else if (n <= 3) {
          chooseVerdict(selectedId, (['correct', 'partial', 'wrong'] as const)[n - 1]);
        }
      } else if (key.escape) {
        if (feedbackDraft) setFeedbackDraft(null); // huỷ form feedback
        else setMode('input');
      }
    },
    { isActive: mode === 'list' && feedbackDraft?.stage !== 'note' }, // note đang gõ -> nhường phím cho note
  );

  // PHÍM BƯỚC NOTE: gõ note, Enter gửi, Esc huỷ (chỉ khi form ở bước 'note')
  useInput(
    (input, key) => {
      if (key.return) { submitDraft(); return; }
      if (key.escape) { setFeedbackDraft(null); return; }
      if (key.backspace || key.delete) {
        setFeedbackDraft((d) => (d ? { ...d, note: d.note.slice(0, -1) } : d));
        return;
      }
      if (key.ctrl || key.meta || key.tab) return;
      // lọc SGR mouse "[<col;row;M/m" + CSI khác + control (Ink hay NUỐT ESC -> phải bắt cả khi thiếu \x1b)
      const clean = input
        .replace(/\x1b?\[<\d+;\d+;\d+[Mm]/g, '')
        .replace(/\x1b\[[0-9;<>?]*[ -/]*[@-~]/g, '')
        .replace(/[\x00-\x1f\x7f]/g, '');
      if (clean) setFeedbackDraft((d) => (d ? { ...d, note: d.note + clean } : d));
    },
    { isActive: mode === 'list' && feedbackDraft?.stage === 'note' },
  );

  const handleSubmit = (raw: string) => {
    const text = raw.trim();
    if (!text) return;

    if (text === '/conversation-history' || text === '/conversations') {
      send({ command: 'conversation-history' }); // liệt kê hội thoại đã lưu -> block click được
    } else if (text.startsWith('/ack ')) {
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
      <Viewport ref={vp} state={state} selectedId={selectedId} expandedIds={expanded} expandedTurns={expandedTurns} expandedTools={expandedTools} feedbackDraft={feedbackDraft} />
      <InputBar onSubmit={handleSubmit} isActive={mode === 'input'} />
      <StatusBar status={status} count={state.live.length + state.snapshot.length} mode={mode} note={flash} />
    </Box>
  );
}
