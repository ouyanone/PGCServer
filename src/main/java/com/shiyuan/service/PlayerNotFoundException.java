package com.shiyuan.service;

/** Raised when an operation targets a player id that does not exist. Reported as a 404. */
public class PlayerNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PlayerNotFoundException(String message) {
        super(message);
    }
}
