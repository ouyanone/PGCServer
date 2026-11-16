package com.shiyuan.chat;

import java.util.List;
import java.util.Map;

/**
 * The club assistant's view of Claude: start a conversation, send it, hand back tool results.
 *
 * <p>The conversation keeps the transcript itself, so the model's own responses -- thinking
 * blocks included -- go back unchanged on the next request, as the API requires in a tool loop.
 * {@link AnthropicModelGateway} is the only class that touches the Anthropic SDK.
 */
public interface ModelGateway {

    Conversation start(String system, List<McpToolDef> tools, List<ChatExchangeDto> history, String question);

    interface Conversation {

        /** Sends the transcript so far and appends the model's reply to it. */
        Reply send();

        /** Appends one user turn carrying the results of every tool call in the last reply. */
        void addToolResults(List<ToolResult> results);
    }

    /** {@code stopReason} is the API's value: end_turn, tool_use, max_tokens, refusal, ... */
    record Reply(String text, List<ToolCall> toolCalls, String stopReason, long inputTokens, long outputTokens) {}

    record ToolCall(String id, String name, Map<String, Object> arguments) {}

    record ToolResult(String toolUseId, String text, boolean isError) {}
}
