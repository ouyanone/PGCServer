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
import com.shiyuan.service.EventService;

@RestController
public class EventController extends BaseController {

    private final static Logger log = LoggerFactory.getLogger(EventController.class);

    @Autowired
    EventService eventService;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    SeasonRepository seasonRepository;

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
        event.setId(id);
        Event saved = eventService.saveEvent(event);
        return ResponseEntity.ok(saved);
    }

    @GetMapping(path = "/webapi/courses", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Course>> getAllCourses() {
        List<Course> list = new java.util.ArrayList<>();
        courseRepository.findAll().forEach(list::add);
        return ResponseEntity.ok(list);
    }

    @GetMapping(path = "/webapi/seasons", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Season>> getAllSeasons() {
        List<Season> list = new java.util.ArrayList<>();
        seasonRepository.findAll().forEach(list::add);
        return ResponseEntity.ok(list);
    }
}
