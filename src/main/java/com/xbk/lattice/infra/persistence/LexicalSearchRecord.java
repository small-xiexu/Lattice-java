package com.xbk.lattice.infra.persistence;

import java.util.List;

/**
 * Lexical 检索命中记录
 *
 * 职责：承载 JDBC 仓储下推检索后的统一命中摘要
 *
 * @author xiexu
 */
public class LexicalSearchRecord {

    private final Long sourceId;

    private final String itemKey;

    private final String conceptId;

    private final String title;

    private final String content;

    private final String metadataJson;

    private final List<String> sourcePaths;

    private final Integer chunkIndex;

    private final Boolean verbatim;

    private final double score;

    /**
     * 创建 Lexical 检索命中记录。
     *
     * @param sourceId 资料源主键
     * @param itemKey 命中对象唯一键
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 内容
     * @param metadataJson 元数据 JSON
     * @param sourcePaths 来源路径
     * @param chunkIndex 分块序号
     * @param verbatim 是否逐字内容
     * @param score 检索分数
     */
    public LexicalSearchRecord(
            Long sourceId,
            String itemKey,
            String conceptId,
            String title,
            String content,
            String metadataJson,
            List<String> sourcePaths,
            Integer chunkIndex,
            Boolean verbatim,
            double score
    ) {
        this.sourceId = sourceId;
        this.itemKey = itemKey;
        this.conceptId = conceptId;
        this.title = title;
        this.content = content;
        this.metadataJson = metadataJson;
        this.sourcePaths = sourcePaths;
        this.chunkIndex = chunkIndex;
        this.verbatim = verbatim;
        this.score = score;
    }

    /**
     * 获取资料源主键。
     *
     * @return 资料源主键
     */
    public Long getSourceId() {
        return sourceId;
    }

    /**
     * 获取命中对象唯一键。
     *
     * @return 命中对象唯一键
     */
    public String getItemKey() {
        return itemKey;
    }

    /**
     * 获取概念标识。
     *
     * @return 概念标识
     */
    public String getConceptId() {
        return conceptId;
    }

    /**
     * 获取标题。
     *
     * @return 标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 获取内容。
     *
     * @return 内容
     */
    public String getContent() {
        return content;
    }

    /**
     * 获取元数据 JSON。
     *
     * @return 元数据 JSON
     */
    public String getMetadataJson() {
        return metadataJson;
    }

    /**
     * 获取来源路径。
     *
     * @return 来源路径
     */
    public List<String> getSourcePaths() {
        return sourcePaths;
    }

    /**
     * 获取分块序号。
     *
     * @return 分块序号
     */
    public Integer getChunkIndex() {
        return chunkIndex;
    }

    /**
     * 返回是否逐字内容。
     *
     * @return 是否逐字内容
     */
    public Boolean getVerbatim() {
        return verbatim;
    }

    /**
     * 获取检索分数。
     *
     * @return 检索分数
     */
    public double getScore() {
        return score;
    }
}
