package com.learning.hanzitrainer.extraction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;

/** Sends the page image to the configured chat model and maps the reply onto {@link ExtractionResult}. */
public class SpringAiVocabularyExtractor implements VocabularyExtractor {

    private static final Logger log = LoggerFactory.getLogger(SpringAiVocabularyExtractor.class);
    private static final String USER_TEXT = "Extract the vocabulary from this textbook page.";

    private final ChatClient chatClient;
    private final Resource systemPrompt;
    private final String modelName;

    public SpringAiVocabularyExtractor(ChatClient chatClient, Resource systemPrompt, String modelName) {
        this.chatClient = chatClient;
        this.systemPrompt = systemPrompt;
        this.modelName = modelName;
    }

    @Override
    public ExtractionResult extract(byte[] jpegImage) {
        // Native structured output: the schema goes to the provider (Anthropic output_config.format, Ollama format)
        // instead of being appended to the prompt, which made small vision models ignore the image.
        ResponseEntity<ChatResponse, ExtractionResult> response = chatClient.prompt()
                .system(systemPrompt)
                .user(u -> u.text(USER_TEXT).media(MimeTypeUtils.IMAGE_JPEG, new ByteArrayResource(jpegImage)))
                .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .call()
                .responseEntity(ExtractionResult.class);
        logUsage(response.response());
        ExtractionResult result = response.entity();
        if (result == null) {
            throw new IllegalStateException("Model " + modelName + " returned no extraction result");
        }
        return result;
    }

    /** Token usage per page, to see what extraction costs; providers may omit some counts. */
    private void logUsage(ChatResponse response) {
        Usage usage = response == null ? null : response.getMetadata().getUsage();
        if (usage == null) {
            log.info("Extraction with {}: no token usage reported", modelName);
            return;
        }
        String model = response.getMetadata().getModel();
        log.info("Extraction with {}: {} input tokens, {} output tokens", model == null || model.isBlank()
                ? modelName : model, usage.getPromptTokens(), usage.getCompletionTokens());
    }

    @Override
    public String modelName() {
        return modelName;
    }
}
