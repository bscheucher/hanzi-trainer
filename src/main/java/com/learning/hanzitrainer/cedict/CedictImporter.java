package com.learning.hanzitrainer.cedict;

import com.learning.hanzitrainer.common.HanziProperties;
import com.learning.hanzitrainer.common.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the content of {@code cedict_entry} with the CC-CEDICT file in one transaction and re-links
 * {@code word.cedict_entry_id} (spec 04, "Import"). Readers keep seeing the old content until commit.
 */
@Service
public class CedictImporter {

    private static final Logger log = LoggerFactory.getLogger(CedictImporter.class);
    private static final int BATCH_SIZE = 1000;
    private static final String INSERT = """
            INSERT INTO cedict_entry (simplified, traditional, pinyin_numbered, pinyin, definitions)
            VALUES (?, ?, ?, ?, ?)""";
    /** Duplicates of (simplified, pinyin_numbered) exist; link the entry with the lowest id. */
    private static final String RELINK_WORDS = """
            UPDATE word w SET cedict_entry_id = (
                SELECT min(c.id) FROM cedict_entry c
                WHERE c.simplified = w.hanzi AND c.pinyin_numbered = w.pinyin_numbered)""";

    private final JdbcTemplate jdbc;
    private final HanziProperties properties;

    public CedictImporter(JdbcTemplate jdbc, HanziProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    public Path configuredFile() {
        return properties.cedict().file();
    }

    @Transactional
    public ImportResult importConfiguredFile() {
        return importFile(configuredFile());
    }

    @Transactional
    public ImportResult importFile(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new ServiceUnavailableException("CC-CEDICT file not found at " + file.toAbsolutePath()
                    + ". Download cedict_1_0_ts_utf-8_mdbg.txt.gz from MDBG and unzip it there.");
        }
        long start = System.nanoTime();

        // Not TRUNCATE: the FK from word makes it fail, and TRUNCATE ... CASCADE would empty word.
        jdbc.update("UPDATE word SET cedict_entry_id = NULL WHERE cedict_entry_id IS NOT NULL");
        jdbc.update("DELETE FROM cedict_entry");

        int rows = 0;
        int skipped = 0;
        List<CedictParser.ParsedEntry> batch = new ArrayList<>(BATCH_SIZE);
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (CedictParser.isComment(line)) {
                    continue;
                }
                var entry = CedictParser.parse(line);
                if (entry.isEmpty()) {
                    skipped++;
                    continue;
                }
                batch.add(entry.get());
                if (batch.size() == BATCH_SIZE) {
                    rows += insert(batch);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Reading " + file + " failed", e);
        }
        rows += insert(batch);

        int linked = jdbc.update(RELINK_WORDS);
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        log.info("Imported {} CC-CEDICT entries from {} in {} ms ({} unparsable lines skipped, {} words re-linked)",
                rows, file, durationMs, skipped, linked);
        return new ImportResult(rows, durationMs);
    }

    private int insert(List<CedictParser.ParsedEntry> batch) {
        if (batch.isEmpty()) {
            return 0;
        }
        jdbc.batchUpdate(INSERT, batch, batch.size(), (ps, e) -> {
            ps.setString(1, e.simplified());
            ps.setString(2, e.traditional());
            ps.setString(3, e.pinyinNumbered());
            ps.setString(4, e.pinyin());
            ps.setArray(5, ps.getConnection().createArrayOf("text", e.definitions().toArray()));
        });
        int size = batch.size();
        batch.clear();
        return size;
    }

    public record ImportResult(int rows, long durationMs) {
    }
}
