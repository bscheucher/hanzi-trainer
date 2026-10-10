# 05 — Scan & vocabulary extraction

## Upload flow

1. `POST /api/lessons/{id}/pages` (multipart).
2. Validate: content type `image/jpeg`, `image/png` or `image/webp`; ≤ 20 MB. Else 415/413.
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
5. Timeout: 120 s per page.

On startup, pages stuck in `RUNNING` are reset to `FAILED` ("interrupted by restart").

## Interface

```java
public interface VocabularyExtractor {
    ExtractionResult extract(byte[] jpegImage);
    String modelName();
}
```

Implementation `SpringAiVocabularyExtractor` uses `ChatClient` with the image as `Media`
(`MimeTypeUtils.IMAGE_JPEG`) and `.call().entity(ExtractionResult.class)`.

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
- Output must match the JSON schema exactly (Spring AI appends the schema).

## Validation after extraction

Drop (and add a warning for) items where:
- `hanzi` is empty or contains no CJK characters (U+4E00–U+9FFF, U+3400–U+4DBF).
- `hanzi` contains digits or characters other than CJK and Latin letters. (Mixed words such as
  `T恤` or `卡拉OK` are kept.)

Deduplicate items within one page on `(hanzi, toNumbered(pinyin))`.

## Tests

- `FakeVocabularyExtractor` returning a fixed `ExtractionResult` for service/integration tests.
- One opt-in test (`@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", ...)`, tagged `llm`,
  excluded from `./gradlew build` via `excludeTags 'llm'`) that sends `src/test/resources/sample-page.jpg` to the real model.
