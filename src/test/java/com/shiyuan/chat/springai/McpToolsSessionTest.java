package com.shiyuan.chat.springai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import com.shiyuan.chat.FakeMcpServer;
import com.shiyuan.chat.McpUnauthorizedException;
import com.shiyuan.chat.McpUnavailableException;

class McpToolsSessionTest {

    private static final String TOKEN = "tok-123";
    private final FakeMcpServer mcp;

    McpToolsSessionTest() throws Exception {
        mcp = new FakeMcpServer();
    }

    @AfterEach
    void stop() {
        mcp.close();
    }

    @Test
    void everyRequest_carriesBearer() {
        try (McpToolsSession session = McpToolsSession.open(mcp.url(), TOKEN)) {
            session.toolCallbacks();
        }
        assertThat(mcp.seen).isNotEmpty();
        assertThat(mcp.seen).allSatisfy(s -> assertThat(s.authorization()).isEqualTo("Bearer " + TOKEN));
        assertThat(mcp.count("initialize")).isEqualTo(1);
    }

    @Test
    void toolCallbacks_wrapMcpTools() {
        try (McpToolsSession session = McpToolsSession.open(mcp.url(), TOKEN)) {
            List<ToolCallback> tools = session.toolCallbacks();
            assertThat(tools).hasSize(1);
            assertThat(tools.get(0).getToolDefinition().name()).isEqualTo("get_standings");
            assertThat(tools.get(0).call("{\"limit\":5}")).contains("totalPlayers");
        }
        assertThat(mcp.count("tools/call")).isEqualTo(1);
    }

    @Test
    void initialize401_isUnauthorized() {
        mcp.status = 401;
        Throwable t = catchThrowable(() -> McpToolsSession.open(mcp.url(), TOKEN));
        assertThat(t).isInstanceOf(McpUnauthorizedException.class);
        assertThat(t.getMessage()).doesNotContain(TOKEN);
    }

    @Test
    void initialize500_isUnavailable() {
        mcp.status = 500;
        Throwable t = catchThrowable(() -> McpToolsSession.open(mcp.url(), TOKEN));
        assertThat(t).isInstanceOf(McpUnavailableException.class);
        assertThat(t.getMessage()).doesNotContain(TOKEN);
    }

    @Test
    void callAfter401_isUnauthorized() {
        mcp.toolsCallStatus = 401;
        try (McpToolsSession session = McpToolsSession.open(mcp.url(), TOKEN)) {
            ToolCallback tool = session.toolCallbacks().get(0);
            Throwable thrown = catchThrowable(() -> tool.call("{\"limit\":5}"));
            assertThat(thrown).isNotNull();
            RuntimeException mapped = session.translate(thrown);
            assertThat(mapped).isInstanceOf(McpUnauthorizedException.class);
            assertThat(mapped.getMessage()).doesNotContain(TOKEN);
        }
    }

    @Test
    void callAfter500_isUnavailable() {
        mcp.toolsCallStatus = 500;
        try (McpToolsSession session = McpToolsSession.open(mcp.url(), TOKEN)) {
            ToolCallback tool = session.toolCallbacks().get(0);
            assertThat(session.translate(catchThrowable(() -> tool.call("{}"))))
                    .isInstanceOf(McpUnavailableException.class);
        }
    }

    @Test
    void translate_passesOurExceptionsThrough() {
        try (McpToolsSession session = McpToolsSession.open(mcp.url(), TOKEN)) {
            McpUnauthorizedException ours = new McpUnauthorizedException("mcp tools/call: HTTP 401");
            assertThat(session.translate(ours)).isSameAs(ours);
            assertThatThrownBy(() -> { throw session.translate(new IllegalStateException("x")); })
                    .isInstanceOf(McpUnavailableException.class);
        }
    }
}
