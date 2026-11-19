package com.shiyuan.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shiyuan.dao.entity.db.Event;
import com.shiyuan.dao.entity.db.Tournament;
import com.shiyuan.dao.repository.EventRepository;
import com.shiyuan.dao.repository.TournamentRepository;

/** Creating tournaments and their events, for the website and for {@code /api/admin}. */
@Service
public class TournamentAdminService {

    private final TournamentRepository tournaments;
    private final EventRepository events;
    private final EventService eventService;

    public TournamentAdminService(TournamentRepository tournaments, EventRepository events, EventService eventService) {
        this.tournaments = tournaments;
        this.events = events;
        this.eventService = eventService;
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
