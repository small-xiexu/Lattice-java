package com.xbk.lattice.documentparse.domain;

import lombok.Getter;

/**
 * 文档解析结果。
 *
 * <p>承载文档解析层输出给标准化器的统一结果——解析后的文本、元数据和解析上下文。
 * 为不可变对象，进入标准化器后不再修改。
 *
 * @author xiexu
 */
@Getter
public class DocumentParseResult {

    /** 资料源主键。为 null 表示未关联 source。 */
    private final Long sourceId;

    /** 文件相对路径。 */
    private final String relativePath;

    /** 抽取后的正文文本。可能为大型文本。 */
    private final String extractedText;

    /** 文件格式（如 md / pdf / docx）。 */
    private final String format;

    /** 文件大小（字节）。 */
    private final long fileSize;

    /**
     * 解析模式。
     *
     * <p>驱动下游编译消费路径——不同 parseMode 对应不同的内容标准化策略。</p>
     */
    private final DocumentParseMode parseMode;

    /** 解析供应商标识（如 filesystem / pdfbox / poi_xwpf）。 */
    private final String parseProvider;

    /** 解析扩展元数据 JSON。 */
    private final String metadataJson;

    /** 是否按原文保留（不解析格式）。 */
    private final boolean verbatim;

    /** 原始文件路径（可能为 Vault 中的绝对路径）。 */
    private final String rawPath;

    /**
     * 创建文档解析结果。
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
}
