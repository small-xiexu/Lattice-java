package com.xbk.lattice.documentparse.infra.extractor;

import com.xbk.lattice.documentparse.domain.DocumentParseMode;
import com.xbk.lattice.documentparse.domain.model.ParseOutput;
import com.xbk.lattice.documentparse.domain.model.ParseRequest;
import com.xbk.lattice.documentparse.extractor.DocTextExtractor;
import com.xbk.lattice.documentparse.extractor.ExcelTextExtractor;
import com.xbk.lattice.documentparse.extractor.PptTextExtractor;
import com.xbk.lattice.documentparse.extractor.SourceExtractionResult;
import com.xbk.lattice.documentparse.extractor.WordTextExtractor;
import com.xbk.lattice.documentparse.port.NativeExtractor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Office 文档本地抽取器
 *
 * 职责：统一处理 Word、Excel、PPT 等 Office 文件的本地文本抽取
 *
 * @author xiexu
 */
@Component
public class OfficeDocumentNativeExtractor implements NativeExtractor {

    private static final Set<String> SUPPORTED_FORMATS = new HashSet<String>(Arrays.asList(
            "xlsx", "xls", "docx", "doc", "pptx"
    ));

    private final ExcelTextExtractor excelTextExtractor;

    private final WordTextExtractor wordTextExtractor;

    private final DocTextExtractor docTextExtractor;

    private final PptTextExtractor pptTextExtractor;

    /**
     * 创建 Office 文档本地抽取器。
     */
    public OfficeDocumentNativeExtractor() {
        this.excelTextExtractor = new ExcelTextExtractor();
        this.wordTextExtractor = new WordTextExtractor();
        this.docTextExtractor = new DocTextExtractor();
        this.pptTextExtractor = new PptTextExtractor();
    }

    /**
     * 判断当前抽取器是否支持指定格式。
     *
     * @param format 文件格式
     * @return 是否支持
     */
    @Override
    public boolean supports(String format) {
        return SUPPORTED_FORMATS.contains(format);
    }

    /**
     * 执行 Office 文档抽取。
     *
     * @param parseRequest 解析请求
     * @return 解析输出；无可用正文时返回 null
     * @throws IOException IO 异常
     */
    @Override
    public ParseOutput extract(ParseRequest parseRequest) throws IOException {
        String format = parseRequest.getFormat();
        if ("xlsx".equals(format) || "xls".equals(format)) {
            SourceExtractionResult extractionResult = excelTextExtractor.extract(parseRequest.getFilePath());
            return buildOutput(parseRequest, extractionResult, "poi_excel");
        }
        if ("docx".equals(format)) {
            SourceExtractionResult extractionResult = wordTextExtractor.extract(parseRequest.getFilePath());
            return buildOutput(parseRequest, extractionResult, "poi_xwpf");
        }
        if ("doc".equals(format)) {
            SourceExtractionResult extractionResult = docTextExtractor.extract(parseRequest.getFilePath());
            return buildOutput(parseRequest, extractionResult, "poi_hwpf");
        }
        if ("pptx".equals(format)) {
            SourceExtractionResult extractionResult = pptTextExtractor.extract(parseRequest.getFilePath());
            return buildOutput(parseRequest, extractionResult, "poi_ppt");
        }
        return null;
    }

    /**
     * 组装 Office 抽取结果。
     *
     * @param parseRequest 解析请求
     * @param extractionResult 抽取结果
     * @param parseProvider 解析供应商
     * @return 解析输出；无可用正文时返回 null
     */
    private ParseOutput buildOutput(
            ParseRequest parseRequest,
            SourceExtractionResult extractionResult,
            String parseProvider
    ) {
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
                DocumentParseMode.OFFICE_EXTRACT,
                parseProvider,
                extractionResult.getMetadataJson(),
                extractionResult.isVerbatim(),
                parseRequest.getRelativePath()
        );
    }
}
