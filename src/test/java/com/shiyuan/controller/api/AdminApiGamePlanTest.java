package com.shiyuan.controller.api;

import static com.shiyuan.controller.api.AdminApiTournamentTest.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.shiyuan.dao.entity.db.Event;
import com.shiyuan.dao.entity.db.Player;
import com.shiyuan.testdata.AdminTestData;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class AdminApiGamePlanTest {

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    AdminTestData data;

    @BeforeEach
    void setUp() {
        data = new AdminTestData(em);
    }

    private Event event() {
        return data.event(LocalDate.of(2026, 12, 12));
    }

    @Test
    void addPlayers_200_reportsAddedAndExisting() throws Exception {
        Event e = event();
        List<Player> ps = data.players(2);
        data.roster(e, List.of(ps.get(0)));
        data.flushAndClear();
        mvc.perform(json(post("/api/admin/events/" + e.getId() + "/players"),
                        "{\"playerIds\":[%d,%d]}".formatted(ps.get(0).getId(), ps.get(1).getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added[0]").value(ps.get(1).getId()))
                .andExpect(jsonPath("$.alreadyOnRoster[0]").value(ps.get(0).getId()));
    }

    @Test
    void addPlayers_emptyList_400() throws Exception {
        Event e = event();
        data.flushAndClear();
        mvc.perform(json(post("/api/admin/events/" + e.getId() + "/players"), "{\"playerIds\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generateGroups_200() throws Exception {
        Event e = event();
        data.roster(e, data.players(5));
        data.flushAndClear();
        mvc.perform(json(post("/api/admin/events/" + e.getId() + "/groups"), "{\"strategy\":\"GHIN_BEST_WITH_WORST\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].teeId").isNumber());
    }

    @Test
    void generateGroups_scoredEvent_409_nothingDeleted() throws Exception {
        Event e = event();
        List<Player> ps = data.players(4);
        data.roster(e, ps);
        data.scoreRow(e, ps.get(0), 79);
        data.flushAndClear();
        mvc.perform(json(post("/api/admin/events/" + e.getId() + "/groups"), "{\"strategy\":\"PGC_BEST_TO_WORST\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString("recorded score")));
        data.flushAndClear();
        assertThat(data.count("select count(ps) from PlayerScore ps where ps.tee.event.id = ?1", e.getId())).isEqualTo(1);
    }

    @Test
    void generateGroups_unknownStrategy_400_listsStrategies() throws Exception {
        Event e = event();
        data.roster(e, data.players(2));
        data.flushAndClear();
        mvc.perform(json(post("/api/admin/events/" + e.getId() + "/groups"), "{\"strategy\":\"FASTEST_FIRST\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(allOf(containsString("GHIN_BEST_TO_WORST_BY_GENDER"),
                        containsString("PGC_BEST_WITH_WORST"), containsString("RANDOM"))));
    }

    @Test
    void assign_200_addsToRoster() throws Exception {
        Event e = event();
        Player p = data.players(1).get(0);
        data.flushAndClear();
        mvc.perform(json(post("/api/admin/events/" + e.getId() + "/players/" + p.getId() + "/assign"), ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teeName").value("Tee 1"));
        data.flushAndClear();
        assertThat(data.count("select count(ep) from EventPlayer ep where ep.event.id = ?1", e.getId())).isEqualTo(1);
    }

    @Test
    void setTee_200() throws Exception {
        Event e = event();
        long teeId = data.scoreRow(e, data.players(1).get(0), 0).getTee().getId();
        data.flushAndClear();
        mvc.perform(json(put("/api/admin/tees/" + teeId), "{\"teeTime\":\"7:38 AM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teeTime").value("7:38 AM"))
                .andExpect(jsonPath("$.teeName").value("Tee 1"));
    }

    @Test
    void setTee_nothingToChange_400() throws Exception {
        Event e = event();
        long teeId = data.scoreRow(e, data.players(1).get(0), 0).getTee().getId();
        data.flushAndClear();
        mvc.perform(json(put("/api/admin/tees/" + teeId), "{\"teeTime\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void setTee_unknownTee_404() throws Exception {
        mvc.perform(json(put("/api/admin/tees/-1"), "{\"teeTime\":\"7:30 AM\"}"))
                .andExpect(status().isNotFound());
    }
}
