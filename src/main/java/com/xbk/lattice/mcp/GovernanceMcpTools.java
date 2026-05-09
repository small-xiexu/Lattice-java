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
 * MCP 治理工具实现。
 *
 * 职责：承接质量、生命周期、校正、传播、快照、历史和回滚工具逻辑。
 *
 * @author xiexu
 */
abstract class GovernanceMcpTools extends QueryMcpTools {

    /**
     * 返回当前知识库状态汇总。
     *
     * @return JSON 字符串，包含文章、源文件、反馈与 pending 数量
     */
    @McpTool(name = "lattice_status", description = "Return knowledge base status counts including articles, sources, contributions, and pending queries")
    protected String status() {
        StatusSnapshot statusSnapshot = requireStatusService().snapshot();
        return "{"
                + "\"articleCount\":" + statusSnapshot.getArticleCount() + ","
                + "\"sourceFileCount\":" + statusSnapshot.getSourceFileCount() + ","
                + "\"contributionCount\":" + statusSnapshot.getContributionCount() + ","
                + "\"pendingQueryCount\":" + statusSnapshot.getPendingQueryCount() + ","
                + "\"reviewPendingArticleCount\":" + statusSnapshot.getReviewPendingArticleCount()
                + "}";
    }
    /**
     * 执行最小 6 维治理检查。
     *
     * @return JSON 字符串，包含 totalIssues / checkedDimensions / items
     */
    @McpTool(name = "lattice_lint", description = "Run the minimum governance lint checks and return the discovered issues")
    protected String lint() {
        LintReport lintReport = requireLintService().lint();
        StringBuilder itemsBuilder = new StringBuilder();
        itemsBuilder.append("[");
        for (int index = 0; index < lintReport.getIssues().size(); index++) {
            if (index > 0) {
                itemsBuilder.append(",");
            }
            itemsBuilder.append(toLintIssueJson(lintReport.getIssues().get(index)));
        }
        itemsBuilder.append("]");
        return "{"
                + "\"checkedDimensions\":" + jsonStringList(lintReport.getCheckedDimensions()) + ","
                + "\"totalIssues\":" + lintReport.getTotalIssues() + ","
                + "\"items\":" + itemsBuilder
                + "}";
    }
    /**
     * 自动修复可修复的 lint 问题。
     *
     * @param targetIds 逗号分隔的概念标识，空串表示修全部
     * @return JSON 字符串，包含修复结果
     */
    @McpTool(name = "lattice_lint_fix", description = "Auto-fix lint issues that are marked as fixable using LLM")
    protected String lintFix(
            @McpToolParam(description = "Comma-separated conceptIds to fix, or empty for all fixable issues") String targetIds
    ) {
        LintReport lintReport = requireLintService().lint();
        List<String> ids = null;
        if (targetIds != null && !targetIds.isBlank()) {
            ids = new ArrayList<String>();
            for (String value : targetIds.split(",")) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    ids.add(trimmed);
                }
            }
        }
        LintFixResult result = requireLintFixService().fix(lintReport, ids);
        return "{"
                + "\"fixed\":" + result.getFixed() + ","
                + "\"skipped\":" + result.getSkipped() + ","
                + "\"errors\":" + jsonStringList(result.getErrors())
                + "}";
    }
    /**
     * 返回当前知识库质量指标。
     *
     * @return JSON 字符串，包含文章审查与反馈沉淀汇总
     */
    @McpTool(name = "lattice_quality", description = "Return quality metrics for articles, review states, contributions, and source coverage")
    protected String quality() {
        QualityMetricsReport qualityMetricsReport = requireQualityMetricsService().measure();
        QualityMetricsTrend qualityMetricsTrend = requireQualityMetricsService().trend(7);
        return "{"
                + "\"totalArticles\":" + qualityMetricsReport.getTotalArticles() + ","
                + "\"passedArticles\":" + qualityMetricsReport.getPassedArticles() + ","
                + "\"pendingReviewArticles\":" + qualityMetricsReport.getPendingReviewArticles() + ","
                + "\"needsHumanReviewArticles\":" + qualityMetricsReport.getNeedsHumanReviewArticles() + ","
                + "\"contributionCount\":" + qualityMetricsReport.getContributionCount() + ","
                + "\"sourceFileCount\":" + qualityMetricsReport.getSourceFileCount() + ","
                + "\"trend\":{"
                + "\"days\":" + qualityMetricsTrend.getDays() + ","
                + "\"latestMeasuredAt\":" + jsonString(formatOffsetDateTime(qualityMetricsTrend.getLatestMeasuredAt())) + ","
                + "\"reviewPassRateDelta\":" + qualityMetricsTrend.getReviewPassRateDelta() + ","
                + "\"groundingRateDelta\":" + qualityMetricsTrend.getGroundingRateDelta() + ","
                + "\"referentialRateDelta\":" + qualityMetricsTrend.getReferentialRateDelta() + ","
                + "\"totalArticlesDelta\":" + qualityMetricsTrend.getTotalArticlesDelta()
                + "}"
                + "}";
    }
    /**
     * 返回当前知识库源文件覆盖率。
     *
     * @return JSON 字符串，包含覆盖率汇总与已覆盖源文件列表
     */
    @McpTool(name = "lattice_coverage", description = "Return source coverage metrics based on articles.source_paths and source_files")
    protected String coverage() {
        CoverageReport coverageReport = requireCoverageTrackingService().measure();
        return "{"
                + "\"totalSourceFileCount\":" + coverageReport.getTotalSourceFileCount() + ","
                + "\"coveredSourceFileCount\":" + coverageReport.getCoveredSourceFileCount() + ","
                + "\"uncoveredSourceFileCount\":" + coverageReport.getUncoveredSourceFileCount() + ","
                + "\"coverageRatio\":" + coverageReport.getCoverageRatio() + ","
                + "\"coveredSourcePaths\":" + jsonStringList(coverageReport.getCoveredSourcePaths())
                + "}";
    }
    /**
     * 返回当前知识库未覆盖源文件清单。
     *
     * @return JSON 字符串，包含遗漏数量与遗漏源文件列表
     */
    @McpTool(name = "lattice_omissions", description = "List source files that are not referenced by any article source_paths")
    protected String omissions() {
        OmissionReport omissionReport = requireOmissionTrackingService().track();
        return "{"
                + "\"totalSourceFileCount\":" + omissionReport.getTotalSourceFileCount() + ","
                + "\"count\":" + omissionReport.getOmittedSourceFileCount() + ","
                + "\"items\":" + jsonStringList(omissionReport.getItems())
                + "}";
    }
    /**
     * 返回当前知识文章生命周期汇总。
     *
     * @return JSON 字符串，包含生命周期分布与条目列表
     */
    @McpTool(name = "lattice_lifecycle", description = "Return lifecycle distribution for knowledge articles and list lifecycle items")
    protected String lifecycle() {
        LifecycleReport lifecycleReport = requireLifecycleService().report();
        StringBuilder itemsBuilder = new StringBuilder();
        itemsBuilder.append("[");
        for (int index = 0; index < lifecycleReport.getItems().size(); index++) {
            if (index > 0) {
                itemsBuilder.append(",");
            }
            itemsBuilder.append(toLifecycleItemJson(lifecycleReport.getItems().get(index)));
        }
        itemsBuilder.append("]");
        return "{"
                + "\"totalArticles\":" + lifecycleReport.getTotalArticles() + ","
                + "\"activeCount\":" + lifecycleReport.getActiveCount() + ","
                + "\"deprecatedCount\":" + lifecycleReport.getDeprecatedCount() + ","
                + "\"archivedCount\":" + lifecycleReport.getArchivedCount() + ","
                + "\"otherCount\":" + lifecycleReport.getOtherCount() + ","
                + "\"items\":" + itemsBuilder
                + "}";
    }
    /**
     * 将文章标记为 deprecated。
     *
     * @param conceptId 概念标识
     * @param reason 原因
     * @param updatedBy 更新人
     * @return JSON 字符串，包含生命周期切换结果
     */
    @McpTool(name = "lattice_lifecycle_deprecate", description = "Mark an article as deprecated and persist lifecycle metadata")
    protected String lifecycleDeprecate(
            @McpToolParam(description = "The articleKey or conceptId to deprecate") String conceptId,
            @McpToolParam(description = "Why this article is being deprecated") String reason,
            @McpToolParam(description = "Who performs the lifecycle update") String updatedBy
    ) {
        LifecycleTransitionResult result = requireLifecycleService().deprecate(conceptId, reason, updatedBy);
        return toLifecycleTransitionJson(result);
    }
    /**
     * 将文章标记为 archived。
     *
     * @param conceptId 概念标识
     * @param reason 原因
     * @param updatedBy 更新人
     * @return JSON 字符串，包含生命周期切换结果
     */
    @McpTool(name = "lattice_lifecycle_archive", description = "Archive an article and persist lifecycle metadata")
    protected String lifecycleArchive(
            @McpToolParam(description = "The articleKey or conceptId to archive") String conceptId,
            @McpToolParam(description = "Why this article is being archived") String reason,
            @McpToolParam(description = "Who performs the lifecycle update") String updatedBy
    ) {
        LifecycleTransitionResult result = requireLifecycleService().archive(conceptId, reason, updatedBy);
        return toLifecycleTransitionJson(result);
    }
    /**
     * 将文章恢复为 active。
     *
     * @param conceptId 概念标识
     * @param reason 原因
     * @param updatedBy 更新人
     * @return JSON 字符串，包含生命周期切换结果
     */
    @McpTool(name = "lattice_lifecycle_activate", description = "Reactivate an article and persist lifecycle metadata")
    protected String lifecycleActivate(
            @McpToolParam(description = "The articleKey or conceptId to reactivate") String conceptId,
            @McpToolParam(description = "Why this article is being reactivated") String reason,
            @McpToolParam(description = "Who performs the lifecycle update") String updatedBy
    ) {
        LifecycleTransitionResult result = requireLifecycleService().activate(conceptId, reason, updatedBy);
        return toLifecycleTransitionJson(result);
    }
    /**
     * 执行链接增强，修复标题型 broken wiki-links 并同步受管关系区块。
     *
     * @param persist 是否落库
     * @return JSON 字符串，包含增强汇总与明细条目
     */
    @McpTool(name = "lattice_link_enhance", description = "Repair broken title-based wiki-links and sync managed depends_on / related blocks")
    protected String linkEnhance(
            @McpToolParam(description = "Whether to persist the enhanced content back into articles") boolean persist
    ) {
        LinkEnhancementReport report = requireLinkEnhancementService().enhance(persist);
        StringBuilder itemsBuilder = new StringBuilder();
        itemsBuilder.append("[");
        for (int index = 0; index < report.getItems().size(); index++) {
            if (index > 0) {
                itemsBuilder.append(",");
            }
            itemsBuilder.append(toLinkEnhancementItemJson(report.getItems().get(index)));
        }
        itemsBuilder.append("]");
        return "{"
                + "\"totalArticles\":" + report.getTotalArticles() + ","
                + "\"processedArticleCount\":" + report.getProcessedArticleCount() + ","
                + "\"updatedArticleCount\":" + report.getUpdatedArticleCount() + ","
                + "\"fixedLinkCount\":" + report.getFixedLinkCount() + ","
                + "\"syncedSectionCount\":" + report.getSyncedSectionCount() + ","
                + "\"unresolvedLinkCount\":" + report.getUnresolvedLinkCount() + ","
                + "\"items\":" + itemsBuilder
                + "}";
    }
    /**
     * 输出待人工确认的问题清单。
     *
     * @return JSON 字符串，包含 count / items
     */
    @McpTool(name = "lattice_inspect", description = "List normalized inspection questions that still need human confirmation")
    protected String inspect() {
        InspectionReport inspectionReport = requireInspectService().inspect();
        StringBuilder itemsBuilder = new StringBuilder();
        itemsBuilder.append("[");
        for (int index = 0; index < inspectionReport.getQuestions().size(); index++) {
            if (index > 0) {
                itemsBuilder.append(",");
            }
            itemsBuilder.append(toInspectionQuestionJson(inspectionReport.getQuestions().get(index)));
        }
        itemsBuilder.append("]");
        return "{"
                + "\"count\":" + inspectionReport.getTotalQuestions() + ","
                + "\"items\":" + itemsBuilder
                + "}";
    }
    /**
     * 导入人工最终答案，并将其沉淀到 contribution 层。
     *
     * @param inspectionId inspection 问题标识
     * @param finalAnswer 人工最终答案
     * @param confirmedBy 确认人
     * @return JSON 字符串，包含 importedCount / resolvedIds
     */
    @McpTool(name = "lattice_import_answers", description = "Import a human-reviewed final answer for an inspection item and persist it into contributions")
    protected String importAnswers(
            @McpToolParam(description = "The inspection item id returned by lattice_inspect") String inspectionId,
            @McpToolParam(description = "The final human-reviewed answer") String finalAnswer,
            @McpToolParam(description = "Who confirmed the answer") String confirmedBy
    ) {
        InspectionImportResult importResult = requireInspectionAnswerImportService().importAnswer(
                inspectionId,
                finalAnswer,
                confirmedBy
        );
        return "{"
                + "\"importedCount\":" + importResult.getImportedCount() + ","
                + "\"resolvedIds\":" + jsonStringList(importResult.getResolvedIds())
                + "}";
    }
    /**
     * 执行单篇知识文章纠错，并返回修正预览与下游传播提示。
     *
     * @param conceptId 被纠错的概念标识
     * @param correctionSummary 纠错摘要
     * @return JSON 字符串，包含修正预览、下游数量与证据支持情况
     */
    @McpTool(name = "lattice_correct", description = "Correct a knowledge article using LLM rewrite with source file cross-validation")
    protected String correctKnowledge(
            @McpToolParam(description = "The articleKey or conceptId that has been corrected") String conceptId,
            @McpToolParam(description = "A short summary of the correction") String correctionSummary
    ) {
        ArticleCorrectionResult result = requireArticleCorrectionService().correct(conceptId, correctionSummary);
        requirePropagationService().markDownstream(
                conceptId,
                correctionSummary,
                result.getDownstreamIds()
        );
        return "{"
                + "\"sourceId\":" + result.getSourceId() + ","
                + "\"articleKey\":" + jsonString(result.getArticleKey()) + ","
                + "\"conceptId\":" + jsonString(result.getConceptId()) + ","
                + "\"revisedContentPreview\":" + jsonString(preview(result.getRevisedContent(), 500)) + ","
                + "\"downstreamCount\":" + result.getDownstreamIds().size() + ","
                + "\"downstreamIds\":" + jsonStringList(result.getDownstreamIds()) + ","
                + "\"evidenceSupported\":" + result.isValidationSupported() + ","
                + "\"nextStep\":" + jsonString("如需将纠正传播到下游文章，请调用 lattice_propagate")
                + "}";
    }
    /**
     * 执行指定根概念的下游传播。
     *
     * @param rootConceptId 根概念标识
     * @return JSON 字符串，包含处理统计
     */
    @McpTool(name = "lattice_propagate", description = "Execute downstream propagation: rewrite all articles that depend on a corrected concept")
    protected String propagate(
            @McpToolParam(description = "The corrected root conceptId to propagate from") String rootConceptId
    ) {
        PropagationExecutionResult result = requirePropagateExecutionService().executePropagation(rootConceptId);
        return "{"
                + "\"rootConceptId\":" + jsonString(rootConceptId) + ","
                + "\"processed\":" + result.getProcessed() + ","
                + "\"updated\":" + result.getUpdated() + ","
                + "\"skipped\":" + result.getSkipped()
                + "}";
    }
    /**
     * 返回最近文章快照摘要。
     *
     * @param limit 返回数量
     * @return JSON 字符串，包含 count / items
     */
    @McpTool(name = "lattice_snapshot", description = "List recent article snapshots captured from article upserts")
    protected String snapshot(@McpToolParam(description = "The max number of snapshots to return") int limit) {
        SnapshotReport snapshotReport = requireSnapshotService().snapshot(limit);
        StringBuilder itemsBuilder = new StringBuilder();
        itemsBuilder.append("[");
        for (int index = 0; index < snapshotReport.getItems().size(); index++) {
            if (index > 0) {
                itemsBuilder.append(",");
            }
            itemsBuilder.append(toArticleSnapshotJson(snapshotReport.getItems().get(index)));
        }
        itemsBuilder.append("]");
        return "{"
                + "\"count\":" + snapshotReport.getTotalSnapshots() + ","
                + "\"items\":" + itemsBuilder
                + "}";
    }
    /**
     * 返回指定概念的文章快照历史。
     *
     * @param conceptId 概念标识
     * @param limit 返回数量
     * @return JSON 字符串，包含 conceptId / count / items
     */
    @McpTool(name = "lattice_history", description = "List article snapshot history for an articleKey or conceptId")
    protected String history(
            @McpToolParam(description = "The articleKey or conceptId to inspect history for") String conceptId,
            @McpToolParam(description = "The max number of history entries to return") int limit
    ) {
        HistoryReport historyReport = requireHistoryService().history(conceptId, limit);
        StringBuilder itemsBuilder = new StringBuilder();
        itemsBuilder.append("[");
        for (int index = 0; index < historyReport.getItems().size(); index++) {
            if (index > 0) {
                itemsBuilder.append(",");
            }
            itemsBuilder.append(toArticleSnapshotJson(historyReport.getItems().get(index)));
        }
        itemsBuilder.append("]");
        return "{"
                + "\"sourceId\":" + historyReport.getSourceId() + ","
                + "\"articleKey\":" + jsonString(historyReport.getArticleKey()) + ","
                + "\"conceptId\":" + jsonString(historyReport.getConceptId()) + ","
                + "\"count\":" + historyReport.getTotalEntries() + ","
                + "\"items\":" + itemsBuilder
                + "}";
    }
    /**
     * 将文章恢复到指定快照版本。
     *
     * @param conceptId 概念标识
     * @param snapshotId 快照标识
     * @return JSON 字符串，包含恢复结果
     */
    @McpTool(name = "lattice_rollback", description = "Restore an article to a previous snapshot version")
    protected String rollback(
            @McpToolParam(description = "The articleKey or conceptId to restore") String conceptId,
            @McpToolParam(description = "The snapshotId to restore from") long snapshotId
    ) {
        RollbackResult result = requireSnapshotService().rollback(conceptId, snapshotId);
        return "{"
                + "\"sourceId\":" + result.getSourceId() + ","
                + "\"articleKey\":" + jsonString(result.getArticleKey()) + ","
                + "\"conceptId\":" + jsonString(result.getConceptId()) + ","
                + "\"restoredSnapshotId\":" + result.getRestoredSnapshotId() + ","
                + "\"restoredAt\":" + jsonString(formatOffsetDateTime(result.getRestoredAt()))
                + "}";
    }
}
