package com.shiyuan.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.shiyuan.service.InvalidCourseDataException;

/**
 * A rejected upload has to reach the admin as a readable message, not a 500.
 */
class ScoreControllerTest {

    @Test
    void invalidCourseData_isReportedAsBadRequestWithTheMessage() {
        ScoreController controller = new ScoreController();
        String message = "Event '2026 Test Game' has no course assigned.";

        ResponseEntity<Object> response =
                controller.handleInvalidCourseData(new InvalidCourseDataException(message));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(message, response.getBody());
    }
}
