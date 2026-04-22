package com.xbk.lattice.cli;

import com.xbk.lattice.cli.command.CompileCommand;
import com.xbk.lattice.cli.command.DiffCommand;
import com.xbk.lattice.cli.command.LintCommand;
import com.xbk.lattice.cli.command.McpStdioCommand;
import com.xbk.lattice.cli.command.QueryCommand;
import com.xbk.lattice.cli.command.RepoBaselineCommand;
import com.xbk.lattice.cli.command.RollbackCommand;
import com.xbk.lattice.cli.command.SearchCommand;
import com.xbk.lattice.cli.command.ServeCommand;
import com.xbk.lattice.cli.command.SourceListCommand;
import com.xbk.lattice.cli.command.SourceSyncCommand;
import com.xbk.lattice.cli.command.StatusCommand;
import com.xbk.lattice.cli.command.HistoryCommand;
import com.xbk.lattice.cli.command.VaultExportCommand;
import com.xbk.lattice.cli.command.VaultSyncCommand;
import picocli.CommandLine;

/**
 * CLI 启动入口
 *
 * 职责：注册根命令与首批可用子命令，并执行命令行解析
 *
 * @author xiexu
 */
public final class LatticeCliMain {

    private LatticeCliMain() {
    }

    public static void main(String[] args) {
        int exitCode = createCommandLine().execute(args);
        if (exitCode != CliExitCodes.SUCCESS) {
            System.exit(exitCode);
        }
    }

    /**
     * 创建命令行实例。
     *
     * @return 命令行实例
     */
    static CommandLine createCommandLine() {
        CommandLine commandLine = new CommandLine(new LatticeCliRootCommand());
        commandLine.addSubcommand("compile", new CompileCommand());
        commandLine.addSubcommand("query", new QueryCommand());
        commandLine.addSubcommand("search", new SearchCommand());
        commandLine.addSubcommand("status", new StatusCommand());
        commandLine.addSubcommand("source-list", new SourceListCommand());
        commandLine.addSubcommand("source-sync", new SourceSyncCommand());
        commandLine.addSubcommand("lint", new LintCommand());
        commandLine.addSubcommand("history", new HistoryCommand());
        commandLine.addSubcommand("repo-baseline", new RepoBaselineCommand());
        commandLine.addSubcommand("diff", new DiffCommand());
        commandLine.addSubcommand("rollback", new RollbackCommand());
        commandLine.addSubcommand("vault-export", new VaultExportCommand());
        commandLine.addSubcommand("vault-sync", new VaultSyncCommand());
        commandLine.addSubcommand("mcp-stdio", new McpStdioCommand());
        commandLine.addSubcommand("serve", new ServeCommand());
        return commandLine;
    }
}
