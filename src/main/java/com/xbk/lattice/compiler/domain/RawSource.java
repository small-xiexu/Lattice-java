package com.xbk.lattice.compiler.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 原始源文件。
 *
 * <p>表示采集后的单个源文件最小视图——包含路径、内容、格式、解析上下文和内容哈希。
 * {@code relativePath+contentHash} 唯一标识文件版本。通过 static factory 创建不同场景的实例。
 *
 * @author xiexu
 */
@Getter
public class RawSource {

    /** 资料源主键。为 null 表示未关联 source。 */
    private final Long sourceId;

    /** 文件相对路径。 */
    private final String relativePath;

    /** 抽取后的正文文本。 */
    private final String extractedText;

    /** 解析模式（如 text_read / pdf_text / office_extract / placeholder）。 */
    private final String parseMode;

    /** 解析供应商（如 filesystem / pdfbox / poi_xwpf）。 */
    private final String parseProvider;

    /** 内容 SHA-256 哈希。用于检测文件变更。 */
    private final String contentHash;

    /** 文件格式（如 md / pdf / docx）。 */
    private final String format;

    /** 文件大小（字节）。 */
    private final long fileSize;

    /** 元数据 JSON。 */
    private final String metadataJson;

    /** 是否按原文保留（不解析格式）。 */
    private final boolean verbatim;

    /** 原始文件路径（可能与 relativePath 不同，用于追溯 Vault 中的原始位置）。 */
    private final String rawPath;

    public RawSource(String relativePath, String content, String format, long fileSize) {
        this(
                null, relativePath, content, format, fileSize, "{}", false, relativePath,
                defaultParseMode(format), defaultParseProvider(format), hash(content)
        );
    }

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
                null, relativePath, content, format, fileSize, metadataJson, verbatim, rawPath,
                defaultParseMode(format), defaultParseProvider(format), hash(content)
        );
    }

    @JsonCreator
    public RawSource(
            @JsonProperty("sourceId") Long sourceId,
            @JsonProperty("relativePath") String relativePath,
            @JsonProperty("extractedText") String content,
            @JsonProperty("format") String format,
            @JsonProperty("fileSize") long fileSize,
            @JsonProperty("metadataJson") String metadataJson,
            @JsonProperty("verbatim") boolean verbatim,
            @JsonProperty("rawPath") String rawPath,
            @JsonProperty("parseMode") String parseMode,
            @JsonProperty("parseProvider") String parseProvider,
            @JsonProperty("contentHash") String contentHash
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
     */
    public static RawSource text(String relativePath, String content, String format, long fileSize) {
        return new RawSource(relativePath, content, format, fileSize);
    }

    /**
     * 创建带元数据的源文件。
     */
    public static RawSource extracted(
            String relativePath, String content, String format, long fileSize,
            String metadataJson, boolean verbatim, String rawPath
    ) {
        return new RawSource(relativePath, content, format, fileSize, metadataJson, verbatim, rawPath);
    }

    /**
     * 创建带完整解析上下文的源文件。
     */
    public static RawSource parsed(
            Long sourceId, String relativePath, String content, String format, long fileSize,
            String metadataJson, boolean verbatim, String rawPath,
            String parseMode, String parseProvider
    ) {
        return new RawSource(sourceId, relativePath, content, format, fileSize,
                metadataJson, verbatim, rawPath, parseMode, parseProvider, hash(content));
    }

    /**
     * 返回文件内容（extractedText 的兼容别名）。
     *
     * <p>标注 @JsonIgnore 防止大文本参与 JSON 序列化。</p>
     */
    @JsonIgnore
    public String getContent() {
        return extractedText;
    }

    private static String defaultParseMode(String format) {
        String normalized = format == null ? "" : format.trim().toLowerCase();
        if ("pdf".equals(normalized)) return "pdf_text";
        if ("docx".equals(normalized) || "doc".equals(normalized)
                || "xlsx".equals(normalized) || "xls".equals(normalized) || "pptx".equals(normalized))
            return "office_extract";
        if ("png".equals(normalized) || "jpg".equals(normalized) || "jpeg".equals(normalized)
                || "gif".equals(normalized) || "bmp".equals(normalized)
                || "webp".equals(normalized) || "tiff".equals(normalized))
            return "placeholder";
        return "text_read";
    }

    private static String defaultParseProvider(String format) {
        String normalized = format == null ? "" : format.trim().toLowerCase();
        if ("pdf".equals(normalized)) return "pdfbox";
        if ("docx".equals(normalized)) return "poi_xwpf";
        if ("doc".equals(normalized)) return "poi_hwpf";
        if ("xlsx".equals(normalized) || "xls".equals(normalized)) return "poi_excel";
        if ("pptx".equals(normalized)) return "poi_ppt";
        if ("png".equals(normalized) || "jpg".equals(normalized) || "jpeg".equals(normalized)
                || "gif".equals(normalized) || "bmp".equals(normalized)
                || "webp".equals(normalized) || "tiff".equals(normalized))
            return "placeholder";
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
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }
}
