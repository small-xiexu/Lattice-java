package com.xbk.lattice.api.admin;

import com.xbk.lattice.governance.repo.RepoBaselineResult;
import com.xbk.lattice.governance.repo.RepoRollbackResult;
import com.xbk.lattice.governance.repo.RepoHistoryReport;
import com.xbk.lattice.governance.repo.RepoSnapshotService;
import com.xbk.lattice.vault.snapshot.VaultGitService;
import com.xbk.lattice.vault.snapshot.VaultSnapshotService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 管理侧整库快照控制器
 *
 * 职责：暴露整库级 repo snapshot 历史接口
 *
 * @author xiexu
 */
@RestController
@Profile("jdbc")
public class AdminRepoSnapshotController {

    private final RepoSnapshotService repoSnapshotService;

    private final VaultSnapshotService vaultSnapshotService;

    private final VaultGitService vaultGitService;

    /**
     * 创建管理侧整库快照控制器。
     *
     * @param repoSnapshotService 整库快照服务
     */
    public AdminRepoSnapshotController(
            RepoSnapshotService repoSnapshotService,
            VaultSnapshotService vaultSnapshotService,
            VaultGitService vaultGitService
    ) {
        this.repoSnapshotService = repoSnapshotService;
        this.vaultSnapshotService = vaultSnapshotService;
        this.vaultGitService = vaultGitService;
    }

    /**
     * 返回最近整库快照历史。
     *
     * @param limit 返回数量
     * @return 整库历史
     */
    @GetMapping("/api/v1/admin/snapshot/repo")
    public RepoHistoryReport history(@RequestParam(defaultValue = "10") int limit) {
        return repoSnapshotService.history(limit);
    }

    /**
     * 建立带 Git commit 的 repo baseline snapshot。
     *
     * @param request baseline 请求
     * @return baseline 结果
     * @throws IOException IO 异常
     */
    @PostMapping("/api/v1/admin/snapshot/repo/baseline")
    public RepoBaselineResult createBaseline(@RequestBody AdminRepoBaselineRequest request) throws IOException {
        if (request == null || request.getVaultDir() == null || request.getVaultDir().isBlank()) {
            throw new IllegalArgumentException("vaultDir 不能为空");
        }
        return vaultSnapshotService.createBaselineSnapshot(Path.of(request.getVaultDir()), request.getDescription());
    }

    /**
     * 返回目标 repo snapshot 对应的 Vault Git diff 摘要。
     *
     * @param snapshotId 快照标识
     * @param vaultDir Vault 目录
     * @return diff 摘要
     * @throws IOException IO 异常
     */
    @GetMapping("/api/v1/admin/snapshot/repo/{snapshotId}/diff")
    public AdminRepoDiffResponse diff(
            @PathVariable long snapshotId,
            @RequestParam String vaultDir
    ) throws IOException {
        String targetCommitId = vaultSnapshotService.getSnapshot(snapshotId).getGitCommit();
        String currentCommitId = vaultGitService.headCommitId(Path.of(vaultDir));
        return new AdminRepoDiffResponse(
                snapshotId,
                targetCommitId,
                currentCommitId,
                vaultSnapshotService.diff(Path.of(vaultDir), snapshotId)
        );
    }

    /**
     * 执行整库回滚。
     *
     * @param request 回滚请求
     * @return 回滚结果
     * @throws IOException IO 异常
     */
    @PostMapping("/api/v1/admin/rollback/repo")
    public RepoRollbackResult rollback(@RequestBody AdminRepoRollbackRequest request) throws IOException {
        return vaultSnapshotService.rollback(Path.of(request.getVaultDir()), request.getSnapshotId());
    }
}
