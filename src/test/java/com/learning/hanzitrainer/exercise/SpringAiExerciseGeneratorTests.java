package com.learning.hanzitrainer.exercise;

import com.learning.hanzitrainer.word.WordDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class SpringAiExerciseGeneratorTests {

    private static final WordDto FRIEND = new WordDto(1L, "朋友", "péngyou", "peng2 you5", "friend", null, null, true);

    private final ChatModel chatModel = mock(ChatModel.class);
    private final SpringAiExerciseGenerator generator = new SpringAiExerciseGenerator(
            ChatClient.builder(chatModel).build(), Map.of(
                    ExerciseType.FILL_BLANK, new ByteArrayResource("fill-blank instructions".getBytes()),
                    ExerciseType.WORD_ORDER, new ByteArrayResource("word-order instructions".getBytes()),
                    ExerciseType.TRANSLATION, new ByteArrayResource("translation instructions".getBytes())),
            "test-model");

    @Test
    void sendsTheTypesInstructionsAndTheWordsAndReadsTheExercises(CapturedOutput output) {
        reply("""
                {"exercises": [{"accepted": [["我", "去", "学校"]], "translationEn": "I go to school."}]}""");

        List<GeneratedExercise> result = generator.generate(ExerciseType.WORD_ORDER, List.of(FRIEND),
                new LinkedHashSet<>(List.of("朋友", "学校")), 3);

        assertThat(result).containsExactly(new GeneratedExercise.WordOrder(List.of(List.of("我", "去", "学校")),
                "I go to school."));
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getSystemMessage().getText()).isEqualTo("word-order instructions");
        assertThat(prompt.getValue().getUserMessage().getText())
                .contains("Write 3 exercises.", "- 朋友 (péngyou): friend", "朋友 学校");
        assertThat(output).contains("Generating WORD_ORDER with test-model: 900 input tokens, 300 output tokens");
    }

    @Test
    void readsFillBlankAndTranslationShapes() {
        reply("""
                {"exercises": [{"sentence": "我的___。", "translationEn": "my friend", "correct": "朋友",
                "distractors": ["我", "你", "他"]}]}""");
        assertThat(generator.generate(ExerciseType.FILL_BLANK, List.of(FRIEND), Set.of(), 1))
                .first().isInstanceOf(GeneratedExercise.FillBlank.class);

        reply("""
                {"exercises": [{"sourceDe": "mein Freund", "sourceEn": "my friend", "reference": "我的朋友"}]}""");
        assertThat(generator.generate(ExerciseType.TRANSLATION, List.of(FRIEND), Set.of(), 1))
                .containsExactly(new GeneratedExercise.Translation("mein Freund", "my friend", "我的朋友"));
    }

    @Test
    void codeTypesAreNotGenerated() {
        assertThatThrownBy(() -> generator.generate(ExerciseType.TONE, List.of(FRIEND), Set.of(), 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void reply(String json) {
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage(json))),
                ChatResponseMetadata.builder().model("test-model").usage(new DefaultUsage(900, 300)).build()));
    }
}
