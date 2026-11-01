package com.shiyuan.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.shiyuan.dao.entity.EventGroup;
import com.shiyuan.dao.entity.db.Course;
import com.shiyuan.dao.entity.db.Event;
import com.shiyuan.dao.entity.db.Season;
import com.shiyuan.dao.repository.CourseRepository;
import com.shiyuan.dao.repository.SeasonRepository;
import com.shiyuan.service.EventNotFoundException;
import com.shiyuan.service.EventService;
import com.shiyuan.service.HandicapService;

@RestController
public class EventController extends BaseController {

    private final static Logger log = LoggerFactory.getLogger(EventController.class);

    @Autowired
    EventService eventService;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    SeasonRepository seasonRepository;

    @Autowired
    HandicapService handicapService;

    /** Statuses that mean the round has been played out. */
    private static final List<String> PLAYED_STATUSES = List.of("FINISHED", "CLOSED");

    /** A missing event is a 404, not a 500 leaking out of the persistence layer. */
    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<Object> handleEventNotFound(EventNotFoundException ex) {
        return new ResponseEntity<Object>(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    private boolean isPlayed(String status) {
        return status != null && PLAYED_STATUSES.stream().anyMatch(s -> s.equalsIgnoreCase(status));
    }

    /**
     * Recalculate rolling handicaps when an event first becomes played out. FINISHED to
     * CLOSED is not a transition worth acting on — the qualifying round set is unchanged.
     */
    private void recalculateHandicapsIfNowPlayed(Long eventId, String previousStatus, String newStatus) {
        if (isPlayed(newStatus) && !isPlayed(previousStatus)) {
            int updated = handicapService.recalculateForEvent(eventId);
            log.info("Event {} reached {}; recalculated handicaps for {} player(s)", eventId, newStatus, updated);
        }
    }

    @PostMapping(path = "/webapi/admin/game/grouping", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> createEventGroup(@RequestBody EventGroup eventGroup, @AuthenticationPrincipal OidcUser principal) throws Exception {
        log.debug("input eventGroup=" + eventGroup);
        System.out.println("input eventGroup=" + eventGroup);
        checkPrincipal(principal);
        eventService.createEvent(eventGroup);
        return new ResponseEntity<Object>(null, HttpStatus.OK);
    }

    @GetMapping(path = "/webapi/events", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getAllEvents() throws Exception {
        List<Event> eList = eventService.getAllEvents();
        System.out.println("pList ======  " + eList.size());
        return new ResponseEntity<Object>(eList, HttpStatus.OK);
    }

    @GetMapping(path = "/webapi/events/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getEventsById(@PathVariable("id") String id) throws Exception {
        Event event = eventService.getEventById(id);
        System.out.println("event ======  " + event);
        return new ResponseEntity<Object>(event, HttpStatus.OK);
    }

    @GetMapping(path = "/webapi/events/find/ongoing", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getOngoingEvent() throws Exception {
        String id = "1144";
        Event event = eventService.getEventById(id);
        System.out.println("event ======  " + event);
        return new ResponseEntity<Object>(event, HttpStatus.OK);
    }

    @GetMapping(path = "/webapi/events/upcoming", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getUpcomingEvent() throws Exception {
        Event event = eventService.getUpcomingEvent();
        System.out.println("upcoming event ======  " + event);
        return new ResponseEntity<Object>(event, HttpStatus.OK);
    }

    @GetMapping(path = "/webapi/events/latest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getLatestEvents() throws Exception {
        List<Event> eList = eventService.getLatestEvents();
        System.out.println("pList ======  " + eList.size());
        return new ResponseEntity<Object>(eList, HttpStatus.OK);
    }

    @PostMapping(path = "/webapi/admin/game/submitScore", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> submitScore(@RequestBody Event event, @AuthenticationPrincipal OidcUser principal) throws Exception {
        log.debug("input eventGroup=" + event);
        System.out.println("input submitScore=" + event);
        checkPrincipal(principal);
        eventService.submitScore(event);
        return new ResponseEntity<Object>(null, HttpStatus.OK);
    }

    @DeleteMapping(path = "/webapi/admin/events/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> deleteEventsById(@PathVariable("id") String id, @AuthenticationPrincipal OidcUser principal) throws Exception {
        checkPrincipal(principal);
        eventService.deleteEventById(id);
        return new ResponseEntity<Object>(null, HttpStatus.OK);
    }

    @PostMapping(path = "/webapi/admin/events", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Event> createEvent(@RequestBody Event event, @AuthenticationPrincipal OidcUser principal) throws Exception {
        checkPrincipal(principal);
        event.setId(null);
        Event saved = eventService.saveEvent(event);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping(path = "/webapi/admin/events/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Event> updateEvent(@PathVariable Long id, @RequestBody Event event, @AuthenticationPrincipal OidcUser principal) throws Exception {
        checkPrincipal(principal);
        // Read the previous status first: this is the path the Edit dialog uses to set
        // FINISHED, so it is where the handicap recalculation has to hang.
        Event existing = eventService.getEventById(String.valueOf(id));
        String previousStatus = existing != null ? existing.getStatus() : null;

        event.setId(id);
        Event saved = eventService.saveEvent(event);
        recalculateHandicapsIfNowPlayed(id, previousStatus, saved.getStatus());
        return ResponseEntity.ok(saved);
    }

    @PutMapping(path = "/webapi/admin/events/{id}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> updateEventStatus(@PathVariable Long id,
                                                  @RequestBody java.util.Map<String, String> body,
                                                  @AuthenticationPrincipal OidcUser principal) throws Exception {
        checkPrincipal(principal);
        Event event = eventService.getEventById(String.valueOf(id));
        String previousStatus = event.getStatus();
        String newStatus = body.get("status");
        event.setStatus(newStatus);
        eventService.saveEvent(event);
        // On any transition into STARTED, snapshot each player's current pgc_handicap
        // into their player_score row (entry_pgc_handicap).
        if ("STARTED".equalsIgnoreCase(newStatus)) {
            eventService.copyPgcHandicapToScores(id);
        }
        recalculateHandicapsIfNowPlayed(id, previousStatus, newStatus);
        return ResponseEntity.ok().build();
    }

    /**
     * Re-run the rolling handicap for every active player. Used after correcting a score,
     * and once at cutover so existing FINISHED events are reflected.
     */
    @PostMapping(path = "/webapi/admin/handicaps/recalculate", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> recalculateHandicaps(@AuthenticationPrincipal OidcUser principal) throws Exception {
        checkPrincipal(principal);
        int updated = handicapService.recalculateAll();
        log.info("Manual handicap recalculation updated {} player(s)", updated);
        return ResponseEntity.ok(java.util.Map.of("playersUpdated", updated));
    }

    @GetMapping(path = "/webapi/courses", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Course>> getAllCourses() {
        List<Course> list = new java.util.ArrayList<>();
        courseRepository.findAll().forEach(list::add);
        return ResponseEntity.ok(list);
    }

    @PostMapping(path = "/webapi/admin/courses", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Course> createCourse(@RequestBody Course course,
                                               @AuthenticationPrincipal OidcUser principal) throws Exception {
        checkPrincipal(principal);
        course.setId(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(courseRepository.save(course));
    }

    @PutMapping(path = "/webapi/admin/courses/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Course> updateCourse(@PathVariable Long id,
                                               @RequestBody Course course,
                                               @AuthenticationPrincipal OidcUser principal) throws Exception {
        checkPrincipal(principal);
        course.setId(id);
        return ResponseEntity.ok(courseRepository.save(course));
    }

    @DeleteMapping(path = "/webapi/admin/courses/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deleteCourse(@PathVariable Long id,
                                             @AuthenticationPrincipal OidcUser principal) throws Exception {
        checkPrincipal(principal);
        courseRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping(path = "/webapi/seasons", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Season>> getAllSeasons() {
        List<Season> list = new java.util.ArrayList<>();
        seasonRepository.findAll().forEach(list::add);
        return ResponseEntity.ok(list);
    }
}
