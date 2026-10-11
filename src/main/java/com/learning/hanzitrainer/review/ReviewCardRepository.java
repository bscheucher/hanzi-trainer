package com.learning.hanzitrainer.review;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewCardRepository extends JpaRepository<ReviewCard, Long> {

    long countByWordId(long wordId);

    List<ReviewCard> findByWordId(long wordId);
}
