package com.xbk.lattice.api.admin;

import com.xbk.lattice.vault.VaultExportResult;
import com.xbk.lattice.vault.VaultExportService;
import com.xbk.lattice.vault.VaultSyncResult;
import com.xbk.lattice.vault.VaultSyncService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 管理侧 Vault 控制器
 *
 * 职责：暴露 Vault 导出接口
 *
 * @author xiexu
 */
@RestController
@RequestMapping("/api/v1/admin/vault")
public class AdminVaultController {

    private final VaultExportService vaultExportService;

    private final VaultSyncService vaultSyncService;

    /**
     * 创建管理侧 Vault 控制器。
     *
     * @param vaultExportService Vault 导出服务
     */
    public AdminVaultController(VaultExportService vaultExportService, VaultSyncService vaultSyncService) {
        this.vaultExportService = vaultExportService;
        this.vaultSyncService = vaultSyncService;
    }

    /**
     * 执行 Vault 导出。
     *
     * @param request 导出请求
     * @return 导出结果
     * @throws IOException IO 异常
     */
    @PostMapping("/export")
    public VaultExportResult export(@RequestBody AdminVaultExportRequest request) throws IOException {
        if (request == null || request.getVaultDir() == null || request.getVaultDir().isBlank()) {
            throw new IllegalArgumentException("vaultDir 不能为空");
        }
        return vaultExportService.export(Path.of(request.getVaultDir()));
    }

    /**
     * 执行 Vault inbound 回写。
     *
     * @param request 回写请求
     * @return 回写结果
     * @throws IOException IO 异常
     */
    @PostMapping("/sync")
    public VaultSyncResult sync(@RequestBody AdminVaultSyncRequest request) throws IOException {
        if (request == null || request.getVaultDir() == null || request.getVaultDir().isBlank()) {
            throw new IllegalArgumentException("vaultDir 不能为空");
        }
        return vaultSyncService.sync(Path.of(request.getVaultDir()), request.isForce());
    }
}
