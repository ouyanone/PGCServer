package com.shiyuan.chat;

import java.util.Map;

/** One MCP tool as the server lists it; handed to Claude as a tool definition. */
public record McpToolDef(String name, String description, Map<String, Object> inputSchema) {}
