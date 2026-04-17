package com.xbk.lattice.cli.command;

import com.xbk.lattice.api.admin.AdminArticleRollbackRequest;
import com.xbk.lattice.api.admin.AdminRepoRollbackRequest;
import com.xbk.lattice.cli.CliExitCodes;
import com.xbk.lattice.cli.remote.LatticeHttpClient;
import com.xbk.lattice.governance.RollbackResult;
import com.xbk.lattice.governance.SnapshotService;
import com.xbk.lattice.governance.repo.RepoRollbackResult;
import com.xbk.lattice.vault.snapshot.VaultSnapshotService;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine;

import java.nio.file.Path;

/**
 * rollback 命令
 *
 * 职责：回滚单篇文章或整库到指定快照
 *
 * @author xiexu
 */
@CommandLine.Command(name = "rollback", description = "回滚单篇文章或整库到指定快照")
public class RollbackCommand extends AbstractCliCommand {

    @CommandLine.Option(names = "--concept-id", description = "文章 conceptId")
    private String conceptId;

    @CommandLine.Option(names = "--snapshot-id", required = true, description = "快照 snapshotId")
    private long snapshotId;

    @CommandLine.Option(names = "--vault", description = "Vault 目录；提供且未指定 conceptId 时执行整库回滚")
    private Path vaultDir;

    @Override
    protected Integer runInStandaloneMode() throws Exception {
        try (ConfigurableApplicationContext context = com.xbk.lattice.cli.CliRuntimeSupport.createContext()) {
            if (conceptId == null || conceptId.isBlank()) {
                if (vaultDir == null) {
                    throw new IllegalArgumentException("整库回滚必须提供 --vault，文章回滚必须提供 --concept-id");
                }
                VaultSnapshotService vaultSnapshotService = context.getBean(VaultSnapshotService.class);
                RepoRollbackResult rollbackResult = vaultSnapshotService.rollback(vaultDir, snapshotId);
                printJson(rollbackResult);
                return CliExitCodes.SUCCESS;
            }
            SnapshotService snapshotService = context.getBean(SnapshotService.class);
            RollbackResult rollbackResult = snapshotService.rollback(conceptId, snapshotId);
            printJson(rollbackResult);
            return CliExitCodes.SUCCESS;
        }
    }

    @Override
    protected Integer runInRemoteMode(LatticeHttpClient latticeHttpClient) throws Exception {
        if (conceptId == null || conceptId.isBlank()) {
            if (vaultDir == null) {
                throw new IllegalArgumentException("整库回滚必须提供 --vault，文章回滚必须提供 --concept-id");
            }
            AdminRepoRollbackRequest request = new AdminRepoRollbackRequest();
            request.setSnapshotId(snapshotId);
            request.setVaultDir(vaultDir.toString());
            RepoRollbackResult rollbackResult = latticeHttpClient.post(
                    "/api/v1/admin/rollback/repo",
                    request,
                    RepoRollbackResult.class
            );
            printJson(rollbackResult);
            return CliExitCodes.SUCCESS;
        }
        AdminArticleRollbackRequest request = new AdminArticleRollbackRequest();
        request.setConceptId(conceptId);
        request.setSnapshotId(snapshotId);
        RollbackResult rollbackResult = latticeHttpClient.post(
                "/api/v1/admin/rollback/article",
                request,
                RollbackResult.class
        );
        printJson(rollbackResult);
        return CliExitCodes.SUCCESS;
    }
}
