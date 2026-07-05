// 1 incident chia block click CHÍNH XÁC:
//  - IncidentMeta: DÒNG icon ▸/▾ + badge -> click mở/thu.
//  - IncidentBody: tiêu đề + root cause (luôn hiện).
//  - incidentSections(): mỗi mục (summary/findings/…/evidence) -> {tiêu đề + nội dung + text}
//    để Viewport render tiêu đề thành block CLICK-ĐỂ-COPY (copy riêng từng mục).
//  - IncidentAction: nút a/r/e mỗi cái 1 dòng click.
import { Box, Text } from 'ink';
import type { IncidentView, Verdict, MissCategory } from '../utils/types';
import { C, severityColor, statusColor } from '../utils/theme';
import { stripEmoji } from '../utils/format';
import type { ReactNode } from 'react';

// ms -> chuỗi gọn: >=1s hiện "48.2s", nhỏ hơn hiện "820ms"
const fmtDur = (ms: number) => (ms >= 1000 ? `${(ms / 1000).toFixed(1)}s` : `${ms}ms`);

// màu theo verdict feedback
const fbColor = (v: string) => (v === 'correct' ? C.success : v === 'partial' ? C.warning : C.danger);

// miss-taxonomy: nhãn hiển thị (English) + thứ tự (dùng cho phím [1..6] và click)
export const MISS_ORDER: MissCategory[] = [
  'wrong-root-cause', 'missed-service', 'wrong-severity', 'missed-split', 'wrong-remediation', 'other',
];
export const MISS_LABELS: Record<MissCategory, string> = {
  'wrong-root-cause': 'wrong root cause',
  'missed-service': 'missed a related service',
  'wrong-severity': 'wrong severity',
  'missed-split': 'failed to split incidents',
  'wrong-remediation': 'wrong remediation',
  'other': 'other',
};

