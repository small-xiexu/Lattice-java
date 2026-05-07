package com.xbk.lattice.documentparse.application;

import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.documentparse.domain.DocumentParseResult;
import com.xbk.lattice.documentparse.domain.model.ParseOutput;
import com.xbk.lattice.documentparse.service.DocumentParseResultNormalizer;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 文档解析应用服务
 *
 * 职责：为兼容入口提供基于新解析内核的统一文档解析编排能力
 *
 * @author xiexu
 */
@Service
public class DocumentParseApplicationService {

    private final ParseOrchestrator parseOrchestrator;

    private final DocumentParseResultNormalizer documentParseResultNormalizer;

    /**
     * 创建文档解析应用服务。
     *
     * @param parseOrchestrator 文档解析编排器
     * @param documentParseResultNormalizer 文档解析结果标准化器
     */
    public DocumentParseApplicationService(
            ParseOrchestrator parseOrchestrator,
            DocumentParseResultNormalizer documentParseResultNormalizer
    ) {
        this.parseOrchestrator = parseOrchestrator;
        this.documentParseResultNormalizer = documentParseResultNormalizer;
    }

    /**
     * 解析文件并返回兼容结果。
     *
     * @param workspaceRoot 工作目录根路径
     * @param filePath 文件路径
     * @return 文档解析结果；不支持或无有效正文时返回 null
     * @throws IOException IO 异常
     */
    public DocumentParseResult parse(Path workspaceRoot, Path filePath) throws IOException {
        ParseOutput parseOutput = parseOrchestrator.parse(workspaceRoot, filePath);
        if (parseOutput == null || !parseOutput.hasResolvedContent()) {
            return null;
        }
        String resolvedContent = parseOutput.resolveContent();
        return new DocumentParseResult(
                parseOutput.getSourceId(),
                parseOutput.getRelativePath(),
                resolvedContent,
                parseOutput.getFormat(),
                parseOutput.getFileSize(),
                parseOutput.getParseMode(),
                parseOutput.getParseProvider(),
                parseOutput.getMetadataJson(),
                parseOutput.isVerbatim(),
                parseOutput.getRawPath()
        );
    }

    /**
     * 解析文件并标准化为 RawSource。
     *
     * @param workspaceRoot 工作目录根路径
     * @param filePath 文件路径
     * @return RawSource；不支持或无有效正文时返回 null
     * @throws IOException IO 异常
     */
    public RawSource parseRawSource(Path workspaceRoot, Path filePath) throws IOException {
        ParseOutput parseOutput = parseOrchestrator.parse(workspaceRoot, filePath);
        if (parseOutput == null || !parseOutput.hasResolvedContent()) {
            return null;
        }
        return documentParseResultNormalizer.normalize(parseOutput);
    }
}
