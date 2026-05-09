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
 * Lattice MCP 工具集
 *
 * 职责：将知识查询与反馈闭环能力通过 MCP 协议暴露给外部 AI 客户端（Claude Desktop / Cursor）
 *
 * @author xiexu
 */
@Service
public class LatticeMcpTools extends CompileMcpTools {

    /**
     * 创建 Lattice MCP 工具集。
     *
     * @param queryFacadeService 查询门面服务
     * @param pendingQueryManager PendingQuery 管理器
     */
    @Autowired
    public LatticeMcpTools(
            QueryFacadeService queryFacadeService,
            PendingQueryManager pendingQueryManager,
            KnowledgeSearchService knowledgeSearchService,
            KnowledgeLookupService knowledgeLookupService,
            StatusService statusService,
            LintService lintService,
            QualityMetricsService qualityMetricsService,
            CompileApplicationFacade compileApplicationFacade,
            InspectService inspectService,
            InspectionAnswerImportService inspectionAnswerImportService,
            ArticleCorrectionService articleCorrectionService,
            PropagationService propagationService,
            SnapshotService snapshotService,
            HistoryService historyService,
            CoverageTrackingService coverageTrackingService,
            OmissionTrackingService omissionTrackingService,
            LifecycleService lifecycleService,
            LinkEnhancementService linkEnhancementService
    ) {
        this.queryFacadeService = queryFacadeService;
        this.pendingQueryManager = pendingQueryManager;
        this.knowledgeSearchService = knowledgeSearchService;
        this.knowledgeLookupService = knowledgeLookupService;
        this.statusService = statusService;
        this.lintService = lintService;
        this.qualityMetricsService = qualityMetricsService;
        this.coverageTrackingService = coverageTrackingService;
        this.omissionTrackingService = omissionTrackingService;
        this.compileApplicationFacade = compileApplicationFacade;
        this.inspectService = inspectService;
        this.inspectionAnswerImportService = inspectionAnswerImportService;
        this.articleCorrectionService = articleCorrectionService;
        this.propagationService = propagationService;
        this.snapshotService = snapshotService;
        this.historyService = historyService;
        this.lifecycleService = lifecycleService;
        this.linkEnhancementService = linkEnhancementService;
    }

    /**
     * 注入传播执行服务。
     *
     * @param propagateExecutionService 传播执行服务
     */
    @Autowired(required = false)
    void setPropagateExecutionService(PropagateExecutionService propagateExecutionService) {
        this.propagateExecutionService = propagateExecutionService;
    }

