"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { createTextbook } from "@/lib/api";
import { errorMessage } from "@/lib/errors";

export default function NewTextbookForm() {
  const router = useRouter();
  const [title, setTitle] = useState("");
  const [edition, setEdition] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await createTextbook({ title: title.trim(), edition: edition.trim() || undefined });
      setTitle("");
      setEdition("");
      router.refresh();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-wrap items-end gap-3" aria-label="New textbook">
      <label className="flex flex-col text-sm">
        Title
        <input
          required
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="HSK Standard Course 1"
          className="mt-1 rounded border border-border bg-background px-2 py-1"
        />
      </label>
      <label className="flex flex-col text-sm">
        Edition
        <input
          value={edition}
          onChange={(e) => setEdition(e.target.value)}
          placeholder="optional"
          className="mt-1 w-32 rounded border border-border bg-background px-2 py-1"
        />
      </label>
      <button
        type="submit"
        disabled={busy || !title.trim()}
        className="rounded bg-foreground px-3 py-1.5 text-sm text-background disabled:opacity-50"
      >
        Add textbook
      </button>
      {error && <p role="alert" className="w-full text-sm text-accent">{error}</p>}
    </form>
  );
}
