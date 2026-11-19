package com.shiyuan.controller.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.shiyuan.controller.api.AdminApiDtos.CreateEvent;
import com.shiyuan.controller.api.AdminApiDtos.CreateReward;
import com.shiyuan.controller.api.AdminApiDtos.CreateTournament;
import com.shiyuan.controller.api.AdminApiDtos.ErrorBody;
import com.shiyuan.controller.api.AdminApiDtos.EventView;
import com.shiyuan.controller.api.AdminApiDtos.RewardView;
import com.shiyuan.controller.api.AdminApiDtos.TournamentView;
import com.shiyuan.dao.entity.db.Event;
import com.shiyuan.dao.entity.db.Reward;
import com.shiyuan.dao.entity.db.Tournament;
import com.shiyuan.service.AdminRequestException;
import com.shiyuan.service.TournamentAdminService;

/**
 * The token-only admin API the MCP server's admin tools call ({@code ApiSecurityConfiguration}
 * guards it). Errors come back as {@code {"error": "..."}} sentences a model can relay.
 */
@RestController
public class AdminApiController {

    private static final Logger log = LoggerFactory.getLogger(AdminApiController.class);

    private final TournamentAdminService tournaments;

    public AdminApiController(TournamentAdminService tournaments) {
        this.tournaments = tournaments;
    }

    @PostMapping(path = "/api/admin/tournaments", consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TournamentView> createTournament(@RequestBody CreateTournament req) {
        Tournament t = tournaments.createTournamentChecked(req);
        log.info("api admin create tournament id={}", t.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(new TournamentView(t.getId(), t.getName(),
                t.getStartDate(), t.getEndDate(), t.getSeason().getId(), t.getSeason().getSeasonName()));
    }

    @PostMapping(path = "/api/admin/tournaments/{tournamentId}/events", consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EventView> createEvent(@PathVariable long tournamentId, @RequestBody CreateEvent req) {
        Event e = tournaments.createEventChecked(tournamentId, req);
        log.info("api admin create event id={} tournament={}", e.getId(), tournamentId);
        return ResponseEntity.status(HttpStatus.CREATED).body(new EventView(e.getId(), e.getEventName(),
                e.getEventDate(), e.getCourse().getId(), e.getCourse().getCourseName(), tournamentId));
    }

    @PostMapping(path = "/api/admin/rewards", consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RewardView> createReward(@RequestBody CreateReward req) {
        Reward r = tournaments.createRewardChecked(req);
        log.info("api admin create reward id={} event={}", r.getId(), r.getEvent().getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(new RewardView(r.getId(), r.getEvent().getId(),
                r.getPlayer().getId(), r.getPlayer().getfName() + " " + r.getPlayer().getlName(), r.getRewardName()));
    }

    @ExceptionHandler(AdminRequestException.class)
    ResponseEntity<ErrorBody> refused(AdminRequestException e) {
        return ResponseEntity.status(e.status()).body(new ErrorBody(e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorBody> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ErrorBody(
                "The request is not valid: send JSON, with dates as yyyy-MM-dd and ids as numbers."));
    }
}
