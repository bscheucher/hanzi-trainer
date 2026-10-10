# 01 — Architecture

## Repo layout

```
hanzi-trainer/
├── CLAUDE.md
├── docker-compose.yml
├── docs/
│   ├── ROADMAP.md
│   └── specs/
├── data/                      # git-ignored: cedict file, uploaded images
│   ├── cedict/cedict_ts.u8
│   └── uploads/
├── backend/
│   ├── pom.xml
│   └── src/main/java/com/example/hanzi/
│       ├── HanziApplication.java
│       ├── common/            # exception handler, config, JSON utils
│       ├── textbook/          # textbook + lesson
│       ├── page/              # page_image, file storage
│       ├── extraction/        # VocabularyExtractor + Spring AI impl
│       ├── cedict/            # import, lookup, PinyinConverter
│       ├── word/              # word, lesson_word, confirmation
│       ├── exercise/          # generation, grading, ExerciseGenerator + LLM impl
│       └── review/            # review_card, review_log, FSRS
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

## Configuration (`application.yml`)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/hanzi
    username: hanzi
    password: hanzi
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
  storage.upload-dir: ../data/uploads
  cedict.file: ../data/cedict/cedict_ts.u8
  llm:
    provider: anthropic          # anthropic | ollama
    extraction-model: <vision-capable model name>
    exercise-model: <model name>
  exercises.default-session-size: 15
```

`hanzi.*` is bound to a `@ConfigurationProperties` record `HanziProperties`.
Model names are configuration, never hard-coded.

## LLM provider selection

- Exactly one `ChatClient` bean, built from the provider selected by `hanzi.llm.provider`.
- Exclude the auto-configuration of the provider that is not used, or use
  `@ConditionalOnProperty` so the app starts without an Anthropic key when using Ollama.
- If the selected provider is not configured, the app still starts; LLM endpoints return
  `503` with a ProblemDetail explaining which setting is missing.

## docker-compose.yml

PostgreSQL 16, database/user/password `hanzi`, port 5432, named volume.
