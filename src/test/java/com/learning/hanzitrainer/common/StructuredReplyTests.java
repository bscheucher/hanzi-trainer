package com.learning.hanzitrainer.common;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StructuredReplyTests {

    record Answer(String word) {
    }

    private final ChatModel chatModel = mock(ChatModel.class);
    private final ChatClient client = ChatClient.builder(chatModel).build();

    @Test
    void readsTheTextAfterAThinkingBlock() {
        reply(new Generation(new AssistantMessage("")), new Generation(new AssistantMessage("{\"word\": \"朋友\"}")));

        StructuredReply<Answer> result = StructuredReply.call(client.prompt().user("x"), Answer.class);

        assertThat(result.entity()).isEqualTo(new Answer("朋友"));
        assertThat(result.response()).isNotNull();
    }

    @Test
    void noTextGivesNoEntity() {
        reply(new Generation(new AssistantMessage("")));

        assertThat(StructuredReply.call(client.prompt().user("x"), Answer.class).entity()).isNull();
    }

    @Test
    void truncatedJsonIsAnUnreadableReply() {
        reply(new Generation(new AssistantMessage("{\"word\": \"朋")));

        assertThatThrownBy(() -> StructuredReply.call(client.prompt().user("x"), Answer.class))
                .isInstanceOf(UnreadableReplyException.class).hasMessageContaining("not a valid Answer");
    }

    private void reply(Generation... generations) {
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(generations)));
    }
}
