# 03 — REST API

Base path `/api`. JSON, camelCase. Errors are RFC 7807 `ProblemDetail`.
Timestamps ISO-8601 with offset. IDs are numbers.

## Textbooks & lessons

| Method | Path | Body / Query | Response |
|---|---|---|---|
| GET | `/textbooks` | | `TextbookDto[]` |
| POST | `/textbooks` | `{title, edition?}` | `TextbookDto` (201) |
| GET | `/textbooks/{id}/lessons` | | `LessonSummaryDto[]` |
| POST | `/textbooks/{id}/lessons` | `{lessonNo, title?}` | `LessonSummaryDto` (201) — 409 if lessonNo exists |
| GET | `/lessons/{id}` | | `LessonDetailDto` |
| DELETE | `/lessons/{id}` | | 204 |

```ts
TextbookDto       { id, title, edition, lessonCount }
LessonSummaryDto  { id, textbookId, lessonNo, title, pageCount, wordCount, pendingReviewPages }
LessonDetailDto   { ...LessonSummaryDto, pages: PageDto[], words: WordDto[] }
```

## Pages & extraction

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/lessons/{id}/pages` | multipart `file`, `pageNo?` | `PageDto` (202) — extraction starts |
| GET | `/pages/{id}` | | `PageDto` |
| GET | `/pages/{id}/image` | | image bytes |
| POST | `/pages/{id}/extract` | | `PageDto` (202) — re-run extraction |
| DELETE | `/pages/{id}` | | 204 |

```ts
PageDto {
  id, lessonId, pageNo, status: 'PENDING'|'RUNNING'|'DONE'|'FAILED',
  extraction: ExtractionResult | null,    // see spec 05
  candidates: CandidateDto[] | null,      // extraction items enriched with CEDICT check, see spec 06
  confirmed: boolean,                     // true once vocabulary from this page was confirmed
  errorMessage, createdAt
}
```

## Vocabulary

| Method | Path | Body / Query | Response |
|---|---|---|---|
| POST | `/lessons/{id}/words/confirm` | `ConfirmRequest` | `ConfirmResult` |
| GET | `/words` | `lessonId?`, `q?`, `page`, `size` | `Page<WordDto>` |
| GET | `/words/{id}` | | `WordDetailDto` |
| PATCH | `/words/{id}` | `{meaningEn?, meaningDe?, partOfSpeech?}` | `WordDto` |
| GET | `/dictionary` | `q` (hanzi or pinyin) | `CedictEntryDto[]` (max 20) |

```ts
WordDto        { id, hanzi, pinyin, pinyinNumbered, meaningEn, meaningDe, partOfSpeech, verified }
WordDetailDto  { ...WordDto, lessons: {lessonId, lessonNo, textbookTitle, exampleSentence}[],
                 cards: {cardType, state, due}[] }
CedictEntryDto { id, simplified, traditional, pinyin, pinyinNumbered, definitions: string[] }
ConfirmRequest { sourcePageId?: number, items: ConfirmItem[] }
ConfirmItem    { hanzi, pinyin, meaningEn, meaningDe?, partOfSpeech?, exampleSentence?,
                 acceptMismatch: boolean }
ConfirmResult  { created: number, reused: number, linked: number, words: WordDto[] }
```

`q` search on `/words`: matches `hanzi` (contains), `pinyin_numbered` with tones stripped
(`xuexi` matches `xue2 xi2`), or meaning (case-insensitive contains).

## Exercises

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/lessons/{id}/exercises/generate` | `{types: ExerciseType[], count}` | `GenerationJobDto` (202) |
| GET | `/generation-jobs/{id}` | | `GenerationJobDto` |
| GET | `/lessons/{id}/exercises` | `type?` | `ExerciseDto[]` |
| POST | `/practice/sessions` | `{lessonId, types?, size?}` | `PracticeSessionDto` |
| POST | `/exercises/{id}/answer` | `AnswerRequest` | `AnswerResult` |
| DELETE | `/exercises/{id}` | | 204 |

```ts
ExerciseDto        { id, type, prompt, wordIds: number[] }      // answer NOT included
PracticeSessionDto { exercises: ExerciseDto[] }
AnswerRequest      { response: unknown, durationMs: number, strokeMistakes?: number[] }
AnswerResult       { correct: boolean, score: number /*0..1*/, expected: unknown,
                     feedback?: string, suggestedRating: 1|2|3|4 }
GenerationJobDto   { id, status: 'RUNNING'|'DONE'|'FAILED', created: number, rejected: number, error? }
```

Generation jobs may be held in memory (map of job id → status) in v1; no table needed.

## Review (SRS)

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/reviews/stats` | | `ReviewStatsDto` |
| GET | `/reviews/due` | `limit?` (default 30) | `ReviewItemDto[]` |
| POST | `/reviews/{cardId}/answer` | `AnswerRequest` | `AnswerResult` |
| POST | `/reviews/{cardId}/rate` | `{rating: 1..4}` | `CardDto` |

```ts
ReviewStatsDto { dueNow, dueToday, reviewedToday, byState: {NEW, LEARNING, REVIEW, RELEARNING} }
ReviewItemDto  { cardId, cardType, word: WordDto, exercise: ExerciseDto,      // exercise built on the fly
                 previewIntervals: { "1": string, "2": string, "3": string, "4": string } }  // ISO-8601 durations
CardDto        { id, wordId, cardType, state, due, stability, difficulty, reps, lapses }
```

| Method | Path | Query | Response |
|---|---|---|---|
| GET | `/reviews/forecast` | `days` (default 30) | `{date, count}[]` |

## Admin (dev only)

| Method | Path | Response |
|---|---|---|
| POST | `/admin/cedict/import` | `{rows, durationMs}` |
