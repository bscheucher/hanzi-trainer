package com.learning.hanzitrainer.review;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;

/**
 * FSRS-6 scheduler, ported from py-fsrs 6.3.2 ({@code fsrs/scheduler.py}, PyPI {@code fsrs==6.3.2}) with its
 * defaults: {@link #DEFAULT_PARAMETERS}, desired retention 0.9, learning steps 1 min and 10 min, relearning step
 * 10 min, maximum interval 36500 days, fuzzing on. The formulas are copied from that file, not written from memory;
 * method names follow it ({@code _next_interval} is {@link #nextInterval}, and so on).
 *
 * <p>Differences from upstream, all outside the formulas:
 * <ul>
 *   <li>Upstream has no NEW state: a card starts in Learning at step 0 without stability. NEW maps to that card.</li>
 *   <li>Upstream cards have no {@code reps} / {@code lapses}; here every review counts a rep, and Again on a REVIEW
 *       card counts a lapse.</li>
 *   <li>Python's {@code round} rounds half to even, so {@link Math#rint} is used; {@code timedelta.days} floors.</li>
 *   <li>The fuzz {@link Random} can be passed per review, so a preview and the actual rating fuzz alike.</li>
 * </ul>
 * Reference cases generated with upstream: {@code tools/fsrs-reference}, checked by {@code FsrsSchedulerTests}.
 */
public final class FsrsScheduler implements Scheduler {

    static final double FSRS_DEFAULT_DECAY = 0.1542;
    static final double[] DEFAULT_PARAMETERS = {
            0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666, 0.796, 1.4835, 0.0614,
            0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, FSRS_DEFAULT_DECAY};
    static final double DESIRED_RETENTION = 0.9;
    static final List<Duration> LEARNING_STEPS = List.of(Duration.ofMinutes(1), Duration.ofMinutes(10));
    static final List<Duration> RELEARNING_STEPS = List.of(Duration.ofMinutes(10));
    static final int MAXIMUM_INTERVAL = 36500;

    private static final double STABILITY_MIN = 0.001;
    private static final double MIN_DIFFICULTY = 1.0;
    private static final double MAX_DIFFICULTY = 10.0;
    /** {@code FUZZ_RANGES}: start, end, factor. */
    private static final double[][] FUZZ_RANGES = {
            {2.5, 7.0, 0.15},
            {7.0, 20.0, 0.1},
            {20.0, Double.POSITIVE_INFINITY, 0.05}};

    private final double[] w = DEFAULT_PARAMETERS;
    private final double decay = -w[20];
    private final double factor = Math.pow(0.9, 1 / decay) - 1;
    private final boolean enableFuzzing;
    private final Random random;

    /** @param random used for fuzzing when {@link #review(CardSnapshot, Rating, Instant)} is called */
    public FsrsScheduler(boolean enableFuzzing, Random random) {
        this.enableFuzzing = enableFuzzing;
        this.random = random;
    }

    @Override
    public ScheduledCard review(CardSnapshot card, Rating rating, Instant now) {
        return review(card, rating, now, random);
    }

