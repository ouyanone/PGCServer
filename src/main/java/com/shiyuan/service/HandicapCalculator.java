package com.shiyuan.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * USGA Score Differential for a single round.
 *
 * Deliberately has no fallback values: callers must supply the rating and slope of the
 * course actually played. Defaulting them is what let every event be scored against one
 * hardcoded course (STEP42).
 */
public final class HandicapCalculator {

    /** Slope rating of a course of standard difficulty. */
    private static final double STANDARD_SLOPE = 113.0;

    /** Number of most-recent rounds that make up a player's PGC handicap. */
    public static final int ROUNDS_COUNTED = 5;

    private HandicapCalculator() {
    }

    /**
     * @param grossScore   the player's gross score for the round
     * @param courseRating rating of the course played
     * @param slope        slope rating of the course played, must be positive
     * @return (gross - rating) * 113 / slope, rounded to the nearest tenth. May be negative
     *         when the player beats the course rating.
     */
    public static double scoreDifferential(int grossScore, double courseRating, int slope) {
        if (slope <= 0) {
            throw new IllegalArgumentException("Slope must be positive but was " + slope);
        }
        double differential = (grossScore - courseRating) * STANDARD_SLOPE / slope;
        return BigDecimal.valueOf(differential)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * The PGC handicap: the mean of a player's most recent rounds.
     *
     * @param differentialsNewestFirst round differentials ordered most-recent-first; at most
     *                                 the first {@link #ROUNDS_COUNTED} are used. Must not be
     *                                 empty — a player with no rounds keeps their existing
     *                                 handicap, which is the caller's decision to make.
     * @return the mean, rounded to 2 decimals to match player.pgc_handicap decimal(4,2)
     */
    public static double rollingHandicap(List<Double> differentialsNewestFirst) {
        if (differentialsNewestFirst == null || differentialsNewestFirst.isEmpty()) {
            throw new IllegalArgumentException("At least one round is required to compute a handicap");
        }
        double mean = differentialsNewestFirst.stream()
                .limit(ROUNDS_COUNTED)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElseThrow();
        return BigDecimal.valueOf(mean)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
