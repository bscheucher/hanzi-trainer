package com.learning.hanzitrainer.extraction;

import com.learning.hanzitrainer.extraction.ExtractionResult.ExtractedSentence;
import com.learning.hanzitrainer.extraction.ExtractionResult.ExtractedWord;

import java.util.List;
import java.util.function.Function;

/** Test double for {@link VocabularyExtractor} (spec 05, "Tests"); behaviour can be swapped per test. */
public class FakeVocabularyExtractor implements VocabularyExtractor {

    public static final ExtractionResult SAMPLE = new ExtractionResult(
            "VOCABULARY_LIST",
            List.of(
                    new ExtractedWord("朋友", "péngyou", true, "friend", "Freund", "n.", "他是我的朋友。", 0.98),
                    new ExtractedWord("学习", "xuéxí", true, "to study", null, "v.", null, 0.95)),
            List.of(new ExtractedSentence("他是我的朋友。", "Tā shì wǒ de péngyou.", "He is my friend.")),
            List.of(),
            List.of());

    private volatile Function<byte[], ExtractionResult> behaviour = image -> SAMPLE;
    private volatile byte[] lastImage;
    private final java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();

    @Override
    public ExtractionResult extract(byte[] jpegImage) {
        calls.incrementAndGet();
        lastImage = jpegImage;
        return behaviour.apply(jpegImage);
    }

    @Override
    public String modelName() {
        return "fake-model";
    }

    public void willReturn(ExtractionResult result) {
        behaviour = image -> result;
    }

    public void willAnswer(Function<byte[], ExtractionResult> answer) {
        behaviour = answer;
    }

    public void reset() {
        behaviour = image -> SAMPLE;
        lastImage = null;
        calls.set(0);
    }

    public int calls() {
        return calls.get();
    }

    public byte[] lastImage() {
        return lastImage;
    }
}