    /** As {@link #review(CardSnapshot, Rating, Instant)}, with the fuzz drawn from {@code fuzz}. */
    public ScheduledCard review(CardSnapshot snapshot, Rating rating, Instant now, Random fuzz) {
        // NEW is upstream's initial card: Learning, step 0, no stability or difficulty.
        CardState state = snapshot.state() == CardState.NEW ? CardState.LEARNING : snapshot.state();
        Integer step = snapshot.state() == CardState.NEW ? Integer.valueOf(0) : snapshot.step();
        Double stability = snapshot.stability();
        Double difficulty = snapshot.difficulty();
        Instant lastReview = snapshot.lastReview();

        Long daysSinceLastReview = lastReview == null ? null : days(Duration.between(lastReview, now));
        Duration nextInterval;

        switch (state) {
            case LEARNING -> {
                // update the card's stability and difficulty
                if (stability == null || difficulty == null) {
                    stability = initialStability(rating);
                    difficulty = initialDifficulty(rating, true);
                } else if (daysSinceLastReview != null && daysSinceLastReview < 1) {
                    stability = shortTermStability(stability, rating);
                    difficulty = nextDifficulty(difficulty, rating);
                } else {
                    stability = nextStability(difficulty, stability,
                            retrievability(stability, lastReview, now), rating);
                    difficulty = nextDifficulty(difficulty, rating);
                }

                // calculate the card's next interval
                if (LEARNING_STEPS.isEmpty() || (step >= LEARNING_STEPS.size() && rating != Rating.AGAIN)) {
                    state = CardState.REVIEW;
                    step = null;
                    nextInterval = Duration.ofDays(nextInterval(stability));
                } else {
                    switch (rating) {
                        case AGAIN -> {
                            step = 0;
                            nextInterval = LEARNING_STEPS.get(step);
                        }
                        case HARD -> {
                            // card step stays the same
                            if (step == 0 && LEARNING_STEPS.size() == 1) {
                                nextInterval = LEARNING_STEPS.getFirst().multipliedBy(3).dividedBy(2);
                            } else if (step == 0 && LEARNING_STEPS.size() >= 2) {
                                nextInterval = LEARNING_STEPS.get(0).plus(LEARNING_STEPS.get(1)).dividedBy(2);
                            } else {
                                nextInterval = LEARNING_STEPS.get(step);
                            }
                        }
                        case GOOD -> {
                            if (step + 1 == LEARNING_STEPS.size()) { // the last step
                                state = CardState.REVIEW;
                                step = null;
                                nextInterval = Duration.ofDays(nextInterval(stability));
                            } else {
                                step += 1;
                                nextInterval = LEARNING_STEPS.get(step);
                            }
                        }
                        case EASY -> {
                            state = CardState.REVIEW;
                            step = null;
                            nextInterval = Duration.ofDays(nextInterval(stability));
                        }
                        default -> throw new IllegalArgumentException("Unknown rating: " + rating);
                    }
                }
            }
            case REVIEW -> {
                // update the card's stability and difficulty
                if (daysSinceLastReview != null && daysSinceLastReview < 1) {
                    stability = shortTermStability(stability, rating);
                } else {
                    stability = nextStability(difficulty, stability,
                            retrievability(stability, lastReview, now), rating);
                }
                difficulty = nextDifficulty(difficulty, rating);

                // calculate the card's next interval
                if (rating == Rating.AGAIN) {
                    if (RELEARNING_STEPS.isEmpty()) {
                        nextInterval = Duration.ofDays(nextInterval(stability));
                    } else {
                        state = CardState.RELEARNING;
                        step = 0;
                        nextInterval = RELEARNING_STEPS.get(step);
                    }
                } else {
                    nextInterval = Duration.ofDays(nextInterval(stability));
                }
            }
            case RELEARNING -> {
                // update the card's stability and difficulty
                if (daysSinceLastReview != null && daysSinceLastReview < 1) {
                    stability = shortTermStability(stability, rating);
                    difficulty = nextDifficulty(difficulty, rating);
                } else {
                    stability = nextStability(difficulty, stability,
                            retrievability(stability, lastReview, now), rating);
                    difficulty = nextDifficulty(difficulty, rating);
                }

                // calculate the card's next interval
                if (RELEARNING_STEPS.isEmpty() || (step >= RELEARNING_STEPS.size() && rating != Rating.AGAIN)) {
                    state = CardState.REVIEW;
                    step = null;
                    nextInterval = Duration.ofDays(nextInterval(stability));
                } else {
                    switch (rating) {
                        case AGAIN -> {
                            step = 0;
                            nextInterval = RELEARNING_STEPS.get(step);
                        }
                        case HARD -> {
                            // card step stays the same
                            if (step == 0 && RELEARNING_STEPS.size() == 1) {
                                nextInterval = RELEARNING_STEPS.getFirst().multipliedBy(3).dividedBy(2);
                            } else if (step == 0 && RELEARNING_STEPS.size() >= 2) {
                                nextInterval = RELEARNING_STEPS.get(0).plus(RELEARNING_STEPS.get(1)).dividedBy(2);
                            } else {
                                nextInterval = RELEARNING_STEPS.get(step);
                            }
                        }
                        case GOOD -> {
                            if (step + 1 == RELEARNING_STEPS.size()) { // the last step
                                state = CardState.REVIEW;
                                step = null;
                                nextInterval = Duration.ofDays(nextInterval(stability));
                            } else {
                                step += 1;
                                nextInterval = RELEARNING_STEPS.get(step);
                            }
                        }
                        case EASY -> {
                            state = CardState.REVIEW;
                            step = null;
                            nextInterval = Duration.ofDays(nextInterval(stability));
                        }
                        default -> throw new IllegalArgumentException("Unknown rating: " + rating);
                    }
                }
            }
            default -> throw new IllegalArgumentException("Unknown card state: " + state);
        }

        if (enableFuzzing && state == CardState.REVIEW) {
            nextInterval = fuzzedInterval(nextInterval, fuzz);
        }

        Instant due = now.plus(nextInterval);
        boolean lapse = snapshot.state() == CardState.REVIEW && rating == Rating.AGAIN;
        CardSnapshot reviewed = new CardSnapshot(state, step, stability, difficulty, due, now, snapshot.reps() + 1,
                snapshot.lapses() + (lapse ? 1 : 0));
        return new ScheduledCard(reviewed, daysSinceLastReview == null ? 0 : Math.max(0, daysSinceLastReview),
                days(nextInterval));
    }

