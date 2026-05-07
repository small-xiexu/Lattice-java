package com.xbk.lattice.documentparse.infra.extractor;

import com.xbk.lattice.documentparse.domain.DocumentParseMode;
import com.xbk.lattice.documentparse.domain.model.ParseOutput;
import com.xbk.lattice.documentparse.domain.model.ParseRequest;
import com.xbk.lattice.documentparse.extractor.CsvTextExtractor;
import com.xbk.lattice.documentparse.extractor.SourceExtractionResult;
import com.xbk.lattice.documentparse.port.NativeExtractor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 文本文件本地抽取器
 *
 * 职责：处理纯文本类文件的直读与统一结果组装
 *
 * @author xiexu
 */
@Component
public class TextFileNativeExtractor implements NativeExtractor {

    private static final Set<String> SUPPORTED_FORMATS = new HashSet<String>(Arrays.asList(
            "md", "txt", "markdown", "csv", "java", "xml", "properties",
            "yml", "yaml", "json", "vue", "js", "css", "html", "sh", "py"
    ));

    private final CsvTextExtractor csvTextExtractor;

    /**
     * 创建文本文件本地抽取器。
     */
    public TextFileNativeExtractor() {
        this.csvTextExtractor = new CsvTextExtractor();
    }

    /**
     * 判断当前抽取器是否支持指定格式。
     *
     * @param format 文件格式
     * @return 是否支持
     */
    @Override
    public boolean supports(String format) {
        String normalizedFormat = normalize(format);
        return SUPPORTED_FORMATS.contains(normalizedFormat);
    }

    /**
     * 执行文本文件抽取。
     *
     * @param parseRequest 解析请求
     * @return 解析输出；无可用正文时返回 null
     * @throws IOException IO 异常
     */
    @Override
    public ParseOutput extract(ParseRequest parseRequest) throws IOException {
        if ("csv".equals(normalize(parseRequest.getFormat()))) {
            return extractCsv(parseRequest);
        }
        String content = Files.readString(parseRequest.getFilePath(), StandardCharsets.UTF_8);
        String normalizedContent = content == null ? "" : content.trim();
        if (normalizedContent.isEmpty()) {
            return null;
        }
        return new ParseOutput(
                null,
                parseRequest.getRelativePath(),
                normalizedContent,
                "",
                "",
                parseRequest.getFormat(),
                parseRequest.getFileSize(),
                DocumentParseMode.TEXT_READ,
                "filesystem",
                "{}",
                false,
                parseRequest.getRelativePath()
        );
    }

    private ParseOutput extractCsv(ParseRequest parseRequest) throws IOException {
        SourceExtractionResult extractionResult = csvTextExtractor.extract(parseRequest.getFilePath());
        if (extractionResult == null) {
            return null;
        }
        String extractedContent = extractionResult.getContent();
        if (extractedContent == null || extractedContent.trim().isEmpty()) {
            return null;
        }
        return new ParseOutput(
                null,
                parseRequest.getRelativePath(),
                extractedContent.trim(),
                "",
                extractionResult.getStructuredContentJson(),
                parseRequest.getFormat(),
                parseRequest.getFileSize(),
                DocumentParseMode.TEXT_READ,
                "filesystem",
                extractionResult.getMetadataJson(),
                extractionResult.isVerbatim(),
                parseRequest.getRelativePath()
        );
    }

    /**
     * 规范化文件格式。
     *
     * @param format 文件格式
     * @return 规范化结果
     */
    private String normalize(String format) {
        return format == null ? "" : format.trim().toLowerCase(Locale.ROOT);
    }
}
