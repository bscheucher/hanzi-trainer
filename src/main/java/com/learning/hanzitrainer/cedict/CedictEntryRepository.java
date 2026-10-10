package com.learning.hanzitrainer.cedict;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CedictEntryRepository extends JpaRepository<CedictEntry, Long> {

    List<CedictEntry> findBySimplifiedOrderByIdAsc(String simplified);
}
