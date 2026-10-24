package com.learning.hanzitrainer.extraction;

import com.learning.hanzitrainer.common.LlmUsage;
import com.learning.hanzitrainer.common.StructuredReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
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
        StructuredReply<ExtractionResult> reply = StructuredReply.call(chatClient.prompt()
                .system(systemPrompt)
                .user(u -> u.text(USER_TEXT).media(MimeTypeUtils.IMAGE_JPEG, new ByteArrayResource(jpegImage))),
                ExtractionResult.class);
        LlmUsage.log(log, "Extraction", modelName, reply.response());
        ExtractionResult result = reply.entity();
        if (result == null) {
            throw new IllegalStateException("Model " + modelName + " returned no extraction result");
        }
        return result;
    }

    @Override
    public String modelName() {
        return modelName;
    }
}
