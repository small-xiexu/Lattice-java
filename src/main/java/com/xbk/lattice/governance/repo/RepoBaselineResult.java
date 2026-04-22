package com.xbk.lattice.governance.repo;

import java.time.OffsetDateTime;

/**
 * Repo baseline 建立结果
 *
 * 职责：承载单次 Git-backed repo baseline 的导出、提交与快照结果
 *
 * @author xiexu
 */
public class RepoBaselineResult {

    private final long snapshotId;

    private final OffsetDateTime createdAt;

    private final String triggerEvent;

    private final String description;

    private final String gitCommit;

    private final boolean createdNewCommit;

    private final int articleCount;

    private final String vaultDir;

    private final int writtenFiles;

    private final int skippedFiles;

    private final int deletedFiles;

    /**
     * 创建 repo baseline 结果。
     *
     * @param snapshotId repo snapshot 主键
     * @param createdAt snapshot 创建时间
     * @param triggerEvent snapshot 触发事件
     * @param description snapshot 描述
     * @param gitCommit 绑定的 Vault Git commit
     * @param createdNewCommit 本次是否新建了 Git commit
     * @param articleCount snapshot 文章数
     * @param vaultDir Vault 目录
     * @param writtenFiles 导出写入文件数
     * @param skippedFiles 导出跳过文件数
     * @param deletedFiles 导出删除文件数
     */
    public RepoBaselineResult(
            long snapshotId,
            OffsetDateTime createdAt,
            String triggerEvent,
            String description,
            String gitCommit,
            boolean createdNewCommit,
            int articleCount,
            String vaultDir,
            int writtenFiles,
            int skippedFiles,
            int deletedFiles
    ) {
        this.snapshotId = snapshotId;
        this.createdAt = createdAt;
        this.triggerEvent = triggerEvent;
        this.description = description;
        this.gitCommit = gitCommit;
        this.createdNewCommit = createdNewCommit;
        this.articleCount = articleCount;
        this.vaultDir = vaultDir;
        this.writtenFiles = writtenFiles;
        this.skippedFiles = skippedFiles;
        this.deletedFiles = deletedFiles;
    }

    public long getSnapshotId() {
        return snapshotId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public String getTriggerEvent() {
        return triggerEvent;
    }

    public String getDescription() {
        return description;
    }

    public String getGitCommit() {
        return gitCommit;
    }

    public boolean isCreatedNewCommit() {
        return createdNewCommit;
    }

    public int getArticleCount() {
        return articleCount;
    }

    public String getVaultDir() {
        return vaultDir;
    }

    public int getWrittenFiles() {
        return writtenFiles;
    }

    public int getSkippedFiles() {
        return skippedFiles;
    }

    public int getDeletedFiles() {
        return deletedFiles;
    }
}
