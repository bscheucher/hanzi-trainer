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
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

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

    @Bean
    ExerciseGenerator exerciseGenerator(ObjectProvider<ChatModel> chatModels, HanziProperties properties,
                                        @Value("${spring.ai.anthropic.api-key:}") String anthropicApiKey,
                                        @Value("classpath:prompts/gen-fill-blank.st") Resource fillBlank,
                                        @Value("classpath:prompts/gen-word-order.st") Resource wordOrder,
                                        @Value("classpath:prompts/gen-translation.st") Resource translation) {
        String missing = missingSetting(properties.llm().provider(), anthropicApiKey, chatModels.getIfUnique());
        if (missing != null) {
            log.warn("Exercise generation unavailable: {}", missing);
            return new UnavailableExerciseGenerator(missing);
        }
        String model = properties.llm().exerciseModel();
        log.info("Exercise generation uses {} model {}", properties.llm().provider(), model);
        return new SpringAiExerciseGenerator(exerciseChatClient(chatModels.getObject(), model), Map.of(
                ExerciseType.FILL_BLANK, fillBlank,
                ExerciseType.WORD_ORDER, wordOrder,
                ExerciseType.TRANSLATION, translation), model);
    }

    @Bean
    TranslationGrader translationGrader(ObjectProvider<ChatModel> chatModels, HanziProperties properties,
                                        @Value("${spring.ai.anthropic.api-key:}") String anthropicApiKey,
                                        @Value("classpath:prompts/grade-translation.st") Resource prompt) {
        String missing = missingSetting(properties.llm().provider(), anthropicApiKey, chatModels.getIfUnique());
        if (missing != null) {
            return new UnavailableTranslationGrader(missing);
        }
        String model = properties.llm().exerciseModel();
        return new SpringAiTranslationGrader(exerciseChatClient(chatModels.getObject(), model), prompt, model);
    }

    private static ChatClient exerciseChatClient(ChatModel chatModel, String model) {
        return ChatClient.builder(chatModel)
                .defaultOptions(ChatOptions.builder().model(model).maxTokens(EXERCISE_MAX_TOKENS))
                .build();
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
