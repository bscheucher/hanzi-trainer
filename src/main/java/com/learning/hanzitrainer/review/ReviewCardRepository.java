package com.learning.hanzitrainer.review;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewCardRepository extends JpaRepository<ReviewCard, Long> {

    long countByWordId(long wordId);

    List<ReviewCard> findByWordId(long wordId);

    /** Every card with its word; a single user has a few thousand at most. */
    @Query("select c from ReviewCard c join fetch c.word")
    List<ReviewCard> findAllWithWord();

    /** Locks the card, so two ratings of it cannot interleave (spec 09, idempotency). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ReviewCard c join fetch c.word where c.id = :id")
    Optional<ReviewCard> findByIdForUpdate(@Param("id") long id);
}
