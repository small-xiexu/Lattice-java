package com.xbk.lattice.api.admin;

/**
 * 资料源文件响应。
 *
 * 职责：承载资料源详情页展示的解析后文件信息
 *
 * @author xiexu
 */
public class AdminSourceFileResponse {

    private final Long id;

    private final Long sourceId;

    private final String relativePath;

    private final String format;

    private final long fileSize;

    private final String parseMode;

    private final String parseProvider;

    private final String contentPreview;

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

    public Long getId() {
        return id;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getFormat() {
        return format;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getParseMode() {
        return parseMode;
    }

    public String getParseProvider() {
        return parseProvider;
    }

    public String getContentPreview() {
        return contentPreview;
    }
}
