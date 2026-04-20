package com.xbk.lattice.compiler.node;

import com.xbk.lattice.compiler.config.CompilerProperties;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.documentparse.extractor.DocTextExtractor;
import com.xbk.lattice.documentparse.extractor.ExcelTextExtractor;
import com.xbk.lattice.documentparse.extractor.PdfTextExtractor;
import com.xbk.lattice.documentparse.extractor.PptTextExtractor;
import com.xbk.lattice.documentparse.extractor.SourceExtractionResult;
import com.xbk.lattice.documentparse.extractor.WordTextExtractor;
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
            new HashSet<String>(Arrays.asList(
                    "md", "txt", "markdown", "csv", "java", "xml", "properties",
                    "yml", "yaml", "json", "vue", "js", "css", "html", "sh", "py"
            ))
    );

    private static final Set<String> SUPPORTED_DOCUMENT_FORMATS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("pdf", "xlsx", "xls", "docx", "doc", "pptx"))
    );

    private static final Set<String> SUPPORTED_IMAGE_FORMATS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("png", "jpg", "jpeg", "gif", "svg", "drawio"))
    );

    private static final Set<String> SKIPPED_DIRECTORIES = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(".git", "target", "dist", "node_modules"))
    );

    private final CompilerProperties compilerProperties;

    private final PdfTextExtractor pdfTextExtractor;

    private final ExcelTextExtractor excelTextExtractor;

    private final WordTextExtractor wordTextExtractor;

    private final DocTextExtractor docTextExtractor;

    private final PptTextExtractor pptTextExtractor;

    /**
     * 创建文件采集节点。
     *
     * @param compilerProperties 编译配置
     */
    public IngestNode(CompilerProperties compilerProperties) {
        this.compilerProperties = compilerProperties;
        this.pdfTextExtractor = new PdfTextExtractor();
        this.excelTextExtractor = new ExcelTextExtractor();
        this.wordTextExtractor = new WordTextExtractor();
        this.docTextExtractor = new DocTextExtractor();
        this.pptTextExtractor = new PptTextExtractor();
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
                    .forEach(path -> {
                        RawSource rawSource = readSource(sourceDir, path);
                        if (rawSource != null) {
                            rawSources.add(rawSource);
                        }
                    });
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
        return SUPPORTED_TEXT_FORMATS.contains(format)
                || SUPPORTED_DOCUMENT_FORMATS.contains(format)
                || SUPPORTED_IMAGE_FORMATS.contains(format);
    }

    /**
     * 读取单个源文件。
     *
     * @param sourceDir 源目录
     * @param path 文件路径
     * @return 原始源文件；不支持或无可提取正文时返回 null
     */
    private RawSource readSource(Path sourceDir, Path path) {
        String format = extractFormat(path.getFileName().toString());
        if (SUPPORTED_TEXT_FORMATS.contains(format)) {
            return readTextSource(sourceDir, path);
        }
        if ("pdf".equals(format)) {
            return readPdfSource(sourceDir, path);
        }
        if ("xlsx".equals(format) || "xls".equals(format)) {
            return readExcelSource(sourceDir, path);
        }
        if ("docx".equals(format)) {
            return readWordSource(sourceDir, path);
        }
        if ("doc".equals(format)) {
            return readLegacyWordSource(sourceDir, path);
        }
        if ("pptx".equals(format)) {
            return readPptSource(sourceDir, path);
        }
        if (SUPPORTED_IMAGE_FORMATS.contains(format)) {
            return readImagePlaceholder(sourceDir, path, format);
        }
        return null;
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
            return RawSource.extracted(relativePath, trimmedContent, format, fileSize, "{}", false, relativePath);
        }
        catch (IOException ex) {
            log.error("Failed to read source file path: {}", path, ex);
            throw new IllegalStateException("读取源文件失败: " + path, ex);
        }
    }

    /**
     * 读取 PDF 源文件。
     *
     * @param sourceDir 源目录
     * @param path PDF 路径
     * @return 原始源文件；无可提取正文时返回 null
     */
    private RawSource readPdfSource(Path sourceDir, Path path) {
        try {
            SourceExtractionResult extractionResult = pdfTextExtractor.extract(path);
            if (extractionResult == null) {
                log.warn("PDF has no extractable text path: {}", path);
                return null;
            }
            String relativePath = normalizePath(sourceDir.relativize(path));
            long fileSize = Files.size(path);
            return RawSource.extracted(
                    relativePath,
                    trimContent(extractionResult.getContent()),
                    "pdf",
                    fileSize,
                    extractionResult.getMetadataJson(),
                    extractionResult.isVerbatim(),
                    relativePath
            );
        }
        catch (IOException ex) {
            log.warn("Failed to extract PDF path: {}", path, ex);
            return null;
        }
    }

    /**
     * 读取 Excel 源文件。
     *
     * @param sourceDir 源目录
     * @param path Excel 路径
     * @return 原始源文件；无可提取正文时返回 null
     */
    private RawSource readExcelSource(Path sourceDir, Path path) {
        try {
            SourceExtractionResult extractionResult = excelTextExtractor.extract(path);
            if (extractionResult == null) {
                log.warn("Excel has no extractable text path: {}", path);
                return null;
            }
            String relativePath = normalizePath(sourceDir.relativize(path));
            long fileSize = Files.size(path);
            String format = extractFormat(path.getFileName().toString());
            return RawSource.extracted(
                    relativePath,
                    trimContent(extractionResult.getContent()),
                    format,
                    fileSize,
                    extractionResult.getMetadataJson(),
                    extractionResult.isVerbatim(),
                    relativePath
            );
        }
        catch (IOException ex) {
            log.warn("Failed to extract Excel path: {}", path, ex);
            return null;
        }
    }

    /**
     * 读取 Word 源文件。
     *
     * @param sourceDir 源目录
     * @param path Word 路径
     * @return 原始源文件；无可提取正文时返回 null
     */
    private RawSource readWordSource(Path sourceDir, Path path) {
        try {
            SourceExtractionResult extractionResult = wordTextExtractor.extract(path);
            if (extractionResult == null) {
                log.warn("Word has no extractable text path: {}", path);
                return null;
            }
            String relativePath = normalizePath(sourceDir.relativize(path));
            long fileSize = Files.size(path);
            return RawSource.extracted(
                    relativePath,
                    trimContent(extractionResult.getContent()),
                    "docx",
                    fileSize,
                    extractionResult.getMetadataJson(),
                    extractionResult.isVerbatim(),
                    relativePath
            );
        }
        catch (IOException ex) {
            log.warn("Failed to extract Word path: {}", path, ex);
            return null;
        }
    }

    /**
     * 读取旧版 Word 源文件。
     *
     * @param sourceDir 源目录
     * @param path Word 路径
     * @return 原始源文件；无可提取正文时返回 null
     */
    private RawSource readLegacyWordSource(Path sourceDir, Path path) {
        try {
            SourceExtractionResult extractionResult = docTextExtractor.extract(path);
            if (extractionResult == null) {
                log.warn("Legacy Word has no extractable text path: {}", path);
                return null;
            }
            String relativePath = normalizePath(sourceDir.relativize(path));
            long fileSize = Files.size(path);
            return RawSource.extracted(
                    relativePath,
                    trimContent(extractionResult.getContent()),
                    "doc",
                    fileSize,
                    extractionResult.getMetadataJson(),
                    extractionResult.isVerbatim(),
                    relativePath
            );
        }
        catch (IOException ex) {
            log.warn("Failed to extract legacy Word path: {}", path, ex);
            return null;
        }
    }

    /**
     * 读取 PPT 源文件。
     *
     * @param sourceDir 源目录
     * @param path PPT 路径
     * @return 原始源文件；无可提取正文时返回 null
     */
    private RawSource readPptSource(Path sourceDir, Path path) {
        try {
            SourceExtractionResult extractionResult = pptTextExtractor.extract(path);
            if (extractionResult == null) {
                log.warn("PPT has no extractable text path: {}", path);
                return null;
            }
            String relativePath = normalizePath(sourceDir.relativize(path));
            long fileSize = Files.size(path);
            return RawSource.extracted(
                    relativePath,
                    trimContent(extractionResult.getContent()),
                    "pptx",
                    fileSize,
                    extractionResult.getMetadataJson(),
                    extractionResult.isVerbatim(),
                    relativePath
            );
        }
        catch (IOException ex) {
            log.warn("Failed to extract PPT path: {}", path, ex);
            return null;
        }
    }

    /**
     * 读取图片占位源文件。
     *
     * @param sourceDir 源目录
     * @param path 图片路径
     * @param format 图片格式
     * @return 原始源文件
     */
    private RawSource readImagePlaceholder(Path sourceDir, Path path, String format) {
        try {
            String relativePath = normalizePath(sourceDir.relativize(path));
            long fileSize = Files.size(path);
            String content = "[Image file: " + relativePath + "]";
            return RawSource.extracted(relativePath, content, format, fileSize, "{}", false, relativePath);
        }
        catch (IOException ex) {
            log.warn("Failed to read image placeholder path: {}", path, ex);
            return null;
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
