# 08 — Handwriting input

Two client-side components, both in `frontend/src/components/handwriting/`. No server-side recognition.

## Libraries

| Library | Use | Notes |
|---|---|---|
| `hanzi-writer` (npm) | Stroke-order animation and quiz for a **known** target character | Character data (Make Me a Hanzi) loaded per character from CDN by default; configure `charDataLoader` to load from `hanzi-writer-data` on jsDelivr, or self-host later |
| HanziLookupJS (github.com/gugray/HanziLookupJS) | Recognise a **free-drawn** character, return ranked candidates | Not on npm: vendor the JS + `mmah.json` data into `frontend/public/hanzilookup/`, load lazily on first use; check and respect its license |

Before implementing, verify the current API of both libraries from their README; the snippets below are intent, not exact signatures.

## `<HanziQuiz>` — writing a known word

Props: `{ hanzi: string; onComplete(result: { strokeMistakes: number[]; hintsUsed: number; durationMs: number }) }`

- Renders one Hanzi Writer quiz per character, one after another (current char large, finished chars small).
- Grid background (田字格: square with diagonal + cross guides) drawn in SVG behind the canvas.
- Shows outline only after the user clicks **Hint** (counts as hint) or after 3 mistakes on one stroke
  (Hanzi Writer's `showHintAfterMisses: 3`).
- Records `totalMistakes` per character from the quiz's completion callback.
- After the last character: animate the full word once, then call `onComplete`.
- Buttons: Hint · Show stroke order (animates current char, counts as hint) · Skip (counts as wrong).
- Size: square, `min(90vw, 320px)`; works with mouse, touch and pen (pointer events; `touch-action: none` on the canvas).
- If a character has no data (load error), show it with a message "no stroke data for X" and let the user
  self-assess (correct/wrong buttons).

Also export `<StrokeOrder hanzi>` (animation only) for the vocabulary detail page.

## `<HanziPad>` — free input

Props: `{ value: string; onChange(v: string) }`

- A text field (normal keyboard/IME input always works) plus a drawing pad underneath.
- User draws one character; after each stroke (debounced 300 ms) run HanziLookup and show the top 8
  candidates as large buttons. Tapping a candidate appends it to the text and clears the pad.
- Buttons: Undo stroke · Clear · Backspace.
- Same grid background and pointer handling as `<HanziQuiz>`.
- Strokes are captured as arrays of points; convert to the format HanziLookup expects.

Used by: `TRANSLATION` exercises, and the "add word" row on the review screen.

## Tests

- Vitest + RTL: `HanziQuiz` with a mocked `hanzi-writer` module → `onComplete` receives mistakes per character.
- `HanziPad`: candidate click appends to value; Backspace removes the last character.
- Manual checklist in the PR description: mouse, touch (DevTools device mode), pen if available.
