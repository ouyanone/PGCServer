package com.shiyuan.chat;

/** Claude could not be reached or answered with an error (rate limit, overload, timeout, 5xx). */
public class ModelUnavailableException extends RuntimeException {
    public ModelUnavailableException(String message) {
        super(message, null, false, false);
    }
}
