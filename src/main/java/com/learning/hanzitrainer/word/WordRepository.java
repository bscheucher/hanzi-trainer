package com.learning.hanzitrainer.word;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WordRepository extends JpaRepository<Word, Long> {

    Optional<Word> findByHanziAndPinyinNumbered(String hanzi, String pinyinNumbered);

    /** Random other words with a meaning: distractor candidates outside the lesson (spec 07). */
    @Query(value = """
            SELECT * FROM word WHERE id <> :excludeId AND meaning_en IS NOT NULL
            ORDER BY random() LIMIT :limit""", nativeQuery = true)
    List<Word> findRandomOthers(@Param("excludeId") long excludeId, @Param("limit") int limit);

    String SEARCH_WHERE = """
            WHERE (CAST(:lessonId AS bigint) IS NULL OR EXISTS (
                     SELECT 1 FROM lesson_word lw WHERE lw.word_id = w.id AND lw.lesson_id = :lessonId))
              AND (CAST(:text AS text) IS NULL
                   OR lower(w.hanzi) LIKE :text
                   OR lower(w.meaning_en) LIKE :text
                   OR lower(w.meaning_de) LIKE :text
                   OR (:pinyinMode = 'TONELESS' AND translate(w.pinyin_numbered, '12345 ', '') LIKE :pinyin)
                   OR (:pinyinMode = 'NUMBERED' AND replace(w.pinyin_numbered, ' ', '') LIKE :pinyin)
                   OR (:pinyinMode = 'MARKED' AND replace(lower(w.pinyin), ' ', '') LIKE :pinyin))
            """;

    /**
     * Vocabulary search (spec 03, {@code GET /words}), newest first. Patterns are lowercase {@code LIKE} patterns;
     * {@code text} null means no text filter, {@code pinyinMode} is {@code NONE} when the query is not pinyin.
     * See {@link WordSearch} for how a query becomes these parameters.
     */
    @Query(value = "SELECT w.* FROM word w " + SEARCH_WHERE + " ORDER BY w.created_at DESC, w.id DESC",
            countQuery = "SELECT count(*) FROM word w " + SEARCH_WHERE,
            nativeQuery = true)
    Page<Word> search(@Param("lessonId") Long lessonId, @Param("text") String text,
                      @Param("pinyinMode") String pinyinMode, @Param("pinyin") String pinyin, Pageable pageable);
}
