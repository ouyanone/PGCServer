package com.shiyuan.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

/**
 * The real Anthropic Java SDK against a loopback stand-in for api.anthropic.com. Proves the
 * request we build (model, effort, system, tools, history), that replies parse -- including
 * with the Jackson version Spring Boot 3.3 pins -- and that the model's own content, thinking
 * included, goes back unchanged with the tool results.
 */
class AnthropicModelGatewayTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpServer server;
    private final List<JsonNode> requests = new ArrayList<>();
    private final List<String> apiKeys = new ArrayList<>();
    private final Deque<String> replies = new ArrayDeque<>();
    private volatile int status = 200;

    AnthropicModelGatewayTest() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", ex -> {
            requests.add(JSON.readTree(ex.getRequestBody()));
            // SDK 3.x sends the key as a bearer token; older releases used x-api-key.
            String key = ex.getRequestHeaders().getFirst("x-api-key");
            String auth = ex.getRequestHeaders().getFirst("Authorization");
            apiKeys.add(key != null ? key : auth == null ? null : auth.replaceFirst("^Bearer ", ""));
            byte[] body = (status == 200 ? replies.poll()
                    : "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"busy\"}}")
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, body.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private AnthropicModelGateway gateway(String key) {
        return new AnthropicModelGateway(key, "http://127.0.0.1:" + server.getAddress().getPort(),
                "claude-sonnet-5-5", 2048);
    }

    private static final String TOOL_USE_REPLY = """
            {"id":"msg_1","type":"message","role":"assistant","model":"claude-sonnet-5-5",
             "content":[
               {"type":"thinking","thinking":"","signature":"sig-abc"},
               {"type":"tool_use","id":"toolu_1","name":"get_standings","input":{"limit":5}}],
             "stop_reason":"tool_use","stop_sequence":null,
             "usage":{"input_tokens":1200,"output_tokens":80}}""";

    private static final String TEXT_REPLY = """
            {"id":"msg_2","type":"message","role":"assistant","model":"claude-sonnet-5-5",
             "content":[{"type":"text","text":"You're #7."}],
             "stop_reason":"end_turn","stop_sequence":null,
             "usage":{"input_tokens":1500,"output_tokens":20}}""";

    private ModelGateway.Conversation start(AnthropicModelGateway g) {
        return g.start("SYSTEM RULES",
                List.of(new McpToolDef("get_standings", "Club standings", Map.of(
                        "type", "object",
                        "properties", Map.of("limit", Map.of("type", "integer")),
                        "required", List.of()))),
                List.of(new ChatExchangeDto("q1", "a1", Instant.parse("2026-11-15T10:00:00Z"))),
                "Where am I?");
    }

    @Test
    void firstRequest_hasModelEffortSystemToolsHistoryQuestion() {
        replies.add(TEXT_REPLY);
        start(gateway("sk-test")).send();

        JsonNode r = requests.get(0);
        assertEquals("claude-sonnet-5-5", r.path("model").asText());
        assertEquals(2048, r.path("max_tokens").asInt());
        assertEquals("low", r.path("output_config").path("effort").asText());
        assertEquals("SYSTEM RULES", r.path("system").asText());
        assertEquals("get_standings", r.path("tools").get(0).path("name").asText());
        assertEquals("integer", r.path("tools").get(0).path("input_schema").path("properties").path("limit").path("type").asText());
        JsonNode m = r.path("messages");
        assertEquals(3, m.size());
        assertEquals("user", m.get(0).path("role").asText());
        assertEquals("q1", m.get(0).path("content").asText());
        assertEquals("assistant", m.get(1).path("role").asText());
        assertEquals("a1", m.get(1).path("content").asText());
        assertEquals("Where am I?", m.get(2).path("content").asText());
        assertEquals("sk-test", apiKeys.get(0));
    }

    @Test
    void toolUseReply_parses() {
        replies.add(TOOL_USE_REPLY);
        ModelGateway.Reply reply = start(gateway("sk-test")).send();

        assertEquals("tool_use", reply.stopReason());
        assertEquals(1200, reply.inputTokens());
        assertEquals(80, reply.outputTokens());
        assertEquals(1, reply.toolCalls().size());
        assertEquals("toolu_1", reply.toolCalls().get(0).id());
        assertEquals("get_standings", reply.toolCalls().get(0).name());
        assertEquals(5, ((Number) reply.toolCalls().get(0).arguments().get("limit")).intValue());
    }

    @Test
    void secondRequest_replaysModelContentUnchanged_thenToolResults() {
        replies.add(TOOL_USE_REPLY);
        replies.add(TEXT_REPLY);
        ModelGateway.Conversation c = start(gateway("sk-test"));
        c.send();
        c.addToolResults(List.of(new ModelGateway.ToolResult("toolu_1", "Event 999 does not exist.", true)));
        ModelGateway.Reply last = c.send();

        assertEquals("You're #7.", last.text());
        JsonNode m = requests.get(1).path("messages");
        assertEquals(5, m.size());
        JsonNode assistant = m.get(3);
        assertEquals("assistant", assistant.path("role").asText());
        assertEquals("thinking", assistant.path("content").get(0).path("type").asText());
        assertEquals("sig-abc", assistant.path("content").get(0).path("signature").asText());
        assertEquals("tool_use", assistant.path("content").get(1).path("type").asText());
        JsonNode result = m.get(4).path("content").get(0);
        assertEquals("tool_result", result.path("type").asText());
        assertEquals("toolu_1", result.path("tool_use_id").asText());
        assertTrue(result.path("is_error").asBoolean());
    }

    @Test
    void apiError_becomesModelUnavailable() {
        status = 529;
        assertThrows(ModelUnavailableException.class, () -> start(gateway("sk-test")).send());
    }

    @Test
    void missingApiKey_becomesModelUnavailable_withoutCallingTheApi() {
        assertThrows(ModelUnavailableException.class, () -> start(gateway("")).send());
        assertTrue(requests.isEmpty());
    }
}
