package com.shiyuan.chat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Answers one member question: Claude decides which MCP tools to call, this class calls them
 * with the member's session, and the loop ends at a text answer or at five tool calls.
 */
public class ChatAssistant {

    static final String TOO_MANY_LOOKUPS = "I couldn't finish looking that up — please try a narrower question.";
    static final String CANNOT_HELP = "Sorry, I can't help with that one.";

    private final ModelGateway model;
    private final ChatPricing pricing;
    private final String modelId;
    private final int maxToolCalls;

    public ChatAssistant(ModelGateway model, ChatPricing pricing, String modelId, int maxToolCalls) {
        this.model = model;
        this.pricing = pricing;
        this.modelId = modelId;
        this.maxToolCalls = maxToolCalls;
    }

    /**
     * @param usage filled in as each request completes; spend made before a failure stays in it
     * @throws ModelUnavailableException Claude failed
     * @throws McpUnauthorizedException  the MCP server refused the member's token
     * @throws McpUnavailableException   the MCP server failed
     */
    public String answer(String question, Optional<PlayerRef> me, List<ChatExchangeDto> history,
                         McpSession mcp, ChatUsage usage, LocalDate today) {
        ModelGateway.Conversation conversation =
                model.start(ChatPrompt.system(me, today), mcp.tools(), history, question);
        int toolCalls = 0;
        while (true) {
            ModelGateway.Reply reply;
            try {
                reply = conversation.send();
            } catch (ModelUnavailableException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ModelUnavailableException("model call failed: " + e.getClass().getSimpleName());
            }
            usage.add(reply.inputTokens(), reply.outputTokens(),
                    pricing.cost(modelId, reply.inputTokens(), reply.outputTokens()));

            if ("refusal".equals(reply.stopReason())) {
                return CANNOT_HELP;
            }
            if (reply.toolCalls().isEmpty()) {
                String text = reply.text() == null ? "" : reply.text().trim();
                return text.isEmpty() ? CANNOT_HELP : text;
            }
            // A reply cut off by max_tokens may carry a half-written tool call: never run it.
            if ("max_tokens".equals(reply.stopReason())
                    || toolCalls + reply.toolCalls().size() > maxToolCalls) {
                return TOO_MANY_LOOKUPS;
            }

            List<ModelGateway.ToolResult> results = new ArrayList<>();
            for (ModelGateway.ToolCall call : reply.toolCalls()) {
                ToolOutcome out = mcp.call(call.name(), call.arguments());
                results.add(new ModelGateway.ToolResult(call.id(), out.text(), out.isError()));
            }
            toolCalls += reply.toolCalls().size();
            conversation.addToolResults(results);
        }
    }
}
