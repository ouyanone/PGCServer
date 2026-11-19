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
        @Override public ToolOutcome call(String name, Map<String, Object> args) {
            called.add(name);
            return new ToolOutcome("{\"ok\":true}", false);
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

    @Test
    void adminLimitAfterWrites_saysPartlyDone() {
        ChatAssistantTest.ScriptedModel model = new ChatAssistantTest.ScriptedModel()
                .reply(tool("admin_create_tournament")).reply(tool("admin_create_tournament")).reply(text("x"));
        assertThat(ask(assistant(model, 5, 1), new McpWithAdminTool(), ChatRole.ADMIN))
                .isEqualTo(ChatAssistant.ADMIN_STOPPED);
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
}
