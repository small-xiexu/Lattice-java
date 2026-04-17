package com.xbk.lattice.cli.command;

import com.xbk.lattice.cli.CliExitCodes;
import com.xbk.lattice.cli.remote.LatticeHttpClient;
import com.xbk.lattice.governance.repo.RepoHistoryReport;
import com.xbk.lattice.governance.repo.RepoSnapshotService;
import com.xbk.lattice.vault.snapshot.VaultGitService;
import com.xbk.lattice.vault.snapshot.VaultHistoryItem;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.List;

/**
 * history 命令
 *
 * 职责：查看整库级快照历史
 *
 * @author xiexu
 */
@CommandLine.Command(name = "history", description = "查看整库快照历史")
public class HistoryCommand extends AbstractCliCommand {

    @CommandLine.Option(names = {"-n", "--limit"}, defaultValue = "10", description = "返回数量")
    private int limit;

    @CommandLine.Option(names = "--vault", description = "Vault 目录；提供后切换为 Vault Git 历史")
    private Path vaultDir;

    @Override
    protected Integer runInStandaloneMode() throws Exception {
        try (ConfigurableApplicationContext context = com.xbk.lattice.cli.CliRuntimeSupport.createContext()) {
            if (vaultDir != null) {
                VaultGitService vaultGitService = context.getBean(VaultGitService.class);
                List<VaultHistoryItem> historyItems = vaultGitService.history(vaultDir, limit);
                printJson(historyItems);
                return CliExitCodes.SUCCESS;
            }
            RepoSnapshotService repoSnapshotService = context.getBean(RepoSnapshotService.class);
            RepoHistoryReport repoHistoryReport = repoSnapshotService.history(limit);
            printJson(repoHistoryReport);
            return CliExitCodes.SUCCESS;
        }
    }

    @Override
    protected Integer runInRemoteMode(LatticeHttpClient latticeHttpClient) throws Exception {
        RepoHistoryReport repoHistoryReport = latticeHttpClient.get(
                "/api/v1/admin/snapshot/repo",
                java.util.Map.of("limit", String.valueOf(limit)),
                RepoHistoryReport.class
        );
        printJson(repoHistoryReport);
        return CliExitCodes.SUCCESS;
    }
}
