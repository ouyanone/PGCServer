package com.shiyuan.service.impl;

import com.shiyuan.dao.entity.db.*;
import com.shiyuan.dao.repository.*;
import com.shiyuan.dto.EventScoreDetail;
import com.shiyuan.service.PointsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PointsServiceImpl implements PointsService {

    @Autowired private EventRepository eventRepository;
    @Autowired private PlayerScoreRepository playerScoreRepository;
    @Autowired private PlayerRepository playerRepository;
    @Autowired private SeasonRepository seasonRepository;
    @Autowired private RewardRepository rewardRepository;

    @Override
    @Transactional
    public void calculateCurrentSeasonPoints() {
        Season currentSeason = seasonRepository.findCurrentSeason(Date.valueOf(LocalDate.now()));
        if (currentSeason == null) return;

        List<Event> events = eventRepository.findBySeasonIdAndStatusIn(
                currentSeason.getId(), List.of("FINISHED", "CLOSED"));

        // Build points map in memory from PlayerScore data — avoids re-querying
        // event_player within the same transaction (Hibernate flush timing issue)
        Map<Long, List<Integer>> pointsByPlayer = new HashMap<>();

        for (Event event : events) {
            Map<Long, Integer> eventPoints = calculateAndSaveEventPoints(event);
            for (Map.Entry<Long, Integer> entry : eventPoints.entrySet()) {
                pointsByPlayer
                        .computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                        .add(entry.getValue());
            }
        }

        // Write totals directly to DB via @Modifying query — bypasses entity cache
        List<Player> activePlayers = playerRepository.findByIsActiveTrueOrderByPgcPointsDesc();
        for (Player player : activePlayers) {
            List<Integer> pts = pointsByPlayer.getOrDefault(player.getId(), Collections.emptyList());
            int total = pts.stream()
                    .sorted(Comparator.reverseOrder())
                    .limit(3)
                    .mapToInt(Integer::intValue)
                    .sum();
            playerRepository.updatePgcPoints(player.getId(), total);
        }
    }

    private Map<Long, Integer> calculateAndSaveEventPoints(Event event) {
        Map<Long, Integer> eventPoints = new HashMap<>();

        List<PlayerScore> scores = playerScoreRepository.getPlayerScoreForEvent(event.getId());
        List<PlayerScore> valid = scores.stream()
                .filter(s -> s.getScore() != null && s.getScore() > 0)
                .sorted(scoreComparator())
                .collect(Collectors.toList());

        // Assign points respecting ties: tied players share the higher rank's points,
        // and the next player skips the consumed ranks.
        int rank = 0;
        for (int i = 0; i < valid.size(); i++) {
            if (i > 0 && !isTied(valid.get(i - 1), valid.get(i))) {
                rank = i;
            }
            int points = Math.max(0, 16 - rank);
            PlayerScore ps = valid.get(i);
            ps.setGamePoint(points);
            playerScoreRepository.save(ps);
            eventPoints.put(ps.getPlayer().getId(), points);
        }
        return eventPoints;
    }

    private Comparator<PlayerScore> scoreComparator() {
        return Comparator
                .comparingInt((PlayerScore ps) -> ps.getScore())
                .thenComparingInt(ps -> holeSum(ps.getHole10(), ps.getHole11(), ps.getHole12(),
                        ps.getHole13(), ps.getHole14(), ps.getHole15(),
                        ps.getHole16(), ps.getHole17(), ps.getHole18()))
                .thenComparingInt(ps -> holeSum(ps.getHole13(), ps.getHole14(), ps.getHole15(),
                        ps.getHole16(), ps.getHole17(), ps.getHole18()))
                .thenComparingInt(ps -> holeSum(ps.getHole16(), ps.getHole17(), ps.getHole18()))
                .thenComparingInt(ps -> ps.getHole18() != null ? ps.getHole18() : 0);
    }

    private boolean isTied(PlayerScore a, PlayerScore b) {
        return a.getScore().equals(b.getScore())
                && holeSum(a.getHole10(), a.getHole11(), a.getHole12(), a.getHole13(), a.getHole14(), a.getHole15(), a.getHole16(), a.getHole17(), a.getHole18())
                == holeSum(b.getHole10(), b.getHole11(), b.getHole12(), b.getHole13(), b.getHole14(), b.getHole15(), b.getHole16(), b.getHole17(), b.getHole18())
                && holeSum(a.getHole13(), a.getHole14(), a.getHole15(), a.getHole16(), a.getHole17(), a.getHole18())
                == holeSum(b.getHole13(), b.getHole14(), b.getHole15(), b.getHole16(), b.getHole17(), b.getHole18())
                && holeSum(a.getHole16(), a.getHole17(), a.getHole18()) == holeSum(b.getHole16(), b.getHole17(), b.getHole18())
                && (a.getHole18() == null ? 0 : a.getHole18()) == (b.getHole18() == null ? 0 : b.getHole18());
    }

    private int holeSum(Integer... vals) {
        int sum = 0;
        for (Integer v : vals) if (v != null) sum += v;
        return sum;
    }

    @Override
    public List<Player> getStandings() {
        return playerRepository.findByIsActiveTrueOrderByPgcPointsDesc();
    }

    @Override
    public List<EventScoreDetail> getCurrentSeasonGameScores() {
        Season currentSeason = seasonRepository.findCurrentSeason(Date.valueOf(LocalDate.now()));
        if (currentSeason == null) return Collections.emptyList();

        List<Event> events = eventRepository.findBySeasonIdAndStatusIn(
                currentSeason.getId(), List.of("FINISHED", "CLOSED"));

        events.sort(Comparator.comparing(Event::getEventDate).reversed());
        return events.stream().map(this::buildEventScoreDetail).collect(Collectors.toList());
    }

    @Override
    public EventScoreDetail getEventScoreDetail(Long eventId) {
        Event event = eventRepository.findById(eventId.longValue());
        if (event == null) return null;
        return buildEventScoreDetail(event);
    }

    @Override
    public List<EventScoreDetail> getTournamentScoreDetails(Long tournamentId) {
        List<Event> all = (List<Event>) eventRepository.findAllByOrderByEventDateDesc();
        return all.stream()
                .filter(e -> tournamentId.equals(e.getTournamentId())
                        && ("FINISHED".equals(e.getStatus()) || "CLOSED".equals(e.getStatus())))
                .sorted(Comparator.comparing(Event::getEventDate).reversed())
                .map(this::buildEventScoreDetail)
                .collect(Collectors.toList());
    }

    private EventScoreDetail buildEventScoreDetail(Event event) {
        List<PlayerScore> scores = playerScoreRepository.getPlayerScoreForEvent(event.getId());
        List<EventScoreDetail.PlayerScoreRow> rows = scores.stream()
                .filter(s -> s.getScore() != null && s.getScore() > 0)
                .sorted(Comparator.comparingInt(PlayerScore::getScore)
                        .thenComparing(Comparator.comparingInt(
                                (PlayerScore s) -> s.getGamePoint() != null ? s.getGamePoint() : 0).reversed()))
                .map(s -> {
                    String name = s.getPlayer().getfName() + " " + s.getPlayer().getlName();
                    return new EventScoreDetail.PlayerScoreRow(
                            name,
                            s.getPlayer().getGender(),
                            s.getPlayer().getPgcHandicap(),
                            s.getPlayer().getHandicap(),
                            s.getHole1(), s.getHole2(), s.getHole3(),
                            s.getHole4(), s.getHole5(), s.getHole6(),
                            s.getHole7(), s.getHole8(), s.getHole9(),
                            s.getHole10(), s.getHole11(), s.getHole12(),
                            s.getHole13(), s.getHole14(), s.getHole15(),
                            s.getHole16(), s.getHole17(), s.getHole18(),
                            s.getScore(), s.getNetScore(), s.getGamePoint()
                    );
                }).collect(Collectors.toList());

        String courseName = event.getCourse() != null ? event.getCourse().getCourseName() : "";
        Course course = event.getCourse();
        Integer[] pars = course != null ? new Integer[]{
            course.getHole1(),  course.getHole2(),  course.getHole3(),
            course.getHole4(),  course.getHole5(),  course.getHole6(),
            course.getHole7(),  course.getHole8(),  course.getHole9(),
            course.getHole10(), course.getHole11(), course.getHole12(),
            course.getHole13(), course.getHole14(), course.getHole15(),
            course.getHole16(), course.getHole17(), course.getHole18()
        } : new Integer[18];

        List<EventScoreDetail.RewardRow> rewardRows = rewardRepository.findRewardsByEventId(event.getId())
                .stream().map(r -> {
                    String playerName = r.getPlayer() != null
                            ? r.getPlayer().getfName() + " " + r.getPlayer().getlName()
                            : "";
                    return new EventScoreDetail.RewardRow(
                            r.getRewardName(), r.getRewardDesc(), r.getRewardStory(),
                            playerName, r.getDisplayOrder(), r.getRewardGroup());
                }).collect(Collectors.toList());

        int tournamentTotalEvents = (event.getTournament() != null && event.getTournament().getEvents() != null)
                ? event.getTournament().getEvents().size() : 0;

        return new EventScoreDetail(event.getId(), event.getEventName(),
                event.getEventDate(), courseName, pars,
                event.getTournamentId(), event.getTournamentName(), tournamentTotalEvents,
                rows, rewardRows);
    }
}
