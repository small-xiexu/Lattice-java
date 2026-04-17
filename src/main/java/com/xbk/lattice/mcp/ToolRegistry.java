package com.xbk.lattice.mcp;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * MCP 工具注册表
 *
 * 职责：统一暴露当前服务实际注册的 MCP 工具元数据
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class ToolRegistry {

    private final ObjectProvider<List<McpServerFeatures.SyncToolSpecification>> syncToolSpecificationsProvider;

    public ToolRegistry(ObjectProvider<List<McpServerFeatures.SyncToolSpecification>> syncToolSpecificationsProvider) {
        this.syncToolSpecificationsProvider = syncToolSpecificationsProvider;
    }

    /**
     * 返回当前注册的工具定义。
     *
     * @return 工具定义列表
     */
    public List<ToolDefinition> listTools() {
        List<McpServerFeatures.SyncToolSpecification> toolSpecifications = syncToolSpecificationsProvider.getIfAvailable(List::of);
        List<ToolDefinition> definitions = new ArrayList<ToolDefinition>();
        for (McpServerFeatures.SyncToolSpecification toolSpecification : toolSpecifications) {
            McpSchema.Tool tool = toolSpecification.tool();
            definitions.add(new ToolDefinition(
                    tool.name(),
                    tool.title(),
                    tool.description()
            ));
        }
        definitions.sort(Comparator.comparing(ToolDefinition::getName));
        return definitions;
    }

    /**
     * 返回工具数量。
     *
     * @return 工具数量
     */
    public int size() {
        return listTools().size();
    }
}
