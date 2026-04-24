package com.xbk.lattice.documentparse.domain.model;

import java.nio.file.Path;

/**
 * 文档解析请求
 *
 * 职责：封装单个文件进入解析编排层时的统一上下文
 *
 * @author xiexu
 */
public class ParseRequest {

    private final Path workspaceRoot;

    private final Path filePath;

    private final String relativePath;

    private final String format;

    private final long fileSize;

    /**
     * 创建文档解析请求。
     *
     * @param workspaceRoot 工作目录根路径
     * @param filePath 文件路径
     * @param relativePath 相对路径
     * @param format 文件格式
     * @param fileSize 文件大小
     */
    public ParseRequest(
            Path workspaceRoot,
            Path filePath,
            String relativePath,
            String format,
            long fileSize
    ) {
        this.workspaceRoot = workspaceRoot;
        this.filePath = filePath;
        this.relativePath = relativePath;
        this.format = format;
        this.fileSize = fileSize;
    }

    /**
     * 返回工作目录根路径。
     *
     * @return 工作目录根路径
     */
    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    /**
     * 返回文件路径。
     *
     * @return 文件路径
     */
    public Path getFilePath() {
        return filePath;
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
}
