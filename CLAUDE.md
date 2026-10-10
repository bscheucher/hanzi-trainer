# Hanzi Trainer

Personal web app for learning Mandarin Chinese (simplified characters) from a printed textbook.
The user scans textbook pages, an LLM extracts the vocabulary, the user reviews it, and the app
generates exercises (including handwriting practice) scheduled with spaced repetition (FSRS).

Single user, runs locally. No authentication in v1.

## Where things are specified

Read the relevant spec before implementing anything. Specs are the source of truth; if code and spec
disagree, ask before changing the spec.

| File | Content |
|---|---|
| `src/docs/ROADMAP.md` | Milestones M0–M8 with acceptance criteria. Work one milestone at a time. |
| `src/docs/specs/01-architecture.md` | Stack, repo layout, modules, configuration |
| `src/docs/specs/02-data-model.md` | Tables, entities, invariants |
| `src/docs/specs/03-api.md` | REST contract (endpoints + JSON shapes) |
| `src/docs/specs/04-cedict-and-pinyin.md` | CC-CEDICT import, pinyin conversion |
| `src/docs/specs/05-scan-and-extraction.md` | Page upload, vision-LLM extraction |
| `src/docs/specs/06-vocabulary-review.md` | Review screen, confirming words, deduplication |
| `src/docs/specs/07-exercises.md` | Exercise types, generation, grading |
| `src/docs/specs/08-handwriting.md` | Hanzi Writer + HanziLookup integration |
| `src/docs/specs/09-spaced-repetition.md` | FSRS cards, review sessions |

## Stack

- **Backend:** Java 21, Spring Boot 4.x, Gradle, Spring Web MVC, Spring Data JPA, Flyway, PostgreSQL,
  Spring AI (Anthropic + Ollama; use a release compatible with the pinned Boot version), JUnit 5, Testcontainers
- **Frontend:** Next.js (App Router) + React + TypeScript, Tailwind CSS, Vitest + React Testing Library,
  `hanzi-writer`, HanziLookupJS
- **Infra:** `docker-compose.yml` with PostgreSQL 16 on host port 5433 (5432 is often taken by a native install)

The backend is the Gradle project at the repo root; the frontend lives in `frontend/`.

## Commands

```bash
cp .env.example .env                 # once; set HANZI_DB_PASSWORD (never commit .env)
docker compose up -d                 # PostgreSQL on localhost:5433
./gradlew bootRun                    # API on http://localhost:8080
./gradlew build                      # compile + tests (needs Docker for Testcontainers)
cd frontend && npm run dev           # UI on http://localhost:3000
cd frontend && npm test              # Vitest
```

## Conventions

### Backend
- Base package `com.learning.hanzitrainer`, organised **by feature**, not by layer:
  `textbook` (textbook + lesson), `page`, `extraction`, `word`, `cedict`, `exercise`, `review`, `common`.
- Each feature package contains its entity, repository, service, controller and DTOs.
- DTOs are Java `record`s. Never expose JPA entities from controllers.
- Schema changes only via new Flyway migrations (`V2__...sql`); never edit an applied migration.
- `spring.jpa.hibernate.ddl-auto=validate`.
- Constructor injection only. No Lombok.
- Errors: throw domain exceptions, map them in one `@RestControllerAdvice` to RFC 7807 `ProblemDetail`.
- All LLM calls go through interfaces in the `extraction` / `exercise` packages so they can be mocked
  in tests. Tests never call a real LLM.
- Every service gets unit tests; every controller gets a `@WebMvcTest` or a Testcontainers integration test.

### Frontend
- App Router, server components by default; `"use client"` only where interactivity is needed
  (canvas, exercise player, upload).
- API calls through a single typed client in `frontend/src/lib/api.ts`; types in `frontend/src/lib/types.ts`
  mirror the DTOs in `src/docs/specs/03-api.md`.
- Backend base URL from `NEXT_PUBLIC_API_URL` (default `http://localhost:8080`).
- UI language: English labels; German meanings shown when available.
- Chinese text always rendered with `lang="zh-CN"` and a CJK font stack
  (`"Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif`).

### Chinese-text rules (apply everywhere)
- Simplified characters only.
- Pinyin is stored twice: with tone marks (`xuéxí`) for display and numbered (`xue2 xi2`) for search
  and tone exercises. Neutral tone = `5`. `ü` is written `ü` in display form and `v` in numbered form.
- Pinyin uses dictionary (citation) tones as in CC-CEDICT. Tone sandhi of 一 / 不 printed in textbooks
  (`yí ge`, `bú shì`) is normalised to the dictionary reading (`yi1`, `bu4`).
- A word is identified by `(hanzi, pinyin_numbered)`, not by `hanzi` alone (polyphones).

## Working agreement for Claude Code
- Implement one milestone per session. Start by restating the milestone's acceptance criteria.
- Before writing code for a milestone, list the files you will create/change.
- Finish a milestone only when `./gradlew build` and `npm test` pass and the acceptance criteria are met.
- If a spec is ambiguous, ask instead of guessing.
