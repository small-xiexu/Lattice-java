package com.xbk.lattice.cli.command;

import com.xbk.lattice.api.admin.AdminRepoDiffResponse;
import com.xbk.lattice.cli.CliExitCodes;
import com.xbk.lattice.cli.remote.LatticeHttpClient;
import com.xbk.lattice.vault.snapshot.VaultDiffSummary;
import com.xbk.lattice.vault.snapshot.VaultGitService;
import com.xbk.lattice.vault.snapshot.VaultSnapshotService;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.List;

/**
 * diff 命令
 *
 * 职责：查看 Vault Git 两次提交之间的文件级差异摘要
 *
 * @author xiexu
 */
@CommandLine.Command(name = "diff", description = "查看 Vault Git 提交差异")
public class DiffCommand extends AbstractCliCommand {

    @CommandLine.Option(names = "--vault", required = true, description = "Vault 目录")
    private Path vaultDir;

    @CommandLine.Option(names = "--from", description = "起始提交哈希")
    private String fromCommitId;

    @CommandLine.Option(names = "--to", description = "目标提交哈希")
    private String toCommitId;

    @CommandLine.Option(names = "--snapshot-id", description = "repo snapshotId；提供后按目标快照对比当前 Vault HEAD")
    private Long snapshotId;

    @Override
    protected Integer runInStandaloneMode() throws Exception {
        try (ConfigurableApplicationContext context = com.xbk.lattice.cli.CliRuntimeSupport.createContext()) {
            if (snapshotId != null && snapshotId > 0) {
                VaultSnapshotService vaultSnapshotService = context.getBean(VaultSnapshotService.class);
                List<VaultDiffSummary> diffSummaries = vaultSnapshotService.diff(vaultDir, snapshotId);
                printJson(diffSummaries);
                return CliExitCodes.SUCCESS;
            }
            if (fromCommitId == null || toCommitId == null) {
                throw new IllegalArgumentException("请提供 --snapshot-id，或同时提供 --from 与 --to");
            }
            VaultGitService vaultGitService = context.getBean(VaultGitService.class);
            List<VaultDiffSummary> diffSummaries = vaultGitService.diff(vaultDir, fromCommitId, toCommitId);
            printJson(diffSummaries);
            return CliExitCodes.SUCCESS;
        }
    }

    @Override
    protected Integer runInRemoteMode(LatticeHttpClient latticeHttpClient) throws Exception {
        if (snapshotId == null || snapshotId <= 0) {
            throw new IllegalArgumentException("远程模式仅支持 --snapshot-id 形式的 repo diff");
        }
        AdminRepoDiffResponse response = latticeHttpClient.get(
                "/api/v1/admin/snapshot/repo/" + snapshotId + "/diff",
                java.util.Map.of("vaultDir", vaultDir.toString()),
                AdminRepoDiffResponse.class
        );
        printJson(response);
        return CliExitCodes.SUCCESS;
    }
}
