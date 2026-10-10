// Twin of the Java PinyinConverter (src/main/java/.../cedict/PinyinConverter.java).
// Rules and test table: src/docs/specs/04-cedict-and-pinyin.md. Keep both implementations in sync.

import { SYLLABLES } from "./pinyin-syllables";

const VOWELS = "aeiouv";
const MARKED_LOWER = ["āáǎà", "ēéěè", "īíǐì", "ōóǒò", "ūúǔù", "ǖǘǚǜ"];
const MARKED_UPPER = ["ĀÁǍÀ", "ĒÉĚÈ", "ĪÍǏÌ", "ŌÓǑÒ", "ŪÚǓÙ", "ǕǗǙǛ"];

const NUMBERED_SYLLABLE = /^([A-Za-zÜü:]+)([1-5])$/;
const TONE_DIGIT = /(?<=[A-Za-zÜü:])[1-5]/g;

/** Syllables without a vowel; only accepted as a whole word, never inside a segmentation. */
const STANDALONE_ONLY = new Set(["m", "n", "ng", "hm", "hng"]);
const ERHUA = "r";
const MAX_SYLLABLE_LENGTH = 6;

/** Marked vowel → [base letter, tone]. */
const VOWEL_TONES = new Map<string, [string, number]>();
for (let v = 0; v < VOWELS.length; v++) {
  for (let tone = 1; tone <= 4; tone++) {
    VOWEL_TONES.set(MARKED_LOWER[v][tone - 1], [VOWELS[v], tone]);
    VOWEL_TONES.set(MARKED_UPPER[v][tone - 1], [VOWELS[v], tone]);
  }
}

// ------------------------------------------------------------------ numbered → marks

type Token = { syllable: boolean; text: string; tone: number };

/**
 * `"xue2 xi2"` → `"xuéxí"`. Syllables of one word are joined; an apostrophe goes before a non-initial
 * syllable starting with a, e or o. Tokens without a tone digit (Latin letters, punctuation) pass through,
 * separated by spaces; adjacent single Latin letters are joined (`"O K"` → `"OK"`).
 */
export function toToneMarks(numbered: string): string {
  if (!numbered.trim()) return "";
  let out = "";
  let previous: Token | null = null;
  for (const raw of numbered.trim().split(/\s+/)) {
    const m = NUMBERED_SYLLABLE.exec(raw);
    const token: Token = m
      ? { syllable: true, text: normaliseLetters(m[1]), tone: Number(m[2]) }
      : { syllable: false, text: raw, tone: 0 };
    if (previous) {
      if (previous.syllable && token.syllable) {
        if (startsWithAeo(token.text)) out += "'";
      } else if (!(isSingleLetter(previous) && isSingleLetter(token))) {
        out += " ";
      }
    }
    out += token.syllable ? markSyllable(token.text, token.tone) : token.text;
    previous = token;
  }
  return out;
}

function markSyllable(letters: string, tone: number): string {
  const markAt = tone >= 1 && tone <= 4 ? markPosition(letters.toLowerCase()) : -1;
  let out = "";
  for (let i = 0; i < letters.length; i++) {
    const c = letters[i];
    const lower = c.toLowerCase();
    const upper = c !== lower;
    if (i === markAt) {
      out += (upper ? MARKED_UPPER : MARKED_LOWER)[VOWELS.indexOf(lower)][tone - 1];
    } else if (lower === "v") {
      out += upper ? "Ü" : "ü";
    } else {
      out += c;
    }
  }
  return out;
}

/** a or e takes the mark; else o in "ou"; else the last vowel. -1 if there is no vowel. */
function markPosition(lower: string): number {
  for (const v of ["a", "e"]) {
    const i = lower.indexOf(v);
    if (i >= 0) return i;
  }
  const ou = lower.indexOf("ou");
  if (ou >= 0) return ou;
  for (let i = lower.length - 1; i >= 0; i--) {
    if (VOWELS.includes(lower[i])) return i;
  }
  return -1;
}

// ------------------------------------------------------------------ marks → numbered

type Item =
  | { kind: "latin"; letter: string }
  | { kind: "chunk"; base: string; tones: number[]; syllableCount: number };

/**
 * `"xuéxí", 2` → `"xue2 xi2"`. Pinyin syllables are lowercased; Latin letters (uppercase ASCII letters not
 * followed by a lowercase letter, e.g. `OK`, `T`) are emitted one per letter and keep their case.
 *
 * @param syllableCount number of CJK characters plus Latin letters in the word's hanzi
 * @throws Error if no segmentation yields exactly `syllableCount` syllables
 */
