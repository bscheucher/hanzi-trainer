package com.learning.hanzitrainer.page;

import com.learning.hanzitrainer.TestImages;
import com.learning.hanzitrainer.TestcontainersConfiguration;
import com.learning.hanzitrainer.common.ConflictException;
import com.learning.hanzitrainer.common.UnsupportedMediaTypeException;
import com.learning.hanzitrainer.extraction.ExtractionResult;
import com.learning.hanzitrainer.extraction.ExtractionResult.ExtractedWord;
import com.learning.hanzitrainer.extraction.FakeExtractorConfiguration;
import com.learning.hanzitrainer.extraction.FakeVocabularyExtractor;
import com.learning.hanzitrainer.textbook.CreateLessonRequest;
import com.learning.hanzitrainer.textbook.CreateTextbookRequest;
import com.learning.hanzitrainer.textbook.TextbookService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/** Upload → async extraction → stored result, against PostgreSQL with a fake extractor. */
@SpringBootTest(properties = {"hanzi.extraction.timeout=2s", "hanzi.extraction.max-concurrent=1"})
@Import({TestcontainersConfiguration.class, FakeExtractorConfiguration.class})
class PageExtractionTests {

    @TempDir
    static Path uploads;

    @DynamicPropertySource
    static void uploadDir(DynamicPropertyRegistry registry) {
        registry.add("hanzi.storage.upload-dir", uploads::toString);
    }

    @Autowired
    private PageService pageService;
    @Autowired
    private TextbookService textbookService;
    @Autowired
    private FakeVocabularyExtractor extractor;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JsonMapper jsonMapper;

    private long lessonId;

    @BeforeEach
    void createLesson() {
        long textbookId = textbookService.createTextbook(new CreateTextbookRequest("HSK 1", null)).id();
        lessonId = textbookService.createLesson(textbookId, new CreateLessonRequest(1, "你好")).id();
    }

    @AfterEach
    void cleanUp() {
        extractor.reset();
        jdbc.update("DELETE FROM textbook");
    }

