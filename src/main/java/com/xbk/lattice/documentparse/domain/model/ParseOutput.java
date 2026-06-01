package com.xbk.lattice.documentparse.domain.model;

import com.xbk.lattice.documentparse.domain.DocumentParseMode;
import lombok.Getter;

/**
 * 文档解析输出。
 *
 * <p>承载解析编排层输出给标准化器和兼容包装层的统一结果——含多种格式的文本
 * （plainText / markdown / structuredContentJson）和内容解析方法。
 *
 * @author xiexu
 */
@Getter
public class ParseOutput {

    /** 资料源主键。 */
    private final Long sourceId;

    /** 文件相对路径。 */
    private final String relativePath;

    /** 纯文本正文。可能为大型文本。 */
    private final String plainText;

    /** Markdown 格式正文。可能为大型文本。 */
    private final String markdown;

    /** 结构化内容 JSON。可能为大型 JSON。 */
    private final String structuredContentJson;

    /** 文件格式。 */
    private final String format;

    /** 文件大小（字节）。 */
    private final long fileSize;

    /** 解析模式。驱动下游编译消费路径。 */
    private final DocumentParseMode parseMode;

    /** 解析供应商标识。 */
    private final String parseProvider;

    /** 解析扩展元数据 JSON。 */
    private final String metadataJson;

    /** 是否按原文保留。 */
    private final boolean verbatim;

    /** 原始文件路径。 */
    private final String rawPath;

    /**
     * 创建文档解析输出。
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
     * 返回当前输出是否包含可用正文。
     */
    public boolean hasResolvedContent() {
        return hasText(plainText) || hasText(markdown);
    }

    /**
     * 返回当前输出的统一正文（优先 plainText，其次 markdown）。
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
     * 返回统一内容格式（plain_text / markdown / empty）。
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

    private boolean hasText(String text) {
        return text != null && !text.trim().isEmpty();
    }
}
