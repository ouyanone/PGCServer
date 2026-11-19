package com.shiyuan.service;

import java.time.LocalDate;
import java.util.Locale;
import java.util.stream.StreamSupport;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shiyuan.controller.api.AdminApiDtos.CreateEvent;
import com.shiyuan.controller.api.AdminApiDtos.CreateReward;
import com.shiyuan.controller.api.AdminApiDtos.CreateTournament;
import com.shiyuan.dao.entity.db.Course;
import com.shiyuan.dao.entity.db.Event;
import com.shiyuan.dao.entity.db.Player;
import com.shiyuan.dao.entity.db.Reward;
import com.shiyuan.dao.entity.db.Season;
import com.shiyuan.dao.entity.db.Tournament;
import com.shiyuan.dao.repository.CourseRepository;
import com.shiyuan.dao.repository.EventRepository;
import com.shiyuan.dao.repository.PlayerRepository;
import com.shiyuan.dao.repository.SeasonRepository;
import com.shiyuan.dao.repository.TournamentRepository;

/**
 * Creating tournaments, their events and rewards. The plain methods are the website's; the
 * {@code ...Checked} ones are {@code /api/admin}'s and validate everything a model might get
 * wrong before writing anything.
 */
@Service
public class TournamentAdminService {

    private final TournamentRepository tournaments;
    private final EventRepository events;
    private final EventService eventService;
    private final SeasonRepository seasons;
    private final CourseRepository courses;
    private final PlayerRepository players;
    private final RewardService rewards;

    public TournamentAdminService(TournamentRepository tournaments, EventRepository events, EventService eventService,
                                  SeasonRepository seasons, CourseRepository courses, PlayerRepository players,
                                  RewardService rewards) {
        this.tournaments = tournaments;
        this.events = events;
        this.eventService = eventService;
        this.seasons = seasons;
        this.courses = courses;
        this.players = players;
        this.rewards = rewards;
    }

    @Transactional
    public Tournament createTournamentChecked(CreateTournament req) {
        String name = required(req.name(), "name");
        LocalDate start = required(req.startDate(), "startDate");
        LocalDate end = required(req.endDate(), "endDate");
        if (end.isBefore(start)) {
            throw AdminRequestException.badRequest("The end date " + end + " is before the start date " + start + ".");
        }
        Long seasonId = required(req.seasonId(), "seasonId");
        Season season = seasons.findById(seasonId)
                .orElseThrow(() -> AdminRequestException.notFound("Season " + seasonId + " does not exist."));
        String key = name.toLowerCase(Locale.ROOT);
        StreamSupport.stream(tournaments.findAll().spliterator(), false)
                .filter(t -> t.getName() != null && t.getName().trim().toLowerCase(Locale.ROOT).equals(key)
                        && start.equals(t.getStartDate()))
                .findFirst()
                .ifPresent(t -> {
                    throw AdminRequestException.conflict("Tournament " + t.getId() + " \"" + t.getName()
                            + "\" already starts on " + start + "; nothing was created.");
                });
        Tournament t = new Tournament();
        t.setName(name);
        t.setDescription(req.description() == null ? "" : req.description().trim());
        t.setStartDate(start);
        t.setEndDate(end);
        t.setSeason(season);
        return tournaments.save(t);
    }

    @Transactional
    public Event createEventChecked(long tournamentId, CreateEvent req) {
        Tournament t = tournaments.findById(tournamentId)
                .orElseThrow(() -> AdminRequestException.notFound("Tournament " + tournamentId + " does not exist."));
        String name = required(req.name(), "name");
        LocalDate date = required(req.date(), "date");
        if (t.getStartDate() != null && t.getEndDate() != null
                && (date.isBefore(t.getStartDate()) || date.isAfter(t.getEndDate()))) {
            throw AdminRequestException.badRequest("The event date " + date + " is outside the tournament's dates, "
                    + t.getStartDate() + " to " + t.getEndDate() + ".");
        }
        Long courseId = required(req.courseId(), "courseId");
        Course course = courses.findById(courseId)
                .orElseThrow(() -> AdminRequestException.notFound("Course " + courseId + " does not exist."));
        Event e = new Event();
        e.setEventName(name);
        e.setEventDesc(req.description() == null ? "" : req.description().trim());
        e.setEventStory("");
        e.setEventDate(date);
        e.setCourse(course);
        e.setSeason(t.getSeason());
        return createEvent(tournamentId, e);
    }

    @Transactional
    public Reward createRewardChecked(CreateReward req) {
        String name = required(req.rewardName(), "rewardName");
        Long eventId = required(req.eventId(), "eventId");
        Long playerId = required(req.playerId(), "playerId");
        Event event = events.findById(eventId)
                .orElseThrow(() -> AdminRequestException.notFound("Event " + eventId + " does not exist."));
        Player player = players.findById(playerId)
                .orElseThrow(() -> AdminRequestException.notFound("Player " + playerId + " does not exist."));
        Reward r = new Reward();
        r.setRewardName(name);
        r.setRewardDesc(req.rewardDesc() == null ? "" : req.rewardDesc().trim());
        r.setRewardStory("");
        r.setRewardGroup(req.rewardGroup());
        r.setDisplayOrder(req.displayOrder());
        r.setEvent(event);
        r.setPlayer(player);
        return rewards.createReward(r);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw AdminRequestException.badRequest("The " + field + " is required.");
        }
        return value.trim();
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw AdminRequestException.badRequest("The " + field + " is required.");
        }
        return value;
    }

    /** The website's create: saves what it is given as a new tournament. */
    @Transactional
    public Tournament createTournament(Tournament tournament) {
        tournament.setId(null);
        return tournaments.save(tournament);
    }

    /** The website's create-event-in-tournament. */
    @Transactional
    public Event createEvent(long tournamentId, Event event) {
        Tournament t = tournaments.findById(tournamentId)
                .orElseThrow(() -> AdminRequestException.notFound("Tournament " + tournamentId + " does not exist."));
        event.setId(null);
        if (event.getEventDesc() == null) event.setEventDesc("");
        Event saved = eventService.saveEvent(event);
        saved.setTournament(t);
        return events.save(saved);
    }
}
