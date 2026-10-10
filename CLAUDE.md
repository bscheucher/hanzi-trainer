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
| `src/docs` | Milestones M0–M8 with acceptance criteria. Work one milestone at a time. |
| `src/docs` | Stack, repo layout, modules, configuration |
| `src/docs` | Tables, entities, invariants |
| `src/docs` | REST contract (endpoints + JSON shapes) |
| `src/docs` | CC-CEDICT import, pinyin conversion |
| `src/docs` | Page upload, vision-LLM extraction |
| `src/docs` | Review screen, confirming words, deduplication |
| `src/docs` | Exercise types, generation, grading |
| `src/docs` | Hanzi Writer + HanziLookup integration |
| `src/docs` | FSRS cards, review sessions |

## Stack

- **Backend:** Java 21, Spring Boot (current stable 3.x/4.x — check before pinning), Maven, Spring Web,
  Spring Data JPA, Flyway, PostgreSQL, Spring AI (Anthropic + Ollama), JUnit 5, Testcontainers
- **Frontend:** Next.js (App Router) + React + TypeScript, Tailwind CSS, Vitest + React Testing Library,
  `hanzi-writer`, HanziLookupJS
- **Infra:** `docker-compose.yml` with PostgreSQL 16

## Commands

```bash
docker compose up -d                 # PostgreSQL on localhost:5432
cd backend && ./mvnw spring-boot:run # API on http://localhost:8080
cd backend && ./mvnw verify          # tests (needs Docker for Testcontainers)
cd frontend && npm run dev           # UI on http://localhost:3000
cd frontend && npm test              # Vitest
```

## Conventions

### Backend
- Base package `com.example.hanzi`, organised **by feature**, not by layer:
  `lesson`, `page`, `extraction`, `word`, `cedict`, `exercise`, `review`, `common`.
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
  mirror the DTOs in `src/docs`.
- Backend base URL from `NEXT_PUBLIC_API_URL` (default `http://localhost:8080`).
- UI language: English labels; German meanings shown when available.
- Chinese text always rendered with `lang="zh-CN"` and a CJK font stack
  (`"Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif`).

### Chinese-text rules (apply everywhere)
- Simplified characters only.
- Pinyin is stored twice: with tone marks (`xuéxí`) for display and numbered (`xue2 xi2`) for search
  and tone exercises. Neutral tone = `5`. `ü` is written `ü` in display form and `v` in numbered form.
- A word is identified by `(hanzi, pinyin)`, not by `hanzi` alone (polyphones).

## Working agreement for Claude Code
- Implement one milestone per session. Start by restating the milestone's acceptance criteria.
- Before writing code for a milestone, list the files you will create/change.
- Finish a milestone only when `./mvnw verify` and `npm test` pass and the acceptance criteria are met.
- If a spec is ambiguous, ask instead of guessing.
