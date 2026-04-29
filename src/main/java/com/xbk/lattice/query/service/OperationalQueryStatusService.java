package com.xbk.lattice.query.service;

import com.xbk.lattice.api.query.QueryArticleResponse;
import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.api.query.QuerySourceResponse;
import com.xbk.lattice.documentparse.domain.model.ParseRoutePolicy;
import com.xbk.lattice.documentparse.domain.model.ProviderConnection;
import com.xbk.lattice.documentparse.service.DocumentParseConnectionAdminService;
import com.xbk.lattice.documentparse.service.DocumentParseRoutePolicyAdminService;
import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.domain.GenerationMode;
import com.xbk.lattice.query.domain.ModelExecutionStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 运行态问答状态服务
 *
 * 职责：为 OCR / 文档识别这类系统运行态问题直接返回后台真实配置状态，避免把这类问题误走普通知识检索
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class OperationalQueryStatusService {

    public static final String RUNTIME_STATUS_DERIVATION = "RUNTIME_STATUS";

    private static final String OCR_RUNTIME_ARTICLE_KEY = "runtime--document-parse-status";

    private static final String OCR_RUNTIME_CONCEPT_ID = "document-parse-runtime-status";

    private final DocumentParseConnectionAdminService documentParseConnectionAdminService;

    private final DocumentParseRoutePolicyAdminService documentParseRoutePolicyAdminService;

    /**
     * 创建运行态问答状态服务。
     *
     * @param documentParseConnectionAdminService 文档解析连接后台服务
     * @param documentParseRoutePolicyAdminService 文档解析路由策略后台服务
     */
    public OperationalQueryStatusService(
            DocumentParseConnectionAdminService documentParseConnectionAdminService,
            DocumentParseRoutePolicyAdminService documentParseRoutePolicyAdminService
    ) {
        this.documentParseConnectionAdminService = documentParseConnectionAdminService;
        this.documentParseRoutePolicyAdminService = documentParseRoutePolicyAdminService;
    }

    /**
     * 尝试直接回答运行态问题。
     *
     * @param question 用户问题
     * @return 命中运行态问题时返回查询响应；否则返回 null
     */
    public QueryResponse resolve(String question) {
        if (!looksLikeOcrStatusQuestion(question)) {
            return null;
        }
        return buildOcrStatusResponse(question);
    }

    /**
     * 判断问题是否在询问 OCR / 文档识别模块当前状态。
     *
     * @param question 用户问题
     * @return 命中返回 true
     */
    private boolean looksLikeOcrStatusQuestion(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String normalizedQuestion = question.toLowerCase(Locale.ROOT);
        boolean mentionsOcr = normalizedQuestion.contains("ocr")
                || normalizedQuestion.contains("文档识别")
                || normalizedQuestion.contains("扫描 pdf")
                || normalizedQuestion.contains("图片识别");
        boolean asksStatus = normalizedQuestion.contains("状态")
                || normalizedQuestion.contains("待配置")
                || normalizedQuestion.contains("已就绪")
                || normalizedQuestion.contains("可用")
                || normalizedQuestion.contains("怎样")
                || normalizedQuestion.contains("现在");
        return mentionsOcr && asksStatus;
    }

    /**
     * 构建 OCR / 文档识别运行态回答。
     *
     * @param question 用户问题
     * @return 查询响应
     */
    private QueryResponse buildOcrStatusResponse(String question) {
        List<ProviderConnection> enabledConnections = listEnabledConnections();
        ParseRoutePolicy parseRoutePolicy = documentParseRoutePolicyAdminService.getDefaultPolicy();
        boolean imageReady = containsConnection(enabledConnections, parseRoutePolicy.getImageConnectionId());
        boolean scannedPdfReady = containsConnection(enabledConnections, parseRoutePolicy.getScannedPdfConnectionId());
        String answerMarkdown = buildOcrStatusMarkdown(question, enabledConnections, imageReady, scannedPdfReady);
        List<String> sourcePaths = List.of(
                "/api/v1/admin/document-parse/connections",
                "/api/v1/admin/document-parse/policies/default"
        );
        QuerySourceResponse querySourceResponse = new QuerySourceResponse(
                null,
                OCR_RUNTIME_ARTICLE_KEY,
                OCR_RUNTIME_CONCEPT_ID,
                "OCR / 文档识别运行态",
                sourcePaths,
                RUNTIME_STATUS_DERIVATION
        );
        QueryArticleResponse queryArticleResponse = new QueryArticleResponse(
                null,
                OCR_RUNTIME_ARTICLE_KEY,
                OCR_RUNTIME_CONCEPT_ID,
                "OCR / 文档识别运行态",
                RUNTIME_STATUS_DERIVATION
        );
        return new QueryResponse(
                answerMarkdown,
                List.of(querySourceResponse),
                List.of(queryArticleResponse),
                null,
                "PASSED",
                AnswerOutcome.SUCCESS,
                GenerationMode.RULE_BASED,
                ModelExecutionStatus.SKIPPED,
                null,
                null
        );
    }

    /**
     * 生成 OCR / 文档识别运行态 Markdown。
     *
     * @param question 用户问题
     * @param enabledConnections 已启用连接
     * @param imageReady 图片 OCR 是否就绪
     * @param scannedPdfReady 扫描 PDF OCR 是否就绪
     * @return Markdown 回答
     */
    private String buildOcrStatusMarkdown(
            String question,
            List<ProviderConnection> enabledConnections,
            boolean imageReady,
            boolean scannedPdfReady
    ) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("# 查询回答").append("\n\n");
        stringBuilder.append("## 问题").append("\n");
        stringBuilder.append(question == null ? "" : question.trim()).append("\n\n");
        stringBuilder.append("## 结论").append("\n");
        if (enabledConnections.isEmpty()) {
            stringBuilder.append("- 当前还没有可用的 OCR / 文档识别连接，系统处于待配置状态。").append("\n");
            stringBuilder.append("- 普通文本导入通常不受影响，但扫描 PDF、图片和复杂文档识别会受限。").append("\n\n");
        }
        else if (imageReady || scannedPdfReady) {
            stringBuilder.append("- 当前已经存在可用的 OCR / 文档识别连接，运行态不是“完全未配置”。").append("\n");
            stringBuilder.append("- 图片 OCR 路由：")
                    .append(imageReady ? "已就绪" : "未绑定默认连接")
                    .append("；扫描 PDF OCR 路由：")
                    .append(scannedPdfReady ? "已就绪" : "未绑定默认连接")
                    .append("。").append("\n\n");
        }
        else {
            stringBuilder.append("- 当前虽然已经配置了可用 OCR / 文档识别连接，但默认路由还没有绑定到图片或扫描 PDF 场景。").append("\n");
            stringBuilder.append("- 如果现在直接导入扫描 PDF 或图片资料，仍可能因为默认路由未就绪而受限。").append("\n\n");
        }
        stringBuilder.append("## 参考说明").append("\n");
        stringBuilder.append("- 运行态来源：当前后台文档解析连接列表与默认路由策略。").append("\n");
        stringBuilder.append("- 已启用连接数：").append(enabledConnections.size()).append("。").append("\n");
        if (!enabledConnections.isEmpty()) {
            stringBuilder.append("- 已启用连接：").append(joinConnectionCodes(enabledConnections)).append("。").append("\n");
        }
        stringBuilder.append("- 默认图片 OCR 路由：")
                .append(imageReady ? "已绑定" : "未绑定")
                .append("；默认扫描 PDF 路由：")
                .append(scannedPdfReady ? "已绑定" : "未绑定")
                .append("。").append("\n");
        return stringBuilder.toString().trim();
    }

    /**
     * 返回已启用连接。
     *
     * @return 已启用连接
     */
    private List<ProviderConnection> listEnabledConnections() {
        List<ProviderConnection> enabledConnections = new ArrayList<ProviderConnection>();
        for (ProviderConnection providerConnection : documentParseConnectionAdminService.listConnections()) {
            if (providerConnection != null && providerConnection.isEnabled()) {
                enabledConnections.add(providerConnection);
            }
        }
        return enabledConnections;
    }

    /**
     * 判断已启用连接列表中是否包含指定连接。
     *
     * @param enabledConnections 已启用连接
     * @param connectionId 连接主键
     * @return 包含返回 true
     */
    private boolean containsConnection(List<ProviderConnection> enabledConnections, Long connectionId) {
        if (connectionId == null || enabledConnections == null || enabledConnections.isEmpty()) {
            return false;
        }
        for (ProviderConnection providerConnection : enabledConnections) {
            if (providerConnection != null && connectionId.equals(providerConnection.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 拼接连接编码。
     *
     * @param enabledConnections 已启用连接
     * @return 连接编码串
     */
    private String joinConnectionCodes(List<ProviderConnection> enabledConnections) {
        List<String> connectionCodes = new ArrayList<String>();
        for (ProviderConnection providerConnection : enabledConnections) {
            if (providerConnection == null || providerConnection.getConnectionCode() == null) {
                continue;
            }
            connectionCodes.add(providerConnection.getConnectionCode());
        }
        return String.join("、", connectionCodes);
    }
}
