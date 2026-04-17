package com.xbk.lattice.api.admin;

/**
 * 管理侧文章回滚请求
 *
 * 职责：承载文章级回滚所需的概念标识与快照标识
 *
 * @author xiexu
 */
public class AdminArticleRollbackRequest {

    private String conceptId;

    private long snapshotId;

    public String getConceptId() {
        return conceptId;
    }

    public void setConceptId(String conceptId) {
        this.conceptId = conceptId;
    }

    public long getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(long snapshotId) {
        this.snapshotId = snapshotId;
    }
}
