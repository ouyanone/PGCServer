package com.shiyuan.chat;

import java.time.Instant;

/** One past question and answer, as the panel shows it and the model receives it. */
public record ChatExchangeDto(String question, String answer, Instant at) {}
