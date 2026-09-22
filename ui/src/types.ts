// Mirrors backend/search's SpanRow / SpanDetail JSON shape exactly.

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
