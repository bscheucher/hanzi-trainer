"use client";

import { useRef, useState } from "react";
import { uploadPage } from "@/lib/api";
import { errorMessage } from "@/lib/errors";
import { prepareImage } from "@/lib/image";
import type { PageDto } from "@/lib/types";

interface Props {
  lessonId: number;
  onUploaded: (page: PageDto) => void;
}

/** File picker (several pages at once) and camera capture on phones; images are resized in the browser. */
export default function PageUpload({ lessonId, onUploaded }: Props) {
  const [firstPageNo, setFirstPageNo] = useState("");
  const [progress, setProgress] = useState<string | null>(null);
  const [errors, setErrors] = useState<string[]>([]);
  const pickerRef = useRef<HTMLInputElement>(null);
  const cameraRef = useRef<HTMLInputElement>(null);

  async function upload(files: FileList | null) {
    if (!files || files.length === 0) return;
    const list = Array.from(files);
    const start = firstPageNo ? Number(firstPageNo) : undefined;
    const failures: string[] = [];
    for (const [index, file] of list.entries()) {
      setProgress(`Uploading ${index + 1} of ${list.length}…`);
      try {
        const image = await prepareImage(file);
        onUploaded(await uploadPage(lessonId, image, start === undefined ? undefined : start + index));
      } catch (e) {
        failures.push(`${file.name}: ${errorMessage(e)}`);
      }
    }
    setErrors(failures);
    setProgress(null);
    if (start !== undefined) setFirstPageNo(String(start + list.length));
    if (pickerRef.current) pickerRef.current.value = "";
    if (cameraRef.current) cameraRef.current.value = "";
  }

  const busy = progress !== null;
  return (
    <section aria-label="Upload pages" className="rounded border border-dashed border-border p-4">
      <div className="flex flex-wrap items-end gap-3">
        <label className="flex flex-col text-sm">
          First page no.
          <input
            type="number"
            min={1}
            value={firstPageNo}
            onChange={(e) => setFirstPageNo(e.target.value)}
            placeholder="optional"
            className="mt-1 w-28 rounded border border-border bg-background px-2 py-1"
          />
        </label>
        <label className={`cursor-pointer rounded bg-foreground px-3 py-1.5 text-sm text-background ${busy ? "pointer-events-none opacity-50" : ""}`}>
          Choose images
          <input
            ref={pickerRef}
            type="file"
            accept="image/*"
            multiple
            disabled={busy}
            onChange={(e) => upload(e.target.files)}
            className="sr-only"
            data-testid="page-file-input"
          />
        </label>
        <label className={`cursor-pointer rounded border border-border px-3 py-1.5 text-sm ${busy ? "pointer-events-none opacity-50" : ""}`}>
          Take photo
          <input
            ref={cameraRef}
            type="file"
            accept="image/*"
            capture="environment"
            disabled={busy}
            onChange={(e) => upload(e.target.files)}
            className="sr-only"
          />
        </label>
        {progress && <span className="text-sm text-muted" role="status">{progress}</span>}
      </div>
      {errors.length > 0 && (
        <ul role="alert" className="mt-2 text-sm text-accent">
          {errors.map((e) => (
            <li key={e}>{e}</li>
          ))}
        </ul>
      )}
    </section>
  );
}
