# 09 — Spaced repetition (FSRS)

## Algorithm

Use FSRS (Free Spaced Repetition Scheduler) from the open-spaced-repetition project.

- Port the scheduler from the **official reference implementation** (`py-fsrs` or `ts-fsrs`, latest release),
  including its default parameters (`w` weights), `request_retention = 0.9`, `maximum_interval = 36500`,
  learning steps (1 min, 10 min) and relearning step (10 min).
- If a maintained Java port exists, it may be used instead; record the decision in this file.
- Write down the exact upstream version ported in a comment at the top of `FsrsScheduler.java`.
- Do **not** write the formulas from memory; copy them from the upstream source.
- Fuzzing of intervals: enabled (as upstream), but injectable `Random` so tests are deterministic.

```java
public interface Scheduler {
    ScheduledCard review(CardSnapshot card, Rating rating, Instant now);
}
record CardSnapshot(CardState state, Double stability, Double difficulty, Instant due,
                    Instant lastReview, int reps, int lapses) {}
record ScheduledCard(CardSnapshot card, long elapsedDays, long scheduledDays) {}
enum Rating { AGAIN(1), HARD(2), GOOD(3), EASY(4) }
```

Pure Java, no Spring, no DB — `ReviewService` maps entities to/from snapshots.

## Reference tests

Create `src/test/resources/fsrs/reference.json` by running the upstream implementation (a small Python
or Node script committed under `tools/fsrs-reference/`) on these rating sequences, fuzz off, starting
from a new card at `2026-01-01T08:00:00Z`, each review exactly at the previous `due`:

1. `3,3,3,3,3`
2. `1,3,3,4`
3. `3,1,3,3,2,3`
4. `4,4,4`
5. `2,2,2,3`

The Java scheduler must reproduce `state`, `stability`, `difficulty` (±1e-4) and `due` (±1 s) at every step.

## Review flow

1. `GET /api/reviews/due?limit=30` → cards with `due <= now`, ordered: `LEARNING`/`RELEARNING` first (by due),
   then `REVIEW` (by due), then `NEW` (by `word.created_at`, `lesson_no`).
   **New-card limit:** at most 15 new cards introduced per local day (Europe/Vienna; configurable
   `hanzi.review.new-per-day`), counted from `review_log` entries with `state_before = NEW`.
2. For each card the backend builds an exercise on the fly (not stored):
   - `RECOGNITION` → `HANZI_TO_MEANING`, or a stored `FILL_BLANK` containing the word if one exists (50 %)
   - `TONE` → `TONE`
   - `WRITING` → `MEANING_TO_HANZI`
   Sibling cards of the same word are not shown in the same session within 10 minutes of each other.
3. `POST /api/reviews/{cardId}/answer` grades and returns `suggestedRating` (table in spec 07); nothing is saved yet.
4. UI shows the result and four rating buttons with the suggested one pre-selected and the next interval
   shown on each button (`<10m`, `1d`, `3d`, ...). Keyboard: `1`–`4`, `Enter` = accept suggestion.
5. `POST /api/reviews/{cardId}/rate` applies FSRS, updates `review_card`, inserts `review_log`. Idempotency:
   reject a second rating for the same card within 5 s (409).

To show intervals on buttons, `GET /api/reviews/due` items include
`previewIntervals: { "1": "PT10M", "2": "P1D", "3": "P3D", "4": "P8D" }` (ISO-8601 durations).

## Dashboard (`/review`)

- Due now, due today, reviewed today, new cards left today.
- Count per card state, per card type.
- 30-day forecast bar chart of due cards (`GET /api/reviews/forecast?days=30` → `{date, count}[]`).
- Button **Start review**.

## Out of scope for v1
Parameter optimisation from the user's own review log (possible later with upstream optimizer on exported `review_log`).
