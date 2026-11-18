package com.shiyuan.chat.springai;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.chat.model.ToolContext;

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
                .<ToolCallback>map(t -> new FailuresAsToolExecution(SyncMcpToolCallback.builder()
                        .mcpClient(client)
                        .tool(t)
                        .prefixedToolName(t.name())
                        .build()))
                .toList();
    }

    /**
     * {@code SyncMcpToolCallback} throws a plain {@code IllegalStateException} when a tool returns
     * an error result, and Spring AI's tool-calling manager only hands {@link ToolExecutionException}
     * to the error processor. Wrapping every failure lets {@link #toolErrorProcessor} decide.
     */
    private static final class FailuresAsToolExecution implements ToolCallback {
        private final ToolCallback delegate;

        FailuresAsToolExecution(ToolCallback delegate) {
            this.delegate = delegate;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return delegate.getToolMetadata();
        }

        @Override
        public String call(String toolInput) {
            return call(toolInput, null);
        }

        @Override
        public String call(String toolInput, ToolContext context) {
            try {
                return context == null ? delegate.call(toolInput) : delegate.call(toolInput, context);
            } catch (ToolExecutionException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ToolExecutionException(delegate.getToolDefinition(), e);
            }
        }
    }

    /**
     * How Spring AI's tool-calling manager should treat a failed tool call. A tool that answered
     * (HTTP 200) with an error result goes back to Claude as the tool's reply, Spring AI's usual
     * behaviour. A call the MCP server refused or failed (no 200) ends the question through
     * {@link #translate}, so a 401 still means "sign in again".
     */
    public ToolExecutionExceptionProcessor toolErrorProcessor() {
        return e -> {
            if (http.lastStatus() == 200) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                return cause.getMessage() == null ? "Tool call failed." : cause.getMessage();
            }
            throw translate(e);
        };
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
