"use client";

import { useState } from "react";
import { pageImageUrl } from "@/lib/api";
import type { PageDto } from "@/lib/types";
import StatusBadge from "./StatusBadge";

interface Props {
  page: PageDto;
  onRerun: (id: number) => void;
  onDelete: (id: number) => void;
}

export default function PageCard({ page, onRerun, onDelete }: Props) {
  const [confirmDelete, setConfirmDelete] = useState(false);
  const words = page.extraction?.words ?? [];
  const warnings = page.extraction?.warnings ?? [];

  return (
    <li className="flex gap-4 rounded border border-border p-3" aria-label={pageLabel(page)}>
      {/* eslint-disable-next-line @next/next/no-img-element -- served by the backend, not optimisable */}
      <img
        src={pageImageUrl(page.id)}
        alt={pageLabel(page)}
        loading="lazy"
        className="h-28 w-20 shrink-0 rounded border border-border object-cover"
      />
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <div className="flex items-center gap-2">
          <span className="font-medium">{pageLabel(page)}</span>
          <StatusBadge status={page.status} />
          {page.confirmed && <span className="text-xs text-muted">confirmed</span>}
        </div>

        {page.status === "DONE" && page.extraction && (
          <>
            <p className="text-sm text-muted">
              {words.length} {words.length === 1 ? "word" : "words"} · {page.extraction.pageType.toLowerCase().replace("_", " ")}
              {warnings.length > 0 && ` · ${warnings.length} ${warnings.length === 1 ? "warning" : "warnings"}`}
            </p>
            {words.length > 0 && (
              <p lang="zh-CN" className="truncate text-lg">
                {words.map((w) => w.hanzi).join("　")}
              </p>
            )}
          </>
        )}

        {page.status === "FAILED" && (
          <p role="alert" className="text-sm text-accent">
            {page.errorMessage ?? "Extraction failed."}
          </p>
        )}

        <div className="mt-auto flex gap-3 text-sm">
          {page.status === "FAILED" && (
            <button onClick={() => onRerun(page.id)} className="underline hover:text-foreground">
              Re-run extraction
            </button>
          )}
          {confirmDelete ? (
            <>
              <button onClick={() => onDelete(page.id)} className="text-accent underline">
                Confirm delete
              </button>
              <button onClick={() => setConfirmDelete(false)} className="text-muted underline">
                Cancel
              </button>
            </>
          ) : (
            <button onClick={() => setConfirmDelete(true)} className="text-muted hover:text-accent">
              Delete page
            </button>
          )}
        </div>
      </div>
    </li>
  );
}

function pageLabel(page: PageDto) {
  return page.pageNo != null ? `Page ${page.pageNo}` : `Page #${page.id}`;
}
