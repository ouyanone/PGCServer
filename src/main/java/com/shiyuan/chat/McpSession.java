package com.shiyuan.chat;

import java.util.List;
import java.util.Map;

/** One question's connection to the MCP server, carrying one member's token. */
public interface McpSession extends AutoCloseable {

    List<McpToolDef> tools();

    ToolOutcome call(String name, Map<String, Object> arguments);

    @Override
    void close();
}
