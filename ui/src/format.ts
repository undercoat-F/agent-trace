export function formatTime(iso: string): string {
  // Stored/queried as UTC but the driver returns a zone-less string; treat it as UTC explicitly.
  const d = new Date(iso.endsWith("Z") ? iso : `${iso}Z`);
  return d.toLocaleString(undefined, {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

export function formatDuration(ms: number | null): string {
  if (ms === null) return "—";
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(2)}s`;
}

export function statusLabel(code: number): "OK" | "失敗" | "不明" {
  if (code === 2) return "失敗";
  if (code === 1) return "OK";
  return "不明";
}
