package com.learning.hanzitrainer.word;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WordRepository extends JpaRepository<Word, Long> {

    Optional<Word> findByHanziAndPinyinNumbered(String hanzi, String pinyinNumbered);
}
