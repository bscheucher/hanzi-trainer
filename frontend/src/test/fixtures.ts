import type { PageDto } from "@/lib/types";

export function page(overrides: Partial<PageDto> = {}): PageDto {
  return {
    id: 1,
    lessonId: 7,
    pageNo: 12,
    status: "PENDING",
    extraction: null,
    candidates: null,
    confirmed: false,
    errorMessage: null,
    createdAt: "2026-10-10T12:00:00Z",
    ...overrides,
  };
}

export const EXTRACTION = {
  pageType: "VOCABULARY_LIST" as const,
  words: [
    { hanzi: "朋友", pinyin: "péngyou", pinyinFromPage: true, meaningEn: "friend", meaningDe: "Freund",
      partOfSpeech: "n.", exampleSentence: null, confidence: 0.98 },
    { hanzi: "老师", pinyin: "lǎoshī", pinyinFromPage: true, meaningEn: "teacher", meaningDe: null,
      partOfSpeech: "n.", exampleSentence: null, confidence: 0.95 },
  ],
  sentences: [],
  grammarPoints: [],
  warnings: ["bottom of page cut off"],
};
