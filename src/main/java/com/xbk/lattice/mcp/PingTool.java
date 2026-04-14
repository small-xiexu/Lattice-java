package com.xbk.lattice.mcp;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

@Service
public class PingTool {

    @McpTool(name = "lattice_ping", description = "Returns a simple pong response for MCP discovery smoke tests")
    public String ping(@McpToolParam(description = "Optional echo payload") String payload) {
        if (payload == null || payload.isBlank()) {
            return "pong";
        }
        return "pong:" + payload;
    }
}
