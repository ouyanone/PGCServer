package com.shiyuan.service;

import java.util.List;

import com.shiyuan.dao.entity.EventGroup;
import com.shiyuan.dao.entity.db.Event;
public interface EventService {

    void createEvent(EventGroup eventGroup);

    List<Event> getAllEvents();

    Event getEventById(String id);

    void submitScore(Event event);

    void deleteEventById(String id);

    Event getUpcomingEvent();

    List<Event> getLatestEvents();

    Event saveEvent(Event event);
}
