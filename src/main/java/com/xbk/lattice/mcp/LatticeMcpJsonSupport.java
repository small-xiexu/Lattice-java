package com.xbk.lattice.mcp;

import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.compiler.service.CompileApplicationFacade;
import com.xbk.lattice.compiler.service.CompileResult;
import com.xbk.lattice.compiler.service.DocumentSectionSelector;
import com.xbk.lattice.governance.ArticleCorrectionResult;
import com.xbk.lattice.governance.ArticleCorrectionService;
import com.xbk.lattice.governance.LintIssue;
import com.xbk.lattice.governance.LintFixResult;
import com.xbk.lattice.governance.LintFixService;
import com.xbk.lattice.governance.LintReport;
import com.xbk.lattice.governance.LintService;
import com.xbk.lattice.governance.InspectService;
import com.xbk.lattice.governance.InspectionAnswerImportService;
import com.xbk.lattice.governance.InspectionImportResult;
import com.xbk.lattice.governance.InspectionQuestion;
import com.xbk.lattice.governance.InspectionReport;
import com.xbk.lattice.governance.CoverageReport;
import com.xbk.lattice.governance.CoverageTrackingService;
import com.xbk.lattice.governance.domain.LifecycleItem;
import com.xbk.lattice.governance.domain.LifecycleReport;
import com.xbk.lattice.governance.domain.LifecycleTransitionResult;
import com.xbk.lattice.governance.LifecycleService;
import com.xbk.lattice.governance.LinkEnhancementItem;
import com.xbk.lattice.governance.LinkEnhancementReport;
import com.xbk.lattice.governance.LinkEnhancementService;
import com.xbk.lattice.governance.OmissionReport;
import com.xbk.lattice.governance.OmissionTrackingService;
import com.xbk.lattice.governance.PropagationItem;
import com.xbk.lattice.governance.PropagationExecutionResult;
import com.xbk.lattice.governance.PropagationReport;
import com.xbk.lattice.governance.PropagationService;
import com.xbk.lattice.governance.PropagateExecutionService;
import com.xbk.lattice.governance.QualityMetricsReport;
import com.xbk.lattice.governance.QualityMetricsService;
import com.xbk.lattice.governance.QualityMetricsTrend;
import com.xbk.lattice.governance.RollbackResult;
import com.xbk.lattice.governance.HistoryReport;
import com.xbk.lattice.governance.HistoryService;
import com.xbk.lattice.governance.SnapshotReport;
import com.xbk.lattice.governance.SnapshotService;
import com.xbk.lattice.governance.StatusService;
import com.xbk.lattice.governance.StatusSnapshot;
import com.xbk.lattice.infra.persistence.ArticleSnapshotRecord;
import com.xbk.lattice.infra.persistence.PendingQueryRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import com.xbk.lattice.query.service.KnowledgeLookupResult;
import com.xbk.lattice.query.service.KnowledgeLookupService;
import com.xbk.lattice.query.service.KnowledgeSearchService;
import com.xbk.lattice.query.service.PendingQueryManager;
import com.xbk.lattice.query.service.QueryArticleHit;
import com.xbk.lattice.query.service.QueryFacadeService;
import com.xbk.lattice.source.domain.KnowledgeSource;
import com.xbk.lattice.source.domain.KnowledgeSourcePage;
import com.xbk.lattice.source.domain.SourceSyncRunDetail;
import com.xbk.lattice.source.service.SourceService;
import com.xbk.lattice.source.service.SourceSyncWorkflowService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * MCP 工具 JSON 序列化支持。
 *
 * 职责：把查询、治理、资料源和处理任务响应转换为 MCP 文本 JSON。
 *
 * @author xiexu
 */
abstract class LatticeMcpJsonSupport extends LatticeMcpToolBaseSupport {

