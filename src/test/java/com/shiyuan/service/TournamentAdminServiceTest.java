package com.shiyuan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.shiyuan.dao.entity.db.Event;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class TournamentAdminServiceTest {

    @Autowired TournamentAdminService service;

    @Test
    void createEvent_unknownTournament_notFound() {
        AdminRequestException ex = catchThrowableOfType(AdminRequestException.class,
                () -> service.createEvent(-1L, new Event()));
        assertThat(ex.status()).isEqualTo(404);
    }
}
