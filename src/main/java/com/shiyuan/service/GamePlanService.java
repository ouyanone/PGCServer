package com.shiyuan.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shiyuan.dao.entity.db.Event;
import com.shiyuan.dao.entity.db.EventPlayer;
import com.shiyuan.dao.entity.db.Player;
import com.shiyuan.dao.entity.db.PlayerScore;
import com.shiyuan.dao.entity.db.Tee;
import com.shiyuan.dao.repository.EventPlayerRepository;
import com.shiyuan.dao.repository.EventRepository;
import com.shiyuan.dao.repository.PlayerRepository;
import com.shiyuan.dao.repository.PlayerScoreRepository;
import com.shiyuan.dao.repository.TeeRepository;
import com.shiyuan.service.GroupingService.Strategy;

/**
 * An event's roster, tees and tee times: the logic behind both the website's
 * {@code /webapi/admin/gameplan/**} and the token-only {@code /api/admin/**}, so a fix to one is
 * a fix to both. The flags are where the two deliberately differ.
 */
@Service
public class GamePlanService {

    public record AddPlayersResult(List<Long> added, List<Long> alreadyOnRoster) {}

    public record TeeRef(long teeId, String teeName, String teeTime) {}

    private static final int TEE_SIZE = 4;

    private final EventRepository events;
    private final EventPlayerRepository roster;
    private final PlayerRepository players;
    private final TeeRepository tees;
    private final PlayerScoreRepository scores;
    private final GroupingService grouping;

    public GamePlanService(EventRepository events, EventPlayerRepository roster, PlayerRepository players,
                           TeeRepository tees, PlayerScoreRepository scores, GroupingService grouping) {
        this.events = events;
        this.roster = roster;
        this.players = players;
        this.tees = tees;
        this.scores = scores;
        this.grouping = grouping;
    }

    @Transactional
    public AddPlayersResult addPlayers(long eventId, List<Long> playerIds) {
        Event event = event(eventId);
        List<Long> added = new ArrayList<>();
        List<Long> existing = new ArrayList<>();
        for (Long playerId : playerIds) {
            if (roster.existsByEventIdAndPlayerId(eventId, playerId)) {
                existing.add(playerId);
                continue;
            }
            EventPlayer ep = new EventPlayer();
            ep.setEvent(event);
            ep.setPlayer(player(playerId));
            roster.save(ep);
            added.add(playerId);
        }
        return new AddPlayersResult(added, existing);
    }

    /**
     * Replaces every tee and player score of the event with fresh groups of the roster.
     *
     * @param refuseIfScored refuse (409, nothing deleted) when any player of the event already has
     *                       a recorded score -- regrouping would wipe it. The website passes false.
     */
    @Transactional
    public List<Map<String, Object>> generateGroups(long eventId, Strategy strategy, boolean refuseIfScored) {
        Event event = event(eventId);
        if (refuseIfScored) {
            long scored = tees.findByEventIdOrderByTeeName(eventId).stream()
                    .flatMap(t -> t.getPlayerScoreList().stream())
                    .filter(ps -> ps.getScore() > 0)
                    .count();
            if (scored > 0) {
                throw AdminRequestException.conflict("Event " + eventId + " already has " + scored
                        + " recorded score(s); regrouping would delete them, so nothing was changed.");
            }
        }

        List<Player> rosterPlayers = roster.findByEventId(eventId)
                .stream().map(EventPlayer::getPlayer).collect(Collectors.toList());
        if (rosterPlayers.isEmpty()) {
            throw AdminRequestException.badRequest("Event " + eventId
                    + " has no players on its roster; add players before grouping.");
        }

        // Bulk-delete player_scores then tees (bypasses JPA lifecycle / orphanRemoval conflicts)
        scores.deleteByEventId(eventId);
        tees.deleteByEventId(eventId);

        List<List<Player>> groups = grouping.generateGroups(rosterPlayers, strategy);
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            Tee tee = tees.save(newTee(event, "Tee " + (i + 1)));
            List<Map<String, Object>> groupPlayers = new ArrayList<>();
            for (Player player : groups.get(i)) {
                PlayerScore ps = scores.save(newScore(player, tee));
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
        return result;
    }

    /**
     * Puts one player into the first tee with an open place, or a new tee when all are full.
     *
     * @param ensureOnRoster the {@code /api} path: also add the player to the roster when missing,
     *                       and refuse (409) a player who is already in one of the event's tees.
     *                       The website passes false and keeps its old behaviour.
     */
    @Transactional
    public TeeRef assignPlayer(long eventId, long playerId, boolean ensureOnRoster) {
        Event event = event(eventId);
        Player player = player(playerId);
        List<Tee> eventTees = tees.findByEventIdOrderByTeeName(eventId);

        if (ensureOnRoster) {
            boolean alreadyGrouped = eventTees.stream()
                    .flatMap(t -> t.getPlayerScoreList().stream())
                    .anyMatch(ps -> ps.getPlayer().getId().equals(playerId));
            if (alreadyGrouped) {
                throw AdminRequestException.conflict("Player " + playerId + " is already in a tee for event "
                        + eventId + "; nothing was changed.");
            }
            if (!roster.existsByEventIdAndPlayerId(eventId, playerId)) {
                EventPlayer ep = new EventPlayer();
                ep.setEvent(event);
                ep.setPlayer(player);
                roster.save(ep);
            }
        }

        Tee target = eventTees.stream()
                .filter(t -> t.getPlayerScoreList().size() < TEE_SIZE)
                .findFirst()
                .orElseGet(() -> tees.save(newTee(event, "Tee " + (eventTees.size() + 1))));
        scores.save(newScore(player, target));
        return new TeeRef(target.getId(), target.getTeeName(), target.getTeeTime());
    }

    /** Sets a tee's time and/or name; {@code null} leaves that field as it is. */
    @Transactional
    public TeeRef updateTee(long teeId, String teeTime, String teeName) {
        Tee tee = tees.findById(teeId)
                .orElseThrow(() -> AdminRequestException.notFound("Tee " + teeId + " does not exist."));
        if (teeName != null) tee.setTeeName(teeName);
        if (teeTime != null) tee.setTeeTime(teeTime);
        tees.save(tee);
        return new TeeRef(tee.getId(), tee.getTeeName(), tee.getTeeTime());
    }

    private Event event(long eventId) {
        return events.findById(Long.valueOf(eventId))
                .orElseThrow(() -> AdminRequestException.notFound("Event " + eventId + " does not exist."));
    }

    private Player player(long playerId) {
        return players.findById(playerId)
                .orElseThrow(() -> AdminRequestException.notFound("Player " + playerId + " does not exist."));
    }

    private static Tee newTee(Event event, String name) {
        Tee tee = new Tee();
        tee.setTeeName(name);
        tee.setTeeDesc("");
        tee.setTeeStroy("");
        tee.setEvent(event);
        return tee;
    }

    private static PlayerScore newScore(Player player, Tee tee) {
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
        return ps;
    }
}
