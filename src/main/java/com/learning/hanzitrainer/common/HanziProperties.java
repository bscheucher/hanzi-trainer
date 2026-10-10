package com.learning.hanzitrainer.common;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;

/** All {@code hanzi.*} settings (spec 01). */
@ConfigurationProperties("hanzi")
public record HanziProperties(@DefaultValue Cedict cedict) {

    /**
     * @param file            CC-CEDICT file, downloaded by the user (the app never downloads it)
     * @param importOnStartup import on startup if {@code cedict_entry} is empty and the file exists
     */
    public record Cedict(
            @DefaultValue("./data/cedict/cedict_ts.u8") Path file,
            @DefaultValue("true") boolean importOnStartup) {
    }
}
