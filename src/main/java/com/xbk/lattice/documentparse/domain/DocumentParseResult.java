package com.xbk.lattice.documentparse.domain;

/**
 * 文档解析结果
 *
 * 职责：承载文档解析层输出给标准化器的统一结果
 *
 * @author xiexu
 */
public class DocumentParseResult {

    private final Long sourceId;

    private final String relativePath;

    private final String extractedText;

    private final String format;

    private final long fileSize;

    private final DocumentParseMode parseMode;

    private final String parseProvider;

    private final String metadataJson;

    private final boolean verbatim;

    private final String rawPath;

    /**
     * 创建文档解析结果。
     *
     * @param sourceId 资料源主键
     * @param relativePath 相对路径
     * @param extractedText 抽取正文
     * @param format 文件格式
     * @param fileSize 文件大小
     * @param parseMode 解析模式
     * @param parseProvider 解析供应商
     * @param metadataJson 元数据 JSON
     * @param verbatim 是否按原文保留
     * @param rawPath 原始路径
     */
    public DocumentParseResult(
            Long sourceId,
            String relativePath,
            String extractedText,
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
        this.extractedText = extractedText;
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
     * 返回抽取正文。
     *
     * @return 抽取正文
     */
    public String getExtractedText() {
        return extractedText;
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
}
