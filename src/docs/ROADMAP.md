# Roadmap

Each milestone is a self-contained unit for one Claude Code session. Do them in order.

---

## M0 — Project skeleton

**Build**
- Repo layout per `specs/01-architecture.md`: `backend/`, `frontend/`, `docker-compose.yml`, `docs/`.
- Spring Boot app with Web, Data JPA, Flyway, PostgreSQL driver, Actuator, Validation.
- `V1__init_schema.sql` (provided) in `backend/src/main/resources/db/migration/`.
- JPA entities for all tables in `specs/02-data-model.md` (no services yet).
- Next.js app with Tailwind, a layout with nav: *Lessons · Vocabulary · Review*.
- Global `ProblemDetail` exception handler.

**Done when**
- `docker compose up -d && ./mvnw spring-boot:run` starts, Flyway applies V1, `ddl-auto=validate` passes.
- `GET /actuator/health` → `UP`.
- A Testcontainers test boots the context against PostgreSQL.
- `npm run dev` shows the layout with three empty pages.

---

## M1 — CC-CEDICT import & pinyin utilities

Spec: `specs/04-cedict-and-pinyin.md`

**Done when**
- `PinyinConverter` converts numbered → tone marks for all cases in the spec's test table.
- Import of the real CC-CEDICT file completes (~120k rows) and is idempotent (second run = no change).
- `GET /api/dictionary?q=学习` returns the CEDICT entries.

---

## M2 — Lessons & page upload with extraction

Spec: `specs/05-scan-and-extraction.md`

**Done when**
- Textbooks and lessons can be created/listed in UI and API.
- Uploading a JPEG/PNG/HEIC-converted image to a lesson stores the file and creates a `page_image` with `PENDING`.
- Extraction runs asynchronously; status goes `PENDING → RUNNING → DONE|FAILED`.
- `raw_extraction` contains JSON matching the extraction schema.
- Extraction is behind `VocabularyExtractor` interface; tests use a fake.
- UI: upload (file picker + mobile camera), shows per-page status with polling.

---

## M3 — Vocabulary review & confirmation

Spec: `specs/06-vocabulary-review.md`

**Done when**
- Review screen shows extracted items, each with CEDICT match status (match / pinyin mismatch / not found).
- User can edit, delete, add items, and confirm.
- Confirm creates/reuses `word` rows (dedupe on `hanzi + pinyin`), creates `lesson_word` and
  `review_card` rows (RECOGNITION, WRITING, TONE) for new words.
- Confirming twice does not create duplicates.

---

## M4 — Vocabulary browser

**Done when**
- `/vocabulary` lists all words with filter by lesson and text search (hanzi, pinyin with or without tones, meaning).
- Word detail shows lessons it appears in, example sentences, and an animated stroke order (Hanzi Writer, per character).

---

## M5 — Code-generated exercises & exercise player

Spec: `specs/07-exercises.md`

**Done when**
- `HANZI_TO_MEANING` and `TONE` exercises can be generated for a lesson without any LLM.
- Exercise player runs a session of N exercises, grades deterministically, shows correct answer + feedback.

---

## M6 — Handwriting exercises

Spec: `specs/08-handwriting.md`

**Done when**
- `MEANING_TO_HANZI` exercise: user writes each character in a Hanzi Writer quiz; mistakes per character are recorded.
- Works with mouse, touch and pen; canvas is responsive.

---

## M7 — LLM-generated exercises & free handwriting input

Specs: `specs/07-exercises.md`, `specs/08-handwriting.md`

**Done when**
- `FILL_BLANK`, `WORD_ORDER`, `TRANSLATION` are generated via `ExerciseGenerator` interface.
- Generated sentences pass the allowed-character validation (rejected/retried otherwise, max 2 retries).
- `TRANSLATION` answers are graded by LLM with feedback.
- Free-input component: HanziLookup candidate picker + normal keyboard/IME input.

---

## M8 — Spaced repetition

Spec: `specs/09-spaced-repetition.md`

**Done when**
- `/review` shows due count and runs a session across all lessons.
- Each answer produces a suggested rating the user can override; FSRS updates the card and writes `review_log`.
- FSRS implementation passes the reference test cases in the spec.
- Dashboard: due today, reviewed today, words per state.
