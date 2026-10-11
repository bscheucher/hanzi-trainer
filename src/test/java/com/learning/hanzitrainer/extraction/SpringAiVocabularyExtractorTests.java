package com.learning.hanzitrainer.extraction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ByteArrayResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class SpringAiVocabularyExtractorTests {

    private static final String JSON = """
            {"pageType":"VOCABULARY_LIST","words":[{"hanzi":"朋友","pinyin":"péngyou","pinyinFromPage":true,
            "meaningEn":"friend","meaningDe":"Freund","partOfSpeech":"n.","exampleSentence":null,"confidence":0.98}],
            "sentences":[],"grammarPoints":[],"warnings":[]}""";

    private final ChatModel chatModel = mock(ChatModel.class);
    private final SpringAiVocabularyExtractor extractor = new SpringAiVocabularyExtractor(
            ChatClient.builder(chatModel).build(), new ByteArrayResource("system prompt".getBytes()), "test-model");

    @Test
    void returnsTheResultAndLogsTokenUsage(CapturedOutput output) {
        reply(ChatResponseMetadata.builder().model("test-model-2026").usage(new DefaultUsage(7210, 1840)).build());

        ExtractionResult result = extractor.extract(new byte[] {1, 2, 3});

        assertThat(result.words()).extracting(ExtractionResult.ExtractedWord::hanzi).containsExactly("朋友");
        assertThat(output).contains("Extraction with test-model-2026: 7210 input tokens, 1840 output tokens");
    }

    @Test
    void fallsBackToTheConfiguredModelName(CapturedOutput output) {
        reply(ChatResponseMetadata.builder().usage(new DefaultUsage(10, 5)).build());

        extractor.extract(new byte[] {1, 2, 3});

        assertThat(output).contains("Extraction with test-model: 10 input tokens, 5 output tokens");
    }

    private void reply(ChatResponseMetadata metadata) {
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(JSON))), metadata));
    }
}
