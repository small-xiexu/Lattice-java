package com.xbk.lattice.documentparse.domain.model;

import lombok.Getter;

import java.nio.file.Path;

/**
 * 文档解析请求。
 *
 * <p>封装单个文件进入解析编排层时的统一上下文——包含工作区根路径、
 * 文件绝对路径和元数据，供解析适配器定位和读取源文件。
 *
 * @author xiexu
 */
@Getter
public class ParseRequest {

    /**
     * 工作目录根路径（{@link Path}）。
     *
     * <p>解析编排层在此路径下寻找和写入临时文件。
     * 应做路径规范化校验以防止遍历攻击。</p>
     */
    private final Path workspaceRoot;

    /**
     * 待解析文件的绝对路径（{@link Path}）。
     *
     * <p>由 {@code workspaceRoot} + {@code relativePath} 拼接得出，
     * 解析适配器直接读取此路径的文件内容。</p>
     */
    private final Path filePath;

    /** 文件相对路径（相对于 workspaceRoot）。 */
    private final String relativePath;

    /** 文件格式（如 md / pdf / docx）。 */
    private final String format;

    /** 文件大小（字节）。 */
    private final long fileSize;

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
}
