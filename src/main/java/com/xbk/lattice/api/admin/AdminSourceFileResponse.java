package com.xbk.lattice.api.admin;

import lombok.Getter;

/**
 * 资料源文件响应。
 *
 * <p>承载资料源详情页中单个解析后文件的展示信息，由 {@code AdminSourceController.listSourceFiles()}
 * 从 {@code SourceFile} 记录投影构造。
 *
 * @author xiexu
 */
@Getter
public class AdminSourceFileResponse {

    /**
     * 文件主键。
     *
     * <p>对应 source_files.id，调用方用它标识和操作具体文件记录。</p>
     */
    private final Long id;

    /**
     * 所属资料源主键。
     *
     * <p>对应 source_files.source_id，指向 knowledge_sources.id。
     * 调用方通过它关联文件与所属的资料源。</p>
     */
    private final Long sourceId;

    /**
     * 相对路径。
     *
     * <p>文件在资料源仓库中的相对路径，调用方据此展示文件树和目录结构。</p>
     */
    private final String relativePath;

    /**
     * 文件格式。
     *
     * <p>如 markdown、yaml、json、java、xlsx 等，决定前端展示时的文件图标和预览策略。</p>
     */
    private final String format;

    /**
     * 文件大小（字节）。
     *
     * <p>对应 source_files.file_size。调用方用于展示文件大小信息。</p>
     */
    private final long fileSize;

    /**
     * 解析模式。
     *
     * <p>系统解析该文件时使用的模式，如 document_parse（文档解析）、code_ast（代码 AST 抽取）等。
     * 为空表示该文件尚未被解析或被解析器跳过。</p>
     */
    private final String parseMode;

    /**
     * 解析提供者。
     *
     * <p>负责解析该文件的具体提供者名称，如 github-markdown、tika-xlsx 等。
     * 为空表示未解析或系统中无匹配的解析器。</p>
     */
    private final String parseProvider;

    /**
     * 内容预览。
     *
     * <p>文件开头部分内容的文本预览，调用方在文件列表中用它提供快速内容概览。</p>
     */
    private final String contentPreview;

    /**
     * 创建资料源文件响应。
     *
     * @param id 文件主键
     * @param sourceId 所属资料源主键
     * @param relativePath 相对路径
     * @param format 文件格式
     * @param fileSize 文件大小（字节）
     * @param parseMode 解析模式
     * @param parseProvider 解析提供者
     * @param contentPreview 内容预览
     */
    public AdminSourceFileResponse(
            Long id,
            Long sourceId,
            String relativePath,
            String format,
            long fileSize,
            String parseMode,
            String parseProvider,
            String contentPreview
    ) {
        this.id = id;
        this.sourceId = sourceId;
        this.relativePath = relativePath;
        this.format = format;
        this.fileSize = fileSize;
        this.parseMode = parseMode;
        this.parseProvider = parseProvider;
        this.contentPreview = contentPreview;
    }
}
