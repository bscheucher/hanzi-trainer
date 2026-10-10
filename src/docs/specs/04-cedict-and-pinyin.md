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
- Single transaction: `UPDATE word SET cedict_entry_id = NULL`, `DELETE FROM cedict_entry`, batch inserts of
  1000 rows (`JdbcTemplate.batchUpdate`), then re-link `word.cedict_entry_id` by `(simplified, pinyin_numbered)`.
  Do **not** `TRUNCATE`: the FK from `word` makes it fail, and `TRUNCATE … CASCADE` would empty `word`.
- Idempotent: a second run yields the same table content and links.
- Log row count and duration.

## Lookup

`CedictService.lookup(String hanzi)` → all entries with that simplified form (backs `GET /api/dictionary`).
`CedictService.match(String hanzi, String pinyinNumbered)` → `MATCH | PINYIN_MISMATCH | NOT_FOUND`,
comparing pinyin syllable-by-syllable, case-insensitive, spaces ignored.

- **Tone sandhi:** textbooks print 一 and 不 with sandhi tones (`yí ge`, `bú shì`). When comparing, a syllable
  for 一 matches `yi1|yi2|yi4` and for 不 matches `bu4|bu2`. On `MATCH`, the CEDICT reading is the canonical
  pinyin (stored words always use dictionary tones).
- **Duplicates:** `(simplified, pinyin_numbered)` is not unique (干 `gan1` exists for traditional 乾 and 干).
  Link the entry with the lowest `id`; show definitions of all matching entries.

## PinyinConverter (Java, plus a TS twin in `frontend/src/lib/pinyin.ts`)

### `toToneMarks(String numbered)`
Input: space-separated syllables with tone digits 1–5, `v` or `u:` for ü.
Tokens without a trailing tone digit (Latin letters as in `AA制 [A A zhi4]`, `xx`, punctuation such as `,` `·`)
are passed through unchanged and separated by spaces. `r5` (erhua) is appended without a mark: `dian3 r5` → `diǎnr`.
Capitalisation of the first letter is preserved (`Bei3 jing1` → `Běijīng`).

Tone-mark placement rule:
1. If the syllable has `a` or `e`, it takes the mark.
2. Else if it has `ou`, `o` takes the mark.
3. Else the **last** vowel takes the mark.

Tone 5 (neutral) → no mark. `v` → `ü` (with mark if applicable: `ǖ ǘ ǚ ǜ`).
Output joins syllables of one word **without** spaces (`xue2 xi2` → `xuéxí`); for display of
multi-syllable words where ambiguity exists (`xi1 an1` → `xī'ān`), insert an apostrophe before a
syllable starting with `a`, `e` or `o` that is not the first syllable.

### `toNumbered(String marked, int syllableCount)` — needed for LLM output
LLM returns tone-marked pinyin; convert to numbered for storage and comparison. Output is lowercase.
`syllableCount` = number of CJK characters in the hanzi (each counts one syllable, 儿 included) plus the
number of Latin-letter runs; the hanzi is always known when this is called.

Segmentation: backtracking search against the list of valid Mandarin syllables (≈410, embed as a resource
file), plus `r` for a trailing erhua. Rules:
- An apostrophe always separates syllables.
- A non-initial syllable may not start with `a`, `e` or `o` unless preceded by an apostrophe
  (so `xīnán` = `xi1 nan2`, never `xin1 an2`).
- Accept only segmentations with exactly `syllableCount` syllables; if several remain, prefer longer
  syllables first. None → `IllegalArgumentException` (reported as "unparsable pinyin").
- Unmarked syllables get tone 5.

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
| `xi1 nan2` | `xīnán` |
| `yi1 dian3 r5` | `yīdiǎnr` |
| `Bei3 jing1` | `Běijīng` |
| `A A zhi4` | `A A zhì` |

And reverse: `toNumbered` of each marked value returns the numbered value (lowercased: `Běijīng` → `bei3 jing1`).
