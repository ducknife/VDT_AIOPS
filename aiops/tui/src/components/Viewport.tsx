// Viewport cuộn THEO DÒNG + scrollbar + y-map mức BLOCK click-chính-xác.
//  Mỗi "dòng có icon" (turn toggle ▸/▾, incident meta ▸/▾) là 1 block RIÊNG -> chỉ click
//  đúng dòng đó mới toggle; click phần thân (spacer) không làm gì.
import { forwardRef, useEffect, useImperativeHandle, useRef, useState, type ReactNode } from 'react';
import { Box, Text, measureElement } from 'ink';
import type { State } from '../store/useFeed';
import { InvestigationCard } from './InvestigationCard';
import { ChatUser, ChatHead, ChatBody } from './ChatBubble';
import { TurnToggle, TurnDetail } from './TurnList';
import { IncidentMeta, IncidentBody, IncidentAction, incidentSections, SectionHeader, incidentFullText } from './IncidentRow';
import { Scrollbar } from './Scrollbar';
import { C } from '../theme';
import { useTerminalSize } from '../hooks/useTerminalSize';

export type Hit =
  | { type: 'inc'; id: number }
  | { type: 'turntoggle'; id: string }
  | { type: 'act'; id: number; action: 'a' | 'r' | 'e' }
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
}

function BridgeBar({ w }: { w: number }) {
  const barW = Math.max(24, Math.round(w * 0.6));
  const label = ' Start Conversation ';
  const side = Math.max(2, Math.floor((barW - label.length) / 2));
  return (
    <Box width={w} marginTop={1} marginBottom={1} justifyContent="center" flexShrink={0}>
      <Text>
        <Text color={C.blue}>{'─'.repeat(side)}</Text>
        <Text color={C.sky} bold>{label}</Text>
        <Text color={C.blue}>{'─'.repeat(side)}</Text>
      </Text>
    </Box>
  );
}

export const Viewport = forwardRef<ViewportHandle, Props>(function Viewport(
  { state, selectedId, expandedIds, expandedTurns }, ref,
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
    // được chọn & chưa resolved -> mỗi nút a/r/e là 1 dòng click được
    if (sel && inc.id != null && inc.status !== 'RESOLVED') {
      (['a', 'r', 'e'] as const).forEach((a) =>
        push({ type: 'act', id: inc.id, action: a }, <IncidentAction key={`${key}-${a}`} act={a} expanded={exp} />));
    }
  };

  const pushTurns = (id: string, turns: any[], open: boolean) => {
    if (!turns || turns.length === 0) return;
    push({ type: 'turntoggle', id }, <TurnToggle turns={turns} open={open} />);
    if (open) push({ type: 'spacer' }, <TurnDetail turns={turns} />);
  };

  if (state.snapshot.length > 0) {
    push({ type: 'spacer' },
      <Box width={w}><Text backgroundColor={C.seal} color={C.contrastGray} bold wrap="truncate-end">{'  snapshot · active incidents'.padEnd(w)}</Text></Box>);
    state.snapshot.forEach((inc, i) => pushIncident(inc, `snap-${inc.id ?? i}`));
    push({ type: 'spacer' }, <BridgeBar w={w} />);
  }

  state.live.forEach((it) => {
    if (it.kind === 'investigation') {
      push({ type: 'spacer' }, <InvestigationCard inv={it} width={w} />);
      pushTurns(it.id, it.turns, it.status === 'analyzing' || expandedTurns.has(it.id));
      it.incidents.forEach((inc, i) => pushIncident(inc, `${it.id}-inc-${inc.id ?? i}`));
    } else if (it.role === 'user') {
      push({ type: 'spacer' }, <ChatUser item={it} width={w} />);
    } else {
      // click dòng "● duckompose" -> copy nguyên câu trả lời (khi đã xong)
      push(it.text ? { type: 'copy', text: it.text, label: 'answer' } : { type: 'spacer' }, <ChatHead item={it} />);
      pushTurns(it.id, it.turns, !!it.streaming || expandedTurns.has(it.id));
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
