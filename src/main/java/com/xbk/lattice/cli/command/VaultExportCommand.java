package com.xbk.lattice.cli.command;

import com.xbk.lattice.cli.CliExitCodes;
import com.xbk.lattice.cli.remote.LatticeHttpClient;
import com.xbk.lattice.api.admin.AdminVaultExportRequest;
import com.xbk.lattice.vault.VaultExportResult;
import com.xbk.lattice.vault.VaultExportService;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine;

import java.nio.file.Path;

/**
 * vault-export 命令
 *
 * 职责：将数据库中的知识状态导出为 Vault 文件树
 *
 * @author xiexu
 */
@CommandLine.Command(name = "vault-export", description = "导出知识库到 Vault 目录")
public class VaultExportCommand extends AbstractCliCommand {

    @CommandLine.Option(names = {"-d", "--dir"}, required = true, description = "Vault 导出目录")
    private Path vaultDir;

    @Override
    protected Integer runInStandaloneMode() throws Exception {
        try (ConfigurableApplicationContext context = com.xbk.lattice.cli.CliRuntimeSupport.createContext()) {
            VaultExportService vaultExportService = context.getBean(VaultExportService.class);
            VaultExportResult vaultExportResult = vaultExportService.export(vaultDir);
            printJson(vaultExportResult);
            return CliExitCodes.SUCCESS;
        }
    }

    @Override
    protected Integer runInRemoteMode(LatticeHttpClient latticeHttpClient) throws Exception {
        AdminVaultExportRequest request = new AdminVaultExportRequest();
        request.setVaultDir(vaultDir.toString());
        VaultExportResult result = latticeHttpClient.post("/api/v1/admin/vault/export", request, VaultExportResult.class);
        printJson(result);
        return CliExitCodes.SUCCESS;
    }
}
