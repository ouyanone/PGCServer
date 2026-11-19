package com.shiyuan.chat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

/**
 * A loopback stand-in for https://pgcgolf.club/mcp: answers the JSON-RPC methods the club
 * assistant uses, and records what each request carried. Not a real MCP server -- just enough
 * of the wire format to prove what the client sends and how it reads replies.
 */
public final class FakeMcpServer implements AutoCloseable {

    public record Seen(String method, String authorization, String protocolVersion) {}

    private static final ObjectMapper JSON = new ObjectMapper();

    public final List<Seen> seen = new CopyOnWriteArrayList<>();
    public volatile int status = 200;
    /** Status for tools/call only; the handshake and tools/list still succeed. */
    public volatile int toolsCallStatus = 200;
    /** When set, every tools/call answers with an error result (isError), as the "broken" tool does. */
    public volatile boolean toolsCallIsError = false;
    /** When set, tools/call gets no HTTP reply at all: the connection is dropped. */
    public volatile boolean toolsCallDropsConnection = false;
    /** When set, tools/list also offers an admin_ tool (as the real server does after this feature). */
    public volatile boolean listsAdminTool = false;
    volatile boolean answerAsSse = false;
    private final HttpServer http;

    public FakeMcpServer() throws IOException {
        http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/mcp", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) { // GET stream / DELETE session
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            JsonNode req = JSON.readTree(exchange.getRequestBody());
            String method = req.path("method").asText();
            seen.add(new Seen(method, exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("MCP-Protocol-Version")));
            if (method.equals("tools/call") && toolsCallDropsConnection) {
                exchange.close();
                return;
            }
            int reply = method.equals("tools/call") && toolsCallStatus != 200 ? toolsCallStatus : status;
            if (reply != 200) {
                exchange.sendResponseHeaders(reply, -1);
                exchange.close();
                return;
            }
            if (!req.has("id")) { // a notification
                exchange.sendResponseHeaders(202, -1);
                exchange.close();
                return;
            }
            String result = switch (method) {
                case "initialize" -> """
                        {"protocolVersion":"2025-06-18","capabilities":{"tools":{}},
                         "serverInfo":{"name":"fake","version":"0"}}""";
                case "tools/list" -> """
                        {"tools":[{"name":"get_standings","description":"Club standings",
                          "inputSchema":{"type":"object","properties":{"limit":{"type":"integer"}}}}%s]}"""
                        .formatted(listsAdminTool ? """
                        ,{"name":"admin_create_tournament","description":"Create a tournament",
                          "inputSchema":{"type":"object","properties":{"name":{"type":"string"}}}}""" : "");
                case "tools/call" -> toolsCallIsError || req.path("params").path("name").asText().equals("broken")
                        ? """
                          {"content":[{"type":"text","text":"Event 999 does not exist."}],"isError":true}"""
                        : """
                          {"content":[{"type":"text","text":"{\\"totalPlayers\\":42}"}],"isError":false}""";
                default -> null;
            };
            String body = result == null
                    ? "{\"jsonrpc\":\"2.0\",\"id\":" + req.get("id") + ",\"error\":{\"code\":-32601,\"message\":\"no such method\"}}"
                    : "{\"jsonrpc\":\"2.0\",\"id\":" + req.get("id") + ",\"result\":" + result + "}";
            byte[] bytes;
            if (answerAsSse) {
                exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
                bytes = ("event: message\ndata: " + body.replace("\n", "") + "\n\n").getBytes(StandardCharsets.UTF_8);
            } else {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                bytes = body.getBytes(StandardCharsets.UTF_8);
            }
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        http.start();
    }

    public String url() {
        return "http://127.0.0.1:" + http.getAddress().getPort() + "/mcp";
    }

    public long count(String method) {
        return seen.stream().filter(s -> s.method().equals(method)).count();
    }

    @Override
    public void close() {
        http.stop(0);
    }
}
