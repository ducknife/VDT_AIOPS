// Bộ não: Frame -> feed hợp nhất, THỜI GIAN (append) — MỚI-Ở-ĐÁY.
//  - live[]: item phiên này (alert group vòng đời + bong bóng chat), cuối mảng = mới nhất.
//  - snapshot[]: incident active lịch sử (phẳng), nằm ĐẦU feed (trên, cũ nhất).
//  - turn: mỗi lượt gọi tool được GIỮ LẠI thành list (không biến mất), kèm timestamp.
// Group biến mất khi MỌI incident của nó resolved. Snapshot incident resolved -> tự rớt.

import { useReducer, useCallback, useRef } from 'react';
import type { Frame, IncidentView, ToolCall, AlertView, SnapshotCard, ConversationSummary } from '../utils/types';
import { notify } from '../utils/notify';

export interface Turn { tools: ToolCall[]; at: number } // at = thời điểm nhận (ms)

export interface Investigation {
  kind: 'investigation';
  id: string;
  investigationId: string;
  root?: string;
  alerts: AlertView[];
  status: 'analyzing' | 'diagnosed' | 'failed';
  turns: Turn[];
  incidents: IncidentView[];
  reason?: string;
}
export interface ChatItem {
  kind: 'chat';
  id: string;
  role: 'user' | 'assistant';
  text: string;
  streaming?: boolean;
  turns: Turn[];
}
// 1 hội thoại đã MỞ (replay) — giữ theo THỨ TỰ CLICK (click trước ở trước).
export interface OpenedConv {
  conversationId: string;
  preview?: string;
  messages: { role: string; text: string }[];
}
// Danh sách hội thoại (từ /conversation-history) — mỗi dòng TOGGLE mở/thu; mở nằm dưới, theo thứ tự click.
export interface ConvList {
  kind: 'convlist';
  id: string;
  items: ConversationSummary[];
  opened: OpenedConv[];
}
export type FeedItem = Investigation | ChatItem | ConvList;

export interface State {
  live: FeedItem[];
  snapshot: IncidentView[];
}

type Action =
  | { type: 'frame'; frame: Frame }
  | { type: 'user'; text: string }
  | { type: 'close-conv'; conversationId: string };

let _seq = 0;
const uid = () => `f${++_seq}`;

const INITIAL: State = { live: [], snapshot: [] };

const isInv = (i: FeedItem): i is Investigation => i.kind === 'investigation';

function patchInv(live: FeedItem[], invId: string, fn: (inv: Investigation) => Investigation): FeedItem[] {
  return live.map((i) => (isInv(i) && i.investigationId === invId ? fn(i) : i));
}

// cập nhật bong bóng assistant đang stream (tạo mới nếu chưa có)
function withStreamingAssistant(state: State, fn: (a: ChatItem) => ChatItem): State {
  const idx = state.live.findIndex((i) => i.kind === 'chat' && i.streaming);
  if (idx === -1) {
    const base: ChatItem = { kind: 'chat', id: uid(), role: 'assistant', text: '', streaming: true, turns: [] };
    return { ...state, live: [...state.live, fn(base)] };
  }
  const live = state.live.slice();
  live[idx] = fn(live[idx] as ChatItem);
  return { ...state, live };
}