    /**
     * 转换单条 pending 记录为 JSON 对象字符串。
     *
     * @param pendingQueryRecord 待确认记录
     * @return JSON 对象字符串
     */
    protected String toPendingItemJson(PendingQueryRecord pendingQueryRecord) {
        return "{"
                + "\"queryId\":" + jsonString(pendingQueryRecord.getQueryId()) + ","
                + "\"question\":" + jsonString(pendingQueryRecord.getQuestion()) + ","
                + "\"answer\":" + jsonString(pendingQueryRecord.getAnswer()) + ","
                + "\"reviewStatus\":" + jsonString(pendingQueryRecord.getReviewStatus()) + ","
                + "\"createdAt\":" + jsonString(pendingQueryRecord.getCreatedAt().toString()) + ","
                + "\"expiresAt\":" + jsonString(pendingQueryRecord.getExpiresAt().toString())
                + "}";
    }
    /**
     * 转换单条搜索命中为 JSON。
     *
     * @param queryArticleHit 搜索命中
     * @return JSON 字符串
     */
    protected String toSearchItemJson(QueryArticleHit queryArticleHit) {
        return "{"
                + "\"evidenceType\":" + jsonString(queryArticleHit.getEvidenceType().name()) + ","
                + "\"id\":" + jsonString(queryArticleHit.getConceptId()) + ","
                + "\"title\":" + jsonString(queryArticleHit.getTitle()) + ","
                + "\"content\":" + jsonString(queryArticleHit.getContent()) + ","
                + "\"score\":" + queryArticleHit.getScore() + ","
                + "\"sourcePaths\":" + jsonStringList(queryArticleHit.getSourcePaths())
                + "}";
    }
    /**
     * 转换单条 Lint 问题为 JSON。
     *
     * @param lintIssue Lint 问题
     * @return JSON 字符串
     */
    protected String toLintIssueJson(LintIssue lintIssue) {
        return "{"
                + "\"dimension\":" + jsonString(lintIssue.getDimension()) + ","
                + "\"targetId\":" + jsonString(lintIssue.getTargetId()) + ","
                + "\"message\":" + jsonString(lintIssue.getMessage()) + ","
                + "\"fixable\":" + lintIssue.isFixable() + ","
                + "\"fixSuggestion\":" + jsonString(lintIssue.getFixSuggestion())
                + "}";
    }
    /**
     * 转换生命周期条目为 JSON。
     *
     * @param lifecycleItem 生命周期条目
     * @return JSON 字符串
     */
    protected String toLifecycleItemJson(LifecycleItem lifecycleItem) {
        return "{"
                + "\"sourceId\":" + lifecycleItem.getSourceId() + ","
                + "\"articleKey\":" + jsonString(lifecycleItem.getArticleKey()) + ","
                + "\"conceptId\":" + jsonString(lifecycleItem.getConceptId()) + ","
                + "\"title\":" + jsonString(lifecycleItem.getTitle()) + ","
                + "\"lifecycle\":" + jsonString(lifecycleItem.getLifecycle()) + ","
                + "\"reviewStatus\":" + jsonString(lifecycleItem.getReviewStatus()) + ","
                + "\"reason\":" + jsonString(lifecycleItem.getReason()) + ","
                + "\"updatedBy\":" + jsonString(lifecycleItem.getUpdatedBy()) + ","
                + "\"updatedAt\":" + jsonString(lifecycleItem.getUpdatedAt())
                + "}";
    }
    /**
     * 转换生命周期切换结果为 JSON。
     *
     * @param result 生命周期切换结果
     * @return JSON 字符串
     */
    protected String toLifecycleTransitionJson(LifecycleTransitionResult result) {
        return "{"
                + "\"sourceId\":" + result.getSourceId() + ","
                + "\"articleKey\":" + jsonString(result.getArticleKey()) + ","
                + "\"conceptId\":" + jsonString(result.getConceptId()) + ","
                + "\"title\":" + jsonString(result.getTitle()) + ","
                + "\"lifecycle\":" + jsonString(result.getLifecycle()) + ","
                + "\"reason\":" + jsonString(result.getReason()) + ","
                + "\"updatedBy\":" + jsonString(result.getUpdatedBy()) + ","
                + "\"updatedAt\":" + jsonString(result.getUpdatedAt())
                + "}";
    }
    /**
     * 转换链接增强条目为 JSON。
     *
     * @param item 链接增强条目
     * @return JSON 字符串
     */
    protected String toLinkEnhancementItemJson(LinkEnhancementItem item) {
        return "{"
                + "\"conceptId\":" + jsonString(item.getConceptId()) + ","
                + "\"title\":" + jsonString(item.getTitle()) + ","
                + "\"updated\":" + item.isUpdated() + ","
                + "\"fixedLinkCount\":" + item.getFixedLinkCount() + ","
                + "\"syncedSectionCount\":" + item.getSyncedSectionCount() + ","
                + "\"unresolvedLinks\":" + jsonStringList(item.getUnresolvedLinks())
                + "}";
    }
    /**
     * 转换 inspection 问题为 JSON。
     *
     * @param inspectionQuestion inspection 问题
     * @return JSON 字符串
     */
    protected String toInspectionQuestionJson(InspectionQuestion inspectionQuestion) {
        return "{"
                + "\"id\":" + jsonString(inspectionQuestion.getId()) + ","
                + "\"type\":" + jsonString(inspectionQuestion.getType()) + ","
                + "\"question\":" + jsonString(inspectionQuestion.getQuestion()) + ","
                + "\"prompt\":" + jsonString(inspectionQuestion.getPrompt()) + ","
                + "\"suggestedAnswer\":" + jsonString(inspectionQuestion.getSuggestedAnswer()) + ","
                + "\"sourcePaths\":" + jsonStringList(inspectionQuestion.getSourcePaths()) + ","
                + "\"reviewStatus\":" + jsonString(inspectionQuestion.getReviewStatus()) + ","
                + "\"createdAt\":" + jsonString(inspectionQuestion.getCreatedAt()) + ","
                + "\"expiresAt\":" + jsonString(inspectionQuestion.getExpiresAt())
                + "}";
    }
    /**
     * 转换传播影响项为 JSON。
     *
     * @param propagationItem 传播影响项
     * @return JSON 字符串
     */
    protected String toPropagationItemJson(PropagationItem propagationItem) {
        return "{"
                + "\"conceptId\":" + jsonString(propagationItem.getConceptId()) + ","
                + "\"title\":" + jsonString(propagationItem.getTitle()) + ","
                + "\"depth\":" + propagationItem.getDepth() + ","
                + "\"triggers\":" + jsonStringList(propagationItem.getTriggers())
                + "}";
    }
    /**
     * 转换文章快照为 JSON。
     *
     * @param articleSnapshotRecord 文章快照
     * @return JSON 字符串
     */
    protected String toArticleSnapshotJson(ArticleSnapshotRecord articleSnapshotRecord) {
        return "{"
                + "\"snapshotId\":" + articleSnapshotRecord.getSnapshotId() + ","
                + "\"sourceId\":" + articleSnapshotRecord.getSourceId() + ","
                + "\"articleKey\":" + jsonString(articleSnapshotRecord.getArticleKey()) + ","
                + "\"conceptId\":" + jsonString(articleSnapshotRecord.getConceptId()) + ","
                + "\"title\":" + jsonString(articleSnapshotRecord.getTitle()) + ","
                + "\"summary\":" + jsonString(articleSnapshotRecord.getSummary()) + ","
                + "\"lifecycle\":" + jsonString(articleSnapshotRecord.getLifecycle()) + ","
                + "\"reviewStatus\":" + jsonString(articleSnapshotRecord.getReviewStatus()) + ","
                + "\"compiledAt\":" + jsonString(articleSnapshotRecord.getCompiledAt() == null
                        ? null
                        : articleSnapshotRecord.getCompiledAt().toString()) + ","
                + "\"capturedAt\":" + jsonString(articleSnapshotRecord.getCapturedAt() == null
                        ? null
                        : articleSnapshotRecord.getCapturedAt().toString()) + ","
                + "\"snapshotReason\":" + jsonString(articleSnapshotRecord.getSnapshotReason())
                + "}";
    }
    /**
     * 转换资料源摘要为 JSON。
     *
     * @param knowledgeSource 资料源
     * @return JSON 字符串
     */
    protected String toSourceSummaryJson(KnowledgeSource knowledgeSource) {
        return "{"
                + "\"id\":" + knowledgeSource.getId() + ","
                + "\"sourceCode\":" + jsonString(knowledgeSource.getSourceCode()) + ","
                + "\"name\":" + jsonString(knowledgeSource.getName()) + ","
                + "\"sourceType\":" + jsonString(knowledgeSource.getSourceType()) + ","
                + "\"contentProfile\":" + jsonString(knowledgeSource.getContentProfile()) + ","
                + "\"status\":" + jsonString(knowledgeSource.getStatus()) + ","
                + "\"defaultSyncMode\":" + jsonString(knowledgeSource.getDefaultSyncMode()) + ","
                + "\"lastSyncStatus\":" + jsonString(knowledgeSource.getLastSyncStatus()) + ","
                + "\"lastSyncAt\":" + jsonString(formatOffsetDateTime(knowledgeSource.getLastSyncAt()))
                + "}";
    }
    /**
     * 转换资料源同步运行详情为 JSON。
     *
     * @param detail 同步运行详情
     * @return JSON 字符串
     */
    protected String toSourceRunJson(SourceSyncRunDetail detail) {
        return "{"
                + "\"runId\":" + detail.getRunId() + ","
                + "\"sourceId\":" + detail.getSourceId() + ","
                + "\"sourceName\":" + jsonString(detail.getSourceName()) + ","
                + "\"sourceType\":" + jsonString(detail.getSourceType()) + ","
                + "\"status\":" + jsonString(detail.getStatus()) + ","
                + "\"resolverMode\":" + jsonString(detail.getResolverMode()) + ","
                + "\"resolverDecision\":" + jsonString(detail.getResolverDecision()) + ","
                + "\"syncAction\":" + jsonString(detail.getSyncAction()) + ","
                + "\"matchedSourceId\":" + detail.getMatchedSourceId() + ","
                + "\"compileJobId\":" + jsonString(detail.getCompileJobId()) + ","
                + "\"compileJobStatus\":" + jsonString(detail.getCompileJobStatus()) + ","
                + "\"compileDerivedStatus\":" + jsonString(detail.getCompileDerivedStatus()) + ","
                + "\"compileCurrentStep\":" + jsonString(detail.getCompileCurrentStep()) + ","
                + "\"compileProgressCurrent\":" + detail.getCompileProgressCurrent() + ","
                + "\"compileProgressTotal\":" + detail.getCompileProgressTotal() + ","
                + "\"compileProgressMessage\":" + jsonString(detail.getCompileProgressMessage()) + ","
                + "\"displayStatus\":" + jsonString(detail.getDisplayStatus()) + ","
                + "\"displayStatusLabel\":" + jsonString(detail.getDisplayStatusLabel()) + ","
                + "\"currentStepLabel\":" + jsonString(detail.getCurrentStepLabel()) + ","
                + "\"nextStepHint\":" + jsonString(detail.getNextStepHint()) + ","
                + "\"progressText\":" + jsonString(detail.getProgressText()) + ","
                + "\"reasonSummary\":" + jsonString(detail.getReasonSummary()) + ","
                + "\"operationalNote\":" + jsonString(detail.getOperationalNote()) + ","
                + "\"displayTone\":" + jsonString(detail.getDisplayTone()) + ","
                + "\"processingActive\":" + detail.isProcessingActive() + ","
                + "\"requiresManualAction\":" + detail.isRequiresManualAction() + ","
                + "\"noticeTone\":" + jsonString(detail.getNoticeTone()) + ","
                + "\"completionNotice\":" + jsonString(detail.getCompletionNotice()) + ","
                + "\"manifestHash\":" + jsonString(detail.getManifestHash()) + ","
                + "\"message\":" + jsonString(detail.getMessage()) + ","
                + "\"errorMessage\":" + jsonString(detail.getErrorMessage()) + ","
                + "\"sourceNames\":" + jsonStringList(detail.getSourceNames()) + ","
                + "\"actions\":" + jsonTaskActions(detail.getActions()) + ","
                + "\"progressSteps\":" + jsonTaskSteps(detail.getProgressSteps()) + ","
                + "\"requestedAt\":" + jsonString(detail.getRequestedAt()) + ","
                + "\"updatedAt\":" + jsonString(detail.getUpdatedAt()) + ","
                + "\"startedAt\":" + jsonString(detail.getStartedAt()) + ","
                + "\"finishedAt\":" + jsonString(detail.getFinishedAt())
                + "}";
    }
    /**
     * 将字符串列表转为 JSON 数组。
     *
     * @param values 字符串列表
     * @return JSON 数组
     */
    protected String jsonStringList(List<String> values) {
        if (values == null) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            builder.append(jsonString(values.get(index)));
        }
        builder.append("]");
        return builder.toString();
    }
    protected String jsonTaskActions(List<com.xbk.lattice.api.admin.AdminProcessingTaskActionResponse> actions) {
        if (actions == null) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        for (int index = 0; index < actions.size(); index++) {
            com.xbk.lattice.api.admin.AdminProcessingTaskActionResponse action = actions.get(index);
            if (index > 0) {
                builder.append(",");
            }
            builder.append("{")
                    .append("\"actionKey\":").append(jsonString(action.getActionKey())).append(",")
                    .append("\"label\":").append(jsonString(action.getLabel())).append(",")
                    .append("\"buttonClass\":").append(jsonString(action.getButtonClass())).append(",")
                    .append("\"runId\":").append(action.getRunId()).append(",")
                    .append("\"sourceId\":").append(action.getSourceId()).append(",")
                    .append("\"decision\":").append(jsonString(action.getDecision())).append(",")
                    .append("\"decisionSourceId\":").append(action.getDecisionSourceId()).append(",")
                    .append("\"uploadRetry\":").append(action.isUploadRetry())
                    .append("}");
        }
        builder.append("]");
        return builder.toString();
    }
    protected String jsonTaskSteps(List<com.xbk.lattice.api.admin.AdminProcessingTaskStepResponse> steps) {
        if (steps == null) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        for (int index = 0; index < steps.size(); index++) {
            com.xbk.lattice.api.admin.AdminProcessingTaskStepResponse step = steps.get(index);
            if (index > 0) {
                builder.append(",");
            }
            builder.append("{")
                    .append("\"key\":").append(jsonString(step.getKey())).append(",")
                    .append("\"label\":").append(jsonString(step.getLabel())).append(",")
                    .append("\"status\":").append(jsonString(step.getStatus())).append(",")
                    .append("\"detail\":").append(jsonString(step.getDetail()))
                    .append("}");
        }
        builder.append("]");
        return builder.toString();
    }
    /**
     * 生成字符串预览。
     *
     * @param value 原始字符串
     * @param limit 最大长度
     * @return 预览文本
     */
    protected String preview(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit);
    }
    /**
     * 把 OffsetDateTime 格式化为稳定 JSON 输出。
     *
     * @param value 时间值
     * @return 格式化后的时间字符串
     */
    protected String formatOffsetDateTime(OffsetDateTime value) {
        if (value == null) {
            return null;
        }
        return value.format(JSON_DATE_TIME_FORMATTER);
    }
    /**
     * 将字符串转义为 JSON 字符串值（含双引号），处理 null 值与特殊字符。
     *
     * @param value 原始字符串
     * @return JSON 字符串表达
     */
    protected String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }
}
