package com.shiyuan.chat;

import java.time.Duration;

/** How much one question may do: tool calls, and wall-clock time before giving up. */
public record QuestionLimits(int maxToolCalls, Duration deadline) {}
