package com.learning.hanzitrainer.cedict;

import com.learning.hanzitrainer.common.HanziProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CedictStartupImportTests {

    private final CedictImporter importer = mock(CedictImporter.class);
    private final CedictEntryRepository repository = mock(CedictEntryRepository.class);

    @TempDir
    Path dir;

    @Test
    void importsWhenTableIsEmptyAndFileExists() throws IOException {
        Path file = Files.writeString(dir.resolve("cedict.u8"), "# empty\n");
        when(repository.count()).thenReturn(0L);

        startupImport(file, true).importIfEmpty();

        verify(importer).importFile(file);
    }

    @Test
    void skipsWhenTableHasEntries() throws IOException {
        Path file = Files.writeString(dir.resolve("cedict.u8"), "# empty\n");
        when(repository.count()).thenReturn(5L);

        startupImport(file, true).importIfEmpty();

        verify(importer, never()).importFile(any());
    }

    @Test
    void skipsWithWarningWhenFileIsMissing() {
        when(repository.count()).thenReturn(0L);

        startupImport(dir.resolve("missing.u8"), true).importIfEmpty();

        verify(importer, never()).importFile(any());
    }

    @Test
    void skipsWhenDisabled() throws IOException {
        Path file = Files.writeString(dir.resolve("cedict.u8"), "# empty\n");

        startupImport(file, false).importIfEmpty();

        verify(importer, never()).importFile(any());
        verify(repository, never()).count();
    }

    private CedictStartupImport startupImport(Path file, boolean enabled) {
        var properties = new HanziProperties(new HanziProperties.Cedict(file, enabled), null, null, null, null);
        return new CedictStartupImport(importer, repository, properties);
    }
}
