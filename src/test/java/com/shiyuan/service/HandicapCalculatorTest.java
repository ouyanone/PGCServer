package com.shiyuan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The USGA Score Differential: (gross - courseRating) * 113 / slope, to one decimal.
 *
 * The first two cases are the point of STEP42 — the same gross score yields a different
 * differential on a different course. Before the fix every round used 126 / 73.4.
 */
class HandicapCalculatorTest {

    @Test
    void scoreDifferential_usesTheCoursesOwnRatingAndSlope() {
        // Forsgate Banks: 134 / 70.2 -> raw 16.69701
        assertEquals(16.7, HandicapCalculator.scoreDifferential(90, 70.2, 134), 0.0001);
    }

    @Test
    void scoreDifferential_matchesTheFormerlyHardcodedCourse() {
        // Charleston Springs South: 126 / 73.4 -> raw 14.88730
        assertEquals(14.9, HandicapCalculator.scoreDifferential(90, 73.4, 126), 0.0001);
    }

    @Test
    void scoreDifferential_isNegativeWhenGrossBeatsTheCourseRating() {
        double differential = HandicapCalculator.scoreDifferential(68, 70.2, 134);
        assertTrue(differential < 0, "expected a negative differential but got " + differential);
    }

    @Test
    void scoreDifferential_roundsUpToTheNearestTenth() {
        // The Architects: 127 / 70.4 -> raw 15.65984
        assertEquals(15.7, HandicapCalculator.scoreDifferential(88, 70.4, 127), 0.0001);
    }

    @Test
    void scoreDifferential_roundsDownToTheNearestTenth() {
        // Forsgate Palmer: 134 / 69.7 -> raw 12.90224
        assertEquals(12.9, HandicapCalculator.scoreDifferential(85, 69.7, 134), 0.0001);
    }

    @Test
    void scoreDifferential_rejectsANonPositiveSlope() {
        assertThrows(IllegalArgumentException.class,
                () -> HandicapCalculator.scoreDifferential(90, 70.2, 0));
    }

    // ── STEP43: rolling handicap from the last 5 rounds ──────────────────────

    @Test
    void rollingHandicap_averagesFiveRounds() {
        // (16.7 + 14.9 + 15.7 + 12.9 + 18.2) / 5 = 15.68
        assertEquals(15.68,
                HandicapCalculator.rollingHandicap(List.of(16.7, 14.9, 15.7, 12.9, 18.2)), 0.0001);
    }

    @Test
    void rollingHandicap_countsOnlyTheNewestFiveWhenMoreExist() {
        // The two trailing (older) rounds must not drag the average up.
        assertEquals(15.68,
                HandicapCalculator.rollingHandicap(
                        List.of(16.7, 14.9, 15.7, 12.9, 18.2, 40.0, 41.0)), 0.0001);
    }

    @Test
    void rollingHandicap_averagesWhateverExistsWhenFewerThanFive() {
        // (16.7 + 14.9) / 2 = 15.80 — the common case: most players have under 5 rounds
        assertEquals(15.80, HandicapCalculator.rollingHandicap(List.of(16.7, 14.9)), 0.0001);
    }

    @Test
    void rollingHandicap_handlesASingleRound() {
        assertEquals(16.70, HandicapCalculator.rollingHandicap(List.of(16.7)), 0.0001);
    }

    @Test
    void rollingHandicap_includesNegativeDifferentials() {
        // (16.7 + -1.9) / 2 = 7.40 — a round under the course rating still counts
        assertEquals(7.40, HandicapCalculator.rollingHandicap(List.of(16.7, -1.9)), 0.0001);
    }

    @Test
    void rollingHandicap_roundsToTwoDecimals() {
        // (10.0 + 10.0 + 10.1) / 3 = 10.0333... -> 10.03
        assertEquals(10.03,
                HandicapCalculator.rollingHandicap(List.of(10.0, 10.0, 10.1)), 0.0001);
    }

    @Test
    void rollingHandicap_rejectsNoRounds() {
        assertThrows(IllegalArgumentException.class,
                () -> HandicapCalculator.rollingHandicap(List.of()));
    }

    @Test
    void rollingHandicap_rejectsANullList() {
        assertThrows(IllegalArgumentException.class,
                () -> HandicapCalculator.rollingHandicap(null));
    }
}
