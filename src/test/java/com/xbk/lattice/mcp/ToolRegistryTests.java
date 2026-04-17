package com.xbk.lattice.mcp;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ToolRegistry 测试
 *
 * 职责：验证注册表可从 SyncToolSpecification 提取统一工具定义
 *
 * @author xiexu
 */
class ToolRegistryTests {

    /**
     * 验证工具定义提取。
     */
    @Test
    void shouldListToolDefinitionsFromSyncSpecifications() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("lattice_query")
                .title("Query")
                .description("Query the knowledge base")
                .inputSchema(new McpSchema.JsonSchema(
                        "object",
                        java.util.Map.of(),
                        java.util.List.of(),
                        false,
                        java.util.Map.of(),
                        java.util.Map.of()
                ))
                .build();
        McpServerFeatures.SyncToolSpecification toolSpecification = new McpServerFeatures.SyncToolSpecification(
                tool,
                (exchange, arguments) -> null
        );
        ToolRegistry toolRegistry = new ToolRegistry(new StaticObjectProvider(List.of(toolSpecification)));

        List<ToolDefinition> toolDefinitions = toolRegistry.listTools();

        assertThat(toolRegistry.size()).isEqualTo(1);
        assertThat(toolDefinitions).hasSize(1);
        assertThat(toolDefinitions.get(0).getName()).isEqualTo("lattice_query");
        assertThat(toolDefinitions.get(0).getDescription()).isEqualTo("Query the knowledge base");
    }

    /**
     * 固定对象提供器。
     *
     * @param <T> 泛型
     * @author xiexu
     */
    private static class StaticObjectProvider implements ObjectProvider<List<McpServerFeatures.SyncToolSpecification>> {

        private final List<McpServerFeatures.SyncToolSpecification> value;

        private StaticObjectProvider(List<McpServerFeatures.SyncToolSpecification> value) {
            this.value = value;
        }

        @Override
        public List<McpServerFeatures.SyncToolSpecification> getObject(Object... args) {
            return value;
        }

        @Override
        public List<McpServerFeatures.SyncToolSpecification> getIfAvailable() {
            return value;
        }

        @Override
        public List<McpServerFeatures.SyncToolSpecification> getIfUnique() {
            return value;
        }

        @Override
        public List<McpServerFeatures.SyncToolSpecification> getObject() {
            return value;
        }

        @Override
        public List<McpServerFeatures.SyncToolSpecification> getIfAvailable(Supplier<List<McpServerFeatures.SyncToolSpecification>> defaultSupplier) {
            return value == null ? defaultSupplier.get() : value;
        }

        @Override
        public List<McpServerFeatures.SyncToolSpecification> getIfUnique(Supplier<List<McpServerFeatures.SyncToolSpecification>> defaultSupplier) {
            return value == null ? defaultSupplier.get() : value;
        }

        @Override
        public java.util.Iterator<List<McpServerFeatures.SyncToolSpecification>> iterator() {
            return List.<List<McpServerFeatures.SyncToolSpecification>>of(value).iterator();
        }
    }
}
