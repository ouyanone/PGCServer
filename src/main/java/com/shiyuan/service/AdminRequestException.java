package com.shiyuan.service;

/**
 * An admin request that cannot be carried out as asked, with the HTTP status that says why and a
 * message written for the person (or bot) that asked. Still a {@link RuntimeException}, so the
 * website's controllers, which never mapped exceptions to statuses, answer exactly as before.
 */
public class AdminRequestException extends RuntimeException {

    private final int status;

    private AdminRequestException(int status, String message) {
        super(message);
        this.status = status;
    }

    public static AdminRequestException badRequest(String message) {
        return new AdminRequestException(400, message);
    }

    public static AdminRequestException notFound(String message) {
        return new AdminRequestException(404, message);
    }

    public static AdminRequestException conflict(String message) {
        return new AdminRequestException(409, message);
    }

    public int status() {
        return status;
    }
}
