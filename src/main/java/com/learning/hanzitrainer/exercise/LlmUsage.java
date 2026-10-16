package com.learning.hanzitrainer.exercise;

import org.slf4j.Logger;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

/** Logs the token usage of one model call, to see what generation and grading cost; providers may omit counts. */
final class LlmUsage {

    private LlmUsage() {
    }

    static void log(Logger log, String purpose, String modelName, ChatResponse response) {
        Usage usage = response == null ? null : response.getMetadata().getUsage();
        if (usage == null) {
            log.info("{} with {}: no token usage reported", purpose, modelName);
            return;
        }
        String model = response.getMetadata().getModel();
        log.info("{} with {}: {} input tokens, {} output tokens", purpose,
                model == null || model.isBlank() ? modelName : model, usage.getPromptTokens(),
                usage.getCompletionTokens());
    }
}
