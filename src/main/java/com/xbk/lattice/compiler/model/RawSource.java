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

    /**
     * 创建原始源文件。
     *
     * @param relativePath 相对路径
     * @param content 文件内容
     * @param format 文件格式
     * @param fileSize 文件大小
     */
    public RawSource(String relativePath, String content, String format, long fileSize) {
        this.relativePath = relativePath;
        this.content = content;
        this.format = format;
        this.fileSize = fileSize;
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
}
