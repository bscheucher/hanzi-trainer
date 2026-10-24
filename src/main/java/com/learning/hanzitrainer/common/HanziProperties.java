package com.learning.hanzitrainer.common;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;

/** All {@code hanzi.*} settings (spec 01). */
@ConfigurationProperties("hanzi")
public record HanziProperties(
        @DefaultValue Cedict cedict,
        @DefaultValue Storage storage,
        @DefaultValue Llm llm,
        @DefaultValue Extraction extraction,
        @DefaultValue Exercises exercises,
        @DefaultValue Review review) {

    /**
     * @param file            CC-CEDICT file, downloaded by the user (the app never downloads it)
     * @param importOnStartup import on startup if {@code cedict_entry} is empty and the file exists
     */
    public record Cedict(
            @DefaultValue("./data/cedict/cedict_ts.u8") Path file,
            @DefaultValue("true") boolean importOnStartup) {
    }

    public record Storage(@DefaultValue("./data/uploads") Path uploadDir) {
    }

    /**
     * @param provider        {@code anthropic} or {@code ollama}; also selects Spring AI's chat model
     * @param extractionModel vision-capable model name for the configured provider
     * @param exerciseModel   model that writes sentence exercises and grades translations (spec 07)
     * @param exerciseTimeout HTTP timeout of one exercise call (generation, grading); extraction calls use
     *                        {@code hanzi.extraction.timeout}
     */
    public record Llm(
            @DefaultValue("anthropic") String provider,
            @DefaultValue("claude-opus-5-5") String extractionModel,
            @DefaultValue("claude-sonnet-5-5") String exerciseModel,
            @DefaultValue("300s") Duration exerciseTimeout) {
    }

    /**
     * @param maxImagePx    long side of the image sent to the model
     * @param timeout       per model call; time spent waiting for a free slot does not count
     * @param maxConcurrent model calls running at once; null = 1 for ollama, 2 for anthropic
     */
    public record Extraction(
            @DefaultValue("2576") int maxImagePx,
            @DefaultValue("120s") Duration timeout,
            Integer maxConcurrent) {

        public int effectiveMaxConcurrent(String provider) {
            if (maxConcurrent != null) {
                return Math.max(1, maxConcurrent);
            }
            return "ollama".equals(provider) ? 1 : 2;
        }
    }

    /** @param defaultSessionSize exercises per practice session when the request gives no size */
    public record Exercises(@DefaultValue("15") int defaultSessionSize) {
    }

    /**
     * Spec 09, "Review flow".
     *
     * @param newPerDay new cards introduced per local day
     * @param timeZone  what "today" means for the new-card limit and the dashboard
     */
    public record Review(
            @DefaultValue("15") int newPerDay,
            @DefaultValue("Europe/Vienna") ZoneId timeZone) {
    }
}
