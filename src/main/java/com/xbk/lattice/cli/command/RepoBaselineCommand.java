package com.xbk.lattice.cli.command;

import com.xbk.lattice.api.admin.AdminRepoBaselineRequest;
import com.xbk.lattice.cli.CliExitCodes;
import com.xbk.lattice.cli.remote.LatticeHttpClient;
import com.xbk.lattice.governance.repo.RepoBaselineResult;
import com.xbk.lattice.vault.snapshot.VaultSnapshotService;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine;

import java.nio.file.Path;
import java.time.Duration;

/**
 * repo-baseline 命令
 *
 * 职责：建立带 Git commit 的 repo snapshot baseline
 *
 * @author xiexu
 */
@CommandLine.Command(name = "repo-baseline", description = "建立 Git-backed repo snapshot baseline")
public class RepoBaselineCommand extends AbstractCliCommand {

    @CommandLine.Option(names = "--vault", required = true, description = "Vault 目录")
    private Path vaultDir;

    @CommandLine.Option(names = "--description", description = "baseline 描述")
    private String description;

    @Override
    protected Integer runInStandaloneMode() throws Exception {
        try (ConfigurableApplicationContext context = com.xbk.lattice.cli.CliRuntimeSupport.createContext()) {
            VaultSnapshotService vaultSnapshotService = context.getBean(VaultSnapshotService.class);
            RepoBaselineResult result = vaultSnapshotService.createBaselineSnapshot(vaultDir, description);
            printJson(result);
            return CliExitCodes.SUCCESS;
        }
    }

    @Override
    protected Integer runInRemoteMode(LatticeHttpClient latticeHttpClient) throws Exception {
        AdminRepoBaselineRequest request = new AdminRepoBaselineRequest();
        request.setVaultDir(vaultDir.toString());
        request.setDescription(description);
        RepoBaselineResult result = latticeHttpClient.post(
                "/api/v1/admin/snapshot/repo/baseline",
                request,
                RepoBaselineResult.class
        );
        printJson(result);
        return CliExitCodes.SUCCESS;
    }

    @Override
    protected Duration defaultRemoteRequestTimeout() {
        return Duration.ofMinutes(10);
    }
}
