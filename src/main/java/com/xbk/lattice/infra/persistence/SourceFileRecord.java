package com.xbk.lattice.infra.persistence;

/**
 * 源文件记录
 *
 * 职责：表示最小源文件落盘对象
 *
 * @author xiexu
 */
public class SourceFileRecord {

    private final String filePath;

    private final String contentPreview;

    private final String format;

    private final long fileSize;

    /**
     * 创建源文件记录。
     *
     * @param filePath 文件路径
     * @param contentPreview 内容预览
     * @param format 文件格式
     * @param fileSize 文件大小
     */
    public SourceFileRecord(String filePath, String contentPreview, String format, long fileSize) {
        this.filePath = filePath;
        this.contentPreview = contentPreview;
        this.format = format;
        this.fileSize = fileSize;
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
}
