package com.xbk.lattice.mcp.stdio;

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.server.McpServerFeatures;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StdioBridgeBootstrap 测试
 *
 * 职责：验证 Bridge 模式会基于远端工具清单生成本地代理工具定义
 *
 * @author xiexu
 */
class StdioBridgeBootstrapTests {

    /**
     * 验证代理工具会转发到远端客户端。
     */
    @Test
    void shouldCreateProxyToolSpecifications() {
        StdioBridgeBootstrap.RecordingBridgeClient bridgeClient = new StdioBridgeBootstrap.RecordingBridgeClient();
        StdioBridgeBootstrap bootstrap = new StdioBridgeBootstrap(ignored -> bridgeClient);
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("lattice_query")
                .title("Query")
                .description("Query tool")
                .inputSchema(new McpSchema.JsonSchema(
                        "object",
                        Collections.<String, Object>emptyMap(),
                        Collections.<String>emptyList(),
                        false,
                        Collections.<String, Object>emptyMap(),
                        Collections.<String, Object>emptyMap()
                ))
                .build();

        List<McpServerFeatures.SyncToolSpecification> specifications = bootstrap.createProxyToolSpecifications(
                Collections.singletonList(tool),
                bridgeClient
        );
        McpSchema.CallToolResult result = specifications.get(0)
                .callHandler()
                .apply(null, new McpSchema.CallToolRequest(
                        "lattice_query",
                        Collections.<String, Object>singletonMap("question", "retry?")
                ));

        assertThat(specifications).hasSize(1);
        assertThat(bridgeClient.getLastRequest()).isNotNull();
        assertThat(bridgeClient.getLastRequest().name()).isEqualTo("lattice_query");
        assertThat(result.isError()).isFalse();
    }
}