export function toNumbered(marked: string, syllableCount: number): string {
  const items = tokenise(marked.trim().normalize("NFC"));
  const latin = items.filter((i) => i.kind === "latin").length;
  const chunks = items.length - latin;
  const target = syllableCount - latin;
  if (items.length === 0 || target < chunks) throw unparsable(marked, syllableCount);

  const syllables: string[] = [];
  if (!segment(items, 0, 0, target, syllables)) throw unparsable(marked, syllableCount);

  const out: string[] = [];
  let next = 0;
  for (const item of items) {
    if (item.kind === "latin") {
      out.push(item.letter);
    } else {
      for (let k = 0; k < item.syllableCount; k++) out.push(syllables[next++]);
    }
  }
  return out.join(" ");
}

function unparsable(marked: string, syllableCount: number): Error {
  return new Error(`Unparsable pinyin '${marked}' for ${syllableCount} syllable(s)`);
}

function tokenise(s: string): Item[] {
  const items: Item[] = [];
  let base = "";
  let tones: number[] = [];
  const flush = () => {
    if (base) items.push({ kind: "chunk", base, tones, syllableCount: 0 });
    base = "";
    tones = [];
  };
  for (let i = 0; i < s.length; i++) {
    const c = s[i];
    const next = s[i + 1] ?? " ";
    if (c >= "A" && c <= "Z" && !isLowerCaseLetter(next)) {
      flush();
      items.push({ kind: "latin", letter: c });
    } else if (/\s/.test(c) || "'’‘-,·".includes(c)) {
      flush();
    } else if (c === "ü" || c === "Ü") {
      base += "v";
      tones.push(0);
    } else if (VOWEL_TONES.has(c)) {
      const [letter, tone] = VOWEL_TONES.get(c)!;
      base += letter;
      tones.push(tone);
    } else if (/[A-Za-z]/.test(c)) {
      base += c.toLowerCase();
      tones.push(0);
    } else {
      throw new Error(`Unexpected character '${c}' in pinyin '${s}'`);
    }
  }
  flush();
  return items;
}

/** Depth-first, longest syllable first; mirrors PinyinConverter.segment. */
function segment(items: Item[], itemIndex: number, pos: number, remaining: number, out: string[]): boolean {
  while (itemIndex < items.length && items[itemIndex].kind === "latin") itemIndex++;
  if (itemIndex === items.length) return remaining === 0;
  const chunk = items[itemIndex] as Extract<Item, { kind: "chunk" }>;
  if (pos === chunk.base.length) return segment(items, itemIndex + 1, 0, remaining, out);
  if (pos === 0) chunk.syllableCount = 0;
  if (remaining <= 0) return false;

  const maxEnd = Math.min(chunk.base.length, pos + MAX_SYLLABLE_LENGTH);
  for (let end = maxEnd; end > pos; end--) {
    const candidate = chunk.base.slice(pos, end);
    const tone = toneOf(chunk.tones, pos, end);
    if (tone < 0 || !acceptable(candidate, chunk.base.length, pos, end, tone, out.length === 0)) continue;
    out.push(candidate + (tone === 0 ? 5 : tone));
    chunk.syllableCount++;
    if (segment(items, itemIndex, end, remaining - 1, out)) return true;
    out.pop();
    chunk.syllableCount--;
  }
  return false;
}

function acceptable(
  candidate: string,
  chunkLength: number,
  pos: number,
  end: number,
  tone: number,
  first: boolean,
): boolean {
  if (pos > 0 && startsWithAeo(candidate)) return false; // xīnán = xi1 nan2, never xin1 an2
  if (candidate === ERHUA) return !first && tone === 0;
  if (STANDALONE_ONLY.has(candidate)) return pos === 0 && end === chunkLength;
  return SYLLABLES.has(candidate);
}

/** The single tone marked within [from, to), 0 if none, -1 if more than one. */
function toneOf(tones: number[], from: number, to: number): number {
  let tone = 0;
  for (let i = from; i < to; i++) {
    if (tones[i] !== 0) {
      if (tone !== 0) return -1;
      tone = tones[i];
    }
  }
  return tone;
}

// ------------------------------------------------------------------ search helper

/** `"xue2 xi2"` → `"xue xi"`. */
export function stripTones(numbered: string): string {
  return numbered.trim().replace(TONE_DIGIT, "");
}

// ------------------------------------------------------------------ helpers

function normaliseLetters(letters: string): string {
  return letters.replaceAll("u:", "v").replaceAll("U:", "V").replaceAll("ü", "v").replaceAll("Ü", "V");
}

function startsWithAeo(syllable: string): boolean {
  return syllable.length > 0 && "aeo".includes(syllable.charAt(0).toLowerCase());
}

function isSingleLetter(token: Token): boolean {
  return !token.syllable && /^[A-Za-z]$/.test(token.text);
}

function isLowerCaseLetter(c: string): boolean {
  return c !== c.toUpperCase() && c === c.toLowerCase();
}