    /**
     * 注入资料源服务。
     *
     * @param sourceService 资料源服务
     */
    @Autowired(required = false)
    void setSourceService(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    /**
     * 注入资料源同步工作流服务。
     *
     * @param sourceSyncWorkflowService 资料源同步工作流服务
     */
    @Autowired(required = false)
    void setSourceSyncWorkflowService(SourceSyncWorkflowService sourceSyncWorkflowService) {
        this.sourceSyncWorkflowService = sourceSyncWorkflowService;
    }

    /**
     * 注入源文件仓储。
     *
     * @param sourceFileJdbcRepository 源文件仓储
     */
    @Autowired(required = false)
    void setSourceFileJdbcRepository(SourceFileJdbcRepository sourceFileJdbcRepository) {
        this.sourceFileJdbcRepository = sourceFileJdbcRepository;
    }

    /**
     * 注入文档章节选择器。
     *
     * @param documentSectionSelector 文档章节选择器
     */
    @Autowired(required = false)
    void setDocumentSectionSelector(DocumentSectionSelector documentSectionSelector) {
        this.documentSectionSelector = documentSectionSelector;
    }

    /**
     * 注入 lint 自动修复服务。
     *
     * @param lintFixService lint 自动修复服务
     */
    @Autowired(required = false)
    void setLintFixService(LintFixService lintFixService) {
        this.lintFixService = lintFixService;
    }

    /**
     * 创建完整依赖的 MCP 工具集。
     */
    public LatticeMcpTools(
            QueryFacadeService queryFacadeService,
            PendingQueryManager pendingQueryManager,
            KnowledgeSearchService knowledgeSearchService,
            KnowledgeLookupService knowledgeLookupService,
            StatusService statusService,
            LintService lintService,
            QualityMetricsService qualityMetricsService,
            CompileApplicationFacade compileApplicationFacade,
            InspectService inspectService,
            InspectionAnswerImportService inspectionAnswerImportService,
            PropagationService propagationService,
            SnapshotService snapshotService,
            HistoryService historyService,
            CoverageTrackingService coverageTrackingService,
            OmissionTrackingService omissionTrackingService,
            LifecycleService lifecycleService,
            LinkEnhancementService linkEnhancementService
    ) {
        this(
                queryFacadeService,
                pendingQueryManager,
                knowledgeSearchService,
                knowledgeLookupService,
                statusService,
                lintService,
                qualityMetricsService,
                compileApplicationFacade,
                inspectService,
                inspectionAnswerImportService,
                null,
                propagationService,
                snapshotService,
                historyService,
                coverageTrackingService,
                omissionTrackingService,
                lifecycleService,
                linkEnhancementService
        );
    }

    /**
     * 创建完整依赖的 MCP 工具集（不含 lifecycle/link-enhance）。
     */
    public LatticeMcpTools(
            QueryFacadeService queryFacadeService,
            PendingQueryManager pendingQueryManager,
            KnowledgeSearchService knowledgeSearchService,
            KnowledgeLookupService knowledgeLookupService,
            StatusService statusService,
            LintService lintService,
            QualityMetricsService qualityMetricsService,
            CompileApplicationFacade compileApplicationFacade,
            InspectService inspectService,
            InspectionAnswerImportService inspectionAnswerImportService,
            PropagationService propagationService,
            SnapshotService snapshotService,
            HistoryService historyService,
            CoverageTrackingService coverageTrackingService,
            OmissionTrackingService omissionTrackingService
    ) {
        this(
                queryFacadeService,
                pendingQueryManager,
                knowledgeSearchService,
                knowledgeLookupService,
                statusService,
                lintService,
                qualityMetricsService,
                compileApplicationFacade,
                inspectService,
                inspectionAnswerImportService,
                null,
                propagationService,
                snapshotService,
                historyService,
                coverageTrackingService,
                omissionTrackingService,
                null,
                null
        );
    }

    /**
     * 创建 lifecycle 测试用 MCP 工具集。
     */
    public LatticeMcpTools(
            QueryFacadeService queryFacadeService,
            PendingQueryManager pendingQueryManager,
            KnowledgeSearchService knowledgeSearchService,
            KnowledgeLookupService knowledgeLookupService,
            StatusService statusService,
            LintService lintService,
            QualityMetricsService qualityMetricsService,
            CompileApplicationFacade compileApplicationFacade,
            InspectService inspectService,
            InspectionAnswerImportService inspectionAnswerImportService,
            PropagationService propagationService,
            SnapshotService snapshotService,
            HistoryService historyService,
            CoverageTrackingService coverageTrackingService,
            OmissionTrackingService omissionTrackingService,
            LifecycleService lifecycleService
    ) {
        this(
                queryFacadeService,
                pendingQueryManager,
                knowledgeSearchService,
                knowledgeLookupService,
                statusService,
                lintService,
                qualityMetricsService,
                compileApplicationFacade,
                inspectService,
                inspectionAnswerImportService,
                null,
                propagationService,
                snapshotService,
                historyService,
                coverageTrackingService,
                omissionTrackingService,
                lifecycleService,
                null
        );
    }

    /**
     * 创建测试用 MCP 工具集。
     *
     * @param queryFacadeService 查询门面服务
     * @param pendingQueryManager PendingQuery 管理器
     */
    public LatticeMcpTools(QueryFacadeService queryFacadeService, PendingQueryManager pendingQueryManager) {
        this(queryFacadeService, pendingQueryManager, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * 创建单元测试用 MCP 工具集。
     */
    public LatticeMcpTools(
            QueryFacadeService queryFacadeService,
            PendingQueryManager pendingQueryManager,
            KnowledgeSearchService knowledgeSearchService,
            KnowledgeLookupService knowledgeLookupService,
            StatusService statusService,
            LintService lintService,
            QualityMetricsService qualityMetricsService,
            CompileApplicationFacade compileApplicationFacade
    ) {
        this(
                queryFacadeService,
                pendingQueryManager,
                knowledgeSearchService,
                knowledgeLookupService,
                statusService,
                lintService,
                qualityMetricsService,
                compileApplicationFacade,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );
    }

    /**
     * 创建 inspect/import-answers 测试用 MCP 工具集。
     */
    public LatticeMcpTools(
            QueryFacadeService queryFacadeService,
            PendingQueryManager pendingQueryManager,
            KnowledgeSearchService knowledgeSearchService,
            KnowledgeLookupService knowledgeLookupService,
            StatusService statusService,
            LintService lintService,
            QualityMetricsService qualityMetricsService,
            CompileApplicationFacade compileApplicationFacade,
            InspectService inspectService,
            InspectionAnswerImportService inspectionAnswerImportService
    ) {
        this(
                queryFacadeService,
                pendingQueryManager,
                knowledgeSearchService,
                knowledgeLookupService,
                statusService,
                lintService,
                qualityMetricsService,
                compileApplicationFacade,
                inspectService,
                inspectionAnswerImportService,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    /**
     * 创建 snapshot/history 测试用 MCP 工具集。
     */
    public LatticeMcpTools(
            QueryFacadeService queryFacadeService,
            PendingQueryManager pendingQueryManager,
            KnowledgeSearchService knowledgeSearchService,
            KnowledgeLookupService knowledgeLookupService,
            StatusService statusService,
            LintService lintService,
            QualityMetricsService qualityMetricsService,
            CompileApplicationFacade compileApplicationFacade,
            InspectService inspectService,
            InspectionAnswerImportService inspectionAnswerImportService,
            ArticleCorrectionService articleCorrectionService,
            PropagationService propagationService
    ) {
        this(
                queryFacadeService,
                pendingQueryManager,
                knowledgeSearchService,
                knowledgeLookupService,
                statusService,
                lintService,
                qualityMetricsService,
                compileApplicationFacade,
                inspectService,
                inspectionAnswerImportService,
                articleCorrectionService,
                propagationService,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    /**
     * 创建 correct 测试用 MCP 工具集。
     */
    public LatticeMcpTools(
            QueryFacadeService queryFacadeService,
            PendingQueryManager pendingQueryManager,
            KnowledgeSearchService knowledgeSearchService,
            KnowledgeLookupService knowledgeLookupService,
            StatusService statusService,
            LintService lintService,
            QualityMetricsService qualityMetricsService,
            CompileApplicationFacade compileApplicationFacade,
            InspectService inspectService,
            InspectionAnswerImportService inspectionAnswerImportService,
            PropagationService propagationService,
            SnapshotService snapshotService,
            HistoryService historyService
    ) {
        this(
                queryFacadeService,
                pendingQueryManager,
                knowledgeSearchService,
                knowledgeLookupService,
                statusService,
                lintService,
                qualityMetricsService,
                compileApplicationFacade,
                inspectService,
                inspectionAnswerImportService,
                null,
                propagationService,
                snapshotService,
                historyService,
                null,
                null,
                null,
                null
        );
    }

    /**
     * 创建 propagate 测试用 MCP 工具集。
     */
    public LatticeMcpTools(
            QueryFacadeService queryFacadeService,
            PendingQueryManager pendingQueryManager,
            KnowledgeSearchService knowledgeSearchService,
            KnowledgeLookupService knowledgeLookupService,
            StatusService statusService,
            LintService lintService,
            QualityMetricsService qualityMetricsService,
            CompileApplicationFacade compileApplicationFacade,
            InspectService inspectService,
            InspectionAnswerImportService inspectionAnswerImportService,
            PropagationService propagationService
    ) {
        this(
                queryFacadeService,
                pendingQueryManager,
                knowledgeSearchService,
                knowledgeLookupService,
                statusService,
                lintService,
                qualityMetricsService,
                compileApplicationFacade,
                inspectService,
                inspectionAnswerImportService,
                null,
                propagationService,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    /**
     * 向知识库发起查询，返回答案、来源数量与待确认查询标识。
     *
     * @param question 查询问题
     * @return JSON 字符串，包含 answer / queryId / reviewStatus / sourceCount
     */
    @McpTool(name = "lattice_query", description = "Query the Lattice knowledge base and return an answer with source count and a queryId for the pending review lifecycle")
    public String query(@McpToolParam(description = "The question to ask the knowledge base") String question) {
        return super.query(question);
    }
    /**
     * 列出当前全部待确认记录，供外部 AI 客户端决定后续 confirm/correct/discard 操作。
     *
     * @return JSON 字符串，包含 count / items
     */
    @McpTool(name = "lattice_query_pending", description = "List all pending query records that still need confirm, correct, or discard actions")
    public String queryPending() {
        return super.queryPending();
    }
    /**
     * 对待确认查询提交纠正内容，修订答案并保持 PENDING 状态。
     *
     * @param queryId 待确认查询标识
     * @param correction 纠正内容
     * @return JSON 字符串，包含 queryId / revisedAnswer / status
     */
    @McpTool(name = "lattice_query_correct", description = "Submit a correction to a pending query answer; the query remains pending until confirmed")
    public String correct(
            @McpToolParam(description = "The queryId of the pending query to correct") String queryId,
            @McpToolParam(description = "The correction text to append to the answer") String correction
    ) {
        return super.correct(queryId, correction);
    }
    /**
     * 确认待确认查询，将其沉淀为贡献记录并从 pending 队列中移除。
     *
     * @param queryId 待确认查询标识
     * @return JSON 字符串，包含 queryId / status
     */
    @McpTool(name = "lattice_query_confirm", description = "Confirm a pending query answer, persisting it as a contribution and removing it from the pending queue")
    public String confirm(@McpToolParam(description = "The queryId of the pending query to confirm") String queryId) {
        return super.confirm(queryId);
    }
    /**
     * 丢弃待确认查询并返回 discarded 状态。
     *
     * @param queryId 待确认查询标识
     * @return JSON 字符串，包含 queryId / status
     */
    @McpTool(name = "lattice_query_discard", description = "Discard a pending query without persisting it as a contribution")
    public String discard(@McpToolParam(description = "The queryId of the pending query to discard") String queryId) {
        return super.discard(queryId);
    }
    /**
     * 搜索知识库，返回融合命中的证据列表而不生成最终答案。
     *
     * @param question 查询问题
     * @param limit 返回数量
     * @return JSON 字符串，包含 count / items
     */
    @McpTool(name = "lattice_search", description = "Search the Lattice knowledge base and return fused evidence hits without generating a final answer")
    public String search(
            @McpToolParam(description = "The question or keywords to search") String question,
            @McpToolParam(description = "The max number of hits to return") int limit
    ) {
        return super.search(question, limit);
    }
    /**
     * 读取文章或源文件详情。
     *
     * @param id 概念标识或源文件路径
     * @return JSON 字符串，包含 status / type / content 等字段
     */
    @McpTool(name = "lattice_get", description = "Get a knowledge article or source file by articleKey, conceptId, or file path")
    public String get(@McpToolParam(description = "The articleKey, conceptId, or file path to fetch") String id) {
        return super.get(id);
    }
    /**
     * 返回当前知识库状态汇总。
     *
     * @return JSON 字符串，包含文章、源文件、反馈与 pending 数量
     */
    @McpTool(name = "lattice_status", description = "Return knowledge base status counts including articles, sources, contributions, and pending queries")
    public String status() {
        return super.status();
    }
    /**
     * 执行最小 6 维治理检查。
     *
     * @return JSON 字符串，包含 totalIssues / checkedDimensions / items
     */
    @McpTool(name = "lattice_lint", description = "Run the minimum governance lint checks and return the discovered issues")
    public String lint() {
        return super.lint();
    }
    /**
     * 自动修复可修复的 lint 问题。
     *
     * @param targetIds 逗号分隔的概念标识，空串表示修全部
     * @return JSON 字符串，包含修复结果
     */
    @McpTool(name = "lattice_lint_fix", description = "Auto-fix lint issues that are marked as fixable using LLM")
    public String lintFix(
            @McpToolParam(description = "Comma-separated conceptIds to fix, or empty for all fixable issues") String targetIds
    ) {
        return super.lintFix(targetIds);
    }
    /**
     * 返回当前知识库质量指标。
     *
     * @return JSON 字符串，包含文章审查与反馈沉淀汇总
     */
    @McpTool(name = "lattice_quality", description = "Return quality metrics for articles, review states, contributions, and source coverage")
    public String quality() {
        return super.quality();
    }
    /**
     * 返回当前知识库源文件覆盖率。
     *
     * @return JSON 字符串，包含覆盖率汇总与已覆盖源文件列表
     */
    @McpTool(name = "lattice_coverage", description = "Return source coverage metrics based on articles.source_paths and source_files")
    public String coverage() {
        return super.coverage();
    }
    /**
     * 返回当前知识库未覆盖源文件清单。
     *
     * @return JSON 字符串，包含遗漏数量与遗漏源文件列表
     */
    @McpTool(name = "lattice_omissions", description = "List source files that are not referenced by any article source_paths")
    public String omissions() {
        return super.omissions();
    }
    /**
     * 返回当前知识文章生命周期汇总。
     *
     * @return JSON 字符串，包含生命周期分布与条目列表
     */
    @McpTool(name = "lattice_lifecycle", description = "Return lifecycle distribution for knowledge articles and list lifecycle items")
    public String lifecycle() {
        return super.lifecycle();
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
    public String lifecycleDeprecate(
            @McpToolParam(description = "The articleKey or conceptId to deprecate") String conceptId,
            @McpToolParam(description = "Why this article is being deprecated") String reason,
            @McpToolParam(description = "Who performs the lifecycle update") String updatedBy
    ) {
        return super.lifecycleDeprecate(conceptId, reason, updatedBy);
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
    public String lifecycleArchive(
            @McpToolParam(description = "The articleKey or conceptId to archive") String conceptId,
            @McpToolParam(description = "Why this article is being archived") String reason,
            @McpToolParam(description = "Who performs the lifecycle update") String updatedBy
    ) {
        return super.lifecycleArchive(conceptId, reason, updatedBy);
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
    public String lifecycleActivate(
            @McpToolParam(description = "The articleKey or conceptId to reactivate") String conceptId,
            @McpToolParam(description = "Why this article is being reactivated") String reason,
            @McpToolParam(description = "Who performs the lifecycle update") String updatedBy
    ) {
        return super.lifecycleActivate(conceptId, reason, updatedBy);
    }
    /**
     * 执行链接增强，修复标题型 broken wiki-links 并同步受管关系区块。
     *
     * @param persist 是否落库
     * @return JSON 字符串，包含增强汇总与明细条目
     */
    @McpTool(name = "lattice_link_enhance", description = "Repair broken title-based wiki-links and sync managed depends_on / related blocks")
    public String linkEnhance(
            @McpToolParam(description = "Whether to persist the enhanced content back into articles") boolean persist
    ) {
        return super.linkEnhance(persist);
    }
    /**
     * 输出待人工确认的问题清单。
     *
     * @return JSON 字符串，包含 count / items
     */
    @McpTool(name = "lattice_inspect", description = "List normalized inspection questions that still need human confirmation")
    public String inspect() {
        return super.inspect();
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
    public String importAnswers(
            @McpToolParam(description = "The inspection item id returned by lattice_inspect") String inspectionId,
            @McpToolParam(description = "The final human-reviewed answer") String finalAnswer,
            @McpToolParam(description = "Who confirmed the answer") String confirmedBy
    ) {
        return super.importAnswers(inspectionId, finalAnswer, confirmedBy);
    }
    /**
     * 执行单篇知识文章纠错，并返回修正预览与下游传播提示。
     *
     * @param conceptId 被纠错的概念标识
     * @param correctionSummary 纠错摘要
     * @return JSON 字符串，包含修正预览、下游数量与证据支持情况
     */
    @McpTool(name = "lattice_correct", description = "Correct a knowledge article using LLM rewrite with source file cross-validation")
    public String correctKnowledge(
            @McpToolParam(description = "The articleKey or conceptId that has been corrected") String conceptId,
            @McpToolParam(description = "A short summary of the correction") String correctionSummary
    ) {
        return super.correctKnowledge(conceptId, correctionSummary);
    }
    /**
     * 执行指定根概念的下游传播。
     *
     * @param rootConceptId 根概念标识
     * @return JSON 字符串，包含处理统计
     */
    @McpTool(name = "lattice_propagate", description = "Execute downstream propagation: rewrite all articles that depend on a corrected concept")
    public String propagate(
            @McpToolParam(description = "The corrected root conceptId to propagate from") String rootConceptId
    ) {
        return super.propagate(rootConceptId);
    }
    /**
     * 返回最近文章快照摘要。
     *
     * @param limit 返回数量
     * @return JSON 字符串，包含 count / items
     */
    @McpTool(name = "lattice_snapshot", description = "List recent article snapshots captured from article upserts")
    public String snapshot(@McpToolParam(description = "The max number of snapshots to return") int limit) {
        return super.snapshot(limit);
    }
    /**
     * 返回指定概念的文章快照历史。
     *
     * @param conceptId 概念标识
     * @param limit 返回数量
     * @return JSON 字符串，包含 conceptId / count / items
     */
    @McpTool(name = "lattice_history", description = "List article snapshot history for an articleKey or conceptId")
    public String history(
            @McpToolParam(description = "The articleKey or conceptId to inspect history for") String conceptId,
            @McpToolParam(description = "The max number of history entries to return") int limit
    ) {
        return super.history(conceptId, limit);
    }
    /**
     * 将文章恢复到指定快照版本。
     *
     * @param conceptId 概念标识
     * @param snapshotId 快照标识
     * @return JSON 字符串，包含恢复结果
     */
    @McpTool(name = "lattice_rollback", description = "Restore an article to a previous snapshot version")
    public String rollback(
            @McpToolParam(description = "The articleKey or conceptId to restore") String conceptId,
            @McpToolParam(description = "The snapshotId to restore from") long snapshotId
    ) {
        return super.rollback(conceptId, snapshotId);
    }
    /**
     * 返回源文件目录。
     *
     * @param path 源文件路径
     * @return JSON 字符串，包含章节标题、层级与行号
     */
    @McpTool(name = "lattice_doc_toc", description = "Return heading hierarchy and line numbers for a source document")
    public String docToc(@McpToolParam(description = "The source file path to inspect") String path) {
        return super.docToc(path);
    }
    /**
     * 读取源文件指定章节。
     *
     * @param path 源文件路径
     * @param heading 章节标题
     * @return JSON 字符串，包含章节标题、行号与正文
     */
    @McpTool(name = "lattice_doc_read", description = "Read a specific heading section from a source document")
    public String docRead(
            @McpToolParam(description = "The source file path to inspect") String path,
            @McpToolParam(description = "The heading title to read") String heading
    ) {
        return super.docRead(path, heading);
    }
    /**
     * 触发知识库编译。
     *
     * @param sourceDir 源目录
     * @param incremental 是否增量编译
     * @return JSON 字符串，包含 persistedCount / jobId / mode
     * @throws IOException IO 异常
     */
    @McpTool(name = "lattice_compile", description = "Compile a source directory into the knowledge base, optionally in incremental mode")
    public String compile(
            @McpToolParam(description = "The source directory to compile") String sourceDir,
            @McpToolParam(description = "Whether to run incremental compile") boolean incremental
    ) throws IOException {
        return super.compile(sourceDir, incremental);
    }
    /**
     * 返回资料源列表。
     *
     * @param limit 返回数量
     * @return JSON 字符串，包含 count / items
     */
    @McpTool(name = "lattice_source_list", description = "List knowledge sources with status, type, and last sync summary")
    public String sourceList(
            @McpToolParam(description = "The max number of sources to return") int limit
    ) {
        return super.sourceList(limit);
    }
    /**
     * 对资料源发起一次同步。
     *
     * @param sourceId 资料源主键
     * @return JSON 字符串，包含 runId / status / sourceId / compileJobStatus 等字段
     * @throws IOException IO 异常
     */
    @McpTool(name = "lattice_source_sync", description = "Trigger a source sync run and return the run detail")
    public String sourceSync(
            @McpToolParam(description = "The sourceId returned by lattice_source_list") long sourceId
    ) throws IOException {
        return super.sourceSync(sourceId);
    }
}
