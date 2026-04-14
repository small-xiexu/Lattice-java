package com.xbk.lattice.compiler.model;

import java.util.List;

/**
 * 源文件批次
 *
 * 职责：表示同一分组内按字符数切分后的批次结果
 *
 * @author xiexu
 */
public class SourceBatch {

    private final String batchId;

    private final String groupKey;

    private final List<RawSource> sources;

    /**
     * 创建源文件批次。
     *
     * @param batchId 批次标识
     * @param groupKey 分组键
     * @param sources 源文件集合
     */
    public SourceBatch(String batchId, String groupKey, List<RawSource> sources) {
        this.batchId = batchId;
        this.groupKey = groupKey;
        this.sources = sources;
    }

    /**
     * 获取批次标识。
     *
     * @return 批次标识
     */
    public String getBatchId() {
        return batchId;
    }

    /**
     * 获取分组键。
     *
     * @return 分组键
     */
    public String getGroupKey() {
        return groupKey;
    }

    /**
     * 获取源文件集合。
     *
     * @return 源文件集合
     */
    public List<RawSource> getSources() {
        return sources;
    }
}
