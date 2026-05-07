package com.xbk.lattice.documentparse.service;

import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.documentparse.domain.DocumentParseResult;
import com.xbk.lattice.documentparse.application.DocumentParseApplicationService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 文档解析路由器
 *
 * 职责：作为兼容入口委托新的文档解析应用服务执行业务编排
 *
 * @author xiexu
 */
@Service
public class DocumentParseRouter {

    private final DocumentParseApplicationService documentParseApplicationService;

    /**
     * 创建文档解析路由器。
     *
     * @param documentParseApplicationService 文档解析应用服务
     */
    public DocumentParseRouter(DocumentParseApplicationService documentParseApplicationService) {
        this.documentParseApplicationService = documentParseApplicationService;
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
        return documentParseApplicationService.parse(workspaceRoot, filePath);
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
        return documentParseApplicationService.parseRawSource(workspaceRoot, filePath);
    }
}
