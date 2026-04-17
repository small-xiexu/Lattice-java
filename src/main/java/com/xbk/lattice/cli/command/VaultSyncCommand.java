package com.xbk.lattice.cli.command;

import com.xbk.lattice.api.admin.AdminVaultSyncRequest;
import com.xbk.lattice.cli.CliExitCodes;
import com.xbk.lattice.cli.remote.LatticeHttpClient;
import com.xbk.lattice.vault.VaultSyncResult;
import com.xbk.lattice.vault.VaultSyncService;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine;

import java.nio.file.Path;

/**
 * vault-sync 命令
 *
 * 职责：将 Vault 中允许回写的概念文章受控同步回数据库
 *
 * @author xiexu
 */
@CommandLine.Command(name = "vault-sync", description = "将 Vault 概念文章受控回写到数据库")
public class VaultSyncCommand extends AbstractCliCommand {

    @CommandLine.Option(names = "--dir", required = true, description = "Vault 目录")
    private Path vaultDir;

    @CommandLine.Option(names = "--force", description = "忽略冲突并强制覆盖")
    private boolean force;

    @Override
    protected Integer runInStandaloneMode() throws Exception {
        try (ConfigurableApplicationContext context = com.xbk.lattice.cli.CliRuntimeSupport.createContext()) {
            VaultSyncService vaultSyncService = context.getBean(VaultSyncService.class);
            VaultSyncResult vaultSyncResult = vaultSyncService.sync(vaultDir, force);
            printJson(vaultSyncResult);
            return CliExitCodes.SUCCESS;
        }
    }

    @Override
    protected Integer runInRemoteMode(LatticeHttpClient latticeHttpClient) throws Exception {
        AdminVaultSyncRequest request = new AdminVaultSyncRequest();
        request.setVaultDir(vaultDir.toString());
        request.setForce(force);
        VaultSyncResult result = latticeHttpClient.post("/api/v1/admin/vault/sync", request, VaultSyncResult.class);
        printJson(result);
        return CliExitCodes.SUCCESS;
    }
}