    /** {@code get_card_retrievability}. */
    private double retrievability(double stability, Instant lastReview, Instant now) {
        long elapsedDays = Math.max(0, days(Duration.between(lastReview, now)));
        return Math.pow(1 + factor * elapsedDays / stability, decay);
    }

    private static double clampDifficulty(double difficulty) {
        return Math.min(Math.max(difficulty, MIN_DIFFICULTY), MAX_DIFFICULTY);
    }

    private static double clampStability(double stability) {
        return Math.max(stability, STABILITY_MIN);
    }

    private double initialStability(Rating rating) {
        return clampStability(w[rating.value() - 1]);
    }

    private double initialDifficulty(Rating rating, boolean clamp) {
        double initialDifficulty = w[4] - Math.exp(w[5] * (rating.value() - 1)) + 1;
        return clamp ? clampDifficulty(initialDifficulty) : initialDifficulty;
    }

    /** {@code _next_interval}: whole days, at least 1, at most the maximum interval. */
    long nextInterval(double stability) {
        double nextInterval = (stability / factor) * (Math.pow(DESIRED_RETENTION, 1 / decay) - 1);
        long days = (long) Math.rint(nextInterval); // intervals are full days
        return Math.min(Math.max(days, 1), MAXIMUM_INTERVAL);
    }

    private double shortTermStability(double stability, Rating rating) {
        double increase = Math.exp(w[17] * (rating.value() - 3 + w[18])) * Math.pow(stability, -w[19]);
        if (rating != Rating.AGAIN) {
            increase = Math.max(increase, 1.0);
        }
        return clampStability(stability * increase);
    }

    private double nextDifficulty(double difficulty, Rating rating) {
        double arg1 = initialDifficulty(Rating.EASY, false);
        double deltaDifficulty = -(w[6] * (rating.value() - 3));
        // linear damping
        double arg2 = difficulty + (10.0 - difficulty) * deltaDifficulty / 9.0;
        // mean reversion
        double nextDifficulty = w[7] * arg1 + (1 - w[7]) * arg2;
        return clampDifficulty(nextDifficulty);
    }

    private double nextStability(double difficulty, double stability, double retrievability, Rating rating) {
        double nextStability = rating == Rating.AGAIN
                ? nextForgetStability(difficulty, stability, retrievability)
                : nextRecallStability(difficulty, stability, retrievability, rating);
        return clampStability(nextStability);
    }

    private double nextForgetStability(double difficulty, double stability, double retrievability) {
        double longTerm = w[11] * Math.pow(difficulty, -w[12]) * (Math.pow(stability + 1, w[13]) - 1)
                * Math.exp((1 - retrievability) * w[14]);
        double shortTerm = stability / Math.exp(w[17] * w[18]);
        return Math.min(longTerm, shortTerm);
    }

    private double nextRecallStability(double difficulty, double stability, double retrievability, Rating rating) {
        double hardPenalty = rating == Rating.HARD ? w[15] : 1;
        double easyBonus = rating == Rating.EASY ? w[16] : 1;
        return stability * (1 + Math.exp(w[8]) * (11 - difficulty) * Math.pow(stability, -w[9])
                * (Math.exp((1 - retrievability) * w[10]) - 1) * hardPenalty * easyBonus);
    }

    /** {@code _get_fuzzed_interval}. */
    private Duration fuzzedInterval(Duration interval, Random fuzz) {
        long intervalDays = days(interval);
        if (intervalDays < 2.5) { // fuzz is not applied to intervals less than 2.5
            return interval;
        }
        double delta = 1.0;
        for (double[] range : FUZZ_RANGES) {
            delta += range[2] * Math.max(Math.min((double) intervalDays, range[1]) - range[0], 0.0);
        }
        long minIvl = (long) Math.rint(intervalDays - delta);
        long maxIvl = (long) Math.rint(intervalDays + delta);
        // make sure the min_ivl and max_ivl fall into a valid range
        minIvl = Math.max(2, minIvl);
        maxIvl = Math.min(maxIvl, MAXIMUM_INTERVAL);
        minIvl = Math.min(minIvl, maxIvl);

        double fuzzedIntervalDays = fuzz.nextDouble() * (maxIvl - minIvl + 1) + minIvl;
        return Duration.ofDays(Math.min((long) Math.rint(fuzzedIntervalDays), MAXIMUM_INTERVAL));
    }

    /** Whole days of {@code duration}, rounded down like Python's {@code timedelta.days}. */
    private static long days(Duration duration) {
        return Math.floorDiv(duration.getSeconds(), 86_400);
    }
}
