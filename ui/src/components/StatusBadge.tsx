import { statusLabel } from "../format";

export function StatusBadge({ code }: { code: number }) {
  const variant = code === 2 ? "error" : code === 1 ? "ok" : "unset";
  return <span className={`badge badge--${variant}`}>{statusLabel(code)}</span>;
}
