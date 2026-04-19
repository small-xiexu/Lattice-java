package com.xbk.lattice.documentparse.service;

import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.documentparse.domain.DocumentParseMode;
import com.xbk.lattice.documentparse.domain.DocumentParseResult;
import com.xbk.lattice.documentparse.extractor.DocTextExtractor;
import com.xbk.lattice.documentparse.extractor.ExcelTextExtractor;
import com.xbk.lattice.documentparse.extractor.PdfTextExtractor;
import com.xbk.lattice.documentparse.extractor.PptTextExtractor;
import com.xbk.lattice.documentparse.extractor.SourceExtractionResult;
import com.xbk.lattice.documentparse.extractor.WordTextExtractor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 文档解析路由器
 *
 * 职责：根据文件类型把资料路由到文本直读、Office 抽取、PDF 文本抽取或 OCR 分支
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class DocumentParseRouter {

    private static final Set<String> TEXT_FORMATS = new HashSet<String>(Arrays.asList(
            "md", "txt", "markdown", "java", "xml", "properties",
            "yml", "yaml", "json", "vue", "js", "css", "html", "sh", "py"
    ));

    private static final Set<String> IMAGE_FORMATS = new HashSet<String>(Arrays.asList(
            "png", "jpg", "jpeg", "bmp", "webp", "tiff", "gif"
    ));

    private final PdfTextExtractor pdfTextExtractor;

    private final ExcelTextExtractor excelTextExtractor;

    private final WordTextExtractor wordTextExtractor;

    private final DocTextExtractor docTextExtractor;

    private final PptTextExtractor pptTextExtractor;

    private final DocumentParseOcrService documentParseOcrService;

    private final DocumentParseResultNormalizer documentParseResultNormalizer;

    /**
     * 创建文档解析路由器。
     *
     * @param documentParseOcrService OCR 服务
     * @param documentParseResultNormalizer 结果标准化器
     */
    public DocumentParseRouter(
            DocumentParseOcrService documentParseOcrService,
            DocumentParseResultNormalizer documentParseResultNormalizer
    ) {
        this.pdfTextExtractor = new PdfTextExtractor();
        this.excelTextExtractor = new ExcelTextExtractor();
        this.wordTextExtractor = new WordTextExtractor();
        this.docTextExtractor = new DocTextExtractor();
        this.pptTextExtractor = new PptTextExtractor();
        this.documentParseOcrService = documentParseOcrService;
        this.documentParseResultNormalizer = documentParseResultNormalizer;
    }

    /**
     * 解析文件并返回统一结果。
     *
     * @param workspaceRoot 工作目录根路径
     * @param filePath 文件路径
     * @return 文档解析结果；不支持或无有效正文时返回 null
     * @throws IOException IO 异常
     */
    public DocumentParseResult parse(Path workspaceRoot, Path filePath) throws IOException {
        String format = extractFormat(filePath.getFileName().toString());
        String relativePath = normalizePath(workspaceRoot.relativize(filePath));
        long fileSize = Files.size(filePath);
        if (TEXT_FORMATS.contains(format)) {
            String content = Files.readString(filePath, StandardCharsets.UTF_8).trim();
            if (content.isBlank()) {
                return null;
            }
            return new DocumentParseResult(
                    null,
                    relativePath,
                    content,
                    format,
                    fileSize,
                    DocumentParseMode.TEXT_READ,
                    "filesystem",
                    "{}",
                    false,
                    relativePath
            );
        }
        if ("pdf".equals(format)) {
            SourceExtractionResult extractionResult = pdfTextExtractor.extract(filePath);
            if (extractionResult != null) {
                return buildResult(relativePath, format, fileSize, extractionResult, DocumentParseMode.PDF_TEXT, "pdfbox");
            }
            DocumentParseOcrService.OcrExtractionResult ocrResult = documentParseOcrService.extractScannedPdf(filePath);
            return new DocumentParseResult(
                    null,
                    relativePath,
                    ocrResult.getContent(),
                    format,
                    fileSize,
                    DocumentParseMode.OCR_SCANNED_PDF,
                    ocrResult.getProviderType(),
                    ocrResult.getMetadataJson(),
                    true,
                    relativePath
            );
        }
        if ("xlsx".equals(format) || "xls".equals(format)) {
            SourceExtractionResult extractionResult = excelTextExtractor.extract(filePath);
            return buildResult(relativePath, format, fileSize, extractionResult, DocumentParseMode.OFFICE_EXTRACT, "poi_excel");
        }
        if ("docx".equals(format)) {
            SourceExtractionResult extractionResult = wordTextExtractor.extract(filePath);
            return buildResult(relativePath, format, fileSize, extractionResult, DocumentParseMode.OFFICE_EXTRACT, "poi_xwpf");
        }
        if ("doc".equals(format)) {
            SourceExtractionResult extractionResult = docTextExtractor.extract(filePath);
            return buildResult(relativePath, format, fileSize, extractionResult, DocumentParseMode.OFFICE_EXTRACT, "poi_hwpf");
        }
        if ("pptx".equals(format)) {
            SourceExtractionResult extractionResult = pptTextExtractor.extract(filePath);
            return buildResult(relativePath, format, fileSize, extractionResult, DocumentParseMode.OFFICE_EXTRACT, "poi_ppt");
        }
        if (IMAGE_FORMATS.contains(format)) {
            DocumentParseOcrService.OcrExtractionResult ocrResult = documentParseOcrService.extractImage(filePath);
            return new DocumentParseResult(
                    null,
                    relativePath,
                    ocrResult.getContent(),
                    format,
                    fileSize,
                    DocumentParseMode.OCR_IMAGE,
                    ocrResult.getProviderType(),
                    ocrResult.getMetadataJson(),
                    true,
                    relativePath
            );
        }
        return null;
    }

    /**
     * 解析文件并直接标准化为 RawSource。
     *
     * @param workspaceRoot 工作目录根路径
     * @param filePath 文件路径
     * @return RawSource；不支持或无有效正文时返回 null
     * @throws IOException IO 异常
     */
    public RawSource parseRawSource(Path workspaceRoot, Path filePath) throws IOException {
        DocumentParseResult result = parse(workspaceRoot, filePath);
        if (result == null) {
            return null;
        }
        return documentParseResultNormalizer.normalize(result);
    }

    private DocumentParseResult buildResult(
            String relativePath,
            String format,
            long fileSize,
            SourceExtractionResult extractionResult,
            DocumentParseMode parseMode,
            String parseProvider
    ) {
        if (extractionResult == null || extractionResult.getContent() == null || extractionResult.getContent().trim().isEmpty()) {
            return null;
        }
        return new DocumentParseResult(
                null,
                relativePath,
                extractionResult.getContent().trim(),
                format,
                fileSize,
                parseMode,
                parseProvider,
                extractionResult.getMetadataJson(),
                extractionResult.isVerbatim(),
                relativePath
        );
    }

    private String normalizePath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private String extractFormat(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase();
    }
}
