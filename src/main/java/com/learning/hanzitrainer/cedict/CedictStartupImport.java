package com.learning.hanzitrainer.cedict;

import com.learning.hanzitrainer.common.HanziProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/** Imports CC-CEDICT on startup when the table is empty and the file exists (spec 04). */
@Component
public class CedictStartupImport {

    private static final Logger log = LoggerFactory.getLogger(CedictStartupImport.class);

    private final CedictImporter importer;
    private final CedictEntryRepository repository;
    private final HanziProperties properties;

    public CedictStartupImport(CedictImporter importer, CedictEntryRepository repository,
                               HanziProperties properties) {
        this.importer = importer;
        this.repository = repository;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void importIfEmpty() {
        if (!properties.cedict().importOnStartup() || repository.count() > 0) {
            return;
        }
        Path file = properties.cedict().file();
        if (!Files.isRegularFile(file)) {
            log.warn("cedict_entry is empty and no CC-CEDICT file at {}; dictionary lookups will find nothing. "
                    + "See src/docs/specs/04-cedict-and-pinyin.md.", file.toAbsolutePath());
            return;
        }
        importer.importFile(file);
    }
}
