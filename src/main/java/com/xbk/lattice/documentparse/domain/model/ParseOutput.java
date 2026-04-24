package com.xbk.lattice.documentparse.domain.model;

import com.xbk.lattice.documentparse.domain.DocumentParseMode;

/**
 * 文档解析输出
 *
 * 职责：承载解析编排层输出给标准化器和兼容包装层的统一结果
 *
 * @author xiexu
 */
public class ParseOutput {

    private final Long sourceId;

    private final String relativePath;

    private final String plainText;

    private final String markdown;

    private final String structuredContentJson;

    private final String format;

    private final long fileSize;

    private final DocumentParseMode parseMode;

    private final String parseProvider;

    private final String metadataJson;

    private final boolean verbatim;

    private final String rawPath;

    /**
     * 创建文档解析输出。
     *
     * @param sourceId 资料源主键
     * @param relativePath 相对路径
     * @param plainText 纯文本正文
     * @param markdown Markdown 正文
     * @param structuredContentJson 结构化内容
     * @param format 文件格式
     * @param fileSize 文件大小
     * @param parseMode 解析模式
     * @param parseProvider 解析供应商
     * @param metadataJson 元数据 JSON
     * @param verbatim 是否按原文保留
     * @param rawPath 原始路径
     */
    public ParseOutput(
            Long sourceId,
            String relativePath,
            String plainText,
            String markdown,
            String structuredContentJson,
            String format,
            long fileSize,
            DocumentParseMode parseMode,
            String parseProvider,
            String metadataJson,
            boolean verbatim,
            String rawPath
    ) {
        this.sourceId = sourceId;
        this.relativePath = relativePath;
        this.plainText = plainText;
        this.markdown = markdown;
        this.structuredContentJson = structuredContentJson;
        this.format = format;
        this.fileSize = fileSize;
        this.parseMode = parseMode;
        this.parseProvider = parseProvider;
        this.metadataJson = metadataJson;
        this.verbatim = verbatim;
        this.rawPath = rawPath;
    }

    /**
     * 返回资料源主键。
     *
     * @return 资料源主键
     */
    public Long getSourceId() {
        return sourceId;
    }

    /**
     * 返回相对路径。
     *
     * @return 相对路径
     */
    public String getRelativePath() {
        return relativePath;
    }

    /**
     * 返回纯文本正文。
     *
     * @return 纯文本正文
     */
    public String getPlainText() {
        return plainText;
    }

    /**
     * 返回 Markdown 正文。
     *
     * @return Markdown 正文
     */
    public String getMarkdown() {
        return markdown;
    }

    /**
     * 返回结构化内容 JSON。
     *
     * @return 结构化内容 JSON
     */
    public String getStructuredContentJson() {
        return structuredContentJson;
    }

    /**
     * 返回文件格式。
     *
     * @return 文件格式
     */
    public String getFormat() {
        return format;
    }

    /**
     * 返回文件大小。
     *
     * @return 文件大小
     */
    public long getFileSize() {
        return fileSize;
    }

    /**
     * 返回解析模式。
     *
     * @return 解析模式
     */
    public DocumentParseMode getParseMode() {
        return parseMode;
    }

    /**
     * 返回解析供应商。
     *
     * @return 解析供应商
     */
    public String getParseProvider() {
        return parseProvider;
    }

    /**
     * 返回元数据 JSON。
     *
     * @return 元数据 JSON
     */
    public String getMetadataJson() {
        return metadataJson;
    }

    /**
     * 返回是否按原文保留。
     *
     * @return 是否按原文保留
     */
    public boolean isVerbatim() {
        return verbatim;
    }

    /**
     * 返回原始路径。
     *
     * @return 原始路径
     */
    public String getRawPath() {
        return rawPath;
    }

    /**
     * 返回当前输出是否包含可用正文。
     *
     * @return 是否包含正文
     */
    public boolean hasResolvedContent() {
        return hasText(plainText) || hasText(markdown);
    }

    /**
     * 返回当前输出的统一正文。
     *
     * @return 统一正文
     */
    public String resolveContent() {
        if (hasText(plainText)) {
            return plainText.trim();
        }
        if (hasText(markdown)) {
            return markdown.trim();
        }
        return "";
    }

    /**
     * 返回统一内容格式。
     *
     * @return 内容格式
     */
    public String resolveContentFormat() {
        if (hasText(plainText)) {
            return "plain_text";
        }
        if (hasText(markdown)) {
            return "markdown";
        }
        return "empty";
    }

    /**
     * 判断给定文本是否有效。
     *
     * @param text 文本
     * @return 是否有效
     */
    private boolean hasText(String text) {
        return text != null && !text.trim().isEmpty();
    }
}
