package com.learning.hanzitrainer.extraction;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Spec 01, "LLM provider selection": exercise calls get their own HTTP timeout. */
class LlmConfigTests {

    private static final RetryTemplate NO_RETRY = new RetryTemplate(RetryPolicy.withMaxRetries(0));

    @Test
    void anthropicExerciseCallsCarryTheExerciseTimeoutWhileOtherCallsKeepTheModels() {
        ChatModel anthropic = mock(ChatModel.class);
        when(anthropic.getOptions()).thenReturn(AnthropicChatOptions.builder().model("default")
                .timeout(Duration.ofSeconds(120)).build());
        when(anthropic.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("ok")))));

        LlmConfig.exerciseChatClient(anthropic, "claude-sonnet-5-5", Duration.ofSeconds(300), "unused", NO_RETRY)
                .prompt().user("x").call().content();
        ChatClient.builder(anthropic).defaultOptions(ChatOptions.builder().model("claude-opus-5-5")).build()
                .prompt().user("x").call().content(); // built like the extraction client

        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(anthropic, times(2)).call(prompts.capture());
        AnthropicChatOptions exercise = (AnthropicChatOptions) prompts.getAllValues().get(0).getOptions();
        AnthropicChatOptions extraction = (AnthropicChatOptions) prompts.getAllValues().get(1).getOptions();
        assertThat(exercise.getModel()).isEqualTo("claude-sonnet-5-5");
        assertThat(exercise.getTimeout()).isEqualTo(Duration.ofSeconds(300));
        assertThat(extraction.getTimeout()).isEqualTo(Duration.ofSeconds(120));
    }

    @Test
    void ollamaExerciseCallsUseAClientWithTheExerciseReadTimeout() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        HttpServer slow = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        slow.createContext("/", exchange -> {
            try {
                release.await(5, TimeUnit.SECONDS); // never answers in time
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        slow.start();
        try {
            String baseUrl = "http://127.0.0.1:" + slow.getAddress().getPort();
            OllamaChatModel ollama = OllamaChatModel.builder()
                    .ollamaApi(OllamaApi.builder().baseUrl(baseUrl).build())
                    .retryTemplate(NO_RETRY)
                    .build();
            ChatClient client = LlmConfig.exerciseChatClient(ollama, "gemma3:4b", Duration.ofMillis(500), baseUrl,
                    NO_RETRY);

            long start = System.nanoTime();
            assertThatThrownBy(() -> client.prompt().user("x").call().content()).isInstanceOf(RuntimeException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(4));
        } finally {
            release.countDown();
            slow.stop(0);
        }
    }
}
