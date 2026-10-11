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
  cedictStatus: 'MATCH' | 'PINYIN_MISMATCH' | 'NOT_FOUND',   // after 一/不 sandhi normalisation (spec 04);
                                                             // on MATCH, pinyin is the CEDICT reading
  cedictReadings: { pinyin: string, pinyinNumbered: string, definitions: string[] }[],  // all CEDICT entries for this hanzi
  pinyinError: string | null,    // pinyin could not be parsed for the hanzi's syllable count (pinyinNumbered is null)
  existingWordId: number | null, // word with same (hanzi, pinyinNumbered) already in vocabulary
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

Re-validate on edit: when the hanzi or pinyin field loses focus, call `GET /api/dictionary/check?hanzi=&pinyin=`.
It applies the same normalisation and comparison as confirmation (so the badge cannot disagree with the confirm
result) and returns the canonical pinyin, which replaces the typed text. A "low confidence", "already known" or
"already in lesson" badge is not recomputed on edit.

Also show `extraction.warnings` above the table and the extracted sentences / grammar points read-only below it.

Buttons: **Confirm selected** · **Re-run extraction** · **Discard page**.

Re-running extraction on a confirmed page asks for confirmation, replaces `raw_extraction` and keeps
`confirmed_at` and all existing words/links; already confirmed words then show `already in lesson`.

## Confirmation (`POST /api/lessons/{id}/words/confirm`)

In one transaction, per item:
1. Normalise: trim; `pinyinNumbered = PinyinConverter.toNumbered(pinyin, syllableCount(hanzi))`; apply
   一/不 sandhi normalisation (spec 04); re-derive display pinyin from numbered so formatting is canonical,
   capitalising its first letter if the input pinyin started with an uppercase letter. When several CEDICT entries
   match, the one whose case agrees with the input wins (`王 wáng` → "king", `王 Wáng` → "surname Wang"). If only
   capitalised entries match (`北京 [Bei3 jing1]`), the display pinyin is capitalised even for lowercase input.
2. Find `word` by `(hanzi, pinyinNumbered)`.
   - **Exists** → reuse. Fill empty `meaning_de` / `part_of_speech` if provided; never overwrite non-empty fields.
   - **New** → insert. `cedict_entry_id` = matching CEDICT entry if `MATCH`. `verified` = `MATCH || acceptMismatch`.
     Create review cards `RECOGNITION`, `WRITING`, `TONE` (state `NEW`, due now).
3. Upsert `lesson_word (lesson_id, word_id)` with `source_page_id` and example sentence
   (keep existing example sentence if the new one is null; an existing `source_page_id` is kept).

Then mark the page as confirmed (`page_image.confirmed_at`, set on the first confirmation only).
An English meaning is required for every item.

Validation errors → 400 with per-item messages: empty hanzi, hanzi without CJK characters, unparsable pinyin
(includes syllable count ≠ `syllableCount(hanzi)`; erhua `儿` counts as the syllable `r5`).

## Tests
- Same word confirmed from two pages of the same lesson → one `word`, one `lesson_word`.
- Same word in two lessons → one `word`, two `lesson_word`, still three cards.
- `了 le` and `了 liǎo` → two words.
- `不是 bú shì` from the page → stored as `bu4 shi4` / `bùshì`, `MATCH`.
- Mismatch without `acceptMismatch` → 400.
