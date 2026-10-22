package com.shiyuan.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shiyuan.dao.entity.db.Course;
import com.shiyuan.dao.entity.db.Event;
import com.shiyuan.dao.entity.db.Player;
import com.shiyuan.dao.entity.db.PlayerScore;
import com.shiyuan.dao.entity.db.Season;
import com.shiyuan.dao.repository.PlayerRepository;
import com.shiyuan.dao.repository.PlayerScoreRepository;
import com.shiyuan.dao.repository.SeasonRepository;
import com.shiyuan.dto.PlayerScoreHistory;
import com.shiyuan.service.PlayerNotFoundException;
import com.shiyuan.service.ScoreHistoryService;

@Service
public class ScoreHistoryServiceImpl implements ScoreHistoryService {

    /** CLOSED is an archived FINISHED event, so it still counts as played — as for handicaps. */
    private static final List<String> PLAYED_STATUSES = List.of("FINISHED", "CLOSED");

    private static final int HOLES = 18;
    private static final int FRONT_NINE = 9;

    @Autowired
    private PlayerScoreRepository playerScoreRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private SeasonRepository seasonRepository;

    @Override
    @Transactional(readOnly = true)
    public PlayerScoreHistory getHistory(Long playerId) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new PlayerNotFoundException("Player " + playerId + " was not found."));

        List<PlayerScore> scores = playerScoreRepository.findHistoryForPlayer(playerId, PLAYED_STATUSES);

        List<PlayerScoreHistory.Round> rounds = new ArrayList<>();
        for (PlayerScore ps : scores) {
            rounds.add(toRound(ps));
        }

        PlayerScoreHistory history = new PlayerScoreHistory();
        history.setPlayerId(player.getId());
        history.setFirstName(player.getfName());
        history.setLastName(player.getlName());
        history.setPgcHandicap(player.getPgcHandicap());
        history.setHandicap(player.getHandicap());
        history.setRounds(rounds);

        // Seasons are calendar-year rows; outside any of them there is no current season
        // and the view falls back to showing everything.
        Season current = seasonRepository.findCurrentSeason(Date.valueOf(LocalDate.now()));
        if (current != null) {
            history.setCurrentSeasonId(current.getId());
            history.setCurrentSeasonName(current.getSeasonName());
        }

        summarise(history, rounds);
        return history;
    }

    /** Best / average / latest across the whole history. Left null when nothing was played. */
    private void summarise(PlayerScoreHistory history, List<PlayerScoreHistory.Round> rounds) {
        history.setRoundsPlayed(rounds.size());
        if (rounds.isEmpty()) {
            return;
        }
        int best = Integer.MAX_VALUE;
        int total = 0;
        for (PlayerScoreHistory.Round r : rounds) {
            int gross = r.getGross() != null ? r.getGross() : 0;
            best = Math.min(best, gross);
            total += gross;
        }
        history.setBestGross(best);
        history.setLatestGross(rounds.get(0).getGross()); // rounds are newest first
        history.setAverageGross(BigDecimal.valueOf((double) total / rounds.size())
                .setScale(1, RoundingMode.HALF_UP).doubleValue());
    }

    private PlayerScoreHistory.Round toRound(PlayerScore ps) {
        PlayerScoreHistory.Round r = new PlayerScoreHistory.Round();

        Event event = ps.getTee() != null ? ps.getTee().getEvent() : null;
        Course course = event != null ? event.getCourse() : null;

        if (event != null) {
            r.setEventId(event.getId());
            r.setEventName(event.getEventName());
            r.setEventDate(event.getEventDate());
        }
        if (course != null) {
            r.setClubName(course.getClubName());
            r.setCourseName(course.getCourseName());
        }
        if (event != null && event.getSeason() != null) {
            r.setSeasonId(event.getSeason().getId());
            r.setSeasonName(event.getSeason().getSeasonName());
        }

        r.setGross(ps.getScore());
        r.setNetScore(ps.getNetScore());
        r.setDifferential(ps.getCurrentRoundHandicap());
        r.setGamePoint(ps.getGamePoint());

        Integer[] offsets = offsetsOf(ps);
        Integer[] pars = parsOf(course);

        // Without a full set of per-hole pars the offsets cannot be turned into strokes, so show
        // them as they are rather than failing the whole history for one bad course.
        if (pars == null) {
            r.setHoles(offsets);
            r.setPars(null);
            r.setCoursePar(null);
        } else {
            Integer[] strokes = new Integer[HOLES];
            for (int i = 0; i < HOLES; i++) {
                strokes[i] = (offsets[i] == null) ? null : pars[i] + offsets[i];
            }
            r.setHoles(strokes);
            r.setPars(pars);
            r.setCoursePar(sum(pars, 0, HOLES));
            r.setOutStrokes(sum(strokes, 0, FRONT_NINE));
            r.setInStrokes(sum(strokes, FRONT_NINE, HOLES));
            r.setOutPar(sum(pars, 0, FRONT_NINE));
            r.setInPar(sum(pars, FRONT_NINE, HOLES));
        }

        countResults(r, offsets);
        return r;
    }

    /** Eagle or better, birdie and par counts, read from the stored offsets. */
    private void countResults(PlayerScoreHistory.Round r, Integer[] offsets) {
        int eagles = 0, birdies = 0, parsMade = 0;
        for (Integer o : offsets) {
            if (o == null) continue;
            if (o <= -2) eagles++;
            else if (o == -1) birdies++;
            else if (o == 0) parsMade++;
        }
        r.setEagles(eagles);
        r.setBirdies(birdies);
        r.setParsMade(parsMade);
    }

    private int sum(Integer[] values, int fromInclusive, int toExclusive) {
        int total = 0;
        for (int i = fromInclusive; i < toExclusive; i++) {
            if (values[i] != null) total += values[i];
        }
        return total;
    }

    private Integer[] offsetsOf(PlayerScore ps) {
        return new Integer[] {
            ps.getHole1(), ps.getHole2(), ps.getHole3(), ps.getHole4(), ps.getHole5(), ps.getHole6(),
            ps.getHole7(), ps.getHole8(), ps.getHole9(), ps.getHole10(), ps.getHole11(), ps.getHole12(),
            ps.getHole13(), ps.getHole14(), ps.getHole15(), ps.getHole16(), ps.getHole17(), ps.getHole18()
        };
    }

    /** Null unless the course supplies a par for every hole. */
    private Integer[] parsOf(Course c) {
        if (c == null) {
            return null;
        }
        Integer[] pars = new Integer[] {
            c.getHole1(), c.getHole2(), c.getHole3(), c.getHole4(), c.getHole5(), c.getHole6(),
            c.getHole7(), c.getHole8(), c.getHole9(), c.getHole10(), c.getHole11(), c.getHole12(),
            c.getHole13(), c.getHole14(), c.getHole15(), c.getHole16(), c.getHole17(), c.getHole18()
        };
        for (Integer p : pars) {
            if (p == null || p <= 0) {
                return null;
            }
        }
        return pars;
    }
}
