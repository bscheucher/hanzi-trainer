import type { ExtractionStatus } from "@/lib/types";

const STYLES: Record<ExtractionStatus, { label: string; className: string }> = {
  PENDING: { label: "Waiting", className: "border-border text-muted" },
  RUNNING: { label: "Reading page…", className: "border-amber-500 text-amber-600 dark:text-amber-400" },
  DONE: { label: "Done", className: "border-emerald-600 text-emerald-700 dark:text-emerald-400" },
  FAILED: { label: "Failed", className: "border-accent text-accent" },
};

export default function StatusBadge({ status }: { status: ExtractionStatus }) {
  const { label, className } = STYLES[status];
  return <span className={`rounded-full border px-2 py-0.5 text-xs ${className}`}>{label}</span>;
}
