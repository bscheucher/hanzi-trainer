package com.learning.hanzitrainer.review;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewCardRepository extends JpaRepository<ReviewCard, Long> {

    long countByWordId(long wordId);
}
