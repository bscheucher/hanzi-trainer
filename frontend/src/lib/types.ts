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

export interface TextbookDto {
  id: number;
  title: string;
  edition: string | null;
  lessonCount: number;
}

export interface LessonSummaryDto {
  id: number;
  textbookId: number;
  lessonNo: number;
  title: string | null;
  pageCount: number;
  wordCount: number;
  /** Pages with a finished extraction whose vocabulary is not confirmed yet. */
  pendingReviewPages: number;
}

export interface LessonDetailDto extends LessonSummaryDto {
  pages: PageDto[];
  words: WordDto[];
}

export type ExtractionStatus = "PENDING" | "RUNNING" | "DONE" | "FAILED";

export interface PageDto {
  id: number;
  lessonId: number;
  pageNo: number | null;
  status: ExtractionStatus;
  extraction: ExtractionResult | null;
  /** Filled from the vocabulary review milestone on (spec 06). */
  candidates: unknown[] | null;
  confirmed: boolean;
  errorMessage: string | null;
  createdAt: string;
}

export interface ExtractionResult {
  pageType: "VOCABULARY_LIST" | "TEXT" | "DIALOGUE" | "GRAMMAR" | "EXERCISES" | "OTHER";
  words: ExtractedWord[];
  sentences: ExtractedSentence[];
  grammarPoints: string[];
  warnings: string[];
}

export interface ExtractedWord {
  hanzi: string;
  pinyin: string;
  pinyinFromPage: boolean;
  meaningEn: string | null;
  meaningDe: string | null;
  partOfSpeech: string | null;
  exampleSentence: string | null;
  confidence: number;
}

export interface ExtractedSentence {
  hanzi: string;
  pinyin: string | null;
  translationEn: string | null;
}

export interface WordDto {
  id: number;
  hanzi: string;
  pinyin: string;
  pinyinNumbered: string;
  meaningEn: string | null;
  meaningDe: string | null;
  partOfSpeech: string | null;
  verified: boolean;
}
