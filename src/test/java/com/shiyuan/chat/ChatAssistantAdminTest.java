package com.shiyuan.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** What changes when the asker is a club admin: which tools the model sees, and the limits. */
class ChatAssistantAdminTest {

    private static final String MODEL = "claude-sonnet-5-5";
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);
    private final ChatPricing pricing = new ChatPricing(Map.of(
            MODEL, new ChatPricing.Price(new BigDecimal("2.00"), new BigDecimal("10.00"))));

    static class McpWithAdminTool implements McpSession {
        final List<String> called = new ArrayList<>();
        @Override public List<McpToolDef> tools() {
            return List.of(new McpToolDef("get_standings", "Club standings", Map.of("type", "object")),
                           new McpToolDef("admin_create_tournament", "Create a tournament", Map.of("type", "object")));
        }
        RuntimeException failOnCall;
        int failAt = -1;
        @Override public ToolOutcome call(String name, Map<String, Object> args) {
            called.add(name);
            if (failOnCall != null && called.size() == failAt) throw failOnCall;
            return name.startsWith("admin_")
                    ? new ToolOutcome("Created tournament TEST Cup (tournamentId " + (11 + called.size())
                            + "), 2026-12-12 to 2026-12-13. {\"id\":12}", false)
                    : new ToolOutcome("{\"ok\":true}", false);
        }
        @Override public void close() {}
    }

    private static ModelGateway.Reply text(String t) {
        return new ModelGateway.Reply(t, List.of(), "end_turn", 10, 5);
    }

    private static ModelGateway.Reply tool(String name) {
        return new ModelGateway.Reply("", List.of(new ModelGateway.ToolCall("tu_" + name, name, Map.of())),
                "tool_use", 10, 5);
    }

    private ChatAssistant assistant(ModelGateway model, int memberCalls, int adminCalls) {
        return new ChatAssistant(model, pricing, MODEL,
                new QuestionLimits(memberCalls, Duration.ofSeconds(35)),
                new QuestionLimits(adminCalls, Duration.ofSeconds(55)), Clock.systemUTC());
    }

    private String ask(ChatAssistant a, McpSession mcp, ChatRole role) {
        return a.answer("q", Optional.empty(), List.of(), mcp, new ChatUsage(), TODAY, role);
    }

    @Test
    void member_adminToolsNotSentToModel() {
        ChatAssistantTest.ScriptedModel model = new ChatAssistantTest.ScriptedModel().reply(text("hi"));
        ask(assistant(model, 5, 15), new McpWithAdminTool(), ChatRole.MEMBER);
        assertThat(model.tools).extracting(McpToolDef::name).containsExactly("get_standings");
    }

    @Test
    void admin_adminToolsSentToModel() {
        ChatAssistantTest.ScriptedModel model = new ChatAssistantTest.ScriptedModel().reply(text("hi"));
        ask(assistant(model, 5, 15), new McpWithAdminTool(), ChatRole.ADMIN);
        assertThat(model.tools).extracting(McpToolDef::name).containsExactly("get_standings", "admin_create_tournament");
    }

    @Test
    void member_modelCallsAdminTool_refusedNotForwarded() {
        ChatAssistantTest.ScriptedModel model = new ChatAssistantTest.ScriptedModel()
                .reply(tool("admin_create_tournament")).reply(text("ok"));
        McpWithAdminTool mcp = new McpWithAdminTool();
        ask(assistant(model, 5, 15), mcp, ChatRole.MEMBER);
        assertThat(mcp.called).isEmpty();
        assertThat(model.resultsSent.get(0).get(0).isError()).isTrue();
    }

    @Test
    void admin_getsAdminLimits() {
        ChatAssistantTest.ScriptedModel adminModel = new ChatAssistantTest.ScriptedModel();
        ChatAssistantTest.ScriptedModel memberModel = new ChatAssistantTest.ScriptedModel();
        for (int i = 0; i < 6; i++) {
            adminModel.reply(tool("get_standings"));
            memberModel.reply(tool("get_standings"));
        }
        adminModel.reply(text("done"));
        memberModel.reply(text("done"));
        assertThat(ask(assistant(adminModel, 5, 15), new McpWithAdminTool(), ChatRole.ADMIN)).isEqualTo("done");
        assertThat(ask(assistant(memberModel, 5, 15), new McpWithAdminTool(), ChatRole.MEMBER))
                .isEqualTo(ChatAssistant.TOO_MANY_LOOKUPS);
    }

    /** Final review #4: the stopped reply says what was done, so the next message is not blind. */
    @Test
    void adminLimitAfterWrites_saysPartlyDone() {
        ChatAssistantTest.ScriptedModel model = new ChatAssistantTest.ScriptedModel()
                .reply(tool("admin_create_tournament")).reply(tool("admin_create_tournament")).reply(text("x"));
        assertThat(ask(assistant(model, 5, 1), new McpWithAdminTool(), ChatRole.ADMIN))
                .startsWith(ChatAssistant.ADMIN_STOPPED)
                .contains("Done so far:")
                .contains("Created tournament TEST Cup (tournamentId 12), 2026-12-12 to 2026-12-13.")
                .doesNotContain("{\"id\"");
    }

    /** Final review #1: the deadline passing after a write must not read "try again in a few minutes". */
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
        ChatAssistantTest.ScriptedModel model = new ChatAssistantTest.ScriptedModel()
                .reply(tool("admin_create_tournament")).reply(tool("admin_create_tournament"));
        ChatAssistant a = new ChatAssistant(model, pricing, MODEL, new QuestionLimits(5, Duration.ofSeconds(35)),
                new QuestionLimits(15, Duration.ofSeconds(35)), clock);
        assertThat(ask(a, new McpWithAdminTool(), ChatRole.ADMIN))
                .startsWith(ChatAssistant.ADMIN_STOPPED).contains("tournamentId 12");
    }

    @Test
    void adminMcpOutageAfterWrites_saysPartlyDone() {
        McpWithAdminTool mcp = new McpWithAdminTool();
        mcp.failOnCall = new McpUnavailableException("mcp tools/call: timeout");
        mcp.failAt = 2;
        ChatAssistantTest.ScriptedModel model = new ChatAssistantTest.ScriptedModel()
                .reply(tool("admin_create_tournament")).reply(tool("admin_create_tournament"));
        assertThat(ask(assistant(model, 5, 15), mcp, ChatRole.ADMIN))
                .startsWith(ChatAssistant.ADMIN_STOPPED).contains("tournamentId 12");
    }

    @Test
    void adminOutage_beforeAnyWrite_stillUnavailable() {
        McpWithAdminTool mcp = new McpWithAdminTool();
        mcp.failOnCall = new McpUnavailableException("mcp tools/call: timeout");
        mcp.failAt = 1;
        ChatAssistantTest.ScriptedModel model = new ChatAssistantTest.ScriptedModel().reply(tool("admin_create_tournament"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ask(assistant(model, 5, 15), mcp, ChatRole.ADMIN))
                .isInstanceOf(McpUnavailableException.class);
    }

    @Test
    void memberOutage_unchanged() {
        McpWithAdminTool mcp = new McpWithAdminTool();
        mcp.failOnCall = new McpUnavailableException("mcp tools/call: timeout");
        mcp.failAt = 2;
        ChatAssistantTest.ScriptedModel model = new ChatAssistantTest.ScriptedModel()
                .reply(tool("get_standings")).reply(tool("get_standings"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ask(assistant(model, 5, 15), mcp, ChatRole.MEMBER))
                .isInstanceOf(McpUnavailableException.class);
    }

    /** Final review #2: one more call (up to 20 s) after the deadline must still finish under nginx's 60 s. */
    @Test
    void adminDeadline_leavesRoomForOneCallUnderNginx() {
        assertThat(new ChatProperties().getAdminDeadlineSeconds() + 20).isLessThanOrEqualTo(55);
    }

    @Test
    void sixArgumentAnswer_isMember() {
        ChatAssistantTest.ScriptedModel model = new ChatAssistantTest.ScriptedModel().reply(text("hi"));
        assistant(model, 5, 15).answer("q", Optional.empty(), List.of(), new McpWithAdminTool(), new ChatUsage(), TODAY);
        assertThat(model.tools).extracting(McpToolDef::name).containsExactly("get_standings");
    }

    @Test
    void adminPrompt_usedForAdmin() {
        ChatAssistantTest.ScriptedModel model = new ChatAssistantTest.ScriptedModel().reply(text("hi"));
        ask(assistant(model, 5, 15), new McpWithAdminTool(), ChatRole.ADMIN);
        assertThat(model.system).isEqualTo(ChatPrompt.system(Optional.empty(), TODAY, ChatRole.ADMIN));
    }

    @Test
    void refusedWrite_notListedAsDone() {
        McpWithAdminTool mcp = new McpWithAdminTool() {
            @Override public ToolOutcome call(String name, Map<String, Object> args) {
                return new ToolOutcome("Not done: Event 812 is already called \"TEST Cup Round 1\". More.", false);
            }
        };
        ChatAssistantTest.ScriptedModel model = new ChatAssistantTest.ScriptedModel()
                .reply(tool("admin_create_event")).reply(tool("admin_create_event"));
        assertThat(ask(assistant(model, 5, 1), mcp, ChatRole.ADMIN)).isEqualTo(ChatAssistant.ADMIN_STOPPED);
    }
}
