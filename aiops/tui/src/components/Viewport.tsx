// Viewport cuộn THEO DÒNG + scrollbar + y-map mức BLOCK click-chính-xác.
//  Mỗi "dòng có icon" (turn toggle ▸/▾, incident meta ▸/▾) là 1 block RIÊNG -> chỉ click
//  đúng dòng đó mới toggle; click phần thân (spacer) không làm gì.
import { forwardRef, useEffect, useImperativeHandle, useRef, useState, type ReactNode } from 'react';
import { Box, Text, measureElement } from 'ink';
import type { State } from '../store/useFeed';
import { InvestigationCard } from './InvestigationCard';
import { ChatUser, ChatHead, ChatBody } from './ChatBubble';
import { ToolsHeader, TurnDivider, TurnBody } from './TurnList';
import { IncidentMeta, IncidentBody, IncidentAction, FeedbackAction, MissAction, MISS_ORDER, FeedbackQuestion, FeedbackSummary, NoteField, FeedbackHint, incidentSections, SectionHeader, incidentFullText } from './IncidentRow';
import { ConvListHeader, ConvRow, ConvOpenedHeader, ConvMsg, ConvCollapse } from './ConversationList';
import { Scrollbar } from './Scrollbar';
import { C } from '../utils/theme';
import type { Verdict, MissCategory, FeedbackDraft } from '../utils/types';
import { useTerminalSize } from '../hooks/useTerminalSize';

export type Hit =
  | { type: 'inc'; id: number }
  | { type: 'turntoggle'; id: string }
  | { type: 'toolstoggle'; id: string }
  | { type: 'act'; id: number; action: 'a' | 'r' | 'e' }
  | { type: 'fb'; id: number; verdict: Verdict }
  | { type: 'miss'; id: number; cat: MissCategory }
  | { type: 'convopen'; id: string }
  | { type: 'convclose'; id: string }
  | { type: 'copy'; text: string; label: string };
type BlockMeta = Hit | { type: 'spacer' };

export interface ViewportHandle {
  scrollBy: (deltaLines: number) => void;
  scrollToBottom: () => void;
  scrollToRow: (rowInViewport: number) => void;
  hitTest: (rowInVp0: number) => Hit | null;
}

interface Props {
  state: State;
  selectedId: number | null;
  expandedIds: Set<number>;
  expandedTurns: Set<string>;
  expandedTools: Set<string>;
  feedbackDraft: FeedbackDraft | null; // form feedback đang điền cho incident nào (verdict/miss/note)
}

function BridgeBar({ w }: { w: number }) {
  const barW = Math.max(24, Math.round(w * 0.6));
  const label = ' Start Conversation ';
  const side = Math.max(2, Math.floor((barW - label.length) / 2));
  return (
    <Box width={w} marginTop={1} marginBottom={1} justifyContent="center" flexShrink={0}>
      <Text color={C.gray}>
        <Text>{'─'.repeat(side)}</Text>
        <Text>{label}</Text>
        <Text>{'─'.repeat(side)}</Text>
      </Text>
    </Box>
  );
}

