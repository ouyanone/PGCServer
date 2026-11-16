package com.shiyuan.chat;

/** The MCP server refused the member's token (HTTP 401/403). The member signs in again. */
public class McpUnauthorizedException extends RuntimeException {
    public McpUnauthorizedException(String message) {
        super(message, null, false, false);
    }
}
