package com.shiyuan.service;

/**
 * Maintains player.pgc_handicap as a rolling mean of recent rounds (STEP43).
 *
 * Recalculation happens when an event is played out, not when scores are uploaded, so a
 * corrected score can be re-applied without an index having already moved on it.
 */
public interface HandicapService {

    /** Recalculate every player holding a score row in this event. Returns players updated. */
    int recalculateForEvent(Long eventId);

    /** Recalculate every active player. Returns players updated. */
    int recalculateAll();
}
