package com.xbk.lattice.query.service;

import java.util.List;

/**
 * 知识详情查询结果
 *
 * 职责：承载 MCP `lattice_get` 返回的文章或源文件详情
 *
 * @author xiexu
 */
public class KnowledgeLookupResult {

    private final boolean found;

    private final String type;

    private final String id;

    private final String title;

    private final String content;

    private final List<String> sourcePaths;

    private final String metadataJson;

    /**
     * 创建知识详情查询结果。
     *
     * @param found 是否找到
     * @param type 记录类型
     * @param id 记录标识
     * @param title 标题
     * @param content 内容
     * @param sourcePaths 来源路径
     * @param metadataJson 元数据 JSON
     */
    public KnowledgeLookupResult(
            boolean found,
            String type,
            String id,
            String title,
            String content,
            List<String> sourcePaths,
            String metadataJson
    ) {
        this.found = found;
        this.type = type;
        this.id = id;
        this.title = title;
        this.content = content;
        this.sourcePaths = sourcePaths;
        this.metadataJson = metadataJson;
    }

    /**
     * 创建未找到结果。
     *
     * @param id 查询标识
     * @return 未找到结果
     */
    public static KnowledgeLookupResult notFound(String id) {
        return new KnowledgeLookupResult(false, "unknown", id, "", "", List.of(), "{}");
    }

    /**
     * 是否找到。
     *
     * @return 是否找到
     */
    public boolean isFound() {
        return found;
    }

    /**
     * 获取记录类型。
     *
     * @return 记录类型
     */
    public String getType() {
        return type;
    }

    /**
     * 获取记录标识。
     *
     * @return 记录标识
     */
    public String getId() {
        return id;
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
     * 获取来源路径。
     *
     * @return 来源路径
     */
    public List<String> getSourcePaths() {
        return sourcePaths;
    }

    /**
     * 获取元数据 JSON。
     *
     * @return 元数据 JSON
     */
    public String getMetadataJson() {
        return metadataJson;
    }
}
