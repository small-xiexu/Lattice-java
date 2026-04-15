package com.xbk.lattice.compiler.service;

import java.time.OffsetDateTime;

/**
 * 合成产物记录
 *
 * 职责：表示单个 index/timeline/tradeoffs/gaps 产物
 *
 * @author xiexu
 */
public class SynthesisArtifactRecord {

    private final String artifactType;

    private final String title;

    private final String content;

    private final OffsetDateTime compiledAt;

    /**
     * 创建合成产物记录。
     *
     * @param artifactType 产物类型
     * @param title 标题
     * @param content 内容
     * @param compiledAt 编译时间
     */
    public SynthesisArtifactRecord(String artifactType, String title, String content, OffsetDateTime compiledAt) {
        this.artifactType = artifactType;
        this.title = title;
        this.content = content;
        this.compiledAt = compiledAt;
    }

    public String getArtifactType() {
        return artifactType;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public OffsetDateTime getCompiledAt() {
        return compiledAt;
    }
}
