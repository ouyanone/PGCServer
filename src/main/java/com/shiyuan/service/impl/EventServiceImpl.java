package com.shiyuan.service.impl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import com.shiyuan.dao.entity.EventGroup;
import com.shiyuan.dao.entity.db.Event;
import com.shiyuan.dao.entity.db.Player;
import com.shiyuan.dao.entity.db.PlayerScore;
import com.shiyuan.dao.entity.db.Tee;
import com.shiyuan.dao.repository.CourseRepository;
import com.shiyuan.dao.repository.EventRepository;
import com.shiyuan.dao.repository.PlayerScoreRepository;
import com.shiyuan.dao.repository.SeasonRepository;
import com.shiyuan.dao.repository.TeamRepository;
import com.shiyuan.dao.repository.TeeRepository;
import com.shiyuan.service.EventService;

@Service
public class EventServiceImpl implements EventService {

    @Autowired
    EventRepository eventRepository;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    SeasonRepository seasonRepository;

    @Autowired
    TeamRepository teamRepository;

    @Autowired
    TeeRepository teeRepository;

    @Autowired
    PlayerScoreRepository playerScoreRepository;

    @Override
    public void createEvent(EventGroup eventGroup) {
        // TODO: stub — implementation commented out in original code
    }

    @Override
    public List<Event> getAllEvents() {
        Iterable<Event> ei = eventRepository.findAllByOrderByEventDateDesc();
        List<Event> eList = new ArrayList<Event>();
        ei.forEach(eList::add);
        return eList;
    }

    @Override
    public Event getEventById(String strId) {
        Long id = Long.parseLong(strId);
        return eventRepository.findById(id).orElse(null);
    }

    @Override
    public void submitScore(Event event) {
        List<Tee> teeList = event.getTeeList();
        for (Tee tee : teeList) {
            tee.setEvent(event);
        }
        event.setStatus("CLOSED");
        eventRepository.save(event);
    }

    @Override
    public void deleteEventById(String strId) {
        Long id = Long.parseLong(strId);
        eventRepository.deleteById(id);
    }

    @Override
    public Event getUpcomingEvent() {
        LocalDate now = LocalDate.now();
        return eventRepository.findFirstByStatusAndEventDateGreaterThanEqualOrderByEventDate("INIT", now);
    }

    @Override
    public List<Event> getLatestEvents() {
        Iterable<Event> ei = eventRepository.findFirst3ByStatusOrderByEventDateDesc("FINISHED");
        List<Event> eList = new ArrayList<Event>();
        ei.forEach(eList::add);
        return eList;
    }

    @Override
    public Event saveEvent(Event event) {
        if (event.getId() != null) {
            // Update: load existing to preserve teeList (orphanRemoval would wipe it otherwise)
            Event existing = eventRepository.findById(event.getId()).orElse(null);
            if (existing != null) {
                existing.setEventName(event.getEventName());
                existing.setEventDesc(event.getEventDesc());
                existing.setEventDate(event.getEventDate());
                existing.setStatus(event.getStatus());
                existing.setCourse(resolveCourse(event));
                existing.setSeason(resolveSeason(event));
                return eventRepository.save(existing);
            }
        }
        // Create: brand-new entity
        event.setId(null);
        event.setTeeList(null);
        event.setPlayer(null);
        if (event.getEventDesc() == null) event.setEventDesc("");
        if (event.getEventStory() == null) event.setEventStory("");
        event.setCourse(resolveCourse(event));
        event.setSeason(resolveSeason(event));
        return eventRepository.save(event);
    }

    @Override
    @Transactional
    public void copyPgcHandicapToScores(Long eventId) {
        List<PlayerScore> scores = playerScoreRepository.getPlayerScoreForEvent(eventId);
        for (PlayerScore ps : scores) {
            Player player = ps.getPlayer();
            if (player != null) {
                // Copy as-is (may be null) from the player's current pgc_handicap
                ps.setEntryPGCHandicap(player.getPgcHandicap());
            }
        }
        playerScoreRepository.saveAll(scores);
    }

    private com.shiyuan.dao.entity.db.Course resolveCourse(Event event) {
        if (event.getCourse() != null && event.getCourse().getId() != null) {
            return courseRepository.findById(event.getCourse().getId()).orElse(null);
        }
        return null;
    }

    private com.shiyuan.dao.entity.db.Season resolveSeason(Event event) {
        if (event.getSeason() != null && event.getSeason().getId() != null) {
            return seasonRepository.findById(event.getSeason().getId()).orElse(null);
        }
        return null;
    }
}
