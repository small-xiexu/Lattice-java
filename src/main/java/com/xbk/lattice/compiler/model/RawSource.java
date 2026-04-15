package com.xbk.lattice.compiler.model;

/**
 * 原始源文件
 *
 * 职责：表示采集后的单个源文件最小视图
 *
 * @author xiexu
 */
public class RawSource {

    private final String relativePath;

    private final String content;

    private final String format;

    private final long fileSize;

    private final String metadataJson;

    private final boolean verbatim;

    private final String rawPath;

    /**
     * 创建原始源文件。
     *
     * @param relativePath 相对路径
     * @param content 文件内容
     * @param format 文件格式
     * @param fileSize 文件大小
     */
    public RawSource(String relativePath, String content, String format, long fileSize) {
        this(relativePath, content, format, fileSize, "{}", false, relativePath);
    }

    /**
     * 创建原始源文件。
     *
     * @param relativePath 相对路径
     * @param content 文件内容
     * @param format 文件格式
     * @param fileSize 文件大小
     * @param metadataJson 元数据 JSON
     * @param verbatim 是否按原文保留
     * @param rawPath 原始文件路径
     */
    public RawSource(
            String relativePath,
            String content,
            String format,
            long fileSize,
            String metadataJson,
            boolean verbatim,
            String rawPath
    ) {
        this.relativePath = relativePath;
        this.content = content;
        this.format = format;
        this.fileSize = fileSize;
        this.metadataJson = metadataJson;
        this.verbatim = verbatim;
        this.rawPath = rawPath;
    }

    /**
     * 创建文本类型源文件。
     *
     * @param relativePath 相对路径
     * @param content 文件内容
     * @param format 文件格式
     * @param fileSize 文件大小
     * @return 原始源文件
     */
    public static RawSource text(String relativePath, String content, String format, long fileSize) {
        return new RawSource(relativePath, content, format, fileSize);
    }

    /**
     * 创建带元数据的源文件。
     *
     * @param relativePath 相对路径
     * @param content 文件内容
     * @param format 文件格式
     * @param fileSize 文件大小
     * @param metadataJson 元数据 JSON
     * @param verbatim 是否按原文保留
     * @param rawPath 原始文件路径
     * @return 原始源文件
     */
    public static RawSource extracted(
            String relativePath,
            String content,
            String format,
            long fileSize,
            String metadataJson,
            boolean verbatim,
            String rawPath
    ) {
        return new RawSource(relativePath, content, format, fileSize, metadataJson, verbatim, rawPath);
    }

    /**
     * 获取相对路径。
     *
     * @return 相对路径
     */
    public String getRelativePath() {
        return relativePath;
    }

    /**
     * 获取文件内容。
     *
     * @return 文件内容
     */
    public String getContent() {
        return content;
    }

    /**
     * 获取文件格式。
     *
     * @return 文件格式
     */
    public String getFormat() {
        return format;
    }

    /**
     * 获取文件大小。
     *
     * @return 文件大小
     */
    public long getFileSize() {
        return fileSize;
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
     * 是否按原文保留。
     *
     * @return 是否按原文保留
     */
    public boolean isVerbatim() {
        return verbatim;
    }

    /**
     * 获取原始文件路径。
     *
     * @return 原始文件路径
     */
    public String getRawPath() {
        return rawPath;
    }
}
