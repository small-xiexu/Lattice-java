package com.xbk.lattice.mcp;

/**
 * MCP 工具定义
 *
 * 职责：抽象统一的工具元数据，供 stdio / HTTP MCP 入口共享
 *
 * @author xiexu
 */
public class ToolDefinition {

    private final String name;

    private final String title;

    private final String description;

    public ToolDefinition(String name, String title, String description) {
        this.name = name;
        this.title = title;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }
}
