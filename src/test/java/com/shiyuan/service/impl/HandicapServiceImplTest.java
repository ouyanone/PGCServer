package com.shiyuan.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

import com.shiyuan.dao.entity.db.Player;
import com.shiyuan.dao.entity.db.PlayerScore;
import com.shiyuan.dao.repository.PlayerRepository;
import com.shiyuan.dao.repository.PlayerScoreRepository;

/**
 * STEP43: a player's PGC handicap is the mean of their most recent rounds, recalculated
 * when an event finishes rather than on every score upload.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HandicapServiceImplTest {

    private static final long EVENT_ID = 1152L;

    @Mock
    PlayerScoreRepository playerScoreRepository;

    @Mock
    PlayerRepository playerRepository;

    @InjectMocks
    HandicapServiceImpl handicapService;

    private Player player(long id, Double currentHandicap) {
        Player p = new Player();
        p.setId(id);
        p.setPgcHandicap(currentHandicap);
        return p;
    }

    private PlayerScore scoreRowFor(Player p) {
        PlayerScore ps = new PlayerScore();
        ps.setPlayer(p);
        return ps;
    }

    private void givenRounds(Player p, List<Double> differentials) {
        when(playerScoreRepository.findRecentDifferentials(eq(p.getId()), anyList(), any(Pageable.class)))
                .thenReturn(differentials);
    }

    // ── positive ─────────────────────────────────────────────────────────────

    @Test
    void recalculateForEvent_setsHandicapToTheMeanOfRecentRounds() {
        Player p = player(2076L, 6.82);
        givenRounds(p, List.of(16.7, 14.9, 15.7, 12.9, 18.2));
        when(playerScoreRepository.getPlayerScoreForEvent(EVENT_ID)).thenReturn(List.of(scoreRowFor(p)));

        int updated = handicapService.recalculateForEvent(EVENT_ID);

        assertEquals(15.68, p.getPgcHandicap(), 0.0001);
        assertEquals(1, updated);
    }

    @Test
    void recalculateForEvent_averagesWhateverExistsWhenFewerThanFiveRounds() {
        Player p = player(2076L, 6.82);
        givenRounds(p, List.of(16.7, 14.9));
        when(playerScoreRepository.getPlayerScoreForEvent(EVENT_ID)).thenReturn(List.of(scoreRowFor(p)));

        handicapService.recalculateForEvent(EVENT_ID);

        assertEquals(15.80, p.getPgcHandicap(), 0.0001);
    }

    @Test
    void recalculateForEvent_updatesEveryPlayerInTheEvent() {
        Player first = player(1L, 5.0);
        Player second = player(2L, 9.0);
        givenRounds(first, List.of(10.0));
        givenRounds(second, List.of(20.0));
        when(playerScoreRepository.getPlayerScoreForEvent(EVENT_ID))
                .thenReturn(List.of(scoreRowFor(first), scoreRowFor(second)));

        int updated = handicapService.recalculateForEvent(EVENT_ID);

        assertEquals(10.00, first.getPgcHandicap(), 0.0001);
        assertEquals(20.00, second.getPgcHandicap(), 0.0001);
        assertEquals(2, updated);
    }

    @Test
    void recalculateAll_coversEveryActivePlayer() {
        Player p = player(2076L, 6.82);
        givenRounds(p, List.of(12.0, 14.0));
        when(playerRepository.findByIsActiveTrueOrderByPgcPointsDesc()).thenReturn(List.of(p));

        int updated = handicapService.recalculateAll();

        assertEquals(13.00, p.getPgcHandicap(), 0.0001);
        assertEquals(1, updated);
    }

    // ── negative ─────────────────────────────────────────────────────────────

    @Test
    void recalculate_leavesAPlayerWithNoQualifyingRoundsUntouched() {
        Player p = player(2076L, 6.82);
        givenRounds(p, List.of());
        when(playerScoreRepository.getPlayerScoreForEvent(EVENT_ID)).thenReturn(List.of(scoreRowFor(p)));

        int updated = handicapService.recalculateForEvent(EVENT_ID);

        assertEquals(6.82, p.getPgcHandicap(), 0.0001, "existing handicap must be preserved");
        assertEquals(0, updated);
        verify(playerRepository, never()).save(any());
    }

    @Test
    void recalculate_asksOnlyForFinishedAndClosedRounds() {
        Player p = player(2076L, 6.82);
        givenRounds(p, List.of(10.0));
        when(playerScoreRepository.getPlayerScoreForEvent(EVENT_ID)).thenReturn(List.of(scoreRowFor(p)));

        handicapService.recalculateForEvent(EVENT_ID);

        // Archiving a finished event must not silently drop it from handicap history.
        verify(playerScoreRepository).findRecentDifferentials(
                eq(2076L), eq(List.of("FINISHED", "CLOSED")), any(Pageable.class));
    }
}
