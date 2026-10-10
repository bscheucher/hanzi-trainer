# 01 — Architecture

## Repo layout

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
└── frontend/
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
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY:}
    ollama:
      base-url: http://localhost:11434

hanzi:
  storage.upload-dir: ./data/uploads
  cedict.file: ./data/cedict/cedict_ts.u8
  llm:
    provider: anthropic          # anthropic | ollama
    extraction-model: <vision-capable model name>
    exercise-model: <model name>
  extraction.max-image-px: 1568  # long side; check the provider's image limits for the configured model
  exercises.default-session-size: 15
  review:
    new-per-day: 15
    time-zone: Europe/Vienna
```

`hanzi.*` is bound to a `@ConfigurationProperties` record `HanziProperties`.
Model names are configuration, never hard-coded.

## LLM provider selection

- Two `ChatClient` beans, `extractionChatClient` and `exerciseChatClient`, both built from the provider
  selected by `hanzi.llm.provider`, with the model set via default chat options from
  `hanzi.llm.extraction-model` / `hanzi.llm.exercise-model`.
- Exclude the auto-configuration of the provider that is not used, or use
  `@ConditionalOnProperty` so the app starts without an Anthropic key when using Ollama.
- If the selected provider is not configured, the app still starts; LLM endpoints return
  `503` with a ProblemDetail explaining which setting is missing.

## Spring Boot 4 notes (verify when adding dependencies in M0)

- Auto-configuration is modular in Boot 4: Flyway needs `spring-boot-starter-flyway` plus
  `org.flywaydb:flyway-database-postgresql`.
- Boot 4 uses Jackson 3 (`tools.jackson.*`); check which `JsonNode` Hibernate's JSON mapping expects.
- Use the Spring AI release line that supports the pinned Boot version.

## docker-compose.yml

PostgreSQL 16, database/user `hanzi`, password from `HANZI_DB_PASSWORD` in `.env`, host port 5433 → container 5432, named volume.
