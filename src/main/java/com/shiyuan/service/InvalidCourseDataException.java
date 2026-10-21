package com.shiyuan.service;

/**
 * Raised when scores cannot be submitted because the event's course is missing or its
 * rating/slope is unusable. The message is written for the admin and is surfaced
 * verbatim by the Upload Score page.
 */
public class InvalidCourseDataException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidCourseDataException(String message) {
        super(message);
    }
}
