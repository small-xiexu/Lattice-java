package com.xbk.lattice.api.admin;

import com.xbk.lattice.vault.snapshot.VaultDiffSummary;
import lombok.Getter;

import java.util.List;

/**
 * 管理侧整库 diff 响应。
 *
 * <p>承载目标 snapshot 与当前 Vault HEAD 之间的 Git 差异摘要，
 * 由 {@code AdminRepoSnapshotController.diff()} 构造。
 *
 * @author xiexu
 */
@Getter
public class AdminRepoDiffResponse {

    /**
     * 目标 snapshot ID。
     *
     * <p>要对比的基准快照标识。调用方通过它关联 diff 结果与具体的 snapshot 记录。</p>
     */
    private final long snapshotId;

    /**
     * 目标 commit ID。
     *
     * <p>snapshot 创建时 Vault 仓库的 Git HEAD commit hash，作为 diff 的基准端。</p>
     */
    private final String targetCommitId;

    /**
     * 当前 commit ID。
     *
     * <p>Vault 仓库当前的 Git HEAD commit hash，作为 diff 的比较端。
     * 与 targetCommitId 对比即可得到两次快照之间的所有变更。</p>
     */
    private final String currentCommitId;

    /**
     * 差异条目列表。
     *
     * <p>包含两个 commit 之间变更的文件清单，每条记录描述一个文件的变更类型和摘要。
     * 为 null 时 getCount() 返回 0。getItems() 通过类级 @Getter 生成。</p>
     */
    private final List<VaultDiffSummary> items;

    /**
     * 创建管理侧整库 diff 响应。
     *
     * @param snapshotId 目标 snapshot ID
     * @param targetCommitId 目标 commit ID（基准端）
     * @param currentCommitId 当前 commit ID（比较端）
     * @param items 差异条目列表
     */
    public AdminRepoDiffResponse(
            long snapshotId,
            String targetCommitId,
            String currentCommitId,
            List<VaultDiffSummary> items
    ) {
        this.snapshotId = snapshotId;
        this.targetCommitId = targetCommitId;
        this.currentCommitId = currentCommitId;
        this.items = items;
    }

    /**
     * 差异条目数量。
     *
     * @return items 列表的大小；items 为 null 时返回 0
     */
    public int getCount() {
        return items == null ? 0 : items.size();
    }
}
