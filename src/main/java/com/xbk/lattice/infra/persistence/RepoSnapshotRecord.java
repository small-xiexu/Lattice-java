package com.xbk.lattice.infra.persistence;

import java.time.OffsetDateTime;

/**
 * 整库快照记录
 *
 * 职责：表示一次整库级 snapshot 主记录
 *
 * @author xiexu
 */
public class RepoSnapshotRecord {

    private final long id;

    private final OffsetDateTime createdAt;

    private final String triggerEvent;

    private final String gitCommit;

    private final String description;

    private final int articleCount;

    /**
     * 创建整库快照记录。
     *
     * @param id 主键
     * @param createdAt 创建时间
     * @param triggerEvent 触发事件
     * @param gitCommit Git 提交哈希
     * @param description 描述
     * @param articleCount 文章数量
     */
    public RepoSnapshotRecord(
            long id,
            OffsetDateTime createdAt,
            String triggerEvent,
            String gitCommit,
            String description,
            int articleCount
    ) {
        this.id = id;
        this.createdAt = createdAt;
        this.triggerEvent = triggerEvent;
        this.gitCommit = gitCommit;
        this.description = description;
        this.articleCount = articleCount;
    }

    public long getId() {
        return id;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public String getTriggerEvent() {
        return triggerEvent;
    }

    public String getGitCommit() {
        return gitCommit;
    }

    public String getDescription() {
        return description;
    }

    public int getArticleCount() {
        return articleCount;
    }
}
