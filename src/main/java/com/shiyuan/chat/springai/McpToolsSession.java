package com.shiyuan.chat.springai;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.tool.ToolCallback;

import com.shiyuan.chat.McpUnauthorizedException;
import com.shiyuan.chat.McpUnavailableException;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * One question's connection to the MCP server, made with the MCP Java SDK and carrying the
 * member's own access token, its tools exposed as Spring AI {@link ToolCallback}s.
 *
 * <p>Spring AI's MCP client starter is not used: it connects once at start-up with a single
 * identity, and every member needs their own token. Opened and closed per question.
 *
 * <p>Exception messages carry the step and HTTP status only, never the token.
 */
public final class McpToolsSession implements AutoCloseable {

    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final McpSyncClient client;
    private final StatusRecordingHttpClient http;

    private McpToolsSession(McpSyncClient client, StatusRecordingHttpClient http) {
        this.client = client;
        this.http = http;
    }

    public static McpToolsSession open(String mcpUrl, String accessToken) {
        URI uri = URI.create(mcpUrl);
        String base = uri.getScheme() + "://" + uri.getRawAuthority();
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        StatusRecordingHttpClient.Builder httpBuilder = new StatusRecordingHttpClient.Builder();
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(base)
                .clientBuilder(httpBuilder)
                .endpoint(path)
                .customizeRequest(b -> b.header("Authorization", "Bearer " + accessToken))
                .build();
        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(REQUEST_TIMEOUT)
                .initializationTimeout(REQUEST_TIMEOUT)
                .build();
        try {
            client.initialize();
            return new McpToolsSession(client, httpBuilder.built());
        } catch (RuntimeException e) {
            // Read the status first: closing sends a DELETE whose reply would replace it.
            int status = lastStatus(httpBuilder.built());
            client.close();
            throw translate(e, "initialize", status);
        }
    }

    /** The server's tools as Spring AI tool callbacks, named exactly as the server names them. */
    public List<ToolCallback> toolCallbacks() {
        McpSchema.ListToolsResult tools;
        try {
            tools = client.listTools();
        } catch (RuntimeException e) {
            throw translate(e, "tools/list", http.lastStatus());
        }
        return tools.tools().stream()
                .<ToolCallback>map(t -> SyncMcpToolCallback.builder()
                        .mcpClient(client)
                        .tool(t)
                        .prefixedToolName(t.name())
                        .build())
                .toList();
    }

    /**
     * Maps a failure from one of this session's tool calls to the assistant's exceptions:
     * a 401 from the MCP server means "sign in again", anything else "unavailable".
     */
    public RuntimeException translate(Throwable t) {
        return translate(t, "tools/call", http.lastStatus());
    }

    private static int lastStatus(StatusRecordingHttpClient http) {
        return http == null ? 0 : http.lastStatus();
    }

    private static RuntimeException translate(Throwable t, String step, int status) {
        if (t instanceof McpUnauthorizedException u) {
            return u;
        }
        if (t instanceof McpUnavailableException u) {
            return u;
        }
        if (status == 401) {
            return new McpUnauthorizedException("mcp " + step + ": HTTP 401");
        }
        return new McpUnavailableException("mcp " + step + ": "
                + (status >= 400 ? "HTTP " + status : rootType(t)));
    }

    private static String rootType(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c.getClass().getSimpleName();
    }

    @Override
    public void close() {
        client.closeGracefully();
    }
}