// DÒNG icon + meta (block click được)
export function IncidentMeta({
  data, selected = false, expanded = false,
}: { data: IncidentView; selected?: boolean; expanded?: boolean }) {
  const resolved = data.status === 'RESOLVED';
  const sev = resolved ? C.muted : severityColor(data.severity ?? 'P4');
  return (
    <Box marginTop={1}>
      <Text color={selected ? C.sky : C.muted} bold>{expanded ? '▾ ' : '▸ '}</Text>
      {resolved ? <Text color={C.success}>✓ </Text> : null}
      <Text color={sev} bold>{data.severity ?? 'P?'}</Text>
      <Text color={resolved ? C.muted : sev} bold>  {data.service ?? 'unknown'}</Text>
      {data.id != null ? <Text color={C.muted}>  #{data.id}</Text> : null}
      <Text color={C.muted}>  · </Text>
      <Text color={statusColor(data.status ?? 'NEW')}>{data.status ?? 'NEW'}</Text>
      {data.investigationMs != null ? (
        <Text color={C.muted}>  · {fmtDur(data.investigationMs)}</Text>
      ) : null}
      {data.feedback ? (
        <Text color={fbColor(data.feedback)} bold>  feedback: {data.feedback}</Text>
      ) : null}
    </Box>
  );
}

// Thân: tiêu đề + root cause (luôn hiện) — KHÔNG bắt click
export function IncidentBody({
  data, expanded = false,
}: { data: IncidentView; expanded?: boolean }) {
  const resolved = data.status === 'RESOLVED';
  return (
    <Box flexDirection="column" marginLeft={2} width="100%">
      {data.title ? (
        <Text color={resolved ? C.muted : C.white} bold wrap={expanded ? 'wrap' : 'truncate-end'}>
          {stripEmoji(data.title)}
        </Text>
      ) : null}
      {data.rootCause ? (
        <Text color={C.contrastGray} wrap={expanded ? 'wrap' : 'truncate-end'}>
          <Text color={C.gold}>↳ </Text>{stripEmoji(data.rootCause)}
        </Text>
      ) : null}
    </Box>
  );
}

// Mỗi mục chi tiết -> {label, color, content (JSX), text (để copy)}
export interface IncSection { label: string; color: string; content: ReactNode; text: string }

export function incidentSections(data: IncidentView): IncSection[] {
  const vf = data.validatedFindings ?? [];
  const hyp = data.hypotheses ?? [];
  const ra = data.recommendedActions ?? [];
  const ce = data.citedEvidence ?? [];
  const secs: IncSection[] = [];

  if (data.summary) {
    secs.push({
      label: 'summary', color: C.gold,
      content: <Text color={C.contrastGray} wrap="wrap">{stripEmoji(data.summary)}</Text>,
      text: stripEmoji(data.summary),
    });
  }
  if (vf.length > 0) {
    secs.push({
      label: 'validated findings', color: C.lightGreen,
      content: (
        <Box flexDirection="column">
          {vf.map((f, i) => (
            <Text key={i} color={C.contrastGray} wrap="wrap">
              <Text color={C.success}>✓ </Text>{stripEmoji(f.finding)}
              {f.evidence ? <Text color={C.muted}> — {stripEmoji(f.evidence)}</Text> : null}
            </Text>
          ))}
        </Box>
      ),
      text: vf.map((f) => `- ${stripEmoji(f.finding)}${f.evidence ? ` — ${stripEmoji(f.evidence)}` : ''}`).join('\n'),
    });
  }
  if (hyp.length > 0) {
    secs.push({
      label: 'hypotheses', color: C.lightOrange,
      content: (
        <Box flexDirection="column">
          {hyp.map((h, i) => (
            <Text key={i} color={C.contrastGray} wrap="wrap">
              <Text color={C.lightOrange}>? </Text>{stripEmoji(h.claim)}
              {h.needsVerification ? <Text color={C.muted}> (verify: {stripEmoji(h.needsVerification)})</Text> : null}
            </Text>
          ))}
        </Box>
      ),
      text: hyp.map((h) => `- ${stripEmoji(h.claim)}${h.needsVerification ? ` (verify: ${stripEmoji(h.needsVerification)})` : ''}`).join('\n'),
    });
  }
  if (ra.length > 0) {
    secs.push({
      label: 'remediation', color: C.lightPurple,
      content: (
        <Box flexDirection="column">
          {ra.map((a, i) => (
            <Text key={i} color={C.contrastGray} wrap="wrap">
              <Text color={C.lightPurple}>› </Text>{stripEmoji(a.action)}
              {a.why ? <Text color={C.muted}> — {stripEmoji(a.why)}</Text> : null}
            </Text>
          ))}
        </Box>
      ),
      text: ra.map((a) => `- ${stripEmoji(a.action)}${a.why ? ` — ${stripEmoji(a.why)}` : ''}`).join('\n'),
    });
  }
  if (ce.length > 0) {
    secs.push({
      label: 'evidence', color: C.pink,
      content: (
        <Box flexDirection="column">
          {ce.map((e, i) => (
            <Text key={i} color={C.contrastGray} wrap="wrap">
              <Text color={C.lightPink}>{stripEmoji(e.source)}:</Text> {stripEmoji(e.detail)}
            </Text>
          ))}
        </Box>
      ),
      text: ce.map((e) => `${stripEmoji(e.source)}: ${stripEmoji(e.detail)}`).join('\n'),
    });
  }
  return secs;
}

// ghép TOÀN BỘ incident thành plain text (để copy cả cái)
export function incidentFullText(data: IncidentView): string {
  const head = [`#${data.id ?? '?'}`, data.severity, data.service, data.status]
    .filter(Boolean).join(' · ');
  const parts: string[] = [head];
  if (data.title) parts.push(stripEmoji(data.title));
  if (data.rootCause) parts.push(`Root cause: ${stripEmoji(data.rootCause)}`);
  incidentSections(data).forEach((s) => parts.push(`\n${s.label}:\n${s.text}`));
  return parts.join('\n');
}

// tiêu đề mục = block CLICK ĐỂ COPY (Viewport gắn meta copy)
export function SectionHeader({ label, color }: { label: string; color: string }) {
  return (
    <Box marginTop={1}>
      <Text color={color} bold>{label}</Text>
      <Text color={C.muted}>  · click để copy</Text>
    </Box>
  );
}

// 1 nút hành động = 1 DÒNG click được (Viewport render khi incident được chọn).
export function IncidentAction({ act, expanded = false }: { act: 'a' | 'r' | 'e'; expanded?: boolean }) {
  const map = {
    a: { key: '[a]', label: ' acknowledge', color: C.warning },
    r: { key: '[r]', label: ' resolve', color: C.success },
    e: { key: '[e]', label: expanded ? ' collapse' : ' expand', color: C.sky },
  } as const;
  const b = map[act];
  return (
    <Box marginLeft={2}>
      <Text color={b.color} bold>{b.key}</Text><Text color={C.muted}>{b.label}</Text>
    </Box>
  );
}

// nhãn câu hỏi của form feedback (vd "Is the RCA correct?")
export function FeedbackQuestion({ text }: { text: string }) {
  return (
    <Box marginLeft={2} marginTop={1}>
      <Text color={C.gold} bold>{text}</Text>
    </Box>
  );
}

// 1 nút VERDICT = 1 dòng click (phím 1/2/3). Chỉ chữ, KHÔNG ký tự mơ hồ.
export function FeedbackAction({ verdict }: { verdict: Verdict }) {
  const map = {
    correct: { key: '[1]', label: ' correct', color: C.success },
    partial: { key: '[2]', label: ' partial', color: C.warning },
    wrong: { key: '[3]', label: ' wrong', color: C.danger },
  } as const;
  const b = map[verdict];
  return (
    <Box marginLeft={2}>
      <Text color={b.color} bold>{b.key}</Text><Text color={C.muted}>{b.label}</Text>
    </Box>
  );
}

// 1 nút MISS-taxonomy = 1 dòng click (phím [index]) — hiện khi verdict != correct.
export function MissAction({ cat, index }: { cat: MissCategory; index: number }) {
  return (
    <Box marginLeft={4}>
      <Text color={C.lightPink} bold>{`[${index}]`}</Text>
      <Text color={C.muted}>{` ${MISS_LABELS[cat]}`}</Text>
    </Box>
  );
}

// tóm tắt lựa chọn đã chọn (verdict + missed) trong lúc điền form
export function FeedbackSummary({ verdict, missed }: { verdict: Verdict; missed?: MissCategory }) {
  return (
    <Box marginLeft={2} marginTop={1}>
      <Text color={C.muted}>feedback: </Text>
      <Text color={fbColor(verdict)} bold>{verdict}</Text>
      {missed ? (
        <Text color={C.muted}>{'  ·  missed: '}<Text color={C.lightPink} bold>{MISS_LABELS[missed]}</Text></Text>
      ) : null}
    </Box>
  );
}

// ô nhập NOTE: "note: <đang gõ>" — xuống dòng khi dài hơn chiều rộng, có con trỏ.
export function NoteField({ note }: { note: string }) {
  return (
    <Box marginLeft={2} marginTop={1} width="100%">
      <Text wrap="wrap">
        <Text color={C.sky} bold>note: </Text>
        <Text color={C.white}>{note}</Text>
        <Text color={C.muted}>▌</Text>
      </Text>
    </Box>
  );
}

// gợi ý phím trong bước note
export function FeedbackHint() {
  return (
    <Box marginLeft={2}>
      <Text color={C.muted}>Enter to submit · Esc to cancel</Text>
    </Box>
  );
}
