# 01 — Architecture

## Repo layout

Two repositories, checked out side by side: this one (backend + all specs) and `hanzi-trainer-frontend`.

```
hanzi-trainer/
├── CLAUDE.md
├── docker-compose.yml
├── build.gradle, settings.gradle, gradlew   # backend = Gradle project at the repo root
├── data/                      # git-ignored: cedict file, uploaded images
│   ├── cedict/cedict_ts.u8
│   └── uploads/
├── src/
│   ├── docs/
│   │   ├── ROADMAP.md
│   │   └── specs/
│   ├── main/java/com/learning/hanzitrainer/
│   │   ├── HanziTrainerApplication.java
│   │   ├── common/            # exception handler, config, JSON utils
│   │   ├── textbook/          # textbook + lesson
│   │   ├── page/              # page_image, file storage
│   │   ├── extraction/        # VocabularyExtractor + Spring AI impl
│   │   ├── cedict/            # import, lookup, PinyinConverter
│   │   ├── word/              # word, lesson_word, confirmation
│   │   ├── exercise/          # generation, grading, ExerciseGenerator + LLM impl
│   │   └── review/            # review_card, review_log, FSRS
│   ├── main/resources/        # application.yaml, db/migration/, prompts/
│   └── test/

hanzi-trainer-frontend/        # separate repository (github.com/bscheucher/hanzi-trainer-frontend),
│                              # checked out next to this one; specs stay here
├── CLAUDE.md, README.md
└── src/
        ├── app/
        │   ├── lessons/               # list, detail, upload, review extraction
        │   ├── vocabulary/            # browser, word detail
        │   ├── practice/[lessonId]/   # exercise player
        │   └── review/                # SRS session + dashboard
        ├── components/
        │   ├── handwriting/           # HanziQuiz, HanziPad
        │   └── exercises/             # one component per exercise type
        └── lib/ (api.ts, types.ts, pinyin.ts)
```

## Runtime

```
Browser (Next.js) ──REST/JSON──▶ Spring Boot ──JDBC──▶ PostgreSQL
                                     │
                                     ├──▶ Spring AI ChatClient ──▶ Anthropic (default) | Ollama
                                     └──▶ local filesystem (data/uploads)
```

- CORS: allow `http://localhost:3000` on `/api/**`.
- Long-running work (extraction, LLM exercise generation) runs on a virtual-thread executor
  (`spring.threads.virtual.enabled=true` + `@Async`). Client polls status.

## Configuration (`application.yaml`)

Secrets (DB password, later the Anthropic API key) live in a git-ignored `.env` file in the repo root.
Docker Compose reads it automatically; Spring Boot imports it via `spring.config.import`.
`.env.example` lists the keys.

```yaml
spring:
  config.import: optional:file:.env[.properties]
  datasource:
    url: jdbc:postgresql://localhost:5433/hanzi
    username: hanzi
    password: ${HANZI_DB_PASSWORD}
  jpa:
    hibernate.ddl-auto: validate
    open-in-view: false
  servlet.multipart:
    max-file-size: 20MB
    max-request-size: 25MB
  threads.virtual.enabled: true
  jpa.properties.hibernate.type.json_format_mapper: jackson3   # Spring AI adds Jackson 2; entities use Jackson 3
  ai:
    model.chat: ${hanzi.llm.provider}   # Spring AI creates only the selected provider's ChatModel
    anthropic:
      api-key: ${ANTHROPIC_API_KEY:}
    ollama:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}

hanzi:
  storage.upload-dir: ./data/uploads
  cedict:
    file: ./data/cedict/cedict_ts.u8
    import-on-startup: true      # import when cedict_entry is empty and the file exists
  admin.enabled: true            # /api/admin/** endpoints
  llm:
    provider: ${HANZI_LLM_PROVIDER:anthropic}                 # anthropic | ollama
    extraction-model: ${HANZI_EXTRACTION_MODEL:claude-opus-5-5}  # Ollama: a vision model, e.g. gemma3:4b
    exercise-model: ${HANZI_EXERCISE_MODEL:claude-sonnet-5-5}  # sentence exercises + translation grading
    exercise-timeout: 300s   # HTTP timeout of one exercise call; extraction calls use extraction.timeout
  extraction:
    max-image-px: 2576   # Claude's high-resolution limit (long edge); the client resizes to the same value
    timeout: 120s        # per model call; local models on modest hardware may need more on a cold start
    # max-concurrent: 1  # model calls at once; default 1 for ollama, 2 for anthropic
  exercises.default-session-size: 15
  review:
    new-per-day: 15
    time-zone: Europe/Vienna
```

`hanzi.*` is bound to a `@ConfigurationProperties` record `HanziProperties`.
Model names are configuration, never hard-coded.

## LLM provider selection

- Spring AI 2.0.x (its Anthropic module uses the official `anthropic-java` SDK). `spring.ai.model.chat` follows
  `hanzi.llm.provider`, so only the selected provider's `ChatModel` exists.
- One `ChatClient` per purpose, built in `LlmConfig` from that `ChatModel` with the model name and `maxTokens`
  as default options: extraction now, exercises in M7. Current Claude models always think, so `maxTokens` is
  generous (16k), and no `temperature` is set (Claude Opus 5.5 rejects sampling parameters).
- Timeouts per purpose: extraction calls use `hanzi.extraction.timeout` (120 s), the timeout configured on the
  shared clients (`spring.ai.anthropic.timeout`, `spring.http.clients.read-timeout`); exercise calls (generation,
  grading) use `hanzi.llm.exercise-timeout` (300 s), since a reply with thinking can take minutes. Anthropic takes
  the timeout per call (`AnthropicChatOptions.timeout` on the exercise `ChatClient`); Ollama's timeout belongs to the
  HTTP client, so exercises get an `OllamaChatModel` of their own.
- Retries: Spring AI 2.0's Anthropic model does not use `spring.ai.retry`; the Anthropic SDK retries on its own,
  including read timeouts and interrupted calls. `spring.ai.anthropic.max-retries: 1` keeps one retry for 429/529
  while limiting paid duplicates. `spring.ai.retry.max-attempts: 2` applies to Ollama only.
- Structured replies use native structured output via `common/StructuredReply`, not `responseEntity`: a thinking
  model returns the thinking block as the first generation (empty text), and `responseEntity` only parses the first.
- If the selected provider is not configured (e.g. no `ANTHROPIC_API_KEY`), the app still starts; LLM endpoints
  return `503` with a ProblemDetail explaining which setting is missing.

## Spring Boot 4 notes (verify when adding dependencies in M0)

- Auto-configuration is modular in Boot 4: Flyway needs `spring-boot-starter-flyway` plus
  `org.flywaydb:flyway-database-postgresql`.
- Boot 4 uses Jackson 3 (`tools.jackson.*`). Hibernate picks Jackson 2 for JSON columns whenever it is on the
  classpath (Spring AI brings it), so `hibernate.type.json_format_mapper: jackson3` is set explicitly.
- Use the Spring AI release line that supports the pinned Boot version.

## docker-compose.yml

PostgreSQL 16, database/user `hanzi`, password from `HANZI_DB_PASSWORD` in `.env`, host port 5433 → container 5432, named volume.
