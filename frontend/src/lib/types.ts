// Mirrors the DTOs in src/docs/specs/03-api.md.

/** RFC 7807 error body returned by the backend for every error. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
}

export interface CedictEntryDto {
  id: number;
  simplified: string;
  traditional: string;
  /** Tone marks, CEDICT capitalisation kept (`Běijīng`). */
  pinyin: string;
  /** Lowercase, `ü` as `v` (`bei3 jing1`). */
  pinyinNumbered: string;
  definitions: string[];
}
