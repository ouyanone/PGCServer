package com.shiyuan.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.shiyuan.dao.entity.db.Event;
import com.shiyuan.testdata.AdminTestData;

/**
 * Website endpoints whose answer depends on the real commit. No test-managed transaction here:
 * the controller's own transaction is outermost, as in production. Rows are removed afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class GamePlanControllerCommitTest {

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    private Long eventId;

    @AfterEach
    void cleanUp() {
        if (eventId != null) {
            tx.executeWithoutResult(s -> em.remove(em.find(Event.class, eventId)));
        }
    }

    /** Final review #5: the website answered 400 for an empty roster before the refactor. */
    @Test
    void generate_emptyRoster_400() throws Exception {
        eventId = tx.execute(s -> new AdminTestData(em).event(LocalDate.of(2026, 12, 12)).getId());
        mvc.perform(post("/webapi/admin/gameplan/" + eventId + "/generate")
                        .with(oidcLogin().idToken(t -> t.subject("admin-sub").claim("profile", "admin")))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
