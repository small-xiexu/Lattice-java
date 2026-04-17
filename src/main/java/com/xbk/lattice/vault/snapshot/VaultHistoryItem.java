package com.xbk.lattice.vault.snapshot;

import java.time.OffsetDateTime;

/**
 * Vault Git 历史条目
 *
 * 职责：承载单次 Vault Git 提交的基础摘要
 *
 * @author xiexu
 */
public class VaultHistoryItem {

    private final String commitId;

    private final String shortCommitId;

    private final String message;

    private final OffsetDateTime committedAt;

    /**
     * 创建 Vault Git 历史条目。
     *
     * @param commitId 完整提交哈希
     * @param shortCommitId 短提交哈希
     * @param message 提交说明
     * @param committedAt 提交时间
     */
    public VaultHistoryItem(String commitId, String shortCommitId, String message, OffsetDateTime committedAt) {
        this.commitId = commitId;
        this.shortCommitId = shortCommitId;
        this.message = message;
        this.committedAt = committedAt;
    }

    public String getCommitId() {
        return commitId;
    }

    public String getShortCommitId() {
        return shortCommitId;
    }

    public String getMessage() {
        return message;
    }

    public OffsetDateTime getCommittedAt() {
        return committedAt;
    }
}
