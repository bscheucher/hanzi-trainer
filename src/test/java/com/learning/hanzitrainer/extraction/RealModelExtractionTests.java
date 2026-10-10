package com.learning.hanzitrainer.extraction;

import com.learning.hanzitrainer.TestcontainersConfiguration;
import com.learning.hanzitrainer.common.HanziProperties;
import com.learning.hanzitrainer.extraction.ExtractionResult.ExtractedWord;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sends {@code sample-page.jpg} to the configured provider and model (spec 05, "Tests"). Costs money with Anthropic.
 * Run with {@code HANZI_LLM_TEST=true ./gradlew llmTest}; never part of {@code ./gradlew build}.
 */
@Tag("llm")
@EnabledIfEnvironmentVariable(named = "HANZI_LLM_TEST", matches = "true")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RealModelExtractionTests {

    @Autowired
    private VocabularyExtractor extractor;

    @Autowired
    private HanziProperties properties;

    @Test
    void extractsTheVocabularyList() throws IOException {
        assertThat(extractor.unavailableReason()).as("LLM configured").isEmpty();

        byte[] jpeg = ImageProcessor.toJpeg(samplePage(), properties.extraction().maxImagePx());
        ExtractionResult result = ExtractionResultValidator.validate(extractor.extract(jpeg));

        System.out.printf("%s via %s:%n", result.pageType(), extractor.modelName());
        result.words().forEach(w -> System.out.printf("  %s  %s  %s  (fromPage=%s, confidence=%.2f)%n",
                w.hanzi(), w.pinyin(), w.meaningEn(), w.pinyinFromPage(), w.confidence()));

        List<String> hanzi = result.words().stream().map(ExtractedWord::hanzi).toList();
        assertThat(hanzi).contains("朋友", "老师", "学生", "谢谢", "再见");
        assertThat(result.words()).filteredOn(w -> w.hanzi().equals("朋友")).first()
                .satisfies(w -> assertThat(w.pinyin()).startsWith("péng"));
    }

    private static byte[] samplePage() throws IOException {
        try (InputStream in = RealModelExtractionTests.class.getResourceAsStream("/sample-page.jpg")) {
            return in.readAllBytes();
        }
    }
}
