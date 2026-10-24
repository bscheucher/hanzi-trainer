package com.learning.hanzitrainer.exercise;

import com.learning.hanzitrainer.common.LlmUsage;
import com.learning.hanzitrainer.common.StructuredReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.Resource;

/** Grades a translation with the configured chat model ({@code prompts/grade-translation.st}). */
public class SpringAiTranslationGrader implements TranslationGrader {

    private static final Logger log = LoggerFactory.getLogger(SpringAiTranslationGrader.class);

    private final ChatClient chatClient;
    private final Resource systemPrompt;
    private final String modelName;

    public SpringAiTranslationGrader(ChatClient chatClient, Resource systemPrompt, String modelName) {
        this.chatClient = chatClient;
        this.systemPrompt = systemPrompt;
        this.modelName = modelName;
    }

    @Override
    public TranslationGrade grade(String source, String reference, String answer) {
        StructuredReply<TranslationGrade> reply = StructuredReply.call(chatClient.prompt()
                .system(systemPrompt)
                .user("Sentence to translate: " + source + "\nReference translation: " + reference
                        + "\nLearner's translation: " + answer), TranslationGrade.class);
        LlmUsage.log(log, "Grading a translation", modelName, reply.response());
        TranslationGrade grade = reply.entity();
        if (grade == null) {
            throw new IllegalStateException("Model " + modelName + " returned no grade");
        }
        return grade;
    }
}
