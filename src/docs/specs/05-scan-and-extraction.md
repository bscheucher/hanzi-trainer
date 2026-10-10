# 05 — Scan & vocabulary extraction

## Upload flow

1. `POST /api/lessons/{id}/pages` (multipart).
2. Validate: JPEG or PNG, recognised by magic bytes (not the declared content type); ≤ 20 MB. Else 415/413.
   No WebP: JDK ImageIO cannot decode it and the client always sends JPEG anyway.
   Client side, before upload: draw the image onto a canvas (current browsers apply EXIF orientation
   when decoding), downscale the long side to `max-image-px`, and export JPEG quality 0.9. This also converts
   HEIC on Safari; if the browser cannot decode the file, show "please upload JPEG/PNG".
3. Store original under `data/uploads/lesson-{lessonId}/{uuid}.{ext}`.
4. Insert `page_image` with `PENDING`, return 202 with `PageDto`.
5. After commit, publish `PageUploadedEvent`; an `@Async @TransactionalEventListener` runs extraction.

## Extraction job

```
PENDING → RUNNING → DONE
                  ↘ FAILED (error_message set)
```

1. Load image; if the long side > `hanzi.extraction.max-image-px`, downscale (safety net for uploads that
   skipped client-side resizing); re-encode as JPEG quality 90. Use `javax.imageio` / `java.awt`
   (no extra deps). EXIF orientation is not handled server-side (known limitation; the client fixes it).
2. Call `VocabularyExtractor.extract(byte[] jpeg) : ExtractionResult`.
3. Validate result (see below). Store as JSON in `raw_extraction`, set `extraction_model`, `DONE`.
4. On any exception: `FAILED`, short message in `error_message`, full stack trace in the log.
5. Timeout: 120 s per model call. At most `hanzi.extraction.max-concurrent` calls run at once (default 1 for
   Ollama, 2 for Anthropic); a page waits as `PENDING` for a free slot, and waiting does not count towards its
   timeout. On timeout the call is interrupted; the HTTP clients' own timeouts (`spring.ai.anthropic.timeout`,
   `spring.http.clients.read-timeout`) are the backstop, and Spring AI retries are limited to 2 attempts.

On startup (before the web server accepts uploads), pages left `PENDING` or `RUNNING` are set to `FAILED`
("Interrupted by restart"): jobs live only in memory, so nothing would ever finish them.
Re-running extraction (`POST /pages/{id}/extract`) atomically moves a `DONE` or `FAILED` page back to `PENDING`;
on a `PENDING` page (job already queued) it does nothing, on `RUNNING` it is a 409. The previous `raw_extraction`
is kept until the new run succeeds.

## Interface

```java
public interface VocabularyExtractor {
    ExtractionResult extract(byte[] jpegImage);
    String modelName();
    default Optional<String> unavailableReason() { return Optional.empty(); } // non-empty → endpoints answer 503
}
```

Implementation `SpringAiVocabularyExtractor` uses `ChatClient` with the image as `Media`
(`MimeTypeUtils.IMAGE_JPEG`) and `.call().entity(ExtractionResult.class)` with
`AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT`: the JSON schema goes to the provider (Anthropic structured outputs,
Ollama `format`) instead of being appended to the prompt. With the schema in the prompt, `gemma3:4b` ignored the
image and invented a page. `UnavailableVocabularyExtractor` stands in when the provider is not configured.

## ExtractionResult (also the JSON stored in `raw_extraction`)

```java
public record ExtractionResult(
    String pageType,               // "VOCABULARY_LIST" | "TEXT" | "DIALOGUE" | "GRAMMAR" | "EXERCISES" | "OTHER"
    List<ExtractedWord> words,
    List<ExtractedSentence> sentences,
    List<String> grammarPoints,    // short English descriptions, may be empty
    List<String> warnings          // e.g. "bottom of page cut off", "handwriting on page"
) {}

public record ExtractedWord(
    String hanzi,                  // simplified only
    String pinyin,                 // tone marks, as printed in the book if present
    boolean pinyinFromPage,        // true if pinyin was printed, false if model supplied it
    String meaningEn,
    String meaningDe,              // may be null
    String partOfSpeech,           // book's abbreviation if printed (n., v., adj., m., ...) else model guess
    String exampleSentence,        // a sentence from this page that uses the word, or null
    double confidence              // 0..1, model's own estimate of reading the characters correctly
) {}

public record ExtractedSentence(String hanzi, String pinyin, String translationEn) {}
```

## Prompt (system message, keep in `src/main/resources/prompts/extract-vocabulary.st`)

Requirements the prompt must state:
- The image is a page from a Mandarin textbook for learners using **simplified** characters.
- Extract every vocabulary item: explicit vocabulary lists first; on text/dialogue pages, also every
  word a beginner would need to learn — but only words actually printed on the page.
- Copy characters exactly as printed. Never convert to traditional. Never invent words.
- Use the pinyin printed on the page when available (`pinyinFromPage=true`); otherwise supply
  standard Mandarin pinyin with tone marks, using the reading that fits the context.
- Pinyin with tone marks, syllables of one word written together (`péngyou`), neutral tone unmarked.
- Meanings short (≤ 6 words each), English always, German when confident.
- Proper nouns (names, places) are included with partOfSpeech `"name"`.
- Measure words get partOfSpeech `"m"`.
- Report problems in `warnings` instead of guessing silently; lower `confidence` for unclear characters.
- (The JSON schema is enforced by native structured output; the prompt does not repeat it.)

## Validation after extraction

Drop (and add a warning for) items where:
- `hanzi` is empty or contains no CJK characters (U+4E00–U+9FFF, U+3400–U+4DBF).
- `hanzi` contains digits or characters other than CJK (incl. `〇`), Latin letters and the name separator `·`.
  (Mixed words such as `T恤`, `卡拉OK` and transliterated names such as `马克·吐温` are kept.)

Deduplicate items within one page on `(hanzi, toNumbered(pinyin))`.

## Tests

- `FakeVocabularyExtractor` returning a fixed `ExtractionResult` for service/integration tests.
- One opt-in test (`RealModelExtractionTests`, tagged `llm`, enabled by `HANZI_LLM_TEST=true`) that sends
  `src/test/resources/sample-page.jpg` (a rendered lesson-1 vocabulary page) to the **configured** provider and model.
  Run with `HANZI_LLM_TEST=true ./gradlew llmTest`; `./gradlew build` excludes the tag. Verified with Ollama
  `gemma3:4b`; with Anthropic it costs money.
