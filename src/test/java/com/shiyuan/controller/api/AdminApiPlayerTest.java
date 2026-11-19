package com.shiyuan.controller.api;

import static com.shiyuan.controller.api.AdminApiTournamentTest.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.shiyuan.dao.entity.db.Player;
import com.shiyuan.testdata.AdminTestData;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class AdminApiPlayerTest {

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    AdminTestData data;
    String tag;

    @BeforeEach
    void setUp() {
        data = new AdminTestData(em);
        tag = UUID.randomUUID().toString().substring(0, 6);
    }

    private Player existingWithContact() {
        Player p = data.player("Zz" + tag, "Existing");
        p.setPhone("555-0100");
        p.setEmail("existing@example.test");
        p.setGhinNumber("G" + tag);
        p.setPgcPoints(42);
        p.setPgcHandicap(12.5);
        data.flushAndClear();
        return p;
    }

    @Test
    void addPlayer_201_noContactFieldsInResponse() throws Exception {
        mvc.perform(json(post("/api/admin/players"),
                        "{\"firstName\":\"Ann%s\",\"lastName\":\"New\",\"gender\":\"F\",\"handicap\":21.4}".formatted(tag)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.firstName").value("Ann" + tag))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.handicap").value(21.4))
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void addPlayer_missingLastName_400() throws Exception {
        mvc.perform(json(post("/api/admin/players"), "{\"firstName\":\"Ann\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("lastName")));
    }

    @Test
    void addPlayer_badGender_400() throws Exception {
        mvc.perform(json(post("/api/admin/players"), "{\"firstName\":\"Ann\",\"lastName\":\"X%s\",\"gender\":\"female\"}".formatted(tag)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("gender")));
    }

    @Test
    void addPlayer_duplicateName_409() throws Exception {
        Player p = existingWithContact();
        mvc.perform(json(post("/api/admin/players"), "{\"firstName\":\"zz%s\",\"lastName\":\"EXISTING\"}".formatted(tag)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString(String.valueOf(p.getId()))));
    }

    @Test
    void addPlayer_duplicateGhin_409() throws Exception {
        Player p = existingWithContact();
        mvc.perform(json(post("/api/admin/players"),
                        "{\"firstName\":\"Other\",\"lastName\":\"Person%s\",\"ghinNumber\":\"G%s\"}".formatted(tag, tag)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString(String.valueOf(p.getId()))));
    }

    @Test
    void updatePlayer_changesOnlyGivenFields() throws Exception {
        Player p = existingWithContact();
        mvc.perform(json(patch("/api/admin/players/" + p.getId()), "{\"nickName\":\"Ace\",\"handicap\":9.8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickName").value("Ace"))
                .andExpect(jsonPath("$.lastName").value("Existing"));
        data.flushAndClear();
        Player after = em.find(Player.class, p.getId());
        assertThat(after.getHandicap()).isEqualTo(9.8);
        assertThat(after.getPhone()).isEqualTo("555-0100");
        assertThat(after.getEmail()).isEqualTo("existing@example.test");
        assertThat(after.getPgcHandicap()).isEqualTo(12.5);
        assertThat(after.getPgcPoints()).isEqualTo(42);
        assertThat(after.getGhinNumber()).isEqualTo("G" + tag);
    }

    @Test
    void updatePlayer_nothingToChange_400() throws Exception {
        Player p = existingWithContact();
        mvc.perform(json(patch("/api/admin/players/" + p.getId()), "{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatePlayer_unknown_404() throws Exception {
        mvc.perform(json(patch("/api/admin/players/-1"), "{\"nickName\":\"Ace\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updatePlayer_ghinTakenByOther_409() throws Exception {
        Player p = existingWithContact();
        Player other = data.player("Yy" + tag, "Other");
        data.flushAndClear();
        mvc.perform(json(patch("/api/admin/players/" + other.getId()), "{\"ghinNumber\":\"G%s\"}".formatted(tag)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString(String.valueOf(p.getId()))));
    }

    @Test
    void updatePlayer_bodyWithEmail_ignored() throws Exception {
        Player p = existingWithContact();
        mvc.perform(json(patch("/api/admin/players/" + p.getId()),
                        "{\"nickName\":\"Ace\",\"email\":\"hacked@example.test\",\"phone\":\"000\"}"))
                .andExpect(status().isOk());
        data.flushAndClear();
        Player after = em.find(Player.class, p.getId());
        assertThat(after.getEmail()).isEqualTo("existing@example.test");
        assertThat(after.getPhone()).isEqualTo("555-0100");
    }
}
