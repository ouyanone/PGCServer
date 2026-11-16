package com.shiyuan.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * The tool loop, with a scripted model and a fake MCP session: what is asked, what is called,
 * what it costs, and when it stops.
 */
class ChatAssistantTest {

    private static final String MODEL = "claude-sonnet-5-5";
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);
    private final ChatPricing pricing = new ChatPricing(Map.of(
            MODEL, new ChatPricing.Price(new BigDecimal("2.00"), new BigDecimal("10.00"))));

    /** Replies in order; records what the assistant gave it. */
    static final class ScriptedModel implements ModelGateway {
        final Deque<Object> replies = new ArrayDeque<>();
        String system;
        List<McpToolDef> tools;
        List<ChatExchangeDto> history;
        String question;
        final List<List<ModelGateway.ToolResult>> resultsSent = new ArrayList<>();
        int sends;

        ScriptedModel reply(ModelGateway.Reply r) { replies.add(r); return this; }
        ScriptedModel fail(RuntimeException e) { replies.add(e); return this; }

        @Override
        public Conversation start(String system, List<McpToolDef> tools, List<ChatExchangeDto> history, String question) {
            this.system = system;
            this.tools = tools;
            this.history = history;
            this.question = question;
            return new Conversation() {
                @Override
                public Reply send() {
                    sends++;
                    Object next = replies.isEmpty() ? replies.peekLast() : replies.size() == 1 ? replies.peek() : replies.poll();
                    if (next instanceof RuntimeException e) {
                        throw e;
                    }
                    return (Reply) next;
                }

                @Override
                public void addToolResults(List<ToolResult> results) {
                    resultsSent.add(results);
                }
            };
        }
    }

    static class FakeMcp implements McpSession {
        final List<String> called = new ArrayList<>();
        @Override public List<McpToolDef> tools() {
            return List.of(new McpToolDef("get_standings", "Club standings", Map.of("type", "object")));
        }
        @Override public ToolOutcome call(String name, Map<String, Object> args) {
            called.add(name);
            return name.equals("broken") ? new ToolOutcome("Event 999 does not exist.", true)
                                         : new ToolOutcome("{\"rank\":7}", false);
        }
        @Override public void close() {}
    }

    private static ModelGateway.Reply text(String t, long in, long out) {
        return new ModelGateway.Reply(t, List.of(), "end_turn", in, out);
    }

    private static ModelGateway.Reply tool(String name, long in, long out) {
        return new ModelGateway.Reply("", List.of(new ModelGateway.ToolCall("tu_" + name, name, Map.of())),
                "tool_use", in, out);
    }

    private ChatAssistant assistant(ScriptedModel model) {
        return new ChatAssistant(model, pricing, MODEL, 5);
    }

    @Test
    void toolUse_thenText_returnsText() {
        ScriptedModel model = new ScriptedModel().reply(tool("get_standings", 100, 10)).reply(text("You're #7.", 150, 20));
        FakeMcp mcp = new FakeMcp();
        ChatUsage usage = new ChatUsage();

        String answer = assistant(model).answer("Where am I?", Optional.empty(), List.of(), mcp, usage, TODAY);

        assertEquals("You're #7.", answer);
        assertEquals(List.of("get_standings"), mcp.called);
        assertEquals("{\"rank\":7}", model.resultsSent.get(0).get(0).text());
        assertEquals("tu_get_standings", model.resultsSent.get(0).get(0).toolUseId());
    }

    @Test
    void fiveToolCallCap_returnsFixedSentence() {
        ScriptedModel model = new ScriptedModel().reply(tool("get_standings", 10, 1));
        FakeMcp mcp = new FakeMcp();

        String answer = assistant(model).answer("q", Optional.empty(), List.of(), mcp, new ChatUsage(), TODAY);

        assertEquals(5, mcp.called.size());
        assertEquals(ChatAssistant.TOO_MANY_LOOKUPS, answer);
        assertEquals("I couldn't finish looking that up — please try a narrower question.", answer);
    }

    @Test
    void costSummedAcrossEverySend() {
        ScriptedModel model = new ScriptedModel()
                .reply(tool("get_standings", 1000, 100))
                .reply(tool("get_standings", 2000, 200))
                .reply(text("done", 3000, 300));
        ChatUsage usage = new ChatUsage();

        assistant(model).answer("q", Optional.empty(), List.of(), new FakeMcp(), usage, TODAY);

        assertEquals(6000, usage.inputTokens());
        assertEquals(600, usage.outputTokens());
        // 6000 * 2/1M + 600 * 10/1M = 0.012 + 0.006
        assertEquals(0, new BigDecimal("0.018").compareTo(usage.cost()), usage.cost().toPlainString());
    }

    @Test
    void historyAndQuestionHandedToTheModel() {
        ScriptedModel model = new ScriptedModel().reply(text("ok", 1, 1));
        List<ChatExchangeDto> history = List.of(
                new ChatExchangeDto("q1", "a1", Instant.parse("2026-11-15T10:00:00Z")),
                new ChatExchangeDto("q2", "a2", Instant.parse("2026-11-15T11:00:00Z")));

        assistant(model).answer("q3", Optional.empty(), history, new FakeMcp(), new ChatUsage(), TODAY);

        assertEquals(history, model.history);
        assertEquals("q3", model.question);
        assertEquals("get_standings", model.tools.get(0).name());
    }

    @Test
    void noEmailAnywhereInRequest() {
        ScriptedModel model = new ScriptedModel().reply(text("ok", 1, 1));
        // The only '@' in this test's data is the player's email, which PlayerResolver never
        // copies into a PlayerRef -- so if one reaches the model, something leaked it.
        Optional<PlayerRef> me = Optional.of(new PlayerRef(42, "Jane Lee"));

        assistant(model).answer("Where am I?", me, List.of(), new FakeMcp(), new ChatUsage(), TODAY);

        String everything = model.system + model.history + model.question + model.tools;
        assertFalse(everything.contains("@"), everything);
        assertTrue(model.system.contains("player #42, Jane Lee"));
    }

    @Test
    void toolError_passedBackAsErrorResult() {
        ScriptedModel model = new ScriptedModel().reply(tool("broken", 1, 1)).reply(text("No such event.", 1, 1));

        assistant(model).answer("q", Optional.empty(), List.of(), new FakeMcp(), new ChatUsage(), TODAY);

        ModelGateway.ToolResult r = model.resultsSent.get(0).get(0);
        assertTrue(r.isError());
        assertEquals("Event 999 does not exist.", r.text());
    }

    @Test
    void gatewayThrows_raisesModelUnavailable_costSoFarStillMetered() {
        ScriptedModel model = new ScriptedModel()
                .reply(tool("get_standings", 1000, 100))
                .fail(new RuntimeException("529 overloaded"));
        model.replies.add(text("never", 1, 1));
        ChatUsage usage = new ChatUsage();

        assertThrows(ModelUnavailableException.class, () ->
                assistant(model).answer("q", Optional.empty(), List.of(), new FakeMcp(), usage, TODAY));
        assertEquals(1000, usage.inputTokens());
    }

    @Test
    void mcpUnauthorized_propagates_costSoFarStillMetered() {
        ScriptedModel model = new ScriptedModel().reply(tool("get_standings", 500, 50));
        McpSession refusing = new FakeMcp() {
            @Override public ToolOutcome call(String name, Map<String, Object> args) {
                throw new McpUnauthorizedException("tools/call: HTTP 401");
            }
        };
        ChatUsage usage = new ChatUsage();

        assertThrows(McpUnauthorizedException.class, () ->
                assistant(model).answer("q", Optional.empty(), List.of(), refusing, usage, TODAY));
        assertEquals(500, usage.inputTokens());
    }

    @Test
    void refusal_returnsPoliteDecline_andRunsNoTool() {
        ModelGateway.Reply refused = new ModelGateway.Reply("",
                List.of(new ModelGateway.ToolCall("tu_x", "get_standings", Map.of())), "refusal", 5, 5);
        ScriptedModel model = new ScriptedModel().reply(refused);
        FakeMcp mcp = new FakeMcp();

        String answer = assistant(model).answer("q", Optional.empty(), List.of(), mcp, new ChatUsage(), TODAY);

        assertEquals(ChatAssistant.CANNOT_HELP, answer);
        assertTrue(mcp.called.isEmpty());
    }

    @Test
    void truncatedToolCall_isNotRun() {
        ModelGateway.Reply cut = new ModelGateway.Reply("",
                List.of(new ModelGateway.ToolCall("tu_x", "get_standings", Map.of())), "max_tokens", 5, 5);
        ScriptedModel model = new ScriptedModel().reply(cut);
        FakeMcp mcp = new FakeMcp();

        String answer = assistant(model).answer("q", Optional.empty(), List.of(), mcp, new ChatUsage(), TODAY);

        assertEquals(ChatAssistant.TOO_MANY_LOOKUPS, answer);
        assertTrue(mcp.called.isEmpty());
    }

    @Test
    void emptyFinalText_getsAFallbackSentence() {
        ScriptedModel model = new ScriptedModel().reply(text("   ", 1, 1));
        String answer = assistant(model).answer("q", Optional.empty(), List.of(), new FakeMcp(), new ChatUsage(), TODAY);
        assertEquals(ChatAssistant.CANNOT_HELP, answer);
    }
}
