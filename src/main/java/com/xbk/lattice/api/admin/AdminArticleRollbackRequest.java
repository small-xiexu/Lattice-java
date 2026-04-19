package com.xbk.lattice.api.admin;

/**
 * 管理侧文章回滚请求
 *
 * 职责：承载文章级回滚所需的概念标识与快照标识
 *
 * @author xiexu
 */
public class AdminArticleRollbackRequest {

    private String articleId;

    private String conceptId;

    private Long sourceId;

    private long snapshotId;

    public String getArticleId() {
        if (articleId != null && !articleId.isBlank()) {
            return articleId;
        }
        return conceptId;
    }

    public void setArticleId(String articleId) {
        this.articleId = articleId;
    }

    public String getConceptId() {
        return conceptId;
    }

    public void setConceptId(String conceptId) {
        this.conceptId = conceptId;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public long getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(long snapshotId) {
        this.snapshotId = snapshotId;
    }
}
