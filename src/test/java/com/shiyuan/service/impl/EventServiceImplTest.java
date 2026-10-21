package com.shiyuan.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shiyuan.dao.entity.db.Player;
import com.shiyuan.dao.entity.db.PlayerScore;
import com.shiyuan.dao.repository.EventPhotoRepository;
import com.shiyuan.dao.repository.EventPlayerRepository;
import com.shiyuan.dao.repository.EventRepository;
import com.shiyuan.dao.repository.PlayerScoreRepository;
import com.shiyuan.dao.repository.RewardRepository;
import com.shiyuan.service.EventNotFoundException;

@ExtendWith(MockitoExtension.class)
class EventServiceImplTest {

    @Mock
    PlayerScoreRepository playerScoreRepository;

    @Mock
    EventRepository eventRepository;

    @Mock
    EventPlayerRepository eventPlayerRepository;

    @Mock
    RewardRepository rewardRepository;

    @Mock
    EventPhotoRepository eventPhotoRepository;

    @InjectMocks
    EventServiceImpl eventService;

    // ── deleteEventById: children that Event does not cascade ────────────────
    //
    // Event maps only teeList (cascade ALL + orphanRemoval), so tees and their
    // player_score rows are handled by JPA. event_player, reward and event_photo all
    // carry an FK to event and are unmapped — MySQL rejects the parent delete with
    // error 1451 unless they are cleared first.

    @Test
    void deleteEventById_clearsUncascadedChildrenBeforeDeletingTheEvent() {
        when(eventRepository.existsById(1152L)).thenReturn(true);

        eventService.deleteEventById("1152");

        InOrder order = inOrder(eventPlayerRepository, rewardRepository,
                eventPhotoRepository, eventRepository);
        order.verify(eventPlayerRepository).deleteByEventId(1152L);
        order.verify(rewardRepository).deleteByEventId(1152L);
        order.verify(eventPhotoRepository).deleteByEventId(1152L);
        order.verify(eventRepository).deleteById(1152L);
    }

    @Test
    void deleteEventById_rejectsAnEventThatDoesNotExist() {
        when(eventRepository.existsById(99999L)).thenReturn(false);

        assertThrows(EventNotFoundException.class, () -> eventService.deleteEventById("99999"));

        verify(eventRepository, never()).deleteById(anyLong());
        verify(eventPlayerRepository, never()).deleteByEventId(anyLong());
    }

    private PlayerScore scoreFor(Player player) {
        PlayerScore ps = new PlayerScore();
        ps.setPlayer(player);
        ps.setEntryPGCHandicap(0.0); // onboarding default that the fix must overwrite
        return ps;
    }

    private Player playerWithHandicap(Double pgcHandicap) {
        Player p = new Player();
        p.setPgcHandicap(pgcHandicap);
        return p;
    }

    @Test
    void copyPgcHandicapToScores_copiesHandicapFromPlayer() {
        PlayerScore ps1 = scoreFor(playerWithHandicap(12.5));
        PlayerScore ps2 = scoreFor(playerWithHandicap(3.0));
        when(playerScoreRepository.getPlayerScoreForEvent(1L)).thenReturn(Arrays.asList(ps1, ps2));

        eventService.copyPgcHandicapToScores(1L);

        assertEquals(12.5, ps1.getEntryPGCHandicap());
        assertEquals(3.0, ps2.getEntryPGCHandicap());
        verify(playerScoreRepository).saveAll(Arrays.asList(ps1, ps2));
    }

    @Test
    void copyPgcHandicapToScores_copiesNullHandicapAsIs() {
        PlayerScore ps = scoreFor(playerWithHandicap(null));
        when(playerScoreRepository.getPlayerScoreForEvent(2L)).thenReturn(List.of(ps));

        eventService.copyPgcHandicapToScores(2L);

        assertNull(ps.getEntryPGCHandicap());
        verify(playerScoreRepository).saveAll(List.of(ps));
    }

    @Test
    void copyPgcHandicapToScores_skipsScoreWithNullPlayerWithoutError() {
        PlayerScore orphan = scoreFor(null);
        when(playerScoreRepository.getPlayerScoreForEvent(3L)).thenReturn(List.of(orphan));

        eventService.copyPgcHandicapToScores(3L); // must not throw NPE

        assertEquals(0.0, orphan.getEntryPGCHandicap()); // untouched
        verify(playerScoreRepository).saveAll(List.of(orphan));
    }
}
