package com.shiyuan.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import com.shiyuan.dao.entity.db.Player;
import com.shiyuan.dao.repository.PlayerRepository;

/**
 * The players grid needs inactive players too, so it can offer an "All users" view — but
 * the donation and reward pickers must keep getting active players only. Hence the opt-in.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlayerServiceImplTest {

    @Mock
    PlayerRepository playerRepository;

    @InjectMocks
    PlayerServiceImpl playerService;

    private Player player(long id, boolean active) {
        Player p = new Player();
        p.setId(id);
        p.setIsActive(active);
        return p;
    }

    @Test
    void byDefaultOnlyActivePlayersAreReturned() {
        when(playerRepository.findByIsActive(true)).thenReturn(List.of(player(1L, true)));

        List<Player> result = playerService.getAllPlayers(false);

        assertEquals(1, result.size());
        verify(playerRepository).findByIsActive(true);
        verify(playerRepository, never()).findAll();
    }

    @Test
    void inactivePlayersAreIncludedOnlyWhenAskedFor() {
        when(playerRepository.findAll())
                .thenReturn(List.of(player(1L, true), player(2L, false)));

        List<Player> result = playerService.getAllPlayers(true);

        assertEquals(2, result.size(), "both the active and the inactive player");
        verify(playerRepository).findAll();
        verify(playerRepository, never()).findByIsActive(true);
    }

    @Test
    void theNoArgCallStillMeansActiveOnly() {
        // Existing callers must not start seeing inactive players.
        when(playerRepository.findByIsActive(true)).thenReturn(List.of(player(1L, true)));

        assertEquals(1, playerService.getAllPlayers().size());
        verify(playerRepository, never()).findAll();
    }
}
