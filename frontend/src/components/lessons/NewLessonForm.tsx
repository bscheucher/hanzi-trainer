"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { createLesson } from "@/lib/api";
import { errorMessage } from "@/lib/errors";

export default function NewLessonForm({ textbookId, nextLessonNo }: { textbookId: number; nextLessonNo: number }) {
  const router = useRouter();
  const [lessonNo, setLessonNo] = useState(String(nextLessonNo));
  const [title, setTitle] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await createLesson(textbookId, { lessonNo: Number(lessonNo), title: title.trim() || undefined });
      setTitle("");
      setLessonNo(String(Number(lessonNo) + 1));
      router.refresh();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-wrap items-end gap-3" aria-label="New lesson">
      <label className="flex flex-col text-sm">
        Lesson no.
        <input
          type="number"
          min={1}
          required
          value={lessonNo}
          onChange={(e) => setLessonNo(e.target.value)}
          className="mt-1 w-20 rounded border border-border bg-background px-2 py-1"
        />
      </label>
      <label className="flex flex-col text-sm">
        Title
        <input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="optional, e.g. 你好"
          className="mt-1 rounded border border-border bg-background px-2 py-1"
        />
      </label>
      <button
        type="submit"
        disabled={busy || !lessonNo}
        className="rounded border border-border px-3 py-1.5 text-sm hover:bg-foreground/5 disabled:opacity-50"
      >
        Add lesson
      </button>
      {error && <p role="alert" className="w-full text-sm text-accent">{error}</p>}
    </form>
  );
}
