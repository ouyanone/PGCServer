package com.shiyuan.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.shiyuan.dao.entity.db.Course;
import com.shiyuan.dao.entity.db.Event;
import com.shiyuan.dao.entity.db.Player;
import com.shiyuan.dao.entity.db.PlayerScore;
import com.shiyuan.dao.entity.db.Tee;
import com.shiyuan.dao.repository.PlayerRepository;
import com.shiyuan.dao.repository.PlayerScoreRepository;
import com.shiyuan.dto.PlayerScoreHistory;
import com.shiyuan.service.PlayerNotFoundException;

/**
 * STEP45. Hole scores are stored as offsets from par (0 = par, -1 = birdie, +2 = double), so the
 * service's main job is turning them back into real strokes using the course's own per-hole par.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScoreHistoryServiceImplTest {

    private static final long PLAYER_ID = 1000L;

    /** Links At Lighthouse Sound, the real course from the 2026-09-19 round. */
    private static final int[] LIGHTHOUSE_PAR = {4,5,3,4,3,4,5,4,4, 4,4,4,4,4,4,4,4,4};

    /** Yan Ou's real offsets for that round: gross 86 against par 72. */
    private static final int[] REAL_OFFSETS = {1,1,0,1,1,0,3,1,2, 1,0,1,0,-1,2,0,2,-1};

    @Mock
    PlayerScoreRepository playerScoreRepository;

    @Mock
    PlayerRepository playerRepository;

    @InjectMocks
    ScoreHistoryServiceImpl service;

    // ── fixtures ─────────────────────────────────────────────────────────────

    private Player player() {
        Player p = new Player();
        p.setId(PLAYER_ID);
        p.setfName("Yan");
        p.setlName("Ou");
        p.setPgcHandicap(12.0);
        p.setHandicap(14.2);
        return p;
    }

    private Course course(int[] pars) {
        Course c = new Course();
        c.setClubName("Links At Lighthouse Sound");
        c.setCourseName("Links At Lighthouse Sound");
        if (pars != null) {
            c.setParNumber(java.util.Arrays.stream(pars).sum());
            c.setHole1(pars[0]);   c.setHole2(pars[1]);   c.setHole3(pars[2]);
            c.setHole4(pars[3]);   c.setHole5(pars[4]);   c.setHole6(pars[5]);
            c.setHole7(pars[6]);   c.setHole8(pars[7]);   c.setHole9(pars[8]);
            c.setHole10(pars[9]);  c.setHole11(pars[10]); c.setHole12(pars[11]);
            c.setHole13(pars[12]); c.setHole14(pars[13]); c.setHole15(pars[14]);
            c.setHole16(pars[15]); c.setHole17(pars[16]); c.setHole18(pars[17]);
        }
        return c;
    }

    private PlayerScore round(String date, int gross, int[] offsets, Course course) {
        Event e = new Event();
        e.setId(1155L);
        e.setEventName("2026 PGC Maryland Crab Cup Round 2");
        e.setEventDate(LocalDate.parse(date));
        e.setCourse(course);

        Tee t = new Tee();
        t.setEvent(e);

        PlayerScore ps = new PlayerScore();
        ps.setPlayer(player());
        ps.setTee(t);
        ps.setScore(gross);
        ps.setNetScore(65.21);
        ps.setCurrentRoundHandicap(12.0);
        ps.setGamePoint(2);
        ps.setHole1(offsets[0]);   ps.setHole2(offsets[1]);   ps.setHole3(offsets[2]);
        ps.setHole4(offsets[3]);   ps.setHole5(offsets[4]);   ps.setHole6(offsets[5]);
        ps.setHole7(offsets[6]);   ps.setHole8(offsets[7]);   ps.setHole9(offsets[8]);
        ps.setHole10(offsets[9]);  ps.setHole11(offsets[10]); ps.setHole12(offsets[11]);
        ps.setHole13(offsets[12]); ps.setHole14(offsets[13]); ps.setHole15(offsets[14]);
        ps.setHole16(offsets[15]); ps.setHole17(offsets[16]); ps.setHole18(offsets[17]);
        return ps;
    }

    private void givenRounds(List<PlayerScore> rounds) {
        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(player()));
        when(playerScoreRepository.findHistoryForPlayer(eq(PLAYER_ID), anyList())).thenReturn(rounds);
    }

    // ── positive ─────────────────────────────────────────────────────────────

    @Test
    void convertsStoredOffsetsIntoRealStrokes() {
        givenRounds(List.of(round("2026-09-19", 86, REAL_OFFSETS, course(LIGHTHOUSE_PAR))));

        PlayerScoreHistory.Round r = service.getHistory(PLAYER_ID).getRounds().get(0);

        // hole 1: par 4, offset +1 -> 5.  hole 14: par 4, offset -1 -> 3.
        assertEquals(5, r.getHoles()[0]);
        assertEquals(3, r.getHoles()[13]);
        assertEquals(4, r.getPars()[0]);
    }

    @Test
    void sumsTheNinesToMatchTheStoredGross() {
        givenRounds(List.of(round("2026-09-19", 86, REAL_OFFSETS, course(LIGHTHOUSE_PAR))));

        PlayerScoreHistory.Round r = service.getHistory(PLAYER_ID).getRounds().get(0);

        assertEquals(46, r.getOutStrokes(), "front nine");
        assertEquals(40, r.getInStrokes(), "back nine");
        assertEquals(86, r.getOutStrokes() + r.getInStrokes(), "must reconcile with stored gross");
        assertEquals(36, r.getOutPar());
        assertEquals(36, r.getInPar());
    }

    @Test
    void countsEaglesBirdiesAndPars() {
        givenRounds(List.of(round("2026-09-19", 86, REAL_OFFSETS, course(LIGHTHOUSE_PAR))));

        PlayerScoreHistory.Round r = service.getHistory(PLAYER_ID).getRounds().get(0);

        assertEquals(0, r.getEagles());
        assertEquals(2, r.getBirdies(), "two holes at -1");
        assertEquals(5, r.getParsMade(), "five holes at level par");
    }

    @Test
    void summarisesBestAverageAndLatest() {
        Course c = course(LIGHTHOUSE_PAR);
        givenRounds(List.of(
                round("2026-09-19", 86, REAL_OFFSETS, c),
                round("2026-09-18", 90, REAL_OFFSETS, c),
                round("2026-07-17", 94, REAL_OFFSETS, c)));

        PlayerScoreHistory h = service.getHistory(PLAYER_ID);

        assertEquals(3, h.getRoundsPlayed());
        assertEquals(86, h.getBestGross(), "lowest gross");
        assertEquals(86, h.getLatestGross(), "first round is the newest");
        assertEquals(90.0, h.getAverageGross(), 0.01);
    }

    @Test
    void carriesThePlayerAndCourseIdentity() {
        givenRounds(List.of(round("2026-09-19", 86, REAL_OFFSETS, course(LIGHTHOUSE_PAR))));

        PlayerScoreHistory h = service.getHistory(PLAYER_ID);

        assertEquals("Yan", h.getFirstName());
        assertEquals("Ou", h.getLastName());
        assertEquals(12.0, h.getPgcHandicap(), 0.001);
        assertEquals("Links At Lighthouse Sound", h.getRounds().get(0).getClubName());
        assertEquals(72, h.getRounds().get(0).getCoursePar());
    }

    @Test
    void asksOnlyForRoundsFromPlayedEvents() {
        givenRounds(List.of());

        service.getHistory(PLAYER_ID);

        // Archived events still count as played, matching the handicap rule.
        verify(playerScoreRepository).findHistoryForPlayer(PLAYER_ID, List.of("FINISHED", "CLOSED"));
    }

    // ── negative ─────────────────────────────────────────────────────────────

    @Test
    void aPlayerWithNoRoundsGetsAnEmptyHistoryNotAnError() {
        givenRounds(List.of());

        PlayerScoreHistory h = service.getHistory(PLAYER_ID);

        assertTrue(h.getRounds().isEmpty());
        assertEquals(0, h.getRoundsPlayed());
        assertNull(h.getBestGross());
        assertNull(h.getAverageGross());
        assertEquals("Yan", h.getFirstName(), "player identity still present");
    }

    @Test
    void unknownPlayerIsRejected() {
        when(playerRepository.findById(99999L)).thenReturn(Optional.empty());

        assertThrows(PlayerNotFoundException.class, () -> service.getHistory(99999L));
    }

    @Test
    void aRoundWhoseEventHasNoCourseFallsBackToOffsets() {
        givenRounds(List.of(round("2026-09-19", 86, REAL_OFFSETS, null)));

        PlayerScoreHistory.Round r = service.getHistory(PLAYER_ID).getRounds().get(0);

        // One badly configured course must not blank out the member's whole history.
        assertEquals(1, r.getHoles()[0], "offset shown as-is when par is unknown");
        assertNull(r.getPars(), "no par row to show");
        assertNull(r.getCoursePar());
        assertEquals(86, r.getGross(), "the stored gross is still reported");
    }

    @Test
    void aCourseMissingPerHoleParAlsoFallsBack() {
        givenRounds(List.of(round("2026-09-19", 86, REAL_OFFSETS, course(null))));

        PlayerScoreHistory.Round r = service.getHistory(PLAYER_ID).getRounds().get(0);

        assertEquals(1, r.getHoles()[0]);
        assertNull(r.getPars());
    }
}
