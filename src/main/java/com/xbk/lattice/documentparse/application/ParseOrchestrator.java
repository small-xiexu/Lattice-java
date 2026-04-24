package com.xbk.lattice.documentparse.application;

import com.xbk.lattice.documentparse.domain.model.ParseCapability;
import com.xbk.lattice.documentparse.domain.model.ParseOutput;
import com.xbk.lattice.documentparse.domain.model.ParseRequest;
import com.xbk.lattice.documentparse.port.NativeExtractor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 文档解析编排器
 *
 * 职责：统一编排文本直读、本地抽取与 OCR Provider 调用顺序
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class ParseOrchestrator {

    private static final Set<String> IMAGE_FORMATS = new HashSet<String>(Arrays.asList(
            "png", "jpg", "jpeg", "bmp", "webp", "tiff", "gif"
    ));

    private final NativeExtractorRegistry nativeExtractorRegistry;

    private final OcrProviderRegistry ocrProviderRegistry;

    /**
     * 创建文档解析编排器。
     *
     * @param nativeExtractorRegistry 本地抽取器注册表
     * @param ocrProviderRegistry OCR Provider 注册表
     */
    public ParseOrchestrator(
            NativeExtractorRegistry nativeExtractorRegistry,
            OcrProviderRegistry ocrProviderRegistry
    ) {
        this.nativeExtractorRegistry = nativeExtractorRegistry;
        this.ocrProviderRegistry = ocrProviderRegistry;
    }

    /**
     * 解析单个文件。
     *
     * @param workspaceRoot 工作目录根路径
     * @param filePath 文件路径
     * @return 解析输出；不支持或无可用正文时返回 null
     * @throws IOException IO 异常
     */
    public ParseOutput parse(Path workspaceRoot, Path filePath) throws IOException {
        ParseRequest parseRequest = buildParseRequest(workspaceRoot, filePath);
        String format = parseRequest.getFormat();
        Optional<NativeExtractor> nativeExtractor = nativeExtractorRegistry.findExtractor(format);
        if (nativeExtractor.isPresent()) {
            ParseOutput parseOutput = nativeExtractor.get().extract(parseRequest);
            if (parseOutput != null) {
                return parseOutput;
            }
            if ("pdf".equals(format)) {
                return ocrProviderRegistry.parse(ParseCapability.SCANNED_PDF_OCR, parseRequest);
            }
            return null;
        }
        if (IMAGE_FORMATS.contains(format)) {
            return ocrProviderRegistry.parse(ParseCapability.IMAGE_OCR, parseRequest);
        }
        return null;
    }

    /**
     * 构建解析请求。
     *
     * @param workspaceRoot 工作目录根路径
     * @param filePath 文件路径
     * @return 解析请求
     * @throws IOException IO 异常
     */
    private ParseRequest buildParseRequest(Path workspaceRoot, Path filePath) throws IOException {
        String relativePath = normalizePath(workspaceRoot.relativize(filePath));
        String fileName = filePath.getFileName().toString();
        String format = extractFormat(fileName);
        long fileSize = Files.size(filePath);
        return new ParseRequest(workspaceRoot, filePath, relativePath, format, fileSize);
    }

    /**
     * 规范化相对路径。
     *
     * @param path 路径
     * @return 规范化路径
     */
    private String normalizePath(Path path) {
        return path.toString().replace('\\', '/');
    }

    /**
     * 提取文件格式。
     *
     * @param fileName 文件名
     * @return 文件格式
     */
    private String extractFormat(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }
}
