package com.xbk.lattice.mcp.stdio;

import com.xbk.lattice.cli.CliOutputFormatter;
import com.xbk.lattice.cli.CliRuntimeSupport;
import com.xbk.lattice.mcp.ToolRegistry;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * stdio MCP 独立模式启动器
 *
 * 职责：启动轻量 Spring 上下文，通过标准输入输出提供 MCP 服务
 *
 * @author xiexu
 */
public class StdioMcpBootstrap {

    /**
     * 启动 stdio MCP 独立模式。
     *
     * @param serverName 服务名称
     * @param serverVersion 服务版本
     * @return 退出码
     * @throws Exception 启动异常
     */
    public int start(String serverName, String serverVersion) throws Exception {
        ConfigurableApplicationContext context = CliRuntimeSupport.createContext(
                List.of("jdbc", "cli", "mcp-stdio"),
                List.of(
                        "spring.main.lazy-initialization=true",
                        "lattice.compiler.jobs.worker-enabled=false",
                        "spring.ai.mcp.server.enabled=true",
                        "spring.ai.mcp.server.stdio=true",
                        "spring.ai.mcp.server.name=" + serverName,
                        "spring.ai.mcp.server.version=" + serverVersion
                )
        );
        CountDownLatch shutdownSignal = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(shutdownSignal::countDown, "lattice-mcp-stdio-shutdown"));
        try (context) {
            ToolRegistry toolRegistry = context.getBean(ToolRegistry.class);
            CliOutputFormatter.printErrorLine(
                    "MCP stdio server started: " + serverName + "@" + serverVersion + ", tools=" + toolRegistry.size()
            );
            shutdownSignal.await();
            return 0;
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return 0;
        }
    }
}
