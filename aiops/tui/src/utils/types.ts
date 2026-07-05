// Types khớp 1-1 với giao thức backend đẩy qua WebSocket.
// Đây là "hợp đồng" giữa engine (Java) và TUI (React Ink) — đổi backend thì đổi ở đây.

export type Severity = 'P1' | 'P2' | 'P3' | 'P4';
export type IncidentStatus = 'NEW' | 'ACKNOWLEDGED' | 'RESOLVED';

export interface Finding { finding: string; evidence: string }
export interface Hypothesis { claim: string; needsVerification: string }
export interface Action { action: string; why: string }
export interface Evidence { source: string; detail: string }

export interface ToolCall { name: string; arguments?: unknown; type?: string }
export interface AlertView { id: number; service: string; type: string }

// Incident hợp nhất: snapshot cho đủ field (id/status), còn live `incident` (IncidentReport)
// thiếu id/status -> optional. ack/resolve chỉ chạy khi có id.
export interface IncidentView {
  id?: number;
  service?: string;
  title?: string;
  severity?: Severity;
  summary?: string;
  rootCause?: string;
  validatedFindings?: Finding[];
  hypotheses?: Hypothesis[];
  recommendedActions?: Action[];
  citedEvidence?: Evidence[];
  status?: IncidentStatus;
  investigationMs?: number;   // thời gian điều tra (ms) — engine đã gửi sẵn
  feedback?: string;          // verdict người dùng: correct | partial | wrong (chỉ lưu, không nuôi agent)
}

export interface SnapshotCard {
  investigationId: string;
  incidents: IncidentView[];
  alerts: AlertView[];
}

// 1 dòng trong /conversation-history (khớp backend ConversationSummary).
export interface ConversationSummary {
  conversationId: string;
  messageCount: number;
  preview?: string;
  lastAt?: string;
}

export type Verdict = 'correct' | 'partial' | 'wrong';
// miss-taxonomy: chẩn đoán thiếu/sai cái gì (khi verdict != correct)
export type MissCategory =
  | 'wrong-root-cause' | 'missed-service' | 'wrong-severity'
  | 'missed-split' | 'wrong-remediation' | 'other';

// form feedback nhiều bước: verdict -> (miss nếu != correct) -> note -> submit
export interface FeedbackDraft {
  incidentId: number;
  stage: 'verdict' | 'miss' | 'note';
  verdict?: Verdict;
  missed?: MissCategory;
  note: string;
}

// Envelope chung mọi frame server -> TUI.
export interface Frame {
  type: string;
  investigationId?: string;
  data: unknown;
}

// Lệnh TUI -> server.
export interface Command {
  command: 'ack' | 'resolve' | 'ask' | 'feedback' | 'conversation-history' | 'get-conversation';
  incidentId?: number;
  text?: string;
  conversationId?: string;
  verdict?: string;   // feedback: correct | partial | wrong
  missed?: string;    // feedback: miss-taxonomy khi verdict != correct
}
