package com.xbk.lattice.compiler.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 原始源文件
 *
 * 职责：表示采集后的单个源文件最小视图
 *
 * @author xiexu
 */
public class RawSource {

    private final Long sourceId;

    private final String relativePath;

    private final String extractedText;

    private final String parseMode;

    private final String parseProvider;

    private final String contentHash;

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
        this(
                null,
                relativePath,
                content,
                format,
                fileSize,
                "{}",
                false,
                relativePath,
                defaultParseMode(format),
                defaultParseProvider(format),
                hash(content)
        );
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
        this(
                null,
                relativePath,
                content,
                format,
                fileSize,
                metadataJson,
                verbatim,
                rawPath,
                defaultParseMode(format),
                defaultParseProvider(format),
                hash(content)
        );
    }

    /**
     * 创建原始源文件。
     *
     * @param sourceId 资料源主键
     * @param relativePath 相对路径
     * @param content 文件内容
     * @param format 文件格式
     * @param fileSize 文件大小
     * @param metadataJson 元数据 JSON
     * @param verbatim 是否按原文保留
     * @param rawPath 原始文件路径
     * @param parseMode 解析模式
     * @param parseProvider 解析供应商
     * @param contentHash 内容哈希
     */
    public RawSource(
            Long sourceId,
            String relativePath,
            String content,
            String format,
            long fileSize,
            String metadataJson,
            boolean verbatim,
            String rawPath,
            String parseMode,
            String parseProvider,
            String contentHash
    ) {
        this.sourceId = sourceId;
        this.relativePath = relativePath;
        this.extractedText = content;
        this.parseMode = parseMode;
        this.parseProvider = parseProvider;
        this.contentHash = contentHash;
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
     * 创建带完整解析上下文的源文件。
     *
     * @param sourceId 资料源主键
     * @param relativePath 相对路径
     * @param content 文件内容
     * @param format 文件格式
     * @param fileSize 文件大小
     * @param metadataJson 元数据 JSON
     * @param verbatim 是否按原文保留
     * @param rawPath 原始文件路径
     * @param parseMode 解析模式
     * @param parseProvider 解析供应商
     * @return 原始源文件
     */
    public static RawSource parsed(
            Long sourceId,
            String relativePath,
            String content,
            String format,
            long fileSize,
            String metadataJson,
            boolean verbatim,
            String rawPath,
            String parseMode,
            String parseProvider
    ) {
        return new RawSource(
                sourceId,
                relativePath,
                content,
                format,
                fileSize,
                metadataJson,
                verbatim,
                rawPath,
                parseMode,
                parseProvider,
                hash(content)
        );
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
        return extractedText;
    }

    /**
     * 获取抽取后的正文。
     *
     * @return 抽取后的正文
     */
    public String getExtractedText() {
        return extractedText;
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
     * 获取解析模式。
     *
     * @return 解析模式
     */
    public String getParseMode() {
        return parseMode;
    }

    /**
     * 获取解析供应商。
     *
     * @return 解析供应商
     */
    public String getParseProvider() {
        return parseProvider;
    }

    /**
     * 获取内容哈希。
     *
     * @return 内容哈希
     */
    public String getContentHash() {
        return contentHash;
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

    private static String defaultParseMode(String format) {
        String normalized = format == null ? "" : format.trim().toLowerCase();
        if ("pdf".equals(normalized)) {
            return "pdf_text";
        }
        if ("docx".equals(normalized) || "doc".equals(normalized)
                || "xlsx".equals(normalized) || "xls".equals(normalized)
                || "pptx".equals(normalized)) {
            return "office_extract";
        }
        if ("png".equals(normalized) || "jpg".equals(normalized) || "jpeg".equals(normalized)
                || "gif".equals(normalized) || "bmp".equals(normalized)
                || "webp".equals(normalized) || "tiff".equals(normalized)) {
            return "placeholder";
        }
        return "text_read";
    }

    private static String defaultParseProvider(String format) {
        String normalized = format == null ? "" : format.trim().toLowerCase();
        if ("pdf".equals(normalized)) {
            return "pdfbox";
        }
        if ("docx".equals(normalized)) {
            return "poi_xwpf";
        }
        if ("doc".equals(normalized)) {
            return "poi_hwpf";
        }
        if ("xlsx".equals(normalized) || "xls".equals(normalized)) {
            return "poi_excel";
        }
        if ("pptx".equals(normalized)) {
            return "poi_ppt";
        }
        if ("png".equals(normalized) || "jpg".equals(normalized) || "jpeg".equals(normalized)
                || "gif".equals(normalized) || "bmp".equals(normalized)
                || "webp".equals(normalized) || "tiff".equals(normalized)) {
            return "placeholder";
        }
        return "filesystem";
    }

    private static String hash(String content) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(String.valueOf(content).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : digest) {
                builder.append(String.format("%02x", Integer.valueOf(value & 0xff)));
            }
            return builder.toString();
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }
}
