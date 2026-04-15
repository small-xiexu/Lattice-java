package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.CompilerProperties;
import com.xbk.lattice.compiler.model.RawSource;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 文件采集节点
 *
 * 职责：扫描目录并采集支持的文本文件
 *
 * @author xiexu
 */
@Slf4j
public class IngestNode {

    private static final Set<String> SUPPORTED_TEXT_FORMATS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("md", "java", "xml", "yml", "yaml", "json", "vue", "js"))
    );

    private static final Set<String> SKIPPED_DIRECTORIES = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(".git", "target", "dist", "node_modules"))
    );

    private final CompilerProperties compilerProperties;

    /**
     * 创建文件采集节点。
     *
     * @param compilerProperties 编译配置
     */
    public IngestNode(CompilerProperties compilerProperties) {
        this.compilerProperties = compilerProperties;
    }

    /**
     * 采集目录内的源文件。
     *
     * @param sourceDir 源目录
     * @return 原始源文件集合
     * @throws IOException IO 异常
     */
    public List<RawSource> ingest(Path sourceDir) throws IOException {
        List<RawSource> rawSources = new ArrayList<RawSource>();
        try (Stream<Path> pathStream = Files.walk(sourceDir)) {
            pathStream.filter(Files::isRegularFile)
                    .filter(path -> shouldInclude(sourceDir, path))
                    .sorted()
                    .forEach(path -> rawSources.add(readTextSource(sourceDir, path)));
        }
        return rawSources;
    }

    /**
     * 判断文件是否需要采集。
     *
     * @param sourceDir 源目录
     * @param path 文件路径
     * @return 是否采集
     */
    private boolean shouldInclude(Path sourceDir, Path path) {
        Path relativePath = sourceDir.relativize(path);
        for (Path part : relativePath) {
            if (SKIPPED_DIRECTORIES.contains(part.toString())) {
                return false;
            }
        }

        String format = extractFormat(path.getFileName().toString());
        if ("class".equals(format) || "jar".equals(format)) {
            return false;
        }
        return SUPPORTED_TEXT_FORMATS.contains(format);
    }

    /**
     * 读取文本源文件。
     *
     * @param sourceDir 源目录
     * @param path 文件路径
     * @return 原始源文件
     */
    private RawSource readTextSource(Path sourceDir, Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            String trimmedContent = trimContent(content);
            String relativePath = normalizePath(sourceDir.relativize(path));
            String format = extractFormat(path.getFileName().toString());
            long fileSize = Files.size(path);
            return RawSource.text(relativePath, trimmedContent, format, fileSize);
        }
        catch (IOException ex) {
            log.error("Failed to read source file path: {}", path, ex);
            throw new IllegalStateException("读取源文件失败: " + path, ex);
        }
    }

    /**
     * 按配置截断文件内容。
     *
     * @param content 原始内容
     * @return 截断后的内容
     */
    private String trimContent(String content) {
        int maxChars = compilerProperties.getIngestMaxChars();
        if (maxChars <= 0 || content.length() <= maxChars) {
            return content;
        }
        return content.substring(0, maxChars);
    }

    /**
     * 标准化相对路径。
     *
     * @param path 相对路径
     * @return 标准化后的相对路径
     */
    private String normalizePath(Path path) {
        return path.toString().replace('\\', '/');
    }

    /**
     * 提取文件扩展名。
     *
     * @param fileName 文件名
     * @return 扩展名
     */
    private String extractFormat(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase();
    }
}
