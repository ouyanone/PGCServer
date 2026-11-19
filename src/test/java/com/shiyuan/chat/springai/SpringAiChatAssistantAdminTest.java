package com.shiyuan.chat.springai;

import static com.shiyuan.chat.springai.FakeAnthropicServer.text;
import static com.shiyuan.chat.springai.FakeAnthropicServer.toolUse;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.shiyuan.chat.ChatAssistant;
import com.shiyuan.chat.ChatPricing;
import com.shiyuan.chat.ChatPrompt;
import com.shiyuan.chat.ChatRole;
import com.shiyuan.chat.ChatUsage;
import com.shiyuan.chat.FakeMcpServer;
import com.shiyuan.chat.QuestionLimits;

/** The Spring AI bot follows the same admin rules as the SDK bot. */
class SpringAiChatAssistantAdminTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);
    private static final ChatPricing PRICING = new ChatPricing(Map.of("claude-sonnet-5-5",
            new ChatPricing.Price(new BigDecimal("2.00"), new BigDecimal("10.00"))));

    private final FakeAnthropicServer claude;
    private final FakeMcpServer mcp;

    SpringAiChatAssistantAdminTest() throws Exception {
        claude = new FakeAnthropicServer();
        mcp = new FakeMcpServer();
        mcp.listsAdminTool = true;
    }

    @AfterEach
    void stop() {
        claude.close();
        mcp.close();
    }

    private SpringAiChatAssistant assistant(int memberCalls, int adminCalls) {
        return new SpringAiChatAssistant(new SpringAiModels("test-key", claude.url(), "claude-sonnet-5-5", 2048),
                PRICING, "claude-sonnet-5-5", new QuestionLimits(memberCalls, Duration.ofSeconds(35)),
                new QuestionLimits(adminCalls, Duration.ofSeconds(55)), mcp.url(), Clock.systemUTC());
    }

    private String ask(SpringAiChatAssistant a, ChatRole role) {
        return a.answer("Set up the cup", Optional.empty(), List.of(), "tok", new ChatUsage(), TODAY, role);
    }

    private List<String> toolNamesSent(int request) {
        return StreamSupport.stream(claude.requests.get(request).path("tools").spliterator(), false)
                .map(t -> t.path("name").asText()).toList();
    }

    @Test
    void member_adminToolsNotSentToClaude() {
        claude.reply(text("Hi.", "end_turn", 10, 2));
        ask(assistant(5, 15), ChatRole.MEMBER);
        assertThat(toolNamesSent(0)).containsExactly("get_standings");
    }

    @Test
    void admin_adminToolsSentToClaude() {
        claude.reply(text("Hi.", "end_turn", 10, 2));
        ask(assistant(5, 15), ChatRole.ADMIN);
        assertThat(toolNamesSent(0)).containsExactlyInAnyOrder("get_standings", "admin_create_tournament");
    }

    @Test
    void member_claudeCallsAdminTool_notForwardedToMcp() {
        claude.reply(toolUse("toolu_1", "admin_create_tournament", "{\"name\":\"X\"}", "", 100, 10),
                text("I can't do that.", "end_turn", 100, 10));
        assertThat(ask(assistant(5, 15), ChatRole.MEMBER)).isEqualTo("I can't do that.");
        assertThat(mcp.count("tools/call")).isZero();
        List<JsonNode> msgs = StreamSupport.stream(claude.requests.get(1).path("messages").spliterator(), false).toList();
        assertThat(msgs.get(msgs.size() - 1).toString()).contains("not available to you");
    }

    @Test
    void admin_getsAdminLimits() {
        for (int i = 1; i <= 6; i++) {
            claude.reply(toolUse("toolu_" + i, "get_standings", "{}", "", 100, 10));
        }
        claude.reply(text("All set.", "end_turn", 100, 10));
        assertThat(ask(assistant(5, 15), ChatRole.ADMIN)).isEqualTo("All set.");
        assertThat(mcp.count("tools/call")).isEqualTo(6);
    }

    @Test
    void adminLimitAfterWrites_saysPartlyDone() {
        claude.reply(toolUse("toolu_1", "admin_create_tournament", "{\"name\":\"X\"}", "", 100, 10),
                toolUse("toolu_2", "admin_create_tournament", "{\"name\":\"Y\"}", "", 100, 10));
        assertThat(ask(assistant(5, 1), ChatRole.ADMIN))
                .startsWith(ChatAssistant.ADMIN_STOPPED)
                .contains("Created tournament TEST Cup (tournamentId 12), 2026-12-12 to 2026-12-13.");
    }

    private SpringAiChatAssistant assistantWithClock(Clock clock) {
        return new SpringAiChatAssistant(new SpringAiModels("test-key", claude.url(), "claude-sonnet-5-5", 2048),
                PRICING, "claude-sonnet-5-5", new QuestionLimits(5, Duration.ofSeconds(35)),
                new QuestionLimits(15, Duration.ofSeconds(35)), mcp.url(), clock);
    }

    @Test
    void adminDeadlineAfterWrites_saysPartlyDone() {
        java.time.Instant t0 = java.time.Instant.parse("2026-11-18T12:00:00Z");
        java.util.Deque<java.time.Instant> times = new java.util.ArrayDeque<>(java.util.List.of(
                t0, t0, t0, t0, t0.plusSeconds(60)));
        Clock clock = new Clock() {
            @Override public java.time.ZoneId getZone() { return java.time.ZoneId.of("UTC"); }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public java.time.Instant instant() { return times.size() > 1 ? times.poll() : times.peek(); }
        };
        claude.reply(toolUse("toolu_1", "admin_create_tournament", "{\"name\":\"X\"}", "", 100, 10),
                toolUse("toolu_2", "admin_create_tournament", "{\"name\":\"Y\"}", "", 100, 10));
        assertThat(ask(assistantWithClock(clock), ChatRole.ADMIN))
                .startsWith(ChatAssistant.ADMIN_STOPPED).contains("tournamentId 12");
    }

    @Test
    void adminMcpOutageAfterWrites_saysPartlyDone() {
        mcp.toolsCallFailFrom = 2;
        claude.reply(toolUse("toolu_1", "admin_create_tournament", "{\"name\":\"X\"}", "", 100, 10),
                toolUse("toolu_2", "admin_create_tournament", "{\"name\":\"Y\"}", "", 100, 10));
        assertThat(ask(assistant(5, 15), ChatRole.ADMIN))
                .startsWith(ChatAssistant.ADMIN_STOPPED).contains("tournamentId 12");
    }

    @Test
    void refusedWrite_notListedAsDone() {
        mcp.adminCallText = "Not done: Event 812 is already called \"X\". More.";
        claude.reply(toolUse("toolu_1", "admin_create_event", "{}", "", 100, 10),
                toolUse("toolu_2", "admin_create_event", "{}", "", 100, 10));
        assertThat(ask(assistant(5, 1), ChatRole.ADMIN)).isEqualTo(ChatAssistant.ADMIN_STOPPED);
    }

    @Test
    void adminPrompt_usedForAdmin() {
        claude.reply(text("Hi.", "end_turn", 10, 2));
        ask(assistant(5, 15), ChatRole.ADMIN);
        assertThat(claude.requests.get(0).path("system").toString())
                .contains("You are talking to a club admin");
    }

    @Test
    void memberPrompt_hasNoAdminSection() {
        claude.reply(text("Hi.", "end_turn", 10, 2));
        ask(assistant(5, 15), ChatRole.MEMBER);
        assertThat(claude.requests.get(0).path("system").toString())
                .doesNotContain("club admin");
    }
}
