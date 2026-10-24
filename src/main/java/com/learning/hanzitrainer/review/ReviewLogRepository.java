package com.learning.hanzitrainer.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.List;

public interface ReviewLogRepository extends JpaRepository<ReviewLog, Long> {

    long countByReviewedAtGreaterThanEqual(Instant since);

    /** Not a derived query: Spring Data would read {@code StateBefore} as {@code state} + the keyword "Before". */
    @Query("select count(l) from ReviewLog l where l.stateBefore = :stateBefore and l.reviewedAt >= :since")
    long countRatedSince(@Param("stateBefore") CardState stateBefore, @Param("since") Instant since);

    /** Cards rated since {@code since}, with their word: the other cards of that word wait (spec 09). */
    @Query("""
            select distinct new com.learning.hanzitrainer.review.RatedCard(l.card.id, l.card.word.id)
            from ReviewLog l where l.reviewedAt >= :since""")
    List<RatedCard> findRatedSince(@Param("since") Instant since);

    Optional<ReviewLog> findFirstByCardIdOrderByReviewedAtDesc(long cardId);
}
