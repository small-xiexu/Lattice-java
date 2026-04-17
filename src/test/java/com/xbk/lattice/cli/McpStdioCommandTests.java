package com.xbk.lattice.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * mcp-stdio 命令测试
 *
 * 职责：验证 mcp-stdio 已注册且可输出帮助信息
 *
 * @author xiexu
 */
class McpStdioCommandTests {

    /**
     * 验证 mcp-stdio 子命令帮助信息。
     */
    @Test
    void shouldPrintMcpStdioHelp() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        CommandLine commandLine = LatticeCliMain.createCommandLine();
        commandLine.setOut(new PrintWriter(outputStream, true, StandardCharsets.UTF_8));

        int exitCode = commandLine.execute("mcp-stdio", "--help");
        String helpText = outputStream.toString(StandardCharsets.UTF_8);

        assertThat(exitCode).isEqualTo(CliExitCodes.SUCCESS);
        assertThat(helpText).contains("以 stdio 模式启动 MCP 服务");
        assertThat(helpText).contains("--bridge");
        assertThat(helpText).contains("--name");
        assertThat(helpText).contains("--version");
    }
}
