# 06 — Vocabulary review & confirmation

LLM output never goes straight into the vocabulary. The user reviews every page.

## Candidates

When a `PageDto` is returned for a `DONE` page, the backend enriches each `ExtractedWord` into a candidate:

```ts
CandidateDto {
  index: number,                 // position in raw_extraction.words
  hanzi, pinyin, pinyinNumbered, meaningEn, meaningDe, partOfSpeech, exampleSentence,
  confidence: number,
  pinyinFromPage: boolean,
  cedictStatus: 'MATCH' | 'PINYIN_MISMATCH' | 'NOT_FOUND',
  cedictReadings: { pinyin: string, definitions: string[] }[],   // all CEDICT entries for this hanzi
  existingWordId: number | null, // word with same (hanzi, pinyin) already in vocabulary
  alreadyInLesson: boolean
}
```

## Review screen (`/lessons/[id]/pages/[pageId]`)

Layout: page image on the left (zoomable), editable candidate table on the right; stacks vertically on mobile.

Per row:
- Editable: hanzi, pinyin (tone marks; accept numbered input `xue2xi2` and convert on blur), meaning EN,
  meaning DE, part of speech, example sentence.
- Badges: `✓ CEDICT` (green), `pinyin differs` (amber, shows CEDICT readings as one-click replacements),
  `not in dictionary` (grey), `low confidence` when `confidence < 0.7`, `already known` when `existingWordId`,
  `already in lesson`.
- Checkbox "include" (default on, except `alreadyInLesson`).
- Delete row; "Add word" button at the bottom.

Rows with `PINYIN_MISMATCH` that are still included require an explicit "keep my pinyin" tick
(`acceptMismatch = true`) before Confirm is enabled. Changing the pinyin to a CEDICT reading clears the warning.

Re-validate on edit: after hanzi/pinyin changes, call `GET /api/dictionary?q=` to refresh the badge.

Also show `extraction.warnings` above the table and the extracted sentences / grammar points read-only below it.

Buttons: **Confirm selected** · **Re-run extraction** · **Discard page**.

## Confirmation (`POST /api/lessons/{id}/words/confirm`)

In one transaction, per item:
1. Normalise: trim; `pinyinNumbered = PinyinConverter.toNumbered(pinyin)`; re-derive display pinyin
   from numbered so formatting is canonical.
2. Find `word` by `(hanzi, pinyin)`.
   - **Exists** → reuse. Fill empty `meaning_de` / `part_of_speech` if provided; never overwrite non-empty fields.
   - **New** → insert. `cedict_entry_id` = matching CEDICT entry if `MATCH`. `verified` = `MATCH || acceptMismatch`.
     Create review cards `RECOGNITION`, `WRITING`, `TONE` (state `NEW`, due now).
3. Upsert `lesson_word (lesson_id, word_id)` with `source_page_id` and example sentence
   (keep existing example sentence if the new one is null).

Then mark the page as confirmed (add `confirmed_at TIMESTAMPTZ` to `page_image` in migration `V2`).

Validation errors → 400 with per-item messages: empty hanzi, non-CJK hanzi, unparsable pinyin,
syllable count ≠ character count (allow exceptions: words containing `儿` erhua may have one syllable fewer).

## Tests
- Same word confirmed from two pages of the same lesson → one `word`, one `lesson_word`.
- Same word in two lessons → one `word`, two `lesson_word`, still three cards.
- `了 le` and `了 liǎo` → two words.
- Mismatch without `acceptMismatch` → 400.
