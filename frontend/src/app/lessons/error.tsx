"use client";

import { API_BASE_URL } from "@/lib/api";

// Server-side error messages are redacted in production builds, so the hint stays generic.
export default function LessonsError({ retry }: { error: Error & { digest?: string }; retry: () => void }) {
  return (
    <section role="alert" className="flex flex-col gap-3">
      <h1 className="text-2xl font-semibold">Could not load lessons</h1>
      <p className="text-muted">
        Check that the backend at {API_BASE_URL} is running (<code>./gradlew bootRun</code>) and try again.
      </p>
      <button onClick={retry} className="self-start rounded border border-border px-3 py-1.5 text-sm">
        Try again
      </button>
    </section>
  );
}
