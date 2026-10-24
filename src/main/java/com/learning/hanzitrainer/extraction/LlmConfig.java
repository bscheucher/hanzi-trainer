package com.learning.hanzitrainer.extraction;

import com.learning.hanzitrainer.common.HanziProperties;
import com.learning.hanzitrainer.exercise.ExerciseGenerator;
import com.learning.hanzitrainer.exercise.ExerciseType;
import com.learning.hanzitrainer.exercise.SpringAiExerciseGenerator;
import com.learning.hanzitrainer.exercise.SpringAiTranslationGrader;
import com.learning.hanzitrainer.exercise.TranslationGrader;
import com.learning.hanzitrainer.exercise.UnavailableExerciseGenerator;
import com.learning.hanzitrainer.exercise.UnavailableTranslationGrader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * LLM wiring (spec 01, "LLM provider selection"). {@code spring.ai.model.chat} follows {@code hanzi.llm.provider},
 * so Spring AI creates only that provider's {@link ChatModel}; this class configures the model name per purpose:
 * vocabulary extraction, and exercises (generating sentences, grading translations).
 */
@Configuration(proxyBeanMethods = false)
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);
    /** Thinking is always on for current Claude models; leave room for it plus a long word list. */
    private static final int EXTRACTION_MAX_TOKENS = 16_000;
    private static final int EXERCISE_MAX_TOKENS = 16_000;

    @Bean
    VocabularyExtractor vocabularyExtractor(ObjectProvider<ChatModel> chatModels, HanziProperties properties,
                                            @Value("${spring.ai.anthropic.api-key:}") String anthropicApiKey,
                                            @Value("classpath:prompts/extract-vocabulary.st") Resource prompt) {
        String provider = properties.llm().provider();
        String model = properties.llm().extractionModel();
        String missing = missingSetting(provider, anthropicApiKey, chatModels.getIfUnique());
        if (missing != null) {
            log.warn("Vocabulary extraction unavailable: {}", missing);
            return new UnavailableVocabularyExtractor(missing);
        }
        ChatClient extractionChatClient = ChatClient.builder(chatModels.getObject())
                .defaultOptions(ChatOptions.builder().model(model).maxTokens(EXTRACTION_MAX_TOKENS))
                .build();
        log.info("Vocabulary extraction uses {} model {}", provider, model);
        return new SpringAiVocabularyExtractor(extractionChatClient, prompt, model);
    }

    /**
     * The chat client for exercises, shared by generation and translation grading.
     *
     * @param client  null when {@code missing} is set
     * @param missing which setting is missing, or null
     */
    record ExerciseChat(ChatClient client, String model, String missing) {
    }

    @Bean
    ExerciseChat exerciseChat(ObjectProvider<ChatModel> chatModels, HanziProperties properties,
                              @Value("${spring.ai.anthropic.api-key:}") String anthropicApiKey,
                              @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
                              ObjectProvider<RetryTemplate> retryTemplate) {
        String provider = properties.llm().provider();
        String model = properties.llm().exerciseModel();
        String missing = missingSetting(provider, anthropicApiKey, chatModels.getIfUnique());
        if (missing != null) {
            log.warn("Exercise generation and translation grading unavailable: {}", missing);
            return new ExerciseChat(null, model, missing);
        }
        Duration timeout = properties.llm().exerciseTimeout();
        log.info("Exercise generation and translation grading use {} model {}, timeout {} s", provider, model,
                timeout.toSeconds());
        return new ExerciseChat(exerciseChatClient(chatModels.getObject(), model, timeout, ollamaBaseUrl,
                retryTemplate.getIfUnique(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE)), model, null);
    }

    /**
     * The exercise client gets its own HTTP timeout ({@code hanzi.llm.exercise-timeout}): a generation reply with
     * thinking takes longer than an extraction call, whose timeout ({@code hanzi.extraction.timeout}) is the one
     * configured on the shared clients. Anthropic takes a timeout per call in its options; Ollama's timeout belongs
     * to the HTTP client, so exercises get an Ollama model of their own.
     */
    static ChatClient exerciseChatClient(ChatModel chatModel, String model, Duration timeout, String ollamaBaseUrl,
                                         RetryTemplate retryTemplate) {
        if (chatModel instanceof OllamaChatModel ollama) {
            OllamaApi api = OllamaApi.builder()
                    .baseUrl(ollamaBaseUrl)
                    .restClientBuilder(RestClient.builder().requestFactory(ClientHttpRequestFactoryBuilder.detect()
                            .build(HttpClientSettings.defaults().withReadTimeout(timeout))))
                    .build();
            OllamaChatModel exerciseModel = OllamaChatModel.builder()
                    .ollamaApi(api)
                    .options(ollama.getOptions())
                    .retryTemplate(retryTemplate)
                    .build();
            return ChatClient.builder(exerciseModel)
                    .defaultOptions(ChatOptions.builder().model(model).maxTokens(EXERCISE_MAX_TOKENS))
                    .build();
        }
        return ChatClient.builder(chatModel)
                .defaultOptions(AnthropicChatOptions.builder().model(model).maxTokens(EXERCISE_MAX_TOKENS)
                        .timeout(timeout))
                .build();
    }

    @Bean
    ExerciseGenerator exerciseGenerator(ExerciseChat chat,
                                        @Value("classpath:prompts/gen-fill-blank.st") Resource fillBlank,
                                        @Value("classpath:prompts/gen-word-order.st") Resource wordOrder,
                                        @Value("classpath:prompts/gen-translation.st") Resource translation) {
        if (chat.missing() != null) {
            return new UnavailableExerciseGenerator(chat.missing());
        }
        return new SpringAiExerciseGenerator(chat.client(), Map.of(
                ExerciseType.FILL_BLANK, fillBlank,
                ExerciseType.WORD_ORDER, wordOrder,
                ExerciseType.TRANSLATION, translation), chat.model());
    }

    @Bean
    TranslationGrader translationGrader(ExerciseChat chat,
                                        @Value("classpath:prompts/grade-translation.st") Resource prompt) {
        if (chat.missing() != null) {
            return new UnavailableTranslationGrader(chat.missing());
        }
        return new SpringAiTranslationGrader(chat.client(), prompt, chat.model());
    }

    private static String missingSetting(String provider, String anthropicApiKey, ChatModel chatModel) {
        if (!provider.equals("anthropic") && !provider.equals("ollama")) {
            return "hanzi.llm.provider must be 'anthropic' or 'ollama', not '" + provider + "'";
        }
        if (provider.equals("anthropic") && anthropicApiKey.isBlank()) {
            return "LLM provider 'anthropic' needs ANTHROPIC_API_KEY (set it in .env, see .env.example)";
        }
        if (chatModel == null) {
            return "No chat model for provider '" + provider + "' (check spring.ai.model.chat)";
        }
        return null;
    }
}
