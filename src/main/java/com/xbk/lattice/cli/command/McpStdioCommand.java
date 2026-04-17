package com.xbk.lattice.cli.command;

import com.xbk.lattice.cli.CliExitCodes;
import com.xbk.lattice.mcp.stdio.StdioMcpBootstrap;
import picocli.CommandLine;

/**
 * mcp-stdio 命令
 *
 * 职责：以 stdio 传输模式启动 MCP 服务，供 Claude / Codex 直接拉起
 *
 * @author xiexu
 */
@CommandLine.Command(
        name = "mcp-stdio",
        description = "以 stdio 模式启动 MCP 服务",
        mixinStandardHelpOptions = true
)
public class McpStdioCommand extends AbstractCliCommand {

    @CommandLine.Option(names = "--bridge", description = "Bridge 模式代理到远端 HTTP MCP，例如 http://localhost:8080/mcp")
    private String bridgeUrl;

    @CommandLine.Option(names = "--name", defaultValue = "lattice-java", description = "MCP 服务名称")
    private String serverName;

    @CommandLine.Option(names = "--version", defaultValue = "1.0.0", description = "MCP 服务版本")
    private String serverVersion;

    @Override
    protected Integer runInStandaloneMode() throws Exception {
        if (bridgeUrl != null && !bridgeUrl.isBlank()) {
            return new com.xbk.lattice.mcp.stdio.StdioBridgeBootstrap().start(bridgeUrl.trim(), serverName, serverVersion);
        }
        return new StdioMcpBootstrap().start(serverName, serverVersion);
    }
}
