"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { deleteLesson } from "@/lib/api";
import { errorMessage } from "@/lib/errors";

/** Two-step delete: the first click asks for confirmation inline. */
export default function DeleteLessonButton({ lessonId }: { lessonId: number }) {
  const router = useRouter();
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function remove() {
    try {
      await deleteLesson(lessonId);
      router.push("/lessons");
      router.refresh();
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  return (
    <div className="flex items-center gap-2 text-sm">
      {confirming ? (
        <>
          <span>Delete lesson, its pages and exercises? Words stay in your vocabulary.</span>
          <button onClick={remove} className="rounded bg-accent px-2 py-1 text-background">
            Delete
          </button>
          <button onClick={() => setConfirming(false)} className="rounded border border-border px-2 py-1">
            Cancel
          </button>
        </>
      ) : (
        <button onClick={() => setConfirming(true)} className="text-muted hover:text-accent">
          Delete lesson
        </button>
      )}
      {error && <span role="alert" className="text-accent">{error}</span>}
    </div>
  );
}
