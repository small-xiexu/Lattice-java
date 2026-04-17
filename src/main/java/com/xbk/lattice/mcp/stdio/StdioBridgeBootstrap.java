package com.xbk.lattice.mcp.stdio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.cli.CliOutputFormatter;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

/**
 * stdio MCP Bridge 模式启动器
 *
 * 职责：通过本地 stdio 暴露 MCP，并把工具调用转发到远端 HTTP MCP
 *
 * @author xiexu
 */
public class StdioBridgeBootstrap {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final Function<String, BridgeClient> bridgeClientFactory;

    public StdioBridgeBootstrap() {
        this(StdioBridgeBootstrap::createBridgeClient);
    }

    StdioBridgeBootstrap(Function<String, BridgeClient> bridgeClientFactory) {
        this.bridgeClientFactory = bridgeClientFactory;
    }

    /**
     * 启动 Bridge 模式。
     *
     * @param bridgeUrl 远端 HTTP MCP 地址
     * @param serverName 本地服务名称
     * @param serverVersion 本地服务版本
     * @return 退出码
     * @throws Exception 启动异常
     */
    public int start(String bridgeUrl, String serverName, String serverVersion) throws Exception {
        try (BridgeClient bridgeClient = bridgeClientFactory.apply(bridgeUrl)) {
            bridgeClient.initialize();
            List<McpSchema.Tool> remoteTools = bridgeClient.listTools();
            StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(
                    new JacksonMcpJsonMapper(OBJECT_MAPPER)
            );
            McpSyncServer localServer = McpServer.sync(transportProvider)
                    .serverInfo(serverName, serverVersion)
                    .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                    .instructions(bridgeClient.instructions())
                    .tools(createProxyToolSpecifications(remoteTools, bridgeClient))
                    .build();

            CountDownLatch shutdownSignal = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    localServer.closeGracefully();
                }
                finally {
                    shutdownSignal.countDown();
                }
            }, "lattice-mcp-stdio-bridge-shutdown"));
            CliOutputFormatter.printErrorLine(
                    "MCP stdio bridge started: " + bridgeUrl + ", tools=" + remoteTools.size()
            );
            shutdownSignal.await();
            return 0;
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return 0;
        }
    }

    List<McpServerFeatures.SyncToolSpecification> createProxyToolSpecifications(
            List<McpSchema.Tool> remoteTools,
            BridgeClient bridgeClient
    ) {
        List<McpServerFeatures.SyncToolSpecification> specifications = new ArrayList<McpServerFeatures.SyncToolSpecification>();
        for (McpSchema.Tool remoteTool : remoteTools) {
            specifications.add(new McpServerFeatures.SyncToolSpecification(
                    remoteTool,
                    (exchange, arguments) -> bridgeClient.callTool(new McpSchema.CallToolRequest(remoteTool.name(), arguments)),
                    (exchange, request) -> bridgeClient.callTool(request)
            ));
        }
        return specifications;
    }

    private static BridgeClient createBridgeClient(String bridgeUrl) {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(bridgeUrl)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        McpSyncClient syncClient = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("lattice-java-bridge", "lattice-java-bridge", "1.0.0"))
                .requestTimeout(Duration.ofSeconds(60))
                .initializationTimeout(Duration.ofSeconds(60))
                .build();
        return new RemoteBridgeClient(syncClient);
    }

    interface BridgeClient extends AutoCloseable {

        void initialize();

        String instructions();

        List<McpSchema.Tool> listTools();

        McpSchema.CallToolResult callTool(McpSchema.CallToolRequest request);

        @Override
        void close();
    }

    private static class RemoteBridgeClient implements BridgeClient {

        private final McpSyncClient syncClient;

        private RemoteBridgeClient(McpSyncClient syncClient) {
            this.syncClient = syncClient;
        }

        @Override
        public void initialize() {
            syncClient.initialize();
        }

        @Override
        public String instructions() {
            return syncClient.getServerInstructions();
        }

        @Override
        public List<McpSchema.Tool> listTools() {
            return syncClient.listTools().tools();
        }

        @Override
        public McpSchema.CallToolResult callTool(McpSchema.CallToolRequest request) {
            return syncClient.callTool(request);
        }

        @Override
        public void close() {
            syncClient.closeGracefully();
        }
    }

    static class RecordingBridgeClient implements BridgeClient {

        private McpSchema.CallToolRequest lastRequest;

        @Override
        public void initialize() {
            // no-op
        }

        @Override
        public String instructions() {
            return "bridge";
        }

        @Override
        public List<McpSchema.Tool> listTools() {
            return List.of();
        }

        @Override
        public McpSchema.CallToolResult callTool(McpSchema.CallToolRequest request) {
            lastRequest = request;
            return new McpSchema.CallToolResult(List.of(), false, Map.of());
        }

        @Override
        public void close() {
            // no-op
        }

        McpSchema.CallToolRequest getLastRequest() {
            return lastRequest;
        }
    }
}
