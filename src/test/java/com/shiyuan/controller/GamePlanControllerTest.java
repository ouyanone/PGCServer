package com.shiyuan.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.shiyuan.dao.entity.db.Event;
import com.shiyuan.dao.entity.db.Player;
import com.shiyuan.dao.entity.db.Tee;
import com.shiyuan.testdata.AdminTestData;

/**
 * Pins what the website's game-plan admin endpoints do today, so moving their logic into
 * {@code GamePlanService} provably changes nothing for the website.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class GamePlanControllerTest {

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    AdminTestData data;

    @BeforeEach
    void setUp() {
        data = new AdminTestData(em);
    }

    private static OidcLoginRequestPostProcessor admin() {
        return oidcLogin().idToken(t -> t.subject("admin-sub").claim("profile", "admin"));
    }

    @Test
    void generate_replacesTeesAndReturnsGroups() throws Exception {
        Event e = data.event(LocalDate.of(2026, 12, 12));
        List<Player> ps = data.players(6);
        data.roster(e, ps);
        data.scoreRow(e, ps.get(0), 0); // an old tee that grouping must replace
        data.flushAndClear();

        mvc.perform(post("/webapi/admin/gameplan/" + e.getId() + "/generate").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"strategy\":\"PGC_BEST_WITH_WORST\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].teeName").value("Tee 1"))
                .andExpect(jsonPath("$[0].players.length()").value(4));
        data.flushAndClear();
        assertThat(data.count("select count(t) from Tee t where t.event.id = ?1", e.getId())).isEqualTo(2);
        assertThat(data.count("select count(ps) from PlayerScore ps where ps.tee.event.id = ?1", e.getId())).isEqualTo(6);
    }

    @Test
    void addPlayers_skipsExisting_201() throws Exception {
        Event e = data.event(LocalDate.of(2026, 12, 12));
        List<Player> ps = data.players(2);
        data.roster(e, List.of(ps.get(0)));
        data.flushAndClear();

        mvc.perform(post("/webapi/admin/gameplan/" + e.getId() + "/players").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[" + ps.get(0).getId() + "," + ps.get(1).getId() + "]"))
                .andExpect(status().isCreated());
        data.flushAndClear();
        assertThat(data.count("select count(ep) from EventPlayer ep where ep.event.id = ?1", e.getId())).isEqualTo(2);
    }

    @Test
    void assignPlayer_fillsOpenTee_thenCreatesNewTee() throws Exception {
        Event e = data.event(LocalDate.of(2026, 12, 12));
        List<Player> ps = data.players(5);
        data.roster(e, ps.subList(0, 3));
        data.flushAndClear();
        mvc.perform(post("/webapi/admin/gameplan/" + e.getId() + "/generate").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        data.flushAndClear();

        // Tee 1 has 3 of 4 places: the first late player joins it, the next needs a new tee.
        mvc.perform(post("/webapi/admin/gameplan/" + e.getId() + "/players/" + ps.get(3).getId() + "/assign").with(admin()))
                .andExpect(status().isOk());
        data.flushAndClear();
        assertThat(data.count("select count(t) from Tee t where t.event.id = ?1", e.getId())).isEqualTo(1);
        mvc.perform(post("/webapi/admin/gameplan/" + e.getId() + "/players/" + ps.get(4).getId() + "/assign").with(admin()))
                .andExpect(status().isOk());
        data.flushAndClear();
        assertThat(data.count("select count(t) from Tee t where t.event.id = ?1", e.getId())).isEqualTo(2);
        // The website's assign does not touch the roster.
        assertThat(data.count("select count(ep) from EventPlayer ep where ep.event.id = ?1", e.getId())).isEqualTo(3);
    }

    @Test
    void updateTee_setsTime() throws Exception {
        Event e = data.event(LocalDate.of(2026, 12, 12));
        Player p = data.players(1).get(0);
        Tee tee = data.scoreRow(e, p, 0).getTee();
        data.flushAndClear();

        mvc.perform(put("/webapi/admin/gameplan/tee/" + tee.getId()).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"teeTime\":\"7:30 AM\"}"))
                .andExpect(status().isOk());
        data.flushAndClear();
        assertThat(em.find(Tee.class, tee.getId()).getTeeTime()).isEqualTo("7:30 AM");
        assertThat(em.find(Tee.class, tee.getId()).getTeeName()).isEqualTo("Tee 1");
    }
}
