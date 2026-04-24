package com.xbk.lattice.documentparse.infra.extractor;

import com.xbk.lattice.documentparse.domain.DocumentParseMode;
import com.xbk.lattice.documentparse.domain.model.ParseOutput;
import com.xbk.lattice.documentparse.domain.model.ParseRequest;
import com.xbk.lattice.documentparse.extractor.PdfTextExtractor;
import com.xbk.lattice.documentparse.extractor.SourceExtractionResult;
import com.xbk.lattice.documentparse.port.NativeExtractor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * PDF 本地抽取器
 *
 * 职责：优先使用 PDFBox 抽取文本型 PDF，供编排层决定是否回退到 OCR
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class PdfNativeExtractor implements NativeExtractor {

    private final PdfTextExtractor pdfTextExtractor;

    /**
     * 创建 PDF 本地抽取器。
     */
    public PdfNativeExtractor() {
        this.pdfTextExtractor = new PdfTextExtractor();
    }

    /**
     * 判断当前抽取器是否支持指定格式。
     *
     * @param format 文件格式
     * @return 是否支持
     */
    @Override
    public boolean supports(String format) {
        return "pdf".equals(format);
    }

    /**
     * 执行 PDF 本地抽取。
     *
     * @param parseRequest 解析请求
     * @return 解析输出；无可用正文时返回 null
     * @throws IOException IO 异常
     */
    @Override
    public ParseOutput extract(ParseRequest parseRequest) throws IOException {
        SourceExtractionResult extractionResult = pdfTextExtractor.extract(parseRequest.getFilePath());
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
                "",
                parseRequest.getFormat(),
                parseRequest.getFileSize(),
                DocumentParseMode.PDF_TEXT,
                "pdfbox",
                extractionResult.getMetadataJson(),
                extractionResult.isVerbatim(),
                parseRequest.getRelativePath()
        );
    }
}
