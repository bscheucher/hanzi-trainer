package com.learning.hanzitrainer.review;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** Spec 09, "Reference tests": the port reproduces py-fsrs at every step ({@code tools/fsrs-reference}). */
class FsrsSchedulerTests {

    private final FsrsScheduler unfuzzed = new FsrsScheduler(false, new Random(1));

    @Test
    void reproducesTheUpstreamReferenceCases() throws Exception {
        JsonNode reference;
        try (InputStream in = getClass().getResourceAsStream("/fsrs/reference.json")) {
            reference = JsonMapper.builder().build().readTree(in);
        }
        assertThat(reference.get("upstream").asString()).isEqualTo("py-fsrs 6.3.2");
        assertThat(reference.get("cases")).hasSize(5);

        for (JsonNode testCase : reference.get("cases")) {
            CardSnapshot card = CardSnapshot.newCard(Instant.parse(reference.get("start").asString()));
            int n = 0;
            for (JsonNode expected : testCase.get("steps")) {
                String at = "case " + testCase.get("ratings") + ", review " + ++n;
                Instant reviewedAt = Instant.parse(expected.get("reviewedAt").asString());
                assertThat(reviewedAt).as(at + ": each review happens at the previous due").isEqualTo(card.due());

                card = unfuzzed.review(card, Rating.of(expected.get("rating").asInt()), reviewedAt).card();

                assertThat(card.state()).as(at).isEqualTo(CardState.valueOf(expected.get("state").asString()));
                assertThat(card.step()).as(at).isEqualTo(expected.get("step").isNull() ? null
                        : expected.get("step").asInt());
                assertThat(card.stability()).as(at).isCloseTo(expected.get("stability").asDouble(), within(1e-4));
                assertThat(card.difficulty()).as(at).isCloseTo(expected.get("difficulty").asDouble(), within(1e-4));
                assertThat(Duration.between(Instant.parse(expected.get("due").asString()), card.due()).abs())
                        .as(at).isLessThanOrEqualTo(Duration.ofSeconds(1));
                assertThat(card.reps()).as(at).isEqualTo(n);
            }
        }
    }

    @Test
    void againOnAReviewCardIsALapseAndRelearns() {
        Instant now = Instant.parse("2026-01-01T08:00:00Z");
        ScheduledCard learned = unfuzzed.review(CardSnapshot.newCard(now), Rating.EASY, now);
        Instant due = learned.card().due();

        ScheduledCard lapsed = unfuzzed.review(learned.card(), Rating.AGAIN, due);

        assertThat(lapsed.card().state()).isEqualTo(CardState.RELEARNING);
        assertThat(lapsed.card().step()).isZero();
        assertThat(lapsed.card().lapses()).isEqualTo(1);
        assertThat(lapsed.card().due()).isEqualTo(due.plus(Duration.ofMinutes(10)));
        assertThat(lapsed.elapsedDays()).isEqualTo(8);
        assertThat(lapsed.scheduledDays()).isZero();
        assertThat(learned.elapsedDays()).isZero(); // a NEW card
        assertThat(learned.scheduledDays()).isEqualTo(8);
    }

    @Test
    void fuzzIsDeterministicPerRandomAndStaysNearTheInterval() {
        FsrsScheduler fuzzed = new FsrsScheduler(true, new Random(1));
        Instant now = Instant.parse("2026-01-01T08:00:00Z");
        CardSnapshot card = unfuzzed.review(CardSnapshot.newCard(now), Rating.EASY, now).card(); // 8 days
        Instant due = card.due();
        long unfuzzedDays = unfuzzed.review(card, Rating.EASY, due).scheduledDays();

        for (int seed = 0; seed < 50; seed++) {
            long a = fuzzed.review(card, Rating.EASY, due, new Random(seed)).scheduledDays();
            long b = fuzzed.review(card, Rating.EASY, due, new Random(seed)).scheduledDays();
            assertThat(a).isEqualTo(b);
            assertThat(a).isBetween(Math.round(unfuzzedDays * 0.9) - 2, Math.round(unfuzzedDays * 1.1) + 2);
        }
        assertThat(unfuzzedDays).isEqualTo(66); // 2026-01-09 -> 2026-03-16 in the [4, 4, 4] reference case
    }

    @Test
    void ratingValuesAreOneToFour() {
        assertThat(Rating.of(1)).isEqualTo(Rating.AGAIN);
        assertThat(Rating.of(4)).isEqualTo(Rating.EASY);
        assertThatThrownBy(() -> Rating.of(5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
