package com.shiyuan.controller.api;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.shiyuan.controller.api.AdminApiDtos.AddPlayers;
import com.shiyuan.controller.api.AdminApiDtos.CreateEvent;
import com.shiyuan.controller.api.AdminApiDtos.CreateReward;
import com.shiyuan.controller.api.AdminApiDtos.CreateTournament;
import com.shiyuan.controller.api.AdminApiDtos.ErrorBody;
import com.shiyuan.controller.api.AdminApiDtos.EventView;
import com.shiyuan.controller.api.AdminApiDtos.GenerateGroups;
import com.shiyuan.controller.api.AdminApiDtos.PlayerFields;
import com.shiyuan.controller.api.AdminApiDtos.PlayerView;
import com.shiyuan.controller.api.AdminApiDtos.SetTee;
import com.shiyuan.controller.api.AdminApiDtos.RewardView;
import com.shiyuan.controller.api.AdminApiDtos.TournamentView;
import com.shiyuan.dao.entity.db.Event;
import com.shiyuan.dao.entity.db.Player;
import com.shiyuan.dao.entity.db.Reward;
import com.shiyuan.dao.entity.db.Tournament;
import com.shiyuan.service.AdminRequestException;
import com.shiyuan.service.GamePlanService;
import com.shiyuan.service.GamePlanService.AddPlayersResult;
import com.shiyuan.service.GamePlanService.TeeRef;
import com.shiyuan.service.GroupingService.Strategy;
import com.shiyuan.service.PlayerService;
import com.shiyuan.service.TournamentAdminService;

/**
 * The token-only admin API the MCP server's admin tools call ({@code ApiSecurityConfiguration}
 * guards it). Errors come back as {@code {"error": "..."}} sentences a model can relay.
 */
@RestController
public class AdminApiController {

    private static final Logger log = LoggerFactory.getLogger(AdminApiController.class);

    private final TournamentAdminService tournaments;
    private final GamePlanService gamePlan;
    private final PlayerService players;

    public AdminApiController(TournamentAdminService tournaments, GamePlanService gamePlan, PlayerService players) {
        this.tournaments = tournaments;
        this.gamePlan = gamePlan;
        this.players = players;
    }

    @PostMapping(path = "/api/admin/players", consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PlayerView> addPlayer(@RequestBody PlayerFields req) {
        Player p = players.addPlayer(req);
        log.info("api admin add player id={}", p.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(view(p));
    }

    @PatchMapping(path = "/api/admin/players/{playerId}", consumes = MediaType.APPLICATION_JSON_VALUE,
                  produces = MediaType.APPLICATION_JSON_VALUE)
    public PlayerView updatePlayer(@PathVariable long playerId, @RequestBody PlayerFields req) {
        Player p = players.updatePlayer(playerId, req);
        log.info("api admin update player id={}", playerId);
        return view(p);
    }

    private static PlayerView view(Player p) {
        return new PlayerView(p.getId(), p.getfName(), p.getlName(), p.getNickName(), p.getChineseNickName(),
                p.getGhinNumber(), p.getGender(), p.getHandicap(), Boolean.TRUE.equals(p.getIsActive()));
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

    @PostMapping(path = "/api/admin/events/{eventId}/players", consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public AddPlayersResult addPlayers(@PathVariable long eventId, @RequestBody AddPlayers req) {
        if (req.playerIds() == null || req.playerIds().isEmpty()) {
            throw AdminRequestException.badRequest("Give at least one playerId to add.");
        }
        AddPlayersResult r = gamePlan.addPlayers(eventId, req.playerIds());
        log.info("api admin roster event={} added={} existing={}", eventId, r.added().size(), r.alreadyOnRoster().size());
        return r;
    }

    @PostMapping(path = "/api/admin/events/{eventId}/groups", consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> generateGroups(@PathVariable long eventId, @RequestBody GenerateGroups req) {
        Strategy strategy = strategy(req.strategy());
        List<Map<String, Object>> groups = gamePlan.generateGroups(eventId, strategy, true);
        log.info("api admin groups event={} strategy={} tees={}", eventId, strategy, groups.size());
        return groups;
    }

    @PostMapping(path = "/api/admin/events/{eventId}/players/{playerId}/assign",
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public TeeRef assignPlayer(@PathVariable long eventId, @PathVariable long playerId) {
        TeeRef tee = gamePlan.assignPlayer(eventId, playerId, true);
        log.info("api admin assign event={} player={} tee={}", eventId, playerId, tee.teeId());
        return tee;
    }

    @PutMapping(path = "/api/admin/tees/{teeId}", consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
    public TeeRef setTee(@PathVariable long teeId, @RequestBody SetTee req) {
        String time = blankToNull(req.teeTime());
        String name = blankToNull(req.teeName());
        if (time == null && name == null) {
            throw AdminRequestException.badRequest("Give a teeTime or a teeName to change.");
        }
        TeeRef tee = gamePlan.updateTee(teeId, time, name);
        log.info("api admin tee id={}", teeId);
        return tee;
    }

    private static Strategy strategy(String name) {
        String all = Arrays.stream(Strategy.values()).map(Enum::name).collect(Collectors.joining(", "));
        if (name == null || name.isBlank()) {
            throw AdminRequestException.badRequest("The strategy is required; it is one of " + all + ".");
        }
        try {
            return Strategy.valueOf(name.trim());
        } catch (IllegalArgumentException e) {
            throw AdminRequestException.badRequest("Unknown strategy \"" + name.trim() + "\"; use one of " + all + ".");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
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
