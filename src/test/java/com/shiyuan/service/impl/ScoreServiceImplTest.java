package com.shiyuan.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.shiyuan.dao.entity.PlayerGolfScore;
import com.shiyuan.dao.entity.db.Course;
import com.shiyuan.dao.entity.db.Event;
import com.shiyuan.dao.entity.db.Player;
import com.shiyuan.dao.entity.db.PlayerScore;
import com.shiyuan.dao.repository.EventRepository;
import com.shiyuan.dao.repository.PlayerScoreRepository;
import com.shiyuan.service.InvalidCourseDataException;

/**
 * STEP42: the round handicap must come from the event's own course, and a submission
 * against an event with unusable course data must be refused rather than silently
 * scored against a default.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScoreServiceImplTest {

    private static final long EVENT_ID = 42L;

    @Mock
    PlayerScoreRepository playerScoreRepository;

    @Mock
    EventRepository eventRepository;

    @InjectMocks
    ScoreServiceImpl scoreService;

    private PlayerScore rosterEntry;

    @BeforeEach
    void setUp() {
        rosterEntry = playerScore("Bob", "Smith", 10.0);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Course course(Double rating, Integer slope) {
        Course c = new Course();
        c.setClubName("Forsgate Country Club");
        c.setCourseName("Banks");
        c.setRating(rating);
        c.setSlop(slope);
        return c;
    }

    private Event eventWith(Course course) {
        Event e = new Event();
        e.setId(EVENT_ID);
        e.setEventName("2026 Test Game");
        e.setCourse(course);
        return e;
    }

    private PlayerScore playerScore(String first, String last, Double pgcHandicap) {
        Player p = new Player();
        p.setfName(first);
        p.setlName(last);
        p.setPgcHandicap(pgcHandicap);

        PlayerScore ps = new PlayerScore();
        ps.setPlayer(p);
        ps.setEntryPGCHandicap(pgcHandicap);
        return ps;
    }

    private PlayerGolfScore upload(String name, String gross) {
        PlayerGolfScore row = new PlayerGolfScore();
        row.setName(name);
        row.setScore(gross);
        return row;
    }

    private void givenEvent(Event event) {
        when(eventRepository.findById(EVENT_ID)).thenReturn(event);
        when(playerScoreRepository.getPlayerScoreForEvent(EVENT_ID)).thenReturn(List.of(rosterEntry));
    }

    // ── positive ─────────────────────────────────────────────────────────────

    @Test
    void submitPlayerScore_computesTheDifferentialFromTheEventsCourse() {
        givenEvent(eventWith(course(70.2, 134)));

        scoreService.submitPlayerScore(List.of(upload("Bob Smith", "90")), EVENT_ID);

        // (90 - 70.2) * 113 / 134 = 16.69701 -> 16.7, NOT the old hardcoded 126/73.4 answer
        assertEquals(16.7, rosterEntry.getCurrentRoundHandicap(), 0.0001);
        assertEquals(90, rosterEntry.getScore());
    }

    @Test
    void submitPlayerScore_stillComputesNetScoreFromTheEntryHandicap() {
        givenEvent(eventWith(course(70.2, 134)));

        scoreService.submitPlayerScore(List.of(upload("Bob Smith", "90")), EVENT_ID);

        assertEquals(80.0, rosterEntry.getNetScore(), 0.0001); // 90 - entry handicap 10.0
    }

    @Test
    void submitPlayerScore_skipsRowsThatMatchNoPlayerOnTheRoster() {
        givenEvent(eventWith(course(70.2, 134)));

        scoreService.submitPlayerScore(
                List.of(upload("Nobody Here", "80"), upload("Bob Smith", "90")), EVENT_ID);

        assertEquals(16.7, rosterEntry.getCurrentRoundHandicap(), 0.0001);
    }

    @Test
    void submitPlayerScore_handlesAPlayerWithNoPgcHandicapYet() {
        rosterEntry = playerScore("Bob", "Smith", null);
        givenEvent(eventWith(course(70.2, 134)));

        scoreService.submitPlayerScore(List.of(upload("Bob Smith", "90")), EVENT_ID);

        assertEquals(16.7, rosterEntry.getCurrentRoundHandicap(), 0.0001);
        assertNull(rosterEntry.getNetScore(), "no entry handicap means no net score");
    }

    /**
     * STEP43 moved handicap maintenance to HandicapService, triggered when the event
     * finishes. Uploading a score must no longer touch the player's index.
     */
    @Test
    void submitPlayerScore_leavesThePgcHandicapToTheHandicapService() {
        givenEvent(eventWith(course(70.2, 134)));

        scoreService.submitPlayerScore(List.of(upload("Bob Smith", "90")), EVENT_ID);

        assertEquals(16.7, rosterEntry.getCurrentRoundHandicap(), 0.0001);
        assertEquals(10.0, rosterEntry.getPlayer().getPgcHandicap(), 0.0001,
                "submission must not move the player's rolling handicap");
    }

    // ── negative ─────────────────────────────────────────────────────────────

    @Test
    void submitPlayerScore_refusesAnEventWithNoCourse() {
        givenEvent(eventWith(null));

        assertThrows(InvalidCourseDataException.class,
                () -> scoreService.submitPlayerScore(List.of(upload("Bob Smith", "90")), EVENT_ID));

        verify(playerScoreRepository, never()).save(any());
        assertNull(rosterEntry.getCurrentRoundHandicap());
    }

    @Test
    void submitPlayerScore_refusesACourseWithNoRating() {
        givenEvent(eventWith(course(null, 134)));

        assertThrows(InvalidCourseDataException.class,
                () -> scoreService.submitPlayerScore(List.of(upload("Bob Smith", "90")), EVENT_ID));

        verify(playerScoreRepository, never()).save(any());
    }

    @Test
    void submitPlayerScore_refusesACourseWithNoSlope() {
        givenEvent(eventWith(course(70.2, null)));

        assertThrows(InvalidCourseDataException.class,
                () -> scoreService.submitPlayerScore(List.of(upload("Bob Smith", "90")), EVENT_ID));

        verify(playerScoreRepository, never()).save(any());
    }

    @Test
    void submitPlayerScore_refusesACourseWithAZeroSlope() {
        givenEvent(eventWith(course(70.2, 0)));

        assertThrows(InvalidCourseDataException.class,
                () -> scoreService.submitPlayerScore(List.of(upload("Bob Smith", "90")), EVENT_ID));

        verify(playerScoreRepository, never()).save(any());
    }

    @Test
    void submitPlayerScore_refusesAnEventThatDoesNotExist() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(null);

        assertThrows(InvalidCourseDataException.class,
                () -> scoreService.submitPlayerScore(List.of(upload("Bob Smith", "90")), EVENT_ID));

        verify(playerScoreRepository, never()).save(any());
    }
}
