# 02 — Data model

The schema is defined in `src/main/resources/db/migration/V1__init_schema.sql`.
This file explains intent and invariants. Changes go into new migrations.

## Entities

| Table | Purpose |
|---|---|
| `textbook` | A printed book, e.g. "HSK Standard Course 1" |
| `lesson` | Lesson within a textbook, unique `(textbook_id, lesson_no)` |
| `page_image` | One scanned page; holds unreviewed LLM output in `raw_extraction` |
| `cedict_entry` | Read-only reference dictionary |
| `word` | Master vocabulary, unique `(hanzi, pinyin_numbered)` |
| `lesson_word` | Which words a lesson introduces, plus example sentence |
| `exercise` | Stored exercise with `prompt` and `answer` JSON; `sentence` for sentence types (V2) |
| `exercise_word` | Words an exercise practises |
| `review_card` | FSRS state per word per skill |
| `review_log` | Every rating ever given |

## Invariants

1. A `word` is created **only** through vocabulary confirmation (spec 06), never directly from LLM output.
2. When a `word` is created, three `review_card`s are created: `RECOGNITION`, `WRITING`, `TONE`.
   v1 always creates `WRITING`; characters without Hanzi Writer data are self-assessed (spec 08).
3. `word.verified = true` if the word matched a CEDICT entry with the same simplified form and pinyin,
   (after 一/不 sandhi normalisation, spec 04), or the user explicitly confirmed a mismatch.
4. `raw_extraction` is never modified after extraction except by re-running extraction.
5. Deleting a lesson removes `lesson_word` and exercises, but **not** `word` rows (they may belong to
   other lessons). Orphaned words remain and stay in review until deleted via `DELETE /api/words/{id}`.
6. `word.pinyin_numbered` is always lowercase; `word.pinyin` may keep a capital for proper nouns.
7. `exercise.sentence` is the Chinese sentence of a `FILL_BLANK` (`fullSentence`), `WORD_ORDER` (the first accepted
   order joined) or `TRANSLATION` (`reference`) exercise, and null for the other types (CHECK). It is unique per
   `(lesson_id, type)` (partial unique index), so the database rejects duplicate sentences, also from generation
   jobs running at the same time.

## JPA mapping notes

- `JSONB` columns map to `JsonNode` with `@JdbcTypeCode(SqlTypes.JSON)`.
- `TEXT[]` (`cedict_entry.definitions`) maps to `List<String>` with `@JdbcTypeCode(SqlTypes.ARRAY)`.
- Enum-like `VARCHAR` columns map to Java enums with `@Enumerated(EnumType.STRING)`:
  `ExtractionStatus`, `ExerciseType`, `CardType`, `CardState`.
- `lesson_word`, `exercise_word` use `@EmbeddedId` composite keys.
- No bidirectional collections unless needed; fetch lazily; use repository queries / projections for lists.

## Exercise JSON shapes

`prompt` and `answer` per type are defined in `07-exercises.md`. In Java, model them as:

```java
sealed interface ExercisePrompt permits HanziToMeaningPrompt, MeaningToHanziPrompt, TonePrompt,
        FillBlankPrompt, WordOrderPrompt, TranslationPrompt {}
```

Serialize with Jackson using the exercise `type` column as discriminator (not a field inside the JSON).
