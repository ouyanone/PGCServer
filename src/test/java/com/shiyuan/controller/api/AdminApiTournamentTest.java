package com.shiyuan.controller.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.shiyuan.dao.entity.db.Course;
import com.shiyuan.dao.entity.db.Event;
import com.shiyuan.dao.entity.db.Player;
import com.shiyuan.dao.entity.db.Season;
import com.shiyuan.dao.entity.db.Tournament;
import com.shiyuan.testdata.AdminTestData;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class AdminApiTournamentTest {

    static final RequestPostProcessor WRITER =
            jwt().authorities(new SimpleGrantedAuthority("SCOPE_https://pgcgolf.club/mcp/tools.write"));

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    AdminTestData data;
    Season season;
    Course course;

    @BeforeEach
    void setUp() {
        data = new AdminTestData(em);
        season = data.anySeason();
        course = data.anyCourse();
    }

    static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return b.with(WRITER).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    String tournamentBody(String name, String start, String end) {
        return """
               {"name":"%s","startDate":"%s","endDate":"%s","seasonId":%d}""".formatted(name, start, end, season.getId());
    }

    @Test
    void createTournament_201_returnsIdAndSeason() throws Exception {
        mvc.perform(json(post("/api/admin/tournaments"), tournamentBody("TEST Cup A", "2026-12-12", "2026-12-13")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("TEST Cup A"))
                .andExpect(jsonPath("$.startDate").value("2026-12-12"))
                .andExpect(jsonPath("$.seasonId").value(season.getId()))
                .andExpect(jsonPath("$.seasonName").value(season.getSeasonName()));
    }

    @Test
    void createTournament_blankName_400() throws Exception {
        mvc.perform(json(post("/api/admin/tournaments"), tournamentBody("  ", "2026-12-12", "2026-12-13")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("name")));
    }

    @Test
    void createTournament_endBeforeStart_400() throws Exception {
        mvc.perform(json(post("/api/admin/tournaments"), tournamentBody("TEST Cup B", "2026-12-13", "2026-12-12")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("end date")));
    }

    @Test
    void createTournament_unknownSeason_404() throws Exception {
        mvc.perform(json(post("/api/admin/tournaments"),
                        "{\"name\":\"TEST Cup C\",\"startDate\":\"2026-12-12\",\"endDate\":\"2026-12-13\",\"seasonId\":-1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(containsString("Season -1")));
    }

    @Test
    void createTournament_duplicateNameAndStart_409() throws Exception {
        Tournament existing = data.tournament(LocalDate.of(2026, 12, 12), LocalDate.of(2026, 12, 13));
        data.flushAndClear();
        mvc.perform(json(post("/api/admin/tournaments"),
                        tournamentBody(existing.getName().toLowerCase(), "2026-12-12", "2026-12-14")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString(String.valueOf(existing.getId()))));
    }

    @Test
    void createTournament_badJson_400() throws Exception {
        mvc.perform(json(post("/api/admin/tournaments"), "{\"name\":\"X\",\"startDate\":\"May 3\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());
    }

    String eventBody(String name, String date, long courseId) {
        return "{\"name\":\"%s\",\"date\":\"%s\",\"courseId\":%d}".formatted(name, date, courseId);
    }

    @Test
    void createEvent_201_inheritsSeason() throws Exception {
        Tournament t = data.tournament(LocalDate.of(2026, 12, 12), LocalDate.of(2026, 12, 13));
        data.flushAndClear();
        mvc.perform(json(post("/api/admin/tournaments/" + t.getId() + "/events"), eventBody("TEST R1 " + tag(), "2026-12-12", course.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.tournamentId").value(t.getId()))
                .andExpect(jsonPath("$.courseName").value(course.getCourseName()))
                .andExpect(jsonPath("$.date").value("2026-12-12"));
        data.flushAndClear();
        Event saved = em.createQuery("select e from Event e where e.tournament.id = :t", Event.class)
                .setParameter("t", t.getId()).getSingleResult();
        org.assertj.core.api.Assertions.assertThat(saved.getSeason().getId()).isEqualTo(t.getSeason().getId());
    }

    @Test
    void createEvent_dateOutsideTournament_400() throws Exception {
        Tournament t = data.tournament(LocalDate.of(2026, 12, 12), LocalDate.of(2026, 12, 13));
        data.flushAndClear();
        mvc.perform(json(post("/api/admin/tournaments/" + t.getId() + "/events"), eventBody("TEST R3 " + tag(), "2026-12-20", course.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("2026-12-12")));
    }

    @Test
    void createEvent_unknownCourse_404() throws Exception {
        Tournament t = data.tournament(LocalDate.of(2026, 12, 12), LocalDate.of(2026, 12, 13));
        data.flushAndClear();
        mvc.perform(json(post("/api/admin/tournaments/" + t.getId() + "/events"), eventBody("TEST R1 " + tag(), "2026-12-12", -1)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createReward_201() throws Exception {
        Event e = data.event(LocalDate.of(2026, 12, 12));
        Player p = data.players(1).get(0);
        data.flushAndClear();
        mvc.perform(json(post("/api/admin/rewards"),
                        "{\"eventId\":%d,\"playerId\":%d,\"rewardName\":\"Closest to the pin\"}".formatted(e.getId(), p.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.rewardName").value("Closest to the pin"))
                .andExpect(jsonPath("$.playerName").value(p.getfName() + " " + p.getlName()));
    }

    @Test
    void createReward_unknownPlayer_404() throws Exception {
        Event e = data.event(LocalDate.of(2026, 12, 12));
        data.flushAndClear();
        mvc.perform(json(post("/api/admin/rewards"),
                        "{\"eventId\":%d,\"playerId\":-1,\"rewardName\":\"Longest drive\"}".formatted(e.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void createReward_blankName_400() throws Exception {
        Event e = data.event(LocalDate.of(2026, 12, 12));
        Player p = data.players(1).get(0);
        data.flushAndClear();
        mvc.perform(json(post("/api/admin/rewards"),
                        "{\"eventId\":%d,\"playerId\":%d,\"rewardName\":\"\"}".formatted(e.getId(), p.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noToken_401() throws Exception {
        mvc.perform(post("/api/admin/tournaments").contentType(MediaType.APPLICATION_JSON)
                        .content(tournamentBody("TEST Cup D", "2026-12-12", "2026-12-13")))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Final review #1: an admin resending a setup must not get a second event. Event names are
     * unique across the whole club (event.event_name), so the check is club-wide, not per tournament.
     */
    @Test
    void createEvent_duplicateName_409() throws Exception {
        Tournament t = data.tournament(LocalDate.of(2026, 12, 12), LocalDate.of(2026, 12, 13));
        Tournament other = data.tournament(LocalDate.of(2026, 12, 12), LocalDate.of(2026, 12, 13));
        data.flushAndClear();
        String name = "TEST R1 " + tag();
        String first = mvc.perform(json(post("/api/admin/tournaments/" + t.getId() + "/events"), eventBody(name, "2026-12-12", course.getId())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = first.replaceAll(".*\"id\":(\\d+).*", "$1");
        mvc.perform(json(post("/api/admin/tournaments/" + other.getId() + "/events"), eventBody(name.toLowerCase(), "2026-12-13", course.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString(id)));
    }

    @Test
    void createReward_duplicate_409() throws Exception {
        Event e = data.event(LocalDate.of(2026, 12, 12));
        Player p = data.players(1).get(0);
        data.flushAndClear();
        String body = "{\"eventId\":%d,\"playerId\":%d,\"rewardName\":\"Closest to the pin\"}".formatted(e.getId(), p.getId());
        mvc.perform(json(post("/api/admin/rewards"), body)).andExpect(status().isCreated());
        mvc.perform(json(post("/api/admin/rewards"), body.replace("Closest", "closest")))
                .andExpect(status().isConflict());
    }

    private static String tag() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}