    @Test
    void uploadStoresFileAndExtractsAsynchronously() throws Exception {
        PageDto uploaded = pageService.upload(lessonId, TestImages.jpeg(3000, 4000), 12);

        assertThat(uploaded.status()).isIn(ExtractionStatus.PENDING, ExtractionStatus.RUNNING);
        assertThat(uploaded.pageNo()).isEqualTo(12);
        String storagePath = jdbc.queryForObject("SELECT storage_path FROM page_image WHERE id = ?", String.class,
                uploaded.id());
        assertThat(storagePath).startsWith("lesson-" + lessonId + "/").endsWith(".jpg");
        assertThat(uploads.resolve(storagePath)).exists();

        PageDto done = awaitStatus(uploaded.id(), ExtractionStatus.DONE);

        assertThat(done.extraction().words()).extracting(ExtractedWord::hanzi).containsExactly("朋友", "学习");
        assertThat(done.errorMessage()).isNull();
        assertThat(done.confirmed()).isFalse();
        assertThat(jdbc.queryForObject("SELECT extraction_model FROM page_image WHERE id = ?", String.class,
                uploaded.id())).isEqualTo("fake-model");
        // The model received a downscaled JPEG, not the original upload.
        assertThat(extractor.lastImage()).startsWith((byte) 0xFF, (byte) 0xD8);
        assertThat(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(extractor.lastImage())).getHeight())
                .isEqualTo(2576);
    }

    @Test
    void rawExtractionMatchesTheSchema() {
        long pageId = pageService.upload(lessonId, TestImages.png(200, 300), null).id();
        awaitStatus(pageId, ExtractionStatus.DONE);

        JsonNode raw = jsonMapper.readTree(jdbc.queryForObject(
                "SELECT raw_extraction::text FROM page_image WHERE id = ?", String.class, pageId));

        assertThat(raw.propertyNames()).containsExactlyInAnyOrder(
                "pageType", "words", "sentences", "grammarPoints", "warnings");
        assertThat(raw.get("words").get(0).propertyNames()).containsExactlyInAnyOrder("hanzi", "pinyin",
                "pinyinFromPage", "meaningEn", "meaningDe", "partOfSpeech", "exampleSentence", "confidence");
        assertThat(jsonMapper.treeToValue(raw, ExtractionResult.class)).isEqualTo(FakeVocabularyExtractor.SAMPLE);
    }

    @Test
    void storedResultIsValidated() {
        extractor.willReturn(new ExtractionResult("TEXT", List.of(
                new ExtractedWord("hello", "hello", false, "hello", null, null, null, 0.5),
                new ExtractedWord("你好", "nǐhǎo", true, "hello", null, null, null, 0.9)), null, null, null));

        PageDto done = awaitStatus(pageService.upload(lessonId, TestImages.png(50, 50), null).id(),
                ExtractionStatus.DONE);

        assertThat(done.extraction().words()).extracting(ExtractedWord::hanzi).containsExactly("你好");
        assertThat(done.extraction().warnings()).singleElement().asString().contains("hello");
    }

    @Test
    void failureIsRecordedWithShortMessage() {
        extractor.willAnswer(image -> {
            throw new IllegalStateException("model overloaded");
        });

        PageDto failed = awaitStatus(pageService.upload(lessonId, TestImages.png(50, 50), null).id(),
                ExtractionStatus.FAILED);

        assertThat(failed.errorMessage()).isEqualTo("IllegalStateException: model overloaded");
        assertThat(failed.extraction()).isNull();
    }

    @Test
    void slowModelTimesOut() {
        extractor.willAnswer(image -> {
            sleep(Duration.ofSeconds(10));
            return FakeVocabularyExtractor.SAMPLE;
        });

        PageDto failed = awaitStatus(pageService.upload(lessonId, TestImages.png(50, 50), null).id(),
                ExtractionStatus.FAILED);

        assertThat(failed.errorMessage()).isEqualTo("Extraction timed out after 2 s");
    }

    @Test
    void timedOutCallIsInterrupted() {
        AtomicBoolean interrupted = new AtomicBoolean();
        extractor.willAnswer(image -> {
            try {
                Thread.sleep(Duration.ofSeconds(10));
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
            return FakeVocabularyExtractor.SAMPLE;
        });

        awaitStatus(pageService.upload(lessonId, TestImages.png(50, 50), null).id(), ExtractionStatus.FAILED);

        await().atMost(Duration.ofSeconds(2)).untilTrue(interrupted);
    }

    @Test
    void modelCallsAreThrottledAndQueueTimeDoesNotCountTowardsTimeout() {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        extractor.willAnswer(image -> {
            maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
            sleep(Duration.ofMillis(800));
            running.decrementAndGet();
            return FakeVocabularyExtractor.SAMPLE;
        });

        // 4 × 0.8 s queued behind one slot = 3.2 s, longer than the 2 s timeout per call
        List<Long> ids = IntStream.rangeClosed(1, 4)
                .mapToObj(i -> pageService.upload(lessonId, TestImages.png(50, 50), i).id()).toList();

        ids.forEach(id -> awaitStatus(id, ExtractionStatus.DONE));
        assertThat(maxRunning).hasValue(1);
    }

    @Test
    void rerunOfQueuedPageDoesNotStartASecondRun() {
        CountDownLatch release = new CountDownLatch(1);
        extractor.willAnswer(image -> {
            awaitLatch(release);
            return FakeVocabularyExtractor.SAMPLE;
        });
        long first = pageService.upload(lessonId, TestImages.png(50, 50), 1).id();
        awaitStatus(first, ExtractionStatus.RUNNING);
        long queued = pageService.upload(lessonId, TestImages.png(50, 50), 2).id(); // waits for the only slot

        assertThat(pageService.rerun(queued).status()).isEqualTo(ExtractionStatus.PENDING);

        release.countDown();
        awaitStatus(first, ExtractionStatus.DONE);
        awaitStatus(queued, ExtractionStatus.DONE);
        assertThat(extractor.calls()).isEqualTo(2);
    }

    @Test
    void rerunReplacesResultOnlyOnSuccess() {
        long pageId = pageService.upload(lessonId, TestImages.png(50, 50), null).id();
        awaitStatus(pageId, ExtractionStatus.DONE);

        extractor.willAnswer(image -> {
            throw new IllegalStateException("boom");
        });
        pageService.rerun(pageId);
        PageDto failed = awaitStatus(pageId, ExtractionStatus.FAILED);
        assertThat(failed.extraction()).isEqualTo(FakeVocabularyExtractor.SAMPLE);

        ExtractionResult second = new ExtractionResult("TEXT",
                List.of(new ExtractedWord("老师", "lǎoshī", true, "teacher", null, "n.", null, 0.9)),
                List.of(), List.of(), List.of());
        extractor.willReturn(second);
        pageService.rerun(pageId);
        PageDto done = awaitStatus(pageId, ExtractionStatus.DONE);
        assertThat(done.extraction()).isEqualTo(second);
        assertThat(done.errorMessage()).isNull();
    }

    @Test
    void rerunWhileRunningIsConflict() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        extractor.willAnswer(image -> {
            awaitLatch(release);
            return FakeVocabularyExtractor.SAMPLE;
        });
        long pageId = pageService.upload(lessonId, TestImages.png(50, 50), null).id();
        awaitStatus(pageId, ExtractionStatus.RUNNING);

        assertThatThrownBy(() -> pageService.rerun(pageId)).isInstanceOf(ConflictException.class);

        release.countDown();
        awaitStatus(pageId, ExtractionStatus.DONE);
    }

    @Test
    void unsupportedFormatIsRejectedWithoutStoringAnything() {
        assertThatThrownBy(() -> pageService.upload(lessonId, "RIFF\0\0\0\0WEBPVP8 ".getBytes(), null))
                .isInstanceOf(UnsupportedMediaTypeException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM page_image", Integer.class)).isZero();
        assertThat(uploads.resolve("lesson-" + lessonId)).doesNotExist();
    }

    @Test
    void imageAndDeleteUseTheStoredFile() throws Exception {
        byte[] png = TestImages.png(40, 40);
        long pageId = pageService.upload(lessonId, png, null).id();
        awaitStatus(pageId, ExtractionStatus.DONE);

        PageService.StoredImage image = pageService.image(pageId);
        assertThat(image.content()).isEqualTo(png);
        assertThat(image.mimeType()).isEqualTo("image/png");

        Path file = uploads.resolve(jdbc.queryForObject(
                "SELECT storage_path FROM page_image WHERE id = ?", String.class, pageId));
        pageService.delete(pageId);
        assertThat(file).doesNotExist();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM page_image", Integer.class)).isZero();
    }

    @Test
    void deletingLessonRemovesItsUploads() throws Exception {
        long pageId = pageService.upload(lessonId, TestImages.png(40, 40), null).id();
        awaitStatus(pageId, ExtractionStatus.DONE);
        assertThat(Files.list(uploads.resolve("lesson-" + lessonId))).hasSize(1);

        textbookService.deleteLesson(lessonId);

        assertThat(uploads.resolve("lesson-" + lessonId)).doesNotExist();
    }

    private PageDto awaitStatus(long pageId, ExtractionStatus status) {
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(50))
                .until(() -> pageService.get(pageId).status() == status);
        return pageService.get(pageId);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
