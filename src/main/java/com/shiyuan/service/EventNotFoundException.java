package com.shiyuan.service;

/**
 * Raised when an operation targets an event id that does not exist. Reported as a 404
 * rather than surfacing as a 500 from deeper in the persistence layer.
 */
public class EventNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EventNotFoundException(String message) {
        super(message);
    }
}