function reducer(state: State, action: Action): State {
  if (action.type === 'user') {
    const item: ChatItem = { kind: 'chat', id: uid(), role: 'user', text: action.text, turns: [] };
    // đẻ luôn bong bóng assistant RỖNG (streaming) -> có cửa hiện "đang suy nghĩ…" + sóng
    // ngay trước token đầu tiên; chunk/turn/answer sau đó sẽ rót vào chính nó.
    const pending: ChatItem = { kind: 'chat', id: uid(), role: 'assistant', text: '', streaming: true, turns: [] };
    return { ...state, live: [...state.live, item, pending] };
  }

  if (action.type === 'close-conv') {
    // thu gọn 1 hội thoại đã mở (bỏ khỏi opened của mọi convlist)
    return {
      ...state,
      live: state.live.map((it) =>
        it.kind === 'convlist'
          ? { ...it, opened: it.opened.filter((o) => o.conversationId !== action.conversationId) }
          : it),
    };
  }

  const { type, investigationId: invId, data } = action.frame;

  switch (type) {
    case 'snapshot': {
      const cards = (data as { cards?: SnapshotCard[] })?.cards ?? [];
      return { ...state, snapshot: cards.flatMap((c) => c.incidents ?? []) };
    }

    case 'started': {
      if (!invId) return state;
      const arr = Array.isArray(data) ? (data as AlertView[]) : ((data as any)?.alerts as AlertView[]) ?? [];
      const root = Array.isArray(data) ? arr[0]?.service : (data as any)?.root ?? arr[0]?.service;
      const inv: Investigation = {
        kind: 'investigation', id: uid(), investigationId: invId,
        root, alerts: arr, status: 'analyzing', turns: [], incidents: [],
      };
      return { ...state, live: [...state.live, inv] };
    }

    case 'turn': {
      if (!invId) return state;
      const turn: Turn = { tools: (data as ToolCall[]) ?? [], at: Date.now() };
      return { ...state, live: patchInv(state.live, invId, (inv) => ({ ...inv, turns: [...inv.turns, turn] })) };
    }

    case 'incident': {
      const reports = ((data as IncidentView[]) ?? []).map((r) => ({ ...r, status: r.status ?? 'NEW' }));
      const exists = state.live.some((i) => isInv(i) && i.investigationId === invId);
      if (exists) {
        return { ...state, live: patchInv(state.live, invId ?? '', (inv) => ({ ...inv, incidents: reports, status: 'diagnosed' })) };
      }
      const inv: Investigation = {
        kind: 'investigation', id: uid(), investigationId: invId ?? uid(),
        root: reports[0]?.service, alerts: [], status: 'diagnosed', turns: [], incidents: reports,
      };
      return { ...state, live: [...state.live, inv] };
    }

    case 'failed': {
      const d = data as { investigationId?: string; rootService?: string; reason?: string };
      const id = d.investigationId ?? invId ?? '';
      return { ...state, live: patchInv(state.live, id, (inv) => ({ ...inv, status: 'failed', reason: d.reason, root: inv.root ?? d.rootService })) };
    }

    case 'status': {
      const d = data as { incidentId?: number; newStatus?: string };
      const id = d.incidentId;
      const newStatus = d.newStatus as IncidentView['status'];
      const snapshot = newStatus === 'RESOLVED'
        ? state.snapshot.filter((inc) => inc.id !== id)
        : state.snapshot.map((inc) => (inc.id === id ? { ...inc, status: newStatus } : inc));
      const live = state.live
        .map((i) => {
          if (!isInv(i)) return i;
          if (!i.incidents.some((inc) => inc.id === id)) return i;
          return { ...i, incidents: i.incidents.map((inc) => (inc.id === id ? { ...inc, status: newStatus } : inc)) };
        })
        .filter((i) => {
          if (!isInv(i) || i.incidents.length === 0) return true;
          return !i.incidents.every((inc) => inc.status === 'RESOLVED'); // đủ all-resolved -> rớt group
        });
      return { live, snapshot };
    }

    case 'chat-turn': {
      const d = data as { tools?: ToolCall[] };
      const turn: Turn = { tools: d.tools ?? [], at: Date.now() };
      return withStreamingAssistant(state, (a) => ({ ...a, turns: [...a.turns, turn] }));
    }

    case 'answer-chunk': {
      const d = data as { delta?: string };
      return withStreamingAssistant(state, (a) => ({ ...a, text: a.text + (d.delta ?? '') }));
    }

    case 'answer': {
      const d = data as { text?: string };
      return withStreamingAssistant(state, (a) => ({ ...a, text: d.text ?? a.text, streaming: false }));
    }

    case 'feedback': {
      // gắn verdict lên incident tương ứng (snapshot + trong investigation) -> hiện badge
      const d = data as { incidentId?: number; verdict?: string };
      if (d.incidentId == null) return state;
      const patch = (inc: IncidentView) => (inc.id === d.incidentId ? { ...inc, feedback: d.verdict } : inc);
      return {
        ...state,
        snapshot: state.snapshot.map(patch),
        live: state.live.map((i) => (isInv(i) ? { ...i, incidents: i.incidents.map(patch) } : i)),
      };
    }

    case 'conversations': {
      // /conversation-history -> đẩy 1 block danh sách hội thoại (mỗi dòng toggle) xuống cuối feed
      const items = (data as ConversationSummary[]) ?? [];
      const cl: ConvList = { kind: 'convlist', id: uid(), items, opened: [] };
      return { ...state, live: [...state.live, cl] };
    }

    case 'conversation': {
      // MỞ 1 hội thoại -> thêm vào opened của convlist tương ứng (dedupe; append = THỨ TỰ CLICK)
      const d = data as { conversationId?: string; messages?: { role?: string; text?: string }[] };
      const cid = d.conversationId;
      if (!cid) return state;
      const messages = (d.messages ?? []).map((m) => ({
        role: m.role === 'assistant' ? 'assistant' : 'user', text: m.text ?? '',
      }));
      const live = [...state.live];
      for (let i = live.length - 1; i >= 0; i--) {
        const it = live[i];
        if (it.kind !== 'convlist' || !it.items.some((c) => c.conversationId === cid)) continue;
        if (it.opened.some((o) => o.conversationId === cid)) break; // đã mở rồi -> bỏ qua
        const preview = it.items.find((c) => c.conversationId === cid)?.preview;
        live[i] = { ...it, opened: [...it.opened, { conversationId: cid, preview, messages }] };
        return { ...state, live };
      }
      return state;
    }

    default:
      return state;
  }
}

