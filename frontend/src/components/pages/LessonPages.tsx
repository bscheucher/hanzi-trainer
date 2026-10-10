"use client";

import { useEffect, useState } from "react";
import { deletePage, getPage, rerunExtraction } from "@/lib/api";
import { errorMessage } from "@/lib/errors";
import type { PageDto } from "@/lib/types";
import PageCard from "./PageCard";
import PageUpload from "./PageUpload";

export const POLL_INTERVAL_MS = 2000;

const isUnfinished = (page: PageDto) => page.status === "PENDING" || page.status === "RUNNING";

/** Page list of one lesson: upload, per-page status (polled while extraction runs), re-run and delete. */
export default function LessonPages({ lessonId, initialPages }: { lessonId: number; initialPages: PageDto[] }) {
  const [pages, setPages] = useState(initialPages);
  const [error, setError] = useState<string | null>(null);
  const polling = pages.some(isUnfinished);

  useEffect(() => {
    if (!polling) return;
    const timer = setInterval(async () => {
      const unfinished = pages.filter(isUnfinished);
      try {
        const updates = await Promise.all(unfinished.map((p) => getPage(p.id)));
        setPages((current) => current.map((p) => updates.find((u) => u.id === p.id) ?? p));
      } catch (e) {
        setError(errorMessage(e));
      }
    }, POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [polling, pages]);

  const replace = (page: PageDto) => setPages((current) => current.map((p) => (p.id === page.id ? page : p)));

  async function rerun(id: number) {
    try {
      replace(await rerunExtraction(id));
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  async function remove(id: number) {
    try {
      await deletePage(id);
      setPages((current) => current.filter((p) => p.id !== id));
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  return (
    <section className="flex flex-col gap-4">
      <h2 className="text-lg font-semibold">
        Pages <span className="text-sm font-normal text-muted">({pages.length})</span>
      </h2>
      <PageUpload lessonId={lessonId} onUploaded={(page) => setPages((current) => [...current, page])} />
      {error && <p role="alert" className="text-sm text-accent">{error}</p>}
      {pages.length === 0 ? (
        <p className="text-muted">No pages yet. Upload photos of the textbook pages of this lesson.</p>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2">
          {pages.map((page) => (
            <PageCard key={page.id} page={page} onRerun={rerun} onDelete={remove} />
          ))}
        </ul>
      )}
    </section>
  );
}
