package com.shiyuan.chat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Answers one member question: Claude decides which MCP tools to call, this class calls them
 * with the member's session, and the loop ends at a text answer or at five tool calls.
 *
 * <p>Each question also has a deadline (35 s by default). nginx gives the site 60 s; a slow but
 * still-answering API must end as "unavailable" inside that, rather than leaving the member on
 * "thinking..." while the backend finishes and saves an answer they never saw. The deadline is
 * checked before every model call and every tool call; each of those is itself time-limited.
 */
public class ChatAssistant {

    public static final String TOO_MANY_LOOKUPS = "I couldn't finish looking that up — please try a narrower question.";
    public static final String CANNOT_HELP = "Sorry, I can't help with that one.";
    /** An admin's question stopped by a limit: some changes may already have been made. */
    public static final String ADMIN_STOPPED =
            "I stopped before finishing, so the setup may be partly done. Check the admin pages, then tell me what's left.";
    static final String NOT_OFFERED = "That tool is not available to you.";

    private final ModelGateway model;
    private final ChatPricing pricing;
    private final String modelId;
    private final QuestionLimits memberLimits;
    private final QuestionLimits adminLimits;
    private final Clock clock;

    public ChatAssistant(ModelGateway model, ChatPricing pricing, String modelId, int maxToolCalls) {
        this(model, pricing, modelId, maxToolCalls, Clock.systemUTC(), Duration.ofSeconds(35));
    }

    public ChatAssistant(ModelGateway model, ChatPricing pricing, String modelId, int maxToolCalls,
                         Clock clock, Duration deadline) {
        this(model, pricing, modelId, new QuestionLimits(maxToolCalls, deadline),
                new QuestionLimits(maxToolCalls, deadline), clock);
    }

    public ChatAssistant(ModelGateway model, ChatPricing pricing, String modelId,
                         QuestionLimits memberLimits, QuestionLimits adminLimits, Clock clock) {
        this.model = model;
        this.pricing = pricing;
        this.modelId = modelId;
        this.memberLimits = memberLimits;
        this.adminLimits = adminLimits;
        this.clock = clock;
    }

    /** As a {@link ChatRole#MEMBER}. */
    public String answer(String question, Optional<PlayerRef> me, List<ChatExchangeDto> history,
                         McpSession mcp, ChatUsage usage, LocalDate today) {
        return answer(question, me, history, mcp, usage, today, ChatRole.MEMBER);
    }

    /**
     * @param usage filled in as each request completes; spend made before a failure stays in it
     * @throws ModelUnavailableException Claude failed
     * @throws McpUnauthorizedException  the MCP server refused the member's token
     * @throws McpUnavailableException   the MCP server failed
     */
    public String answer(String question, Optional<PlayerRef> me, List<ChatExchangeDto> history,
                         McpSession mcp, ChatUsage usage, LocalDate today, ChatRole role) {
        QuestionLimits limits = role == ChatRole.ADMIN ? adminLimits : memberLimits;
        // A member's model never hears of the admin_ tools.
        List<McpToolDef> offered = mcp.tools().stream().filter(t -> role.mayUse(t.name())).toList();
        ModelGateway.Conversation conversation =
                model.start(ChatPrompt.system(me, today, role), offered, history, question);
        Instant giveUpAt = clock.instant().plus(limits.deadline());
        int toolCalls = 0;
        while (true) {
            checkDeadline(giveUpAt, limits);
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
                    || toolCalls + reply.toolCalls().size() > limits.maxToolCalls()) {
                return role == ChatRole.ADMIN ? ADMIN_STOPPED : TOO_MANY_LOOKUPS;
            }

            List<ModelGateway.ToolResult> results = new ArrayList<>();
            for (ModelGateway.ToolCall call : reply.toolCalls()) {
                checkDeadline(giveUpAt, limits);
                if (!role.mayUse(call.name())) {
                    // Not offered, so never forwarded -- even if the model names it anyway.
                    results.add(new ModelGateway.ToolResult(call.id(), NOT_OFFERED, true));
                    continue;
                }
                ToolOutcome out = mcp.call(call.name(), call.arguments());
                results.add(new ModelGateway.ToolResult(call.id(), out.text(), out.isError()));
            }
            toolCalls += reply.toolCalls().size();
            conversation.addToolResults(results);
        }
    }

    private void checkDeadline(Instant giveUpAt, QuestionLimits limits) {
        if (!clock.instant().isBefore(giveUpAt)) {
            throw new ModelUnavailableException("question deadline of " + limits.deadline().toSeconds() + " s passed");
        }
    }
}