// id incident chọn được theo thứ tự hiển thị (snapshot trên -> live dưới)
export function selectableIds(state: State): number[] {
  const out: number[] = [];
  state.snapshot.forEach((inc) => inc.id != null && out.push(inc.id));
  state.live.forEach((it) => {
    if (it.kind === 'investigation') it.incidents.forEach((inc) => inc.id != null && out.push(inc.id));
  });
  return out;
}

// hội thoại này đang mở (đã có trong opened của convlist nào đó) chưa?
export function isConversationOpen(state: State, conversationId: string): boolean {
  return state.live.some(
    (it) => it.kind === 'convlist' && it.opened.some((o) => o.conversationId === conversationId));
}

// tìm incident theo id (snapshot + trong các investigation) - cho phím copy
export function findIncident(state: State, id: number): IncidentView | undefined {
  const snap = state.snapshot.find((inc) => inc.id === id);
  if (snap) return snap;
  for (const it of state.live) {
    if (it.kind === 'investigation') {
      const m = it.incidents.find((inc) => inc.id === id);
      if (m) return m;
    }
  }
  return undefined;
}

export function useFeed() {
  const [state, dispatch] = useReducer(reducer, INITIAL);

  // STREAM kiểu "đánh máy": gom token vào buffer, mỗi 28ms nhả 1 ÍT ký tự (nhả nhanh hơn
  // khi tồn nhiều để không lag) -> chữ chảy đều, liên tục, không giật theo nhịp lớn.
  const buf = useRef('');
  const timer = useRef<ReturnType<typeof setInterval> | null>(null);

  const stopDrain = useCallback(() => {
    if (timer.current) { clearInterval(timer.current); timer.current = null; }
  }, []);

  const drainTick = useCallback(() => {
    if (!buf.current) { stopDrain(); return; }
    const step = Math.max(1, Math.ceil(buf.current.length / 3)); // bước nhỏ (tới 1 ký tự) -> siêu mượt; tồn nhiều thì bước to đuổi kịp
    const piece = buf.current.slice(0, step);
    buf.current = buf.current.slice(step);
    dispatch({ type: 'frame', frame: { type: 'answer-chunk', data: { delta: piece } } as Frame });
  }, [stopDrain]);

  const flush = useCallback(() => { // nhả hết phần còn lại ngay (giữ thứ tự trước frame khác)
    stopDrain();
    if (buf.current) {
      const delta = buf.current; buf.current = '';
      dispatch({ type: 'frame', frame: { type: 'answer-chunk', data: { delta } } as Frame });
    }
  }, [stopDrain]);

  const onFrame = useCallback((frame: Frame) => {
    if (frame.type === 'answer-chunk') {
      buf.current += (frame.data as { delta?: string })?.delta ?? '';
      if (!timer.current) timer.current = setInterval(drainTick, 12);
      return;
    }
    flush(); // xả nốt buffer trước khi xử frame khác (turn/answer/incident…)
    // 🔔 side-effect (đặt Ở ĐÂY, không trong reducer — reducer phải thuần): ding thông báo
    if (frame.type === 'started') {
      notify(); // vừa phát hiện lỗi -> 1 investigation live hiện lên
    } else if (frame.type === 'snapshot') {
      const cards = (frame.data as { cards?: SnapshotCard[] })?.cards ?? [];
      if (cards.some((c) => (c.incidents?.length ?? 0) > 0)) notify(); // kết nối mà có snapshot
    }
    dispatch({ type: 'frame', frame });
  }, [drainTick, flush]);

  const pushUser = useCallback((text: string) => { flush(); dispatch({ type: 'user', text }); }, [flush]);
  const closeConversation = useCallback((conversationId: string) => dispatch({ type: 'close-conv', conversationId }), []);
  return { state, onFrame, pushUser, closeConversation };
}
