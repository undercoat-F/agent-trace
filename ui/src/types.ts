// Mirrors backend/search's SpanRow / SpanDetail / PromptRow / PromptDetail JSON shape exactly.

export interface SpanRow {
  traceId: string;
  spanId: string;
  parentSpanId: string | null;
  name: string;
  serviceName: string | null;
  toolName: string | null;
  startTime: string; // ISO local datetime, no zone (stored/queried as UTC)
  durationMs: number | null;
  statusCode: number; // 0 unset, 1 ok, 2 error
  statusMessage: string | null;
  errorType: string | null;
  rawPayloadId: number;
}

export interface SpanDetail {
  span: SpanRow;
  attributes: Record<string, unknown>;
}

export type StatusFilter = "" | "ok" | "error";

export interface PromptRow {
  promptId: string;
  sessionId: string;
  promptPreview: string | null;
  startedAt: string;
  endedAt: string;
  toolCalls: number;
  failures: number;
}

export interface PromptEventSummary {
  eventId: string;
  eventSequence: number;
  eventName: string;
  occurredAt: string;
  toolName: string | null;
  toolSuccess: string | null; // "true" | "false" | null — a JSON string in the source data, not a boolean
}

export interface PromptDetail {
  promptId: string;
  sessionId: string;
  userPrompt: string | null;
  assistantResponse: string | null;
  startedAt: string;
  endedAt: string;
  toolCalls: number;
  failures: number;
  events: PromptEventSummary[];
}
