package com.shiyuan.chat;

/** The MCP server could not be reached, timed out, or answered with something unusable. */
public class McpUnavailableException extends RuntimeException {
    public McpUnavailableException(String message) {
        super(message, null, false, false);
    }
}
