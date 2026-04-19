package com.xbk.lattice.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CLI 启动基座测试
 *
 * 职责：验证 CLI 根命令已注册并可输出帮助信息
 *
 * @author xiexu
 */
class LatticeCliBootstrapTests {

    /**
     * 验证 `--help` 可输出首批命令。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldPrintCliHelp() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        CommandLine commandLine = LatticeCliMain.createCommandLine();
        commandLine.setOut(new PrintWriter(outputStream, true, StandardCharsets.UTF_8));

        int exitCode = commandLine.execute("--help");
        String helpText = outputStream.toString(StandardCharsets.UTF_8);

        assertThat(exitCode).isEqualTo(CliExitCodes.SUCCESS);
        assertThat(helpText).contains("lattice-java");
        assertThat(helpText).contains("compile");
        assertThat(helpText).contains("query");
        assertThat(helpText).contains("search");
        assertThat(helpText).contains("status");
        assertThat(helpText).contains("source-list");
        assertThat(helpText).contains("source-sync");
        assertThat(helpText).contains("lint");
        assertThat(helpText).contains("history");
        assertThat(helpText).contains("diff");
        assertThat(helpText).contains("rollback");
        assertThat(helpText).contains("vault-export");
        assertThat(helpText).contains("vault-sync");
        assertThat(helpText).contains("mcp-stdio");
        assertThat(helpText).contains("serve");
    }
}
