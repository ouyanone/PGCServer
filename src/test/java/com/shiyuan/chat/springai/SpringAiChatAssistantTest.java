package com.shiyuan.chat.springai;

import static com.shiyuan.chat.springai.FakeAnthropicServer.text;
import static com.shiyuan.chat.springai.FakeAnthropicServer.toolUse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.shiyuan.chat.ChatAssistant;
import com.shiyuan.chat.ChatExchangeDto;
import com.shiyuan.chat.ChatPricing;
import com.shiyuan.chat.ChatUsage;
import com.shiyuan.chat.FakeMcpServer;
import com.shiyuan.chat.McpUnauthorizedException;
import com.shiyuan.chat.McpUnavailableException;
import com.shiyuan.chat.ModelUnavailableException;

class SpringAiChatAssistantTest {

    private static final String KEY = "test-key";
    private static final String TOKEN = "tok-123";
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);
    private static final List<ChatExchangeDto> HISTORY =
            List.of(new ChatExchangeDto("q1", "a1", Instant.parse("2026-11-15T10:00:00Z")));
    private static final ChatPricing PRICING = new ChatPricing(Map.of("claude-sonnet-5-5",
            new ChatPricing.Price(new BigDecimal("2.00"), new BigDecimal("10.00"))));

    private final FakeAnthropicServer claude;
    private final FakeMcpServer mcp;
    private final ChatUsage usage = new ChatUsage();

    SpringAiChatAssistantTest() throws Exception {
        claude = new FakeAnthropicServer();
        mcp = new FakeMcpServer();
    }

    @AfterEach
    void stop() {
        claude.close();
        mcp.close();
    }

    private SpringAiChatAssistant assistant(String key, int maxToolCalls, Clock clock) {
        return new SpringAiChatAssistant(new SpringAiModels(key, claude.url(), "claude-sonnet-5-5", 2048),
                PRICING, "claude-sonnet-5-5", maxToolCalls, mcp.url(), clock, Duration.ofSeconds(35));
    }

    private String ask(SpringAiChatAssistant a) {
        return a.answer("Where am I?", Optional.empty(), HISTORY, TOKEN, usage, TODAY);
    }

    private String ask() {
        return ask(assistant(KEY, 5, Clock.systemUTC()));
    }

    private static List<JsonNode> list(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).toList();
    }

    @Test
    void firstRequest_hasModelMaxTokensSystemHistoryQuestionTools() {
        claude.reply(text("Hi.", "end_turn", 10, 2));
        ask();
        JsonNode req = claude.requests.get(0);
        assertThat(req.path("model").asText()).isEqualTo("claude-sonnet-5-5");
        assertThat(req.path("max_tokens").asInt()).isEqualTo(2048);
        assertThat(req.path("system").toString()).contains("club assistant for PGC Golf Club");
        List<JsonNode> msgs = list(req.path("messages"));
        assertThat(msgs).extracting(m -> m.path("role").asText()).containsExactly("user", "assistant", "user");
        assertThat(msgs.get(0).toString()).contains("q1");
        assertThat(msgs.get(1).toString()).contains("a1");
        assertThat(msgs.get(2).toString()).contains("Where am I?");
        assertThat(req.path("tools").get(0).path("name").asText()).isEqualTo("get_standings");
        assertThat(claude.apiKeys.get(0)).isEqualTo(KEY);
    }

    @Test
    void toolRound_thenAnswer() {
        claude.reply(toolUse("toolu_1", "get_standings", "{\"limit\":5}", "", 1200, 80),
                text("You're #7.", "end_turn", 1500, 20));
        assertThat(ask()).isEqualTo("You're #7.");
        assertThat(mcp.count("tools/call")).isEqualTo(1);
        assertThat(mcp.seen).allSatisfy(s -> assertThat(s.authorization()).isEqualTo("Bearer " + TOKEN));
        List<JsonNode> msgs = list(claude.requests.get(1).path("messages"));
        JsonNode last = msgs.get(msgs.size() - 1);
        assertThat(last.path("role").asText()).isEqualTo("user");
        assertThat(last.path("content").get(0).path("type").asText()).isEqualTo("tool_result");
        assertThat(last.path("content").get(0).path("tool_use_id").asText()).isEqualTo("toolu_1");
    }

    /**
     * Spring AI 1.1.8 does not carry Claude's thinking blocks into the next round (spec §8). The
     * API accepts a turn with its thinking block removed, so the loop still works; this pins that
     * behaviour so a Spring AI upgrade that starts replaying them is noticed.
     */
    @Test
    void thinkingBlock_droppedBySpringAi_loopStillCompletes() {
        claude.reply(toolUse("toolu_1", "get_standings", "{\"limit\":5}",
                        "{\"type\":\"thinking\",\"thinking\":\"\",\"signature\":\"sig-abc\"},", 1200, 80),
                text("You're #7.", "end_turn", 1500, 20));
        assertThat(ask()).isEqualTo("You're #7.");
        List<JsonNode> msgs = list(claude.requests.get(1).path("messages"));
        JsonNode assistantTurn = msgs.get(msgs.size() - 2);
        assertThat(assistantTurn.path("role").asText()).isEqualTo("assistant");
        assertThat(list(assistantTurn.path("content")))
                .extracting(b -> b.path("type").asText())
                .containsExactly("tool_use");
    }

    @Test
    void toolError_goesBackToModel() {
        mcp.toolsCallIsError = true;
        claude.reply(toolUse("toolu_1", "get_standings", "{}", "", 100, 10), text("No such event.", "end_turn", 100, 10));
        assertThat(ask()).isEqualTo("No such event.");
        List<JsonNode> msgs = list(claude.requests.get(1).path("messages"));
        assertThat(msgs.get(msgs.size() - 1).toString()).contains("Event 999 does not exist.");
    }

    @Test
    void sixthToolCall_tooManyLookups() {
        for (int i = 1; i <= 6; i++) {
            claude.reply(toolUse("toolu_" + i, "get_standings", "{}", "", 100, 10));
        }
        assertThat(ask()).isEqualTo(ChatAssistant.TOO_MANY_LOOKUPS);
        assertThat(mcp.count("tools/call")).isEqualTo(5);
    }

    @Test
    void refusal_cannotHelp() {
        claude.reply(text("", "refusal", 10, 1));
        assertThat(ask()).isEqualTo(ChatAssistant.CANNOT_HELP);
        claude.reply(text("", "end_turn", 10, 1));
        assertThat(ask()).isEqualTo(ChatAssistant.CANNOT_HELP);
    }

    @Test
    void maxTokensWithToolCall_tooManyLookups() {
        claude.reply(toolUse("toolu_1", "get_standings", "{}", "", 100, 2048)
                .replace("\"stop_reason\":\"tool_use\"", "\"stop_reason\":\"max_tokens\""));
        assertThat(ask()).isEqualTo(ChatAssistant.TOO_MANY_LOOKUPS);
        assertThat(mcp.count("tools/call")).isZero();
    }

    @Test
    void deadlinePassed_modelUnavailable() {
        Instant t0 = Instant.parse("2026-11-17T12:00:00Z");
        Deque<Instant> times = new ArrayDeque<>(List.of(t0, t0, t0.plusSeconds(40)));
        Clock clock = new Clock() {
            @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return times.size() > 1 ? times.poll() : times.peek(); }
        };
        claude.reply(toolUse("toolu_1", "get_standings", "{}", "", 100, 10), text("late", "end_turn", 1, 1));
        assertThatThrownBy(() -> ask(assistant(KEY, 5, clock))).isInstanceOf(ModelUnavailableException.class);
        assertThat(claude.requests).hasSize(1);
    }

    @Test
    void usage_pricedPerCall() {
        claude.reply(toolUse("toolu_1", "get_standings", "{}", "", 1200, 80), text("ok", "end_turn", 1500, 20));
        ask();
        assertThat(usage.inputTokens()).isEqualTo(2700);
        assertThat(usage.outputTokens()).isEqualTo(100);
        assertThat(usage.cost()).isEqualByComparingTo("0.006400");
    }

    @Test
    void modelError_oneAttempt_modelUnavailable() {
        claude.status = 529;
        assertThatThrownBy(this::ask).isInstanceOf(ModelUnavailableException.class);
        assertThat(claude.requests).hasSize(1);
    }

    @Test
    void blankKey_noUsageRecorded() {
        assertThatThrownBy(() -> ask(assistant("", 5, Clock.systemUTC())))
                .isInstanceOf(ModelUnavailableException.class);
        assertThat(usage.inputTokens()).isZero();
        assertThat(mcp.seen).isEmpty();
    }

    @Test
    void mcp401_unauthorized() {
        mcp.status = 401;
        assertThatThrownBy(this::ask).isInstanceOf(McpUnauthorizedException.class);
    }

    /** Review Important #1: a call with no HTTP reply must not reuse the 200 from tools/list. */
    @Test
    void toolCallWithNoReply_unavailable_notSentToClaude() {
        mcp.toolsCallDropsConnection = true;
        claude.reply(toolUse("toolu_1", "get_standings", "{}", "", 100, 10), text("x", "end_turn", 1, 1));
        assertThatThrownBy(this::ask).isInstanceOf(McpUnavailableException.class);
        assertThat(claude.requests).hasSize(1);
    }

    /** Review Important #3: the deadline is checked before every tool call, not once per round. */
    @Test
    void deadlinePassedBetweenParallelToolCalls_modelUnavailable() {
        Instant t0 = Instant.parse("2026-11-17T12:00:00Z");
        // giveUpAt, loop check, pre-round check, first call's check: on time; second call's: late.
        Deque<Instant> times = new ArrayDeque<>(List.of(t0, t0, t0, t0, t0.plusSeconds(40)));
        Clock clock = new Clock() {
            @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return times.size() > 1 ? times.poll() : times.peek(); }
        };
        claude.reply("""
                {"id":"msg_p","type":"message","role":"assistant","model":"claude-sonnet-5-5",
                 "content":[{"type":"tool_use","id":"toolu_a","name":"get_standings","input":{}},
                            {"type":"tool_use","id":"toolu_b","name":"get_standings","input":{}}],
                 "stop_reason":"tool_use","stop_sequence":null,
                 "usage":{"input_tokens":100,"output_tokens":10}}""");
        assertThatThrownBy(() -> ask(assistant(KEY, 5, clock))).isInstanceOf(ModelUnavailableException.class);
        assertThat(mcp.count("tools/call")).isEqualTo(1);
    }

    @Test
    void mcp401_onToolCall_unauthorized() {
        mcp.toolsCallStatus = 401;
        claude.reply(toolUse("toolu_1", "get_standings", "{}", "", 100, 10), text("x", "end_turn", 1, 1));
        assertThatThrownBy(this::ask).isInstanceOf(McpUnauthorizedException.class);
    }
}
