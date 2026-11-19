package com.shiyuan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.LocalDate;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.shiyuan.dao.entity.db.Event;
import com.shiyuan.dao.entity.db.Player;
import com.shiyuan.service.GroupingService.Strategy;
import com.shiyuan.testdata.AdminTestData;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class GamePlanServiceTest {

    @Autowired GamePlanService service;
    @Autowired EntityManager em;
    AdminTestData data;

    @BeforeEach
    void setUp() {
        data = new AdminTestData(em);
    }

    private long tees(Event e) {
        return data.count("select count(t) from Tee t where t.event.id = ?1", e.getId());
    }

    private long scoreRows(Event e) {
        return data.count("select count(ps) from PlayerScore ps where ps.tee.event.id = ?1", e.getId());
    }

    @Test
    void generateGroups_refuseIfScored_conflictAndNothingDeleted() {
        Event e = data.event(LocalDate.of(2026, 12, 12));
        List<Player> ps = data.players(4);
        data.roster(e, ps);
        data.scoreRow(e, ps.get(0), 82);
        data.flushAndClear();

        AdminRequestException ex = catchThrowableOfType(AdminRequestException.class,
                () -> service.generateGroups(e.getId(), Strategy.PGC_BEST_WITH_WORST, true));
        assertThat(ex.status()).isEqualTo(409);
        data.flushAndClear();
        assertThat(tees(e)).isEqualTo(1);
        assertThat(scoreRows(e)).isEqualTo(1);
    }

    @Test
    void generateGroups_refuseIfScored_allowsZeroScores() {
        Event e = data.event(LocalDate.of(2026, 12, 12));
        List<Player> ps = data.players(4);
        data.roster(e, ps);
        data.scoreRow(e, ps.get(0), 0);
        data.flushAndClear();

        assertThat(service.generateGroups(e.getId(), Strategy.PGC_BEST_WITH_WORST, true)).hasSize(1);
        data.flushAndClear();
        assertThat(scoreRows(e)).isEqualTo(4);
    }

    @Test
    void generateGroups_emptyRoster_badRequest() {
        Event e = data.event(LocalDate.of(2026, 12, 12));
        data.flushAndClear();
        AdminRequestException ex = catchThrowableOfType(AdminRequestException.class,
                () -> service.generateGroups(e.getId(), Strategy.GHIN_BEST_TO_WORST, true));
        assertThat(ex.status()).isEqualTo(400);
    }

    @Test
    void assignPlayer_ensureOnRoster_addsEventPlayer() {
        Event e = data.event(LocalDate.of(2026, 12, 12));
        Player p = data.players(1).get(0);
        data.flushAndClear();

        GamePlanService.TeeRef tee = service.assignPlayer(e.getId(), p.getId(), true);
        assertThat(tee.teeName()).isEqualTo("Tee 1");
        data.flushAndClear();
        assertThat(data.count("select count(ep) from EventPlayer ep where ep.event.id = ?1", e.getId())).isEqualTo(1);
    }

    @Test
    void assignPlayer_withoutFlag_leavesRosterAlone() {
        Event e = data.event(LocalDate.of(2026, 12, 12));
        Player p = data.players(1).get(0);
        data.flushAndClear();

        service.assignPlayer(e.getId(), p.getId(), false);
        data.flushAndClear();
        assertThat(data.count("select count(ep) from EventPlayer ep where ep.event.id = ?1", e.getId())).isZero();
    }

    @Test
    void assignPlayer_alreadyInATee_conflict() {
        Event e = data.event(LocalDate.of(2026, 12, 12));
        Player p = data.players(1).get(0);
        data.scoreRow(e, p, 0);
        data.flushAndClear();
        AdminRequestException ex = catchThrowableOfType(AdminRequestException.class,
                () -> service.assignPlayer(e.getId(), p.getId(), true));
        assertThat(ex.status()).isEqualTo(409);
    }

    @Test
    void addPlayers_unknownPlayer_notFound() {
        Event e = data.event(LocalDate.of(2026, 12, 12));
        data.flushAndClear();
        AdminRequestException ex = catchThrowableOfType(AdminRequestException.class,
                () -> service.addPlayers(e.getId(), List.of(-5L)));
        assertThat(ex.status()).isEqualTo(404);
    }

    @Test
    void addPlayers_reportsAddedAndExisting() {
        Event e = data.event(LocalDate.of(2026, 12, 12));
        List<Player> ps = data.players(2);
        data.roster(e, List.of(ps.get(0)));
        data.flushAndClear();
        GamePlanService.AddPlayersResult r = service.addPlayers(e.getId(), List.of(ps.get(0).getId(), ps.get(1).getId()));
        assertThat(r.added()).containsExactly(ps.get(1).getId());
        assertThat(r.alreadyOnRoster()).containsExactly(ps.get(0).getId());
    }

    @Test
    void updateTee_unknownTee_notFound() {
        AdminRequestException ex = catchThrowableOfType(AdminRequestException.class,
                () -> service.updateTee(-1L, "7:30 AM", null));
        assertThat(ex.status()).isEqualTo(404);
    }

    @Test
    void updateTee_nullNameLeavesName() {
        Event e = data.event(LocalDate.of(2026, 12, 12));
        long teeId = data.scoreRow(e, data.players(1).get(0), 0).getTee().getId();
        data.flushAndClear();
        GamePlanService.TeeRef ref = service.updateTee(teeId, "8:15 AM", null);
        assertThat(ref.teeName()).isEqualTo("Tee 1");
        assertThat(ref.teeTime()).isEqualTo("8:15 AM");
    }
}
