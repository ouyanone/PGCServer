package com.shiyuan.controller;

import com.shiyuan.dao.entity.db.Event;
import com.shiyuan.dao.entity.db.Tournament;
import com.shiyuan.dao.repository.EventRepository;
import com.shiyuan.dao.repository.TournamentRepository;
import com.shiyuan.service.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class TournamentController extends BaseController {

    @Autowired private TournamentRepository tournamentRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private EventService eventService;

    // ── List all tournaments (with events) ───────────────────────────────────

    @GetMapping(path = "/webapi/tournaments", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Tournament>> getAll() {
        return ResponseEntity.ok(tournamentRepository.findAllByOrderByStartDateDesc());
    }

    // ── Create tournament ─────────────────────────────────────────────────────

    @PostMapping(path = "/webapi/admin/tournaments", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<Tournament> create(@RequestBody Tournament tournament,
                                             @AuthenticationPrincipal OidcUser principal) throws Exception {
        checkPrincipal(principal);
        tournament.setId(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(tournamentRepository.save(tournament));
    }

    // ── Update tournament ─────────────────────────────────────────────────────

    @PutMapping(path = "/webapi/admin/tournaments/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<Tournament> update(@PathVariable Long id,
                                             @RequestBody Tournament tournament,
                                             @AuthenticationPrincipal OidcUser principal) throws Exception {
        checkPrincipal(principal);
        tournament.setId(id);
        return ResponseEntity.ok(tournamentRepository.save(tournament));
    }

    // ── Delete tournament (unlinks events first) ──────────────────────────────

    @DeleteMapping(path = "/webapi/admin/tournaments/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @AuthenticationPrincipal OidcUser principal) throws Exception {
        checkPrincipal(principal);
        Tournament t = tournamentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tournament not found: " + id));
        if (t.getEvents() != null) {
            for (Event e : t.getEvents()) {
                e.setTournament(null);
                eventRepository.save(e);
            }
        }
        tournamentRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    // ── Add existing event to tournament ──────────────────────────────────────

    @PostMapping(path = "/webapi/admin/tournaments/{tournamentId}/events/{eventId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<Void> addEvent(@PathVariable Long tournamentId,
                                         @PathVariable Long eventId,
                                         @AuthenticationPrincipal OidcUser principal) throws Exception {
        checkPrincipal(principal);
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("Tournament not found: " + tournamentId));
        Event e = eventRepository.findById(eventId.longValue());
        if (e == null) throw new RuntimeException("Event not found: " + eventId);
        e.setTournament(t);
        eventRepository.save(e);
        return ResponseEntity.ok().build();
    }

    // ── Remove event from tournament (unlink, do not delete) ─────────────────

    @DeleteMapping(path = "/webapi/admin/tournaments/{tournamentId}/events/{eventId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<Void> removeEvent(@PathVariable Long tournamentId,
                                            @PathVariable Long eventId,
                                            @AuthenticationPrincipal OidcUser principal) throws Exception {
        checkPrincipal(principal);
        Event e = eventRepository.findById(eventId.longValue());
        if (e == null) throw new RuntimeException("Event not found: " + eventId);
        e.setTournament(null);
        eventRepository.save(e);
        return ResponseEntity.ok().build();
    }

    // ── Create new event directly under a tournament ──────────────────────────

    @PostMapping(path = "/webapi/admin/tournaments/{tournamentId}/events", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<Event> createEvent(@PathVariable Long tournamentId,
                                             @RequestBody Event event,
                                             @AuthenticationPrincipal OidcUser principal) throws Exception {
        checkPrincipal(principal);
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("Tournament not found: " + tournamentId));
        event.setId(null);
        if (event.getEventDesc() == null) event.setEventDesc("");
        Event saved = eventService.saveEvent(event);
        saved.setTournament(t);
        return ResponseEntity.status(HttpStatus.CREATED).body(eventRepository.save(saved));
    }
}
