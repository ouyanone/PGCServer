package com.shiyuan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Either an answer, or a refusal code ({@code daily_limit}, {@code budget}, {@code signin},
 * {@code unavailable}) with a sentence the panel can show as-is.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatResponse(String answer, String refusal, String message) {

    public static ChatResponse answer(String text) {
        return new ChatResponse(text, null, null);
    }

    public static ChatResponse refusal(String code, String message) {
        return new ChatResponse(null, code, message);
    }
}
