package com.shiyuan.chat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A minimal MCP client over Streamable HTTP, for the PGC MCP server's stateless mode.
 *
 * <p>Hand-written rather than the MCP Java SDK: the SDK's current release needs newer Reactor
 * and Jackson than Spring Boot 3.3 manages. The club assistant needs only four messages:
 * {@code initialize}, {@code notifications/initialized}, {@code tools/list}, {@code tools/call}.
 * Each is one JSON-RPC POST; a reply arrives as JSON or as a one-event SSE stream.
 *
 * <p>Every request of a session carries that session's member token as a bearer header. The
 * token is never logged and never appears in an exception message.
 */
@Component
public class McpGateway {

    static final String CLIENT_PROTOCOL_VERSION = "2025-11-25";
    private static final Duration TOOLS_TTL = Duration.ofMinutes(5);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final URI endpoint;
    private final Clock clock;
    private final HttpClient http;

    /** Definitions only -- never tokens. Shared by all members. */
    private volatile List<McpToolDef> cachedTools;
    private volatile Instant cachedAt = Instant.MIN;

    @Autowired
    public McpGateway(@Value("${pgc.chat.mcp-url:https://pgcgolf.club/mcp}") String endpoint, Clock chatClock) {
        this.endpoint = URI.create(endpoint);
        this.clock = chatClock;
        this.http = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public McpSession open(String accessToken) {
        return new Session(accessToken);
    }

    private final class Session implements McpSession {

        private final String token;
        private final AtomicLong ids = new AtomicLong();
        private boolean initialized;
        private String protocolVersion;
        private String sessionId;

        Session(String token) {
            this.token = token;
        }

        @Override
        public List<McpToolDef> tools() {
            List<McpToolDef> tools = cachedTools;
            if (tools != null && clock.instant().isBefore(cachedAt.plus(TOOLS_TTL))) {
                return tools;
            }
            List<McpToolDef> fresh = new ArrayList<>();
            String cursor = null;
            do {
                ObjectNode params = JSON.createObjectNode();
                if (cursor != null) {
                    params.put("cursor", cursor);
                }
                JsonNode result = request("tools/list", params);
                for (JsonNode t : result.path("tools")) {
                    Map<String, Object> schema = JSON.convertValue(t.path("inputSchema"),
                            new TypeReference<LinkedHashMap<String, Object>>() {});
                    fresh.add(new McpToolDef(t.path("name").asText(), t.path("description").asText(""),
                            schema == null ? Map.of("type", "object") : schema));
                }
                cursor = result.hasNonNull("nextCursor") ? result.get("nextCursor").asText() : null;
            } while (cursor != null);
            cachedTools = List.copyOf(fresh);
            cachedAt = clock.instant();
            return cachedTools;
        }

        @Override
        public ToolOutcome call(String name, Map<String, Object> arguments) {
            ObjectNode params = JSON.createObjectNode();
            params.put("name", name);
            params.set("arguments", JSON.valueToTree(arguments == null ? Map.of() : arguments));
            JsonNode result = request("tools/call", params);
            StringBuilder text = new StringBuilder();
            for (JsonNode c : result.path("content")) {
                if ("text".equals(c.path("type").asText())) {
                    if (text.length() > 0) {
                        text.append('\n');
                    }
                    text.append(c.path("text").asText());
                }
            }
            return new ToolOutcome(text.toString(), result.path("isError").asBoolean(false));
        }

        @Override
        public void close() {
            // Stateless server: nothing to tear down. A session id, if one was issued, simply lapses.
        }

        private JsonNode request(String method, ObjectNode params) {
            ensureInitialized();
            return send(method, params, true);
        }

        private void ensureInitialized() {
            if (initialized) {
                return;
            }
            ObjectNode params = JSON.createObjectNode();
            params.put("protocolVersion", CLIENT_PROTOCOL_VERSION);
            params.set("capabilities", JSON.createObjectNode());
            ObjectNode info = params.putObject("clientInfo");
            info.put("name", "pgc-club-assistant");
            info.put("version", "1.0");
            JsonNode result = send("initialize", params, true);
            protocolVersion = result.path("protocolVersion").asText(CLIENT_PROTOCOL_VERSION);
            send("notifications/initialized", null, false);
            initialized = true;
        }

        /** One JSON-RPC message. {@code expectReply} false sends a notification (no id). */
        private JsonNode send(String method, ObjectNode params, boolean expectReply) {
            ObjectNode msg = JSON.createObjectNode();
            msg.put("jsonrpc", "2.0");
            long id = ids.incrementAndGet();
            if (expectReply) {
                msg.put("id", id);
            }
            msg.put("method", method);
            if (params != null) {
                msg.set("params", params);
            }

            HttpRequest.Builder req = HttpRequest.newBuilder(endpoint)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(msg.toString()));
            if (protocolVersion != null) {
                req.header("MCP-Protocol-Version", protocolVersion);
            }
            if (sessionId != null) {
                req.header("Mcp-Session-Id", sessionId);
            }

            HttpResponse<String> res;
            try {
                res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                throw new McpUnavailableException(method + ": " + e.getClass().getSimpleName());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new McpUnavailableException(method + ": interrupted");
            }

            int status = res.statusCode();
            if (status == 401 || status == 403) {
                throw new McpUnauthorizedException(method + ": HTTP " + status);
            }
            if (status / 100 != 2) {
                throw new McpUnavailableException(method + ": HTTP " + status);
            }
            res.headers().firstValue("Mcp-Session-Id").ifPresent(s -> sessionId = s);
            if (!expectReply) {
                return null;
            }

            JsonNode reply = parse(res, id, method);
            if (reply.has("error")) {
                throw new McpUnavailableException(method + ": JSON-RPC error "
                        + reply.path("error").path("code").asInt());
            }
            return reply.path("result");
        }

        private JsonNode parse(HttpResponse<String> res, long id, String method) {
            String body = res.body() == null ? "" : res.body();
            String type = res.headers().firstValue("Content-Type").orElse("");
            try {
                if (type.startsWith("text/event-stream")) {
                    // Each event's data lines form one JSON-RPC message; take the reply to our id.
                    for (String event : body.split("\\r?\\n\\r?\\n")) {
                        StringBuilder data = new StringBuilder();
                        for (String line : event.split("\\r?\\n")) {
                            if (line.startsWith("data:")) {
                                data.append(line.substring(5).trim());
                            }
                        }
                        if (data.length() > 0) {
                            JsonNode node = JSON.readTree(data.toString());
                            if (node.path("id").asLong(-1) == id) {
                                return node;
                            }
                        }
                    }
                    throw new McpUnavailableException(method + ": no reply in event stream");
                }
                return JSON.readTree(body);
            } catch (IOException e) {
                throw new McpUnavailableException(method + ": unreadable reply");
            }
        }
    }
}
