package com.shiyuan.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The club assistant's MCP client. What matters most: every request carries the bearer token
 * of the member whose question it is, and nothing else's.
 */
class McpGatewayTest {

    private final FakeMcpServer server;
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-15T15:00:00Z"));
    private final McpGateway gateway;

    McpGatewayTest() throws Exception {
        server = new FakeMcpServer();
        Clock clock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        gateway = new McpGateway(server.url(), clock);
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void everyRequestCarriesBearerOfThisSession() {
        try (McpSession a = gateway.open("tok-A")) {
            a.tools();
            a.call("get_standings", Map.of());
        }
        try (McpSession b = gateway.open("tok-B")) {
            b.call("get_standings", Map.of());
        }
        List<String> auth = server.seen.stream().map(FakeMcpServer.Seen::authorization).toList();
        long sessionA = auth.stream().filter("Bearer tok-A"::equals).count();
        long sessionB = auth.stream().filter("Bearer tok-B"::equals).count();
        assertEquals(auth.size(), sessionA + sessionB, "a request without a member's token: " + auth);
        assertTrue(sessionA >= 3, "initialize, tools/list, tools/call as A: " + auth);
        assertTrue(sessionB >= 2, "initialize, tools/call as B: " + auth);
    }

    @Test
    void initializesBeforeUsingTheServer() {
        try (McpSession s = gateway.open("tok")) {
            s.call("get_standings", Map.of());
        }
        assertEquals(List.of("initialize", "notifications/initialized", "tools/call"),
                server.seen.stream().map(FakeMcpServer.Seen::method).toList());
    }

    @Test
    void sendsNegotiatedProtocolVersionAfterInitialize() {
        try (McpSession s = gateway.open("tok")) {
            s.call("get_standings", Map.of());
        }
        FakeMcpServer.Seen call = server.seen.get(server.seen.size() - 1);
        assertEquals("2025-06-18", call.protocolVersion());
    }

    @Test
    void tools_mapsNameDescriptionSchema() {
        try (McpSession s = gateway.open("tok")) {
            McpToolDef t = s.tools().get(0);
            assertEquals("get_standings", t.name());
            assertEquals("Club standings", t.description());
            assertEquals("object", t.inputSchema().get("type"));
        }
    }

    @Test
    void call_returnsText() {
        try (McpSession s = gateway.open("tok")) {
            ToolOutcome out = s.call("get_standings", Map.of("limit", 5));
            assertEquals("{\"totalPlayers\":42}", out.text());
            assertFalse(out.isError());
        }
    }

    @Test
    void call_returnsErrorFlag() {
        try (McpSession s = gateway.open("tok")) {
            ToolOutcome out = s.call("broken", Map.of());
            assertTrue(out.isError());
            assertEquals("Event 999 does not exist.", out.text());
        }
    }

    @Test
    void readsServerSentEventReplies() {
        server.answerAsSse = true;
        try (McpSession s = gateway.open("tok")) {
            assertEquals("{\"totalPlayers\":42}", s.call("get_standings", Map.of()).text());
        }
    }

    @Test
    void http401_throwsMcpUnauthorized() {
        server.status = 401;
        try (McpSession s = gateway.open("expired")) {
            assertThrows(McpUnauthorizedException.class, () -> s.call("get_standings", Map.of()));
        }
    }

    @Test
    void http500_throwsMcpUnavailable() {
        server.status = 500;
        try (McpSession s = gateway.open("tok")) {
            assertThrows(McpUnavailableException.class, () -> s.call("get_standings", Map.of()));
        }
    }

    @Test
    void unreachableServer_throwsMcpUnavailable() {
        McpGateway nowhere = new McpGateway("http://127.0.0.1:1/mcp", Clock.systemUTC());
        try (McpSession s = nowhere.open("tok")) {
            assertThrows(McpUnavailableException.class, () -> s.call("get_standings", Map.of()));
        }
    }

    @Test
    void toolsCachedAcrossSessions() {
        try (McpSession a = gateway.open("tok-A")) { a.tools(); }
        try (McpSession b = gateway.open("tok-B")) { b.tools(); }
        assertEquals(1, server.count("tools/list"));
    }

    @Test
    void toolsCacheExpiresAfterFiveMinutes() {
        try (McpSession a = gateway.open("tok-A")) { a.tools(); }
        now.set(now.get().plus(Duration.ofMinutes(5)).plusSeconds(1));
        try (McpSession b = gateway.open("tok-B")) { b.tools(); }
        assertEquals(2, server.count("tools/list"));
    }

    @Test
    void errorMessagesNeverContainTheToken() {
        server.status = 401;
        try (McpSession s = gateway.open("secret-token-value")) {
            Exception e = assertThrows(McpUnauthorizedException.class, () -> s.tools());
            assertFalse(String.valueOf(e.getMessage()).contains("secret-token-value"));
        }
    }
}
