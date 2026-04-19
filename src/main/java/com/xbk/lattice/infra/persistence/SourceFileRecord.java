package com.xbk.lattice.infra.persistence;

/**
 * 源文件记录
 *
 * 职责：表示最小源文件落盘对象
 *
 * @author xiexu
 */
public class SourceFileRecord {

    private final Long id;

    private final Long sourceId;

    private final String filePath;

    private final String relativePath;

    private final Long sourceSyncRunId;

    private final String contentPreview;

    private final String format;

    private final long fileSize;

    private final String contentText;

    private final String metadataJson;

    private final boolean verbatim;

    private final String rawPath;

    /**
     * 创建源文件记录。
     *
     * @param filePath 文件路径
     * @param contentPreview 内容预览
     * @param format 文件格式
     * @param fileSize 文件大小
     */
    public SourceFileRecord(String filePath, String contentPreview, String format, long fileSize) {
        this(null, null, filePath, filePath, null, contentPreview, format, fileSize, contentPreview, "{}", false, filePath);
    }

    /**
     * 创建源文件记录。
     *
     * @param filePath 文件路径
     * @param contentPreview 内容预览
     * @param format 文件格式
     * @param fileSize 文件大小
     * @param contentText 全量正文
     * @param metadataJson 元数据 JSON
     * @param verbatim 是否按原文保留
     * @param rawPath 原始文件路径
     */
    public SourceFileRecord(
            String filePath,
            String contentPreview,
            String format,
            long fileSize,
            String contentText,
            String metadataJson,
            boolean verbatim,
            String rawPath
    ) {
        this(null, null, filePath, filePath, null, contentPreview, format, fileSize, contentText, metadataJson, verbatim, rawPath);
    }

    /**
     * 创建源文件记录。
     *
     * @param id 主键
     * @param sourceId 资料源主键
     * @param filePath 兼容文件路径
     * @param relativePath 资料源内相对路径
     * @param sourceSyncRunId 资料源同步运行主键
     * @param contentPreview 内容预览
     * @param format 文件格式
     * @param fileSize 文件大小
     * @param contentText 全量正文
     * @param metadataJson 元数据 JSON
     * @param verbatim 是否按原文保留
     * @param rawPath 原始文件路径
     */
    public SourceFileRecord(
            Long id,
            Long sourceId,
            String filePath,
            String relativePath,
            Long sourceSyncRunId,
            String contentPreview,
            String format,
            long fileSize,
            String contentText,
            String metadataJson,
            boolean verbatim,
            String rawPath
    ) {
        this.id = id;
        this.sourceId = sourceId;
        this.filePath = filePath;
        this.relativePath = relativePath;
        this.sourceSyncRunId = sourceSyncRunId;
        this.contentPreview = contentPreview;
        this.format = format;
        this.fileSize = fileSize;
        this.contentText = contentText;
        this.metadataJson = metadataJson;
        this.verbatim = verbatim;
        this.rawPath = rawPath;
    }

    /**
     * 获取主键。
     *
     * @return 主键
     */
    public Long getId() {
        return id;
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
     * 获取文件路径。
     *
     * @return 文件路径
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * 获取资料源内相对路径。
     *
     * @return 资料源内相对路径
     */
    public String getRelativePath() {
        return relativePath;
    }

    /**
     * 获取资料源同步运行主键。
     *
     * @return 资料源同步运行主键
     */
    public Long getSourceSyncRunId() {
        return sourceSyncRunId;
    }

    /**
     * 获取内容预览。
     *
     * @return 内容预览
     */
    public String getContentPreview() {
        return contentPreview;
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
     * 获取全量正文。
     *
     * @return 全量正文
     */
    public String getContentText() {
        return contentText;
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
