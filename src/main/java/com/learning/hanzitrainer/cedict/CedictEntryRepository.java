package com.learning.hanzitrainer.cedict;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CedictEntryRepository extends JpaRepository<CedictEntry, Long> {

    List<CedictEntry> findBySimplifiedOrderByIdAsc(String simplified);

    /** Random entries with as many characters as {@code length}: last-resort distractors (spec 07). */
    @Query(value = """
            SELECT * FROM cedict_entry
            WHERE char_length(simplified) = :length AND simplified <> :exclude
            ORDER BY random() LIMIT :limit""", nativeQuery = true)
    List<CedictEntry> findRandomOfLength(@Param("length") int length, @Param("exclude") String exclude,
                                         @Param("limit") int limit);
}
