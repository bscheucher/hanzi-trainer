package com.learning.hanzitrainer.common;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientAttributes;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.util.StringUtils;

/**
 * Calls a chat model with native structured output and reads the entity from the reply.
 *
 * <p>Like {@code responseEntity(type)} with {@code AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT}, except for which
 * part of the reply is parsed: a thinking model (current Claude models think) returns the thinking block as the first
 * generation with empty text, and {@code responseEntity} only reads the first one. Here the last generation with
 * text is parsed.
 *
 * @param response the raw reply, for token usage; may be null
 * @param entity   null if the reply had no text
 */
public record StructuredReply<T>(ChatResponse response, T entity) {

    public static <T> StructuredReply<T> call(ChatClient.ChatClientRequestSpec request, Class<T> type) {
        BeanOutputConverter<T> converter = new BeanOutputConverter<>(type);
        // The same context responseEntity sets: the schema goes to the provider (Anthropic output_config.format,
        // Ollama format); providers without native support get the format instructions appended instead.
        ChatResponse response = request
                .advisors(a -> a.param(ChatClientAttributes.STRUCTURED_OUTPUT_NATIVE.getKey(), true)
                        .param(ChatClientAttributes.STRUCTURED_OUTPUT_SCHEMA.getKey(), converter.getJsonSchema())
                        .param(ChatClientAttributes.OUTPUT_FORMAT.getKey(), converter.getFormat()))
                .call()
                .chatResponse();
        String text = text(response);
        return new StructuredReply<>(response, text == null ? null : converter.convert(text));
    }

    private static String text(ChatResponse response) {
        if (response == null) {
            return null;
        }
        String text = null;
        for (Generation generation : response.getResults()) {
            String candidate = generation.getOutput().getText();
            if (StringUtils.hasText(candidate)) {
                text = candidate;
            }
        }
        return text;
    }
}
