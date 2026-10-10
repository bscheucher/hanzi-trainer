package com.learning.hanzitrainer.page;

import com.learning.hanzitrainer.common.HanziProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Stores uploaded page images under {@code hanzi.storage.upload-dir/lesson-{id}/{uuid}.{ext}} (spec 05). */
@Component
public class FileStorage {

    private static final Logger log = LoggerFactory.getLogger(FileStorage.class);

    private final Path root;

    public FileStorage(HanziProperties properties) {
        this.root = properties.storage().uploadDir().toAbsolutePath().normalize();
    }

    /** @return the path relative to the upload root, as stored in {@code page_image.storage_path} */
    public String store(long lessonId, String extension, byte[] content) {
        String relative = "lesson-" + lessonId + "/" + UUID.randomUUID() + "." + extension;
        Path target = resolve(relative);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Storing upload failed", e);
        }
        return relative;
    }

    public byte[] read(String relativePath) {
        try {
            return Files.readAllBytes(resolve(relativePath));
        } catch (IOException e) {
            throw new UncheckedIOException("Reading " + relativePath + " failed", e);
        }
    }

    /** Best effort: a leftover file is harmless, so failures are only logged. */
    public void delete(String relativePath) {
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException e) {
            log.warn("Could not delete {}", relativePath, e);
        }
    }

    public void deleteLesson(long lessonId) {
        try {
            FileSystemUtils.deleteRecursively(resolve("lesson-" + lessonId));
        } catch (IOException e) {
            log.warn("Could not delete uploads of lesson {}", lessonId, e);
        }
    }

    private Path resolve(String relativePath) {
        Path path = root.resolve(relativePath).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes the upload directory: " + relativePath);
        }
        return path;
    }
}
