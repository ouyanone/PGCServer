package com.shiyuan.controller;

import com.shiyuan.dao.entity.db.*;
import com.shiyuan.dao.repository.*;
import com.shiyuan.service.GroupingService;
import com.shiyuan.service.GroupingService.Strategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
public class GamePlanController extends BaseController {

    @Autowired private EventRepository eventRepository;
    @Autowired private EventPlayerRepository eventPlayerRepository;
    @Autowired private PlayerRepository playerRepository;
    @Autowired private TeeRepository teeRepository;
    @Autowired private PlayerScoreRepository playerScoreRepository;
    @Autowired private GroupingService groupingService;

    // ── Roster ───────────────────────────────────────────────────────────────

    @GetMapping(path = "/webapi/gameplan/{eventId}/players", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Player>> getRosterPlayers(@PathVariable Long eventId) {
        List<EventPlayer> roster = eventPlayerRepository.findByEventId(eventId);
        List<Player> players = roster.stream().map(EventPlayer::getPlayer).collect(Collectors.toList());
        return ResponseEntity.ok(players);
    }

    @PostMapping(path = "/webapi/admin/gameplan/{eventId}/players", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<Void> addPlayers(@PathVariable Long eventId,
                                           @RequestBody List<Long> playerIds,
                                           @AuthenticationPrincipal OidcUser principal) throws Exception {
        checkPrincipal(principal);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found: " + eventId));

        for (Long playerId : playerIds) {
            if (!eventPlayerRepository.existsByEventIdAndPlayerId(eventId, playerId)) {
                Player player = playerRepository.findById(playerId)
                        .orElseThrow(() -> new RuntimeException("Player not found: " + playerId));
                EventPlayer ep = new EventPlayer();
                ep.setEvent(event);
                ep.setPlayer(player);
                eventPlayerRepository.save(ep);
            }
        }
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping(path = "/webapi/admin/gameplan/{eventId}/players/{playerId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<Void> removePlayer(@PathVariable Long eventId,
                                             @PathVariable Long playerId,
                                             @AuthenticationPrincipal OidcUser principal) throws Exception {
        checkPrincipal(principal);
        eventPlayerRepository.deleteByEventIdAndPlayerId(eventId, playerId);
        return ResponseEntity.ok().build();
    }

    // ── Group generation ─────────────────────────────────────────────────────

    @PostMapping(path = "/webapi/admin/gameplan/{eventId}/generate", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<List<Map<String, Object>>> generateGroups(
            @PathVariable Long eventId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal OidcUser principal) throws Exception {

        checkPrincipal(principal);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found: " + eventId));

        // Bulk-delete player_scores then tees (bypasses JPA lifecycle / orphanRemoval conflicts)
        playerScoreRepository.deleteByEventId(eventId);
        teeRepository.deleteByEventId(eventId);

        // Load roster
        List<Player> players = eventPlayerRepository.findByEventId(eventId)
                .stream().map(EventPlayer::getPlayer).collect(Collectors.toList());

        if (players.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        Strategy strategy = Strategy.valueOf(body.getOrDefault("strategy", "GHIN_BEST_TO_WORST"));
        List<List<Player>> groups = groupingService.generateGroups(players, strategy);

        // Persist tees and player scores
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            Tee tee = new Tee();
            tee.setTeeName("Tee " + (i + 1));
            tee.setTeeDesc("");
            tee.setTeeStroy("");
            tee.setEvent(event);
            tee = teeRepository.save(tee);

            List<Map<String, Object>> groupPlayers = new ArrayList<>();
            for (Player player : groups.get(i)) {
                PlayerScore ps = new PlayerScore();
                ps.setPlayer(player);
                ps.setTee(tee);
                ps.setRecordStory("");
                ps.setScore(0);
                ps.setGamePoint(0);
                ps.setFlight(0);
                ps.setEntryScore(0.0);
                ps.setNetScore(0.0);
                ps.setEntryPGCHandicap(0.0);
                ps.setCurrentRoundHandicap(0.0);
                ps = playerScoreRepository.save(ps);

                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("playerScoreId", ps.getId());
                pm.put("playerId", player.getId());
                pm.put("playerName", player.getfName() + " " + player.getlName());
                pm.put("handicap", player.getHandicap());
                pm.put("pgcHandicap", player.getPgcHandicap());
                pm.put("gender", player.getGender());
                groupPlayers.add(pm);
            }

            Map<String, Object> groupMap = new LinkedHashMap<>();
            groupMap.put("teeId", tee.getId());
            groupMap.put("teeName", tee.getTeeName());
            groupMap.put("teeTime", tee.getTeeTime());
            groupMap.put("players", groupPlayers);
            result.add(groupMap);
        }

        return ResponseEntity.ok(result);
    }

    // ── Clear all groups ─────────────────────────────────────────────────────

    @DeleteMapping(path = "/webapi/admin/gameplan/{eventId}/groups", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<Void> clearGroups(@PathVariable Long eventId,
                                            @AuthenticationPrincipal OidcUser principal) throws Exception {
        checkPrincipal(principal);
        playerScoreRepository.deleteByEventId(eventId);
        teeRepository.deleteByEventId(eventId);
        return ResponseEntity.ok().build();
    }

    // ── Get current groups ───────────────────────────────────────────────────

    @GetMapping(path = "/webapi/gameplan/{eventId}/groups", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> getGroups(@PathVariable Long eventId) {
        List<Tee> tees = teeRepository.findByEventIdOrderByTeeName(eventId);
        List<Map<String, Object>> result = tees.stream().map(tee -> {
            Map<String, Object> groupMap = new LinkedHashMap<>();
            groupMap.put("teeId", tee.getId());
            groupMap.put("teeName", tee.getTeeName());
            groupMap.put("teeTime", tee.getTeeTime());

            List<Map<String, Object>> players = new ArrayList<>();
            if (tee.getPlayerScoreList() != null) {
                for (PlayerScore ps : tee.getPlayerScoreList()) {
                    Player p = ps.getPlayer();
                    Map<String, Object> pm = new LinkedHashMap<>();
                    pm.put("playerScoreId", ps.getId());
                    pm.put("playerId", p.getId());
                    pm.put("playerName", p.getfName() + " " + p.getlName());
                    pm.put("handicap", p.getHandicap());
                    pm.put("pgcHandicap", p.getPgcHandicap());
                    pm.put("gender", p.getGender());
                    players.add(pm);
                }
            }
            groupMap.put("players", players);
            return groupMap;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // ── Delete an empty tee ──────────────────────────────────────────────────

    @DeleteMapping(path = "/webapi/admin/gameplan/tee/{teeId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<Void> deleteTee(@PathVariable Long teeId,
                                          @AuthenticationPrincipal OidcUser principal) throws Exception {
        checkPrincipal(principal);
        teeRepository.deleteTeeById(teeId);
        return ResponseEntity.ok().build();
    }

    // ── Update tee name / time ───────────────────────────────────────────────

    @PutMapping(path = "/webapi/admin/gameplan/tee/{teeId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<Void> updateTee(@PathVariable Long teeId,
                                          @RequestBody Map<String, String> body,
                                          @AuthenticationPrincipal OidcUser principal) throws Exception {
        checkPrincipal(principal);
        Tee tee = teeRepository.findById(teeId)
                .orElseThrow(() -> new RuntimeException("Tee not found: " + teeId));
        if (body.containsKey("teeName")) tee.setTeeName(body.get("teeName"));
        if (body.containsKey("teeTime")) tee.setTeeTime(body.get("teeTime"));
        teeRepository.save(tee);
        return ResponseEntity.ok().build();
    }

    // ── Move player between groups ───────────────────────────────────────────

    @PutMapping(path = "/webapi/admin/gameplan/playerscore/{playerScoreId}/tee/{teeId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<Void> movePlayer(@PathVariable Long playerScoreId,
                                           @PathVariable Long teeId,
                                           @AuthenticationPrincipal OidcUser principal) throws Exception {
        checkPrincipal(principal);
        PlayerScore ps = playerScoreRepository.findById(playerScoreId)
                .orElseThrow(() -> new RuntimeException("PlayerScore not found: " + playerScoreId));
        Tee tee = teeRepository.findById(teeId)
                .orElseThrow(() -> new RuntimeException("Tee not found: " + teeId));
        ps.setTee(tee);
        playerScoreRepository.save(ps);
        return ResponseEntity.ok().build();
    }

    // ── Remove player from tee ───────────────────────────────────────────────

    @DeleteMapping(path = "/webapi/admin/gameplan/playerscore/{playerScoreId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<Void> removePlayerFromTee(@PathVariable Long playerScoreId,
                                                    @AuthenticationPrincipal OidcUser principal) throws Exception {
        checkPrincipal(principal);
        PlayerScore ps = playerScoreRepository.findById(playerScoreId)
                .orElseThrow(() -> new RuntimeException("PlayerScore not found: " + playerScoreId));
        Long playerId = ps.getPlayer().getId();
        Long eventId = ps.getTee().getEvent().getId();
        playerScoreRepository.deletePlayerScoreById(playerScoreId);
        eventPlayerRepository.deleteByEventIdAndPlayerId(eventId, playerId);
        return ResponseEntity.ok().build();
    }

    // ── Get unassigned roster players ────────────────────────────────────────

    @GetMapping(path = "/webapi/gameplan/{eventId}/unassigned", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Player>> getUnassignedPlayers(@PathVariable Long eventId) {
        List<Player> rosterPlayers = eventPlayerRepository.findByEventId(eventId)
                .stream().map(EventPlayer::getPlayer).collect(Collectors.toList());
        Set<Long> assignedIds = teeRepository.findByEventIdOrderByTeeName(eventId).stream()
                .flatMap(t -> t.getPlayerScoreList().stream())
                .map(ps -> ps.getPlayer().getId())
                .collect(Collectors.toSet());
        List<Player> unassigned = rosterPlayers.stream()
                .filter(p -> !assignedIds.contains(p.getId()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(unassigned);
    }

    // ── Assign player to a tee ───────────────────────────────────────────────

    @PostMapping(path = "/webapi/admin/gameplan/{eventId}/players/{playerId}/assign", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<Void> assignPlayer(@PathVariable Long eventId,
                                             @PathVariable Long playerId,
                                             @AuthenticationPrincipal OidcUser principal) throws Exception {
        checkPrincipal(principal);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found: " + eventId));
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new RuntimeException("Player not found: " + playerId));

        List<Tee> tees = teeRepository.findByEventIdOrderByTeeName(eventId);
        Tee targetTee = tees.stream()
                .filter(t -> t.getPlayerScoreList().size() < 4)
                .findFirst().orElse(null);

        if (targetTee == null) {
            targetTee = new Tee();
            targetTee.setTeeName("Tee " + (tees.size() + 1));
            targetTee.setTeeDesc("");
            targetTee.setTeeStroy("");
            targetTee.setEvent(event);
            targetTee = teeRepository.save(targetTee);
        }

        PlayerScore ps = new PlayerScore();
        ps.setPlayer(player);
        ps.setTee(targetTee);
        ps.setRecordStory("");
        ps.setScore(0);
        ps.setGamePoint(0);
        ps.setFlight(0);
        ps.setEntryScore(0.0);
        ps.setNetScore(0.0);
        ps.setEntryPGCHandicap(0.0);
        ps.setCurrentRoundHandicap(0.0);
        playerScoreRepository.save(ps);
        return ResponseEntity.ok().build();
    }
}
