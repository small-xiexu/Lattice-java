package com.xbk.lattice.query.service;

/**
 * Embedding profile 变更事件
 *
 * 职责：通知向量 embedding 客户端缓存当前配置已发生切换
 *
 * @author xiexu
 */
public class EmbeddingProfileChangedEvent {

    private final Long previousProfileId;

    private final Long currentProfileId;

    /**
     * 创建 Embedding profile 变更事件。
     *
     * @param previousProfileId 变更前 profile
     * @param currentProfileId 变更后 profile
     */
    public EmbeddingProfileChangedEvent(Long previousProfileId, Long currentProfileId) {
        this.previousProfileId = previousProfileId;
        this.currentProfileId = currentProfileId;
    }

    /**
     * 返回变更前 profile。
     *
     * @return 变更前 profile
     */
    public Long getPreviousProfileId() {
        return previousProfileId;
    }

    /**
     * 返回变更后 profile。
     *
     * @return 变更后 profile
     */
    public Long getCurrentProfileId() {
        return currentProfileId;
    }
}
