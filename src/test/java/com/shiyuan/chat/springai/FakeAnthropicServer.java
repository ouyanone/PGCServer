package com.shiyuan.chat.springai;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

/** A loopback stand-in for the Anthropic Messages API: records requests, replies from a queue. */
final class FakeAnthropicServer implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper();

    final List<JsonNode> requests = new CopyOnWriteArrayList<>();
    final List<String> apiKeys = new CopyOnWriteArrayList<>();
    final Deque<String> replies = new ArrayDeque<>();
    volatile int status = 200;
    private final HttpServer http;

    FakeAnthropicServer() throws IOException {
        http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/v1/messages", ex -> {
            requests.add(JSON.readTree(ex.getRequestBody()));
            apiKeys.add(ex.getRequestHeaders().getFirst("x-api-key"));
            String reply;
            synchronized (replies) {
                reply = replies.poll();
            }
            byte[] body = (status == 200 && reply != null ? reply
                    : "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"busy\"}}")
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status == 200 && reply == null ? 500 : status, body.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        });
        http.start();
    }

    String url() {
        return "http://127.0.0.1:" + http.getAddress().getPort();
    }

    void reply(String... json) {
        synchronized (replies) {
            replies.addAll(List.of(json));
        }
    }

    @Override
    public void close() {
        http.stop(0);
    }

    static String toolUse(String id, String tool, String inputJson, String extraBlocks, int in, int out) {
        return """
                {"id":"msg_%s","type":"message","role":"assistant","model":"claude-sonnet-5-5",
                 "content":[%s{"type":"tool_use","id":"%s","name":"%s","input":%s}],
                 "stop_reason":"tool_use","stop_sequence":null,
                 "usage":{"input_tokens":%d,"output_tokens":%d}}"""
                .formatted(id, extraBlocks, id, tool, inputJson, in, out);
    }

    static String text(String text, String stopReason, int in, int out) {
        return """
                {"id":"msg_t","type":"message","role":"assistant","model":"claude-sonnet-5-5",
                 "content":[{"type":"text","text":%s}],
                 "stop_reason":"%s","stop_sequence":null,
                 "usage":{"input_tokens":%d,"output_tokens":%d}}"""
                .formatted(quote(text), stopReason, in, out);
    }

    private static String quote(String s) {
        try {
            return JSON.writeValueAsString(s);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
