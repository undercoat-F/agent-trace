import type { BranchSummary, CommitDetail, CommitSummary, PromptDetail, PromptRow, SpanDetail, SpanRow, StatusFilter } from "./types";

const API_BASE = import.meta.env.VITE_API_BASE ?? "http://localhost:8082";

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function get<T>(path: string): Promise<T> {
  let res: Response;
  try {
    res = await fetch(`${API_BASE}${path}`);
  } catch {
    throw new ApiError(0, `search API 未到達: ${API_BASE}`);
  }
  if (!res.ok) {
    throw new ApiError(res.status, `search API がエラーを返しました (HTTP ${res.status})`);
  }
  return res.json() as Promise<T>;
}

export interface SearchParams {
  status?: StatusFilter;
  tool?: string;
  q?: string;
  limit?: number;
  offset?: number;
}

export function searchSpans(params: SearchParams): Promise<SpanRow[]> {
  const qs = new URLSearchParams();
  if (params.status) qs.set("status", params.status);
  if (params.tool) qs.set("tool", params.tool);
  if (params.q) qs.set("q", params.q);
  qs.set("limit", String(params.limit ?? 50));
  qs.set("offset", String(params.offset ?? 0));
  return get<SpanRow[]>(`/api/spans?${qs}`);
}

export function fetchSpanDetail(traceId: string, spanId: string): Promise<SpanDetail> {
  return get<SpanDetail>(`/api/spans/${traceId}/${spanId}`);
}

export function fetchTools(): Promise<string[]> {
  return get<string[]>("/api/tools");
}

export interface PromptSearchParams {
  q?: string;
  failuresOnly?: boolean;
  limit?: number;
  offset?: number;
}

export function searchPrompts(params: PromptSearchParams): Promise<PromptRow[]> {
  const qs = new URLSearchParams();
  if (params.q) qs.set("q", params.q);
  if (params.failuresOnly) qs.set("failuresOnly", "true");
  qs.set("limit", String(params.limit ?? 50));
  qs.set("offset", String(params.offset ?? 0));
  return get<PromptRow[]>(`/api/prompts?${qs}`);
}

export function fetchPromptDetail(promptId: string): Promise<PromptDetail> {
  return get<PromptDetail>(`/api/prompts/${promptId}`);
}

export function fetchBranches(): Promise<BranchSummary[]> {
  return get<BranchSummary[]>("/api/branches");
}

export function fetchCommits(branch: string, limit = 50, offset = 0): Promise<CommitSummary[]> {
  const qs = new URLSearchParams({ branch, limit: String(limit), offset: String(offset) });
  return get<CommitSummary[]>(`/api/commits?${qs}`);
}

export function fetchCommitDetail(sha: string): Promise<CommitDetail> {
  return get<CommitDetail>(`/api/commits/${sha}`);
}
