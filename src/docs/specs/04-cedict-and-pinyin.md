# 04 — CC-CEDICT import & pinyin

## Source

CC-CEDICT, licensed CC BY-SA 4.0, downloadable from MDBG (`cedict_1_0_ts_utf-8_mdbg.txt.gz`).
The user downloads and unzips it to `data/cedict/cedict_ts.u8`. The app does **not** download it.
Show attribution ("Dictionary data: CC-CEDICT, CC BY-SA 4.0") in the UI footer.

## File format

```
# comment lines start with #
學習 学习 [xue2 xi2] /to learn/to study/
了 了 [le5] /(completed action marker)/.../
了 了 [liao3] /to finish/to understand/.../
女 女 [nu:3] /female/woman/daughter/
```

Regex per line: `^(\S+) (\S+) \[([^\]]*)\] /(.*)/$`
→ traditional, simplified, pinyinNumbered, definitions split on `/`.

Normalise CEDICT pinyin before storing:
- `u:` → `v` in `pinyin_numbered` (so `nu:3` → `nv3`)
- Lowercase syllables, but keep proper-noun info: if CEDICT pinyin starts uppercase, keep that in
  display pinyin only (`Bei3 jing1` → `Běijīng`), lowercase in `pinyin_numbered`.

## Import

- `CedictImporter` triggered by `POST /api/admin/cedict/import` (dev only) and on startup
  if `cedict_entry` is empty and the file exists (log a warning and continue if missing).
- Batch inserts of 1000 rows (`JdbcTemplate.batchUpdate`), single transaction, truncate first.
- Idempotent: re-import replaces the table content; `word.cedict_entry_id` links are re-resolved
  after import by `(simplified, pinyin_numbered)`.
- Log row count and duration.

## Lookup

`CedictService.lookup(String hanzi)` → all entries with that simplified form.
`CedictService.match(String hanzi, String pinyinNumbered)` → `MATCH | PINYIN_MISMATCH | NOT_FOUND`,
comparing pinyin syllable-by-syllable, case-insensitive, spaces ignored.

## PinyinConverter (Java, plus a TS twin in `frontend/src/lib/pinyin.ts`)

### `toToneMarks(String numbered)`
Input: space-separated syllables with tone digits 1–5, `v` or `u:` for ü.

Tone-mark placement rule:
1. If the syllable has `a` or `e`, it takes the mark.
2. Else if it has `ou`, `o` takes the mark.
3. Else the **last** vowel takes the mark.

Tone 5 (neutral) → no mark. `v` → `ü` (with mark if applicable: `ǖ ǘ ǚ ǜ`).
Output joins syllables of one word **without** spaces (`xue2 xi2` → `xuéxí`); for display of
multi-syllable words where ambiguity exists (`xi1 an1` → `xī'ān`), insert an apostrophe before a
syllable starting with `a`, `e` or `o` that is not the first syllable.

### `toNumbered(String marked)` — needed for LLM output
LLM returns tone-marked pinyin; convert to numbered for storage and comparison.
Requires syllable segmentation: use a greedy longest-match against the list of valid Mandarin
syllables (≈410, embed as a resource file), respecting apostrophes. Unmarked syllables get tone 5.

### `stripTones(String numbered)` → `xue xi` (for search)

### Test table (must pass)

| Numbered | Marked |
|---|---|
| `ni3 hao3` | `nǐhǎo` |
| `xue2 xi2` | `xuéxí` |
| `le5` | `le` |
| `nv3` | `nǚ` |
| `lv4 se4` | `lǜsè` |
| `gou3` | `gǒu` |
| `liu2` | `liú` |
| `gui4` | `guì` |
| `xi1 an1` | `xī'ān` |
| `peng2 you5` | `péngyou` |
| `er4` | `èr` |
| `zhuang1` | `zhuāng` |

And reverse: `toNumbered` of each marked value returns the numbered value.
