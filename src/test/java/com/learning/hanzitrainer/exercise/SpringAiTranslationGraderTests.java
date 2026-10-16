package com.learning.hanzitrainer.exercise;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ByteArrayResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiTranslationGraderTests {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final SpringAiTranslationGrader grader = new SpringAiTranslationGrader(
            ChatClient.builder(chatModel).build(), new ByteArrayResource("grading instructions".getBytes()), "test-model");

    @Test
    void sendsSourceReferenceAndAnswerAndReadsTheGrade() {
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("""
                {"correct": true, "score": 1.0, "feedback": "Natural and correct.", "corrected": "我跟朋友学中文。"}
                """)))));

        TranslationGrader.TranslationGrade grade = grader.grade("I study Chinese with my friend.",
                "我和我的朋友学习中文。", "我跟朋友学中文。");

        assertThat(grade).isEqualTo(new TranslationGrader.TranslationGrade(true, 1.0, "Natural and correct.",
                "我跟朋友学中文。"));
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getSystemMessage().getText()).isEqualTo("grading instructions");
        assertThat(prompt.getValue().getUserMessage().getText()).contains("I study Chinese with my friend.",
                "Reference translation: 我和我的朋友学习中文。", "Learner's translation: 我跟朋友学中文。");
    }
}
