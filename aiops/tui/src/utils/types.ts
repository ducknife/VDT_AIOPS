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
}

export interface SnapshotCard {
  investigationId: string;
  incidents: IncidentView[];
  alerts: AlertView[];
}

// Envelope chung mọi frame server -> TUI.
export interface Frame {
  type: string;
  investigationId?: string;
  data: unknown;
}

// Lệnh TUI -> server.
export interface Command {
  command: 'ack' | 'resolve' | 'ask';
  incidentId?: number;
  text?: string;
  conversationId?: string;
}
