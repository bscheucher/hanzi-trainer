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

- `CedictImporter` triggered by `POST /api/admin/cedict/import` (enabled unless `hanzi.admin.enabled=false`)
  and on startup if `cedict_entry` is empty and the file exists (log a warning and continue if missing;
  `hanzi.cedict.import-on-startup=false` disables it — the Gradle test task sets this).
- Missing file on the admin endpoint → 503 ProblemDetail explaining where to put it.
- Single transaction: `UPDATE word SET cedict_entry_id = NULL`, `DELETE FROM cedict_entry`, batch inserts of
  1000 rows (`JdbcTemplate.batchUpdate`), then re-link `word.cedict_entry_id` by `(simplified, pinyin_numbered)`.
  Do **not** `TRUNCATE`: the FK from `word` makes it fail, and `TRUNCATE … CASCADE` would empty `word`.
- Idempotent: a second run yields the same table content and links.
- Log row count and duration.

## Lookup

`CedictService.lookup(String hanzi)` → all entries with that simplified form (backs `GET /api/dictionary`).
`CedictService.match(String hanzi, String pinyinNumbered)` → `MATCH | PINYIN_MISMATCH | NOT_FOUND`,
comparing pinyin syllable-by-syllable, case-insensitive, spaces ignored. Latin letters are compared one by one
(CEDICT writes both `[AA zhi4]` and `[ka3 la1 O K]`, so `AA zhi4` equals `A A zhi4`).

- **Tone sandhi:** textbooks print 一 and 不 with sandhi tones (`yí ge`, `bú shì`). When comparing, a syllable
  for 一 matches `yi1|yi2|yi4` and for 不 matches `bu4|bu2`. On `MATCH`, the CEDICT reading is the canonical
  pinyin (stored words always use dictionary tones).
- **Duplicates:** `(simplified, pinyin_numbered)` is not unique (干 `gan1` exists for traditional 乾 and 干).
  Link the entry with the lowest `id`; show definitions of all matching entries.

## PinyinConverter (Java, plus a TS twin in `frontend/src/lib/pinyin.ts`)

### `toToneMarks(String numbered)`
Input: space-separated syllables with tone digits 1–5, `v` or `u:` for ü.
Tokens without a trailing tone digit (Latin letters as in `AA制 [A A zhi4]`, `xx`, punctuation such as `,` `·`)
are passed through unchanged and separated by spaces; adjacent single Latin letters are joined (`O K` → `OK`).
`r5` (erhua) is appended without a mark: `dian3 r5` → `diǎnr`.
Capitalisation is preserved per syllable (`Bei3 jing1` → `Běijīng`, `Ao4 men2` → `Àomén`).

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
number of Latin **letters** (`AA制` → 3, `T恤` → 2, `卡拉OK` → 4); the hanzi is always known when this is called.
Latin letters in the input are uppercase ASCII letters not followed by a lowercase letter (`OK`, `T`, but not
the `B` of `Běijīng`); each becomes one token and keeps its case. Only pinyin syllables are lowercased.
Whitespace, apostrophes (`'` `’`), `-`, `,` and `·` separate chunks; any other non-letter is an error.

Segmentation: backtracking search against the list of valid syllables in `src/main/resources/pinyin/syllables.txt`
(423 toneless syllables derived from CC-CEDICT readings; the TS twin has a copy and its test fails if they differ),
plus `r` for erhua. Rules:
- An apostrophe always separates syllables.
- A non-initial syllable may not start with `a`, `e` or `o` unless preceded by an apostrophe
  (so `xīnán` = `xi1 nan2`, never `xin1 an2`).
- Accept only segmentations with exactly `syllableCount` syllables; if several remain, prefer longer
  syllables first. None → `IllegalArgumentException` (reported as "unparsable pinyin").
- Unmarked syllables get tone 5.
- `r` (erhua) may appear anywhere except as the first syllable (CEDICT has mid-word erhua: `今儿个 jin1 r5 ge5`).
- Vowel-less syllables `m n ng hm hng` are accepted only as a whole chunk (otherwise `pīnyīn` could split as `pi n yin`).

Known limitations (checked by round-tripping all 125k CEDICT readings; 124,591 of 124,599 succeed):
- Syllabic `m` with a tone (`呣 m2`, `姆妈 m1 ma1`) loses its tone, since there is no vowel to mark.
- `儿` alone read as `r5` cannot be converted back (erhua is never the first syllable).
- Lowercase Latin letters (`Tony老师 [t o n y …]`) are read as pinyin and usually fail to parse.
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
| `A A zhi4` | `AA zhì` |

And reverse: `toNumbered` of each marked value, with `syllableCount` = number of numbered tokens, returns the numbered
value (pinyin lowercased: `Běijīng` → `bei3 jing1`; Latin letters keep their case).