export const Viewport = forwardRef<ViewportHandle, Props>(function Viewport(
  { state, selectedId, expandedIds, expandedTurns, expandedTools, feedbackDraft }, ref,
) {
  const { cols } = useTerminalSize();
  const w = Math.max(1, cols - 1);
  const vpRef = useRef<any>(null);
  const contentRef = useRef<any>(null);
  const blockRefs = useRef<any[]>([]);
  blockRefs.current = [];
  const [vh, setVh] = useState(0);
  const [ch, setCh] = useState(0);
  const [scroll, setScroll] = useState(0);
  const [stick, setStick] = useState(true);

  // ── dựng danh sách block phẳng ──
  const blocks: { node: ReactNode; meta: BlockMeta }[] = [];
  const push = (meta: BlockMeta, node: ReactNode) => blocks.push({ meta, node });

  const pushIncident = (inc: any, key: string) => {
    const sel = inc.id != null && inc.id === selectedId;
    const exp = inc.id != null && expandedIds.has(inc.id);
    push(inc.id != null ? { type: 'inc', id: inc.id } : { type: 'spacer' },
      <IncidentMeta key={`${key}-m`} data={inc} selected={sel} expanded={exp} />);
    push({ type: 'spacer' },
      <IncidentBody key={`${key}-b`} data={inc} expanded={exp} />);
    // khi mở: dòng "copy toàn bộ" + mỗi mục (tiêu đề click để COPY) + nội dung
    if (exp) {
      push({ type: 'copy', text: incidentFullText(inc), label: `incident #${inc.id}` },
        <Box key={`${key}-all`} marginLeft={2}><Text color={C.sky} bold>⎘ copy toàn bộ incident</Text></Box>);
      incidentSections(inc).forEach((s, si) => {
        push({ type: 'copy', text: s.text, label: s.label },
          <Box key={`${key}-sh${si}`} marginLeft={2}><SectionHeader label={s.label} color={s.color} /></Box>);
        push({ type: 'spacer' },
          <Box key={`${key}-sc${si}`} marginLeft={4}>{s.content}</Box>);
      });
    }
    // được chọn -> hành động + form feedback nhiều bước
    if (sel && inc.id != null) {
      // a/r/e chỉ khi chưa resolved
      if (inc.status !== 'RESOLVED') {
        (['a', 'r', 'e'] as const).forEach((a) =>
          push({ type: 'act', id: inc.id, action: a }, <IncidentAction key={`${key}-${a}`} act={a} expanded={exp} />));
      }
      const draft = feedbackDraft && feedbackDraft.incidentId === inc.id ? feedbackDraft : null;
      if (!draft || draft.stage === 'verdict') {
        // bước 1: câu hỏi + 3 verdict (phím 1/2/3 hoặc click)
        push({ type: 'spacer' }, <FeedbackQuestion key={`${key}-fq`} text="Is the RCA correct? Rate the diagnosis:" />);
        (['correct', 'partial', 'wrong'] as const).forEach((v) =>
          push({ type: 'fb', id: inc.id, verdict: v }, <FeedbackAction key={`${key}-fb-${v}`} verdict={v} />));
      } else if (draft.stage === 'miss' && draft.verdict) {
        // bước 2 (verdict != correct): thiếu/sai cái gì (phím 1..6 hoặc click)
        push({ type: 'spacer' }, <FeedbackSummary key={`${key}-fs`} verdict={draft.verdict} />);
        push({ type: 'spacer' }, <FeedbackQuestion key={`${key}-mq`} text="What did it get wrong / miss?" />);
        MISS_ORDER.forEach((cat, i) =>
          push({ type: 'miss', id: inc.id, cat }, <MissAction key={`${key}-miss-${cat}`} cat={cat} index={i + 1} />));
      } else if (draft.stage === 'note' && draft.verdict) {
        // bước 3: ô nhập note (Enter gửi, Esc huỷ)
        push({ type: 'spacer' }, <FeedbackSummary key={`${key}-fs`} verdict={draft.verdict} missed={draft.missed} />);
        push({ type: 'spacer' }, <NoteField key={`${key}-nf`} note={draft.note} />);
        push({ type: 'spacer' }, <FeedbackHint key={`${key}-fh`} />);
      }
    }
  };

  // 2 cấp accordion:
  //  CHA "tool calls" -> mặc định MỞ khi đang chạy (xem tiến trình), THU khi xong (gọn). Click để lật.
  //  CON từng turn -> chỉ turn cuối MỞ khi live; turn mới tới -> turn cũ tự thu. Click divider để lật.
  const pushTurns = (id: string, turns: any[], live: boolean) => {
    if (!turns || turns.length === 0) return;
    const toolsDefOpen = live;
    const toolsOpen = expandedTools.has(id) ? !toolsDefOpen : toolsDefOpen;
    push({ type: 'toolstoggle', id }, <ToolsHeader count={turns.length} open={toolsOpen} live={live} />);
    if (!toolsOpen) return;
    turns.forEach((turn, i) => {
      const active = live && i === turns.length - 1;
      const key = `${id}:${i}`;
      const defOpen = active;
      const open = expandedTurns.has(key) ? !defOpen : defOpen;
      push({ type: 'turntoggle', id: key },
        <Box marginLeft={2}><TurnDivider index={i} active={active} open={open} /></Box>);
      if (open) push({ type: 'spacer' },
        <Box marginLeft={2}><TurnBody turn={turn} /></Box>);
    });
  };

  if (state.snapshot.length > 0) {
    push({ type: 'spacer' },
      <Box width={w}><Text backgroundColor={C.seal} color={C.contrastGray} bold wrap="truncate-end">{'  snapshot · active incidents'.padEnd(w)}</Text></Box>);
    state.snapshot.forEach((inc, i) => pushIncident(inc, `snap-${inc.id ?? i}`));
  }

  push({ type: 'spacer' }, <BridgeBar w={w} />); // LUÔN hiện (kể cả khi chưa có snapshot)

  state.live.forEach((it) => {
    if (it.kind === 'investigation') {
      push({ type: 'spacer' }, <InvestigationCard inv={it} width={w} />);
      pushTurns(it.id, it.turns, it.status === 'analyzing');
      it.incidents.forEach((inc, i) => pushIncident(inc, `${it.id}-inc-${inc.id ?? i}`));
    } else if (it.kind === 'convlist') {
      // danh sách hội thoại: header + mỗi dòng TOGGLE; hội thoại đã mở nằm dưới theo THỨ TỰ CLICK
      push({ type: 'spacer' }, <ConvListHeader key={`${it.id}-h`} count={it.items.length} width={w} />);
      it.items.forEach((c, i) => {
        const open = it.opened.some((o) => o.conversationId === c.conversationId);
        push({ type: 'convopen', id: c.conversationId }, <ConvRow key={`${it.id}-${i}`} data={c} open={open} />);
      });
      it.opened.forEach((o, oi) => {
        push({ type: 'spacer' }, <ConvOpenedHeader key={`${it.id}-oh${oi}`} preview={o.preview} width={w} />);
        o.messages.forEach((m, mi) =>
          push({ type: 'spacer' }, <ConvMsg key={`${it.id}-o${oi}-m${mi}`} role={m.role} text={m.text} />));
        push({ type: 'convclose', id: o.conversationId }, <ConvCollapse key={`${it.id}-oc${oi}`} />);
      });
    } else if (it.role === 'user') {
      push({ type: 'spacer' }, <ChatUser item={it} width={w} />);
    } else {
      // click dòng "● duckompose" -> copy nguyên câu trả lời (khi đã xong)
      push(it.text ? { type: 'copy', text: it.text, label: 'answer' } : { type: 'spacer' }, <ChatHead item={it} />);
      pushTurns(it.id, it.turns, !!it.streaming);
      push({ type: 'spacer' }, <ChatBody item={it} width={w} />);
    }
  });

  const blockHRef = useRef<number[]>([]);
  const metaRef = useRef<BlockMeta[]>([]);
  metaRef.current = blocks.map((b) => b.meta);
  useEffect(() => {
    const nvh = vpRef.current ? measureElement(vpRef.current).height : 0;
    const nch = contentRef.current ? measureElement(contentRef.current).height : 0;
    blockHRef.current = blockRefs.current.map((r) => (r ? measureElement(r).height : 0));
    if (nvh !== vh) setVh(nvh);
    if (nch !== ch) setCh(nch);
  });

  const maxScroll = Math.max(0, ch - vh);
  const effective = stick ? maxScroll : Math.min(scroll, maxScroll);

  const maxRef = useRef(maxScroll); maxRef.current = maxScroll;
  const stickRef = useRef(stick); stickRef.current = stick;
  const vhRef = useRef(vh); vhRef.current = vh;
  const effRef = useRef(effective); effRef.current = effective;

  useImperativeHandle(ref, () => ({
    scrollBy(delta: number) {
      setScroll((s) => {
        const max = maxRef.current;
        const base = stickRef.current ? max : s;
        const n = Math.max(0, Math.min(max, base + delta));
        setStick(n >= max);
        return n;
      });
    },
    scrollToBottom() { setStick(true); },
    scrollToRow(rowInViewport: number) {
      const max = maxRef.current;
      const vph = vhRef.current;
      if (max <= 0 || vph <= 1) return;
      const f = Math.max(0, Math.min(1, (rowInViewport - 1) / (vph - 1)));
      setStick(f >= 0.999);
      setScroll(Math.round(f * max));
    },
    hitTest(rowInVp0: number) {
      let top = -effRef.current;
      const metas = metaRef.current;
      for (let i = 0; i < metas.length; i++) {
        const h = blockHRef.current[i] ?? 0;
        if (rowInVp0 >= top && rowInVp0 < top + h) {
          const m = metas[i];
          return m.type === 'spacer' ? null : m;
        }
        top += h;
      }
      return null;
    },
  }), []);

  return (
    <Box flexDirection="row" flexGrow={1}>
      <Box ref={vpRef} flexGrow={1} overflow="hidden" flexDirection="column">
        {blocks.length === 0 ? (
          <Text color={C.muted}>  (chưa có gì — đang chờ sự cố…)</Text>
        ) : (
          <Box ref={contentRef} flexDirection="column" flexShrink={0} marginTop={-effective} width={w}>
            {blocks.map((b, i) => (
              <Box key={i} ref={(el: any) => { blockRefs.current[i] = el; }} width={w} flexShrink={0}>
                {b.node}
              </Box>
            ))}
          </Box>
        )}
      </Box>
      <Scrollbar height={vh} contentH={ch} scroll={effective} maxScroll={maxScroll} />
    </Box>
  );
});
