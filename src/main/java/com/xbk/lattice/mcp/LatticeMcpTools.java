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
import org.springframework.context.annotation.Profile;
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
@Profile("jdbc")
public class LatticeMcpTools {

    private static final DateTimeFormatter JSON_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final QueryFacadeService queryFacadeService;

    private final PendingQueryManager pendingQueryManager;

    private final KnowledgeSearchService knowledgeSearchService;

    private final KnowledgeLookupService knowledgeLookupService;

    private final StatusService statusService;

    private final LintService lintService;

    private LintFixService lintFixService;

    private final QualityMetricsService qualityMetricsService;

    private final CoverageTrackingService coverageTrackingService;

    private final OmissionTrackingService omissionTrackingService;

    private final CompileApplicationFacade compileApplicationFacade;

    private final InspectService inspectService;

    private final InspectionAnswerImportService inspectionAnswerImportService;

    private final ArticleCorrectionService articleCorrectionService;

    private final PropagationService propagationService;

    private PropagateExecutionService propagateExecutionService;

    private SourceFileJdbcRepository sourceFileJdbcRepository;

    private DocumentSectionSelector documentSectionSelector;

    private final SnapshotService snapshotService;

    private final HistoryService historyService;

    private final LifecycleService lifecycleService;

    private final LinkEnhancementService linkEnhancementService;

    private SourceService sourceService;

    private SourceSyncWorkflowService sourceSyncWorkflowService;

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
     * 创建兼容旧全量构造器的 MCP 工具集。
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
     * 创建兼容旧全量构造器的 MCP 工具集（不含 lifecycle/link-enhance）。
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
     * 创建兼容 lifecycle 测试的 MCP 工具集。
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
     * 创建兼容旧测试构造器的 MCP 工具集。
     *
     * @param queryFacadeService 查询门面服务
     * @param pendingQueryManager PendingQuery 管理器
     */
    public LatticeMcpTools(QueryFacadeService queryFacadeService, PendingQueryManager pendingQueryManager) {
        this(queryFacadeService, pendingQueryManager, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * 创建兼容既有单元测试的 MCP 工具集。
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
     * 创建兼容 inspect/import-answers 测试的 MCP 工具集。
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
     * 创建兼容 snapshot/history 测试的 MCP 工具集。
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
     * 创建兼容 correct 测试的 MCP 工具集。
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
     * 创建兼容 propagate 测试的 MCP 工具集。
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
        QueryResponse response = queryFacadeService.query(question);
        int sourceCount = response.getSources() == null ? 0 : response.getSources().size();
        return "{"
                + "\"answer\":" + jsonString(response.getAnswer()) + ","
                + "\"queryId\":" + jsonString(response.getQueryId()) + ","
                + "\"reviewStatus\":" + jsonString(response.getReviewStatus()) + ","
                + "\"sourceCount\":" + sourceCount
                + "}";
    }

    /**
     * 列出当前全部待确认记录，供外部 AI 客户端决定后续 confirm/correct/discard 操作。
     *
     * @return JSON 字符串，包含 count / items
     */
    @McpTool(name = "lattice_query_pending", description = "List all pending query records that still need confirm, correct, or discard actions")
    public String queryPending() {
        java.util.List<PendingQueryRecord> pendingRecords = pendingQueryManager.listPendingQueries();
        StringBuilder itemsBuilder = new StringBuilder();
        itemsBuilder.append("[");
        for (int index = 0; index < pendingRecords.size(); index++) {
            if (index > 0) {
                itemsBuilder.append(",");
            }
            itemsBuilder.append(toPendingItemJson(pendingRecords.get(index)));
        }
        itemsBuilder.append("]");
        return "{"
                + "\"count\":" + pendingRecords.size() + ","
                + "\"items\":" + itemsBuilder
                + "}";
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
        PendingQueryRecord updated = pendingQueryManager.correct(queryId, correction);
        return "{"
                + "\"queryId\":" + jsonString(updated.getQueryId()) + ","
                + "\"revisedAnswer\":" + jsonString(updated.getAnswer()) + ","
                + "\"status\":\"PENDING\""
                + "}";
    }

    /**
     * 确认待确认查询，将其沉淀为贡献记录并从 pending 队列中移除。
     *
     * @param queryId 待确认查询标识
     * @return JSON 字符串，包含 queryId / status
     */
    @McpTool(name = "lattice_query_confirm", description = "Confirm a pending query answer, persisting it as a contribution and removing it from the pending queue")
    public String confirm(@McpToolParam(description = "The queryId of the pending query to confirm") String queryId) {
        pendingQueryManager.confirm(queryId);
        return "{"
                + "\"queryId\":" + jsonString(queryId) + ","
                + "\"status\":\"confirmed\""
                + "}";
    }

    /**
     * 丢弃待确认查询并返回 discarded 状态。
     *
     * @param queryId 待确认查询标识
     * @return JSON 字符串，包含 queryId / status
     */
    @McpTool(name = "lattice_query_discard", description = "Discard a pending query without persisting it as a contribution")
    public String discard(@McpToolParam(description = "The queryId of the pending query to discard") String queryId) {
        pendingQueryManager.discard(queryId);
        return "{"
                + "\"queryId\":" + jsonString(queryId) + ","
                + "\"status\":\"discarded\""
                + "}";
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
        List<QueryArticleHit> hits = requireKnowledgeSearchService().search(question, limit);
        StringBuilder itemsBuilder = new StringBuilder();
        itemsBuilder.append("[");
        for (int index = 0; index < hits.size(); index++) {
            if (index > 0) {
                itemsBuilder.append(",");
            }
            itemsBuilder.append(toSearchItemJson(hits.get(index)));
        }
        itemsBuilder.append("]");
        return "{"
                + "\"count\":" + hits.size() + ","
                + "\"items\":" + itemsBuilder
                + "}";
    }

    /**
     * 读取文章或源文件详情。
     *
     * @param id 概念标识或源文件路径
     * @return JSON 字符串，包含 status / type / content 等字段
     */
    @McpTool(name = "lattice_get", description = "Get a knowledge article or source file by articleKey, conceptId, or file path")
    public String get(@McpToolParam(description = "The articleKey, conceptId, or file path to fetch") String id) {
        KnowledgeLookupResult lookupResult = requireKnowledgeLookupService().get(id);
        return "{"
                + "\"status\":" + jsonString(lookupResult.isFound() ? "found" : "not_found") + ","
                + "\"type\":" + jsonString(lookupResult.getType()) + ","
                + "\"sourceId\":" + lookupResult.getSourceId() + ","
                + "\"articleKey\":" + jsonString(lookupResult.getArticleKey()) + ","
                + "\"id\":" + jsonString(lookupResult.getId()) + ","
                + "\"title\":" + jsonString(lookupResult.getTitle()) + ","
                + "\"content\":" + jsonString(lookupResult.getContent()) + ","
                + "\"sourcePaths\":" + jsonStringList(lookupResult.getSourcePaths()) + ","
                + "\"metadataJson\":" + jsonString(lookupResult.getMetadataJson())
                + "}";
    }

    /**
     * 返回当前知识库状态汇总。
     *
     * @return JSON 字符串，包含文章、源文件、反馈与 pending 数量
     */
    @McpTool(name = "lattice_status", description = "Return knowledge base status counts including articles, sources, contributions, and pending queries")
    public String status() {
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
    public String lint() {
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
    public String lintFix(
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
    public String quality() {
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
    public String coverage() {
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
    public String omissions() {
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
    public String lifecycle() {
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
    public String lifecycleDeprecate(
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
    public String lifecycleArchive(
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
    public String lifecycleActivate(
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
    public String linkEnhance(
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
    public String inspect() {
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
    public String importAnswers(
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
    public String correctKnowledge(
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
    public String propagate(
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
    public String snapshot(@McpToolParam(description = "The max number of snapshots to return") int limit) {
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
    public String history(
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
    public String rollback(
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

    /**
     * 返回源文件目录。
     *
     * @param path 源文件路径
     * @return JSON 字符串，包含章节标题、层级与行号
     */
    @McpTool(name = "lattice_doc_toc", description = "Return heading hierarchy and line numbers for a source document")
    public String docToc(@McpToolParam(description = "The source file path to inspect") String path) {
        SourceFileRecord sourceFileRecord = requireSourceFileJdbcRepository().findByPath(path)
                .orElseThrow(() -> new IllegalArgumentException("source file not found: " + path));
        List<DocumentSectionSelector.DocumentHeading> headings = requireDocumentSectionSelector().toc(sourceFileRecord.getContentText());
        StringBuilder itemsBuilder = new StringBuilder();
        itemsBuilder.append("[");
        for (int index = 0; index < headings.size(); index++) {
            if (index > 0) {
                itemsBuilder.append(",");
            }
            DocumentSectionSelector.DocumentHeading heading = headings.get(index);
            itemsBuilder.append("{")
                    .append("\"heading\":").append(jsonString(heading.getHeading())).append(",")
                    .append("\"level\":").append(heading.getLevel()).append(",")
                    .append("\"line\":").append(heading.getLine())
                    .append("}");
        }
        itemsBuilder.append("]");
        return "{"
                + "\"path\":" + jsonString(path) + ","
                + "\"items\":" + itemsBuilder
                + "}";
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
        SourceFileRecord sourceFileRecord = requireSourceFileJdbcRepository().findByPath(path)
                .orElseThrow(() -> new IllegalArgumentException("source file not found: " + path));
        DocumentSectionSelector.DocumentSection section = requireDocumentSectionSelector()
                .readSection(sourceFileRecord.getContentText(), heading);
        return "{"
                + "\"path\":" + jsonString(path) + ","
                + "\"heading\":" + jsonString(section.getHeading()) + ","
                + "\"line\":" + section.getLine() + ","
                + "\"content\":" + jsonString(section.getContent())
                + "}";
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
        Path compileSourceDir = Path.of(sourceDir);
        CompileResult compileResult = requireCompileApplicationFacade().compile(compileSourceDir, incremental, null);
        return "{"
                + "\"persistedCount\":" + compileResult.getPersistedCount() + ","
                + "\"jobId\":" + jsonString(compileResult.getJobId()) + ","
                + "\"mode\":" + jsonString(incremental ? "incremental" : "full")
                + "}";
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
        int safeLimit = limit <= 0 ? 10 : Math.min(limit, 50);
        KnowledgeSourcePage page = requireSourceService().listSources(null, null, null, 1, safeLimit);
        StringBuilder itemsBuilder = new StringBuilder();
        itemsBuilder.append("[");
        List<KnowledgeSource> items = page.getItems();
        for (int index = 0; index < items.size(); index++) {
            if (index > 0) {
                itemsBuilder.append(",");
            }
            itemsBuilder.append(toSourceSummaryJson(items.get(index)));
        }
        itemsBuilder.append("]");
        return "{"
                + "\"count\":" + items.size() + ","
                + "\"total\":" + page.getTotal() + ","
                + "\"items\":" + itemsBuilder
                + "}";
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
        SourceSyncRunDetail detail = requireSourceSyncWorkflowService().syncSource(sourceId);
        return toSourceRunJson(detail);
    }

    /**
     * 转换单条 pending 记录为 JSON 对象字符串。
     *
     * @param pendingQueryRecord 待确认记录
     * @return JSON 对象字符串
     */
    private String toPendingItemJson(PendingQueryRecord pendingQueryRecord) {
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
    private String toSearchItemJson(QueryArticleHit queryArticleHit) {
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
    private String toLintIssueJson(LintIssue lintIssue) {
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
    private String toLifecycleItemJson(LifecycleItem lifecycleItem) {
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
    private String toLifecycleTransitionJson(LifecycleTransitionResult result) {
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
    private String toLinkEnhancementItemJson(LinkEnhancementItem item) {
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
    private String toInspectionQuestionJson(InspectionQuestion inspectionQuestion) {
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
    private String toPropagationItemJson(PropagationItem propagationItem) {
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
    private String toArticleSnapshotJson(ArticleSnapshotRecord articleSnapshotRecord) {
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
    private String toSourceSummaryJson(KnowledgeSource knowledgeSource) {
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
    private String toSourceRunJson(SourceSyncRunDetail detail) {
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
                + "\"manifestHash\":" + jsonString(detail.getManifestHash()) + ","
                + "\"message\":" + jsonString(detail.getMessage()) + ","
                + "\"errorMessage\":" + jsonString(detail.getErrorMessage()) + ","
                + "\"sourceNames\":" + jsonStringList(detail.getSourceNames()) + ","
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
    private String jsonStringList(List<String> values) {
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

    /**
     * 生成字符串预览。
     *
     * @param value 原始字符串
     * @param limit 最大长度
     * @return 预览文本
     */
    private String preview(String value, int limit) {
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
    private String formatOffsetDateTime(OffsetDateTime value) {
        if (value == null) {
            return null;
        }
        return value.format(JSON_DATE_TIME_FORMATTER);
    }

    /**
     * 获取源文件仓储。
     *
     * @return 源文件仓储
     */
    private SourceFileJdbcRepository requireSourceFileJdbcRepository() {
        if (sourceFileJdbcRepository == null) {
            throw new UnsupportedOperationException("sourceFileJdbcRepository not configured");
        }
        return sourceFileJdbcRepository;
    }

    /**
     * 获取文档章节选择器。
     *
     * @return 文档章节选择器
     */
    private DocumentSectionSelector requireDocumentSectionSelector() {
        if (documentSectionSelector == null) {
            throw new UnsupportedOperationException("documentSectionSelector not configured");
        }
        return documentSectionSelector;
    }

    /**
     * 获取知识检索服务。
     *
     * @return 知识检索服务
     */
    private KnowledgeSearchService requireKnowledgeSearchService() {
        if (knowledgeSearchService == null) {
            throw new UnsupportedOperationException("knowledgeSearchService not configured");
        }
        return knowledgeSearchService;
    }

    /**
     * 获取知识详情服务。
     *
     * @return 知识详情服务
     */
    private KnowledgeLookupService requireKnowledgeLookupService() {
        if (knowledgeLookupService == null) {
            throw new UnsupportedOperationException("knowledgeLookupService not configured");
        }
        return knowledgeLookupService;
    }

    /**
     * 获取状态服务。
     *
     * @return 状态服务
     */
    private StatusService requireStatusService() {
        if (statusService == null) {
            throw new UnsupportedOperationException("statusService not configured");
        }
        return statusService;
    }

    /**
     * 获取 Lint 服务。
     *
     * @return Lint 服务
     */
    private LintService requireLintService() {
        if (lintService == null) {
            throw new UnsupportedOperationException("lintService not configured");
        }
        return lintService;
    }

    /**
     * 获取 lint 自动修复服务。
     *
     * @return lint 自动修复服务
     */
    private LintFixService requireLintFixService() {
        if (lintFixService == null) {
            throw new UnsupportedOperationException("lintFixService not configured");
        }
        return lintFixService;
    }

    /**
     * 获取质量指标服务。
     *
     * @return 质量指标服务
     */
    private QualityMetricsService requireQualityMetricsService() {
        if (qualityMetricsService == null) {
            throw new UnsupportedOperationException("qualityMetricsService not configured");
        }
        return qualityMetricsService;
    }

    /**
     * 获取覆盖率跟踪服务。
     *
     * @return 覆盖率跟踪服务
     */
    private CoverageTrackingService requireCoverageTrackingService() {
        if (coverageTrackingService == null) {
            throw new UnsupportedOperationException("coverageTrackingService not configured");
        }
        return coverageTrackingService;
    }

    /**
     * 获取遗漏跟踪服务。
     *
     * @return 遗漏跟踪服务
     */
    private OmissionTrackingService requireOmissionTrackingService() {
        if (omissionTrackingService == null) {
            throw new UnsupportedOperationException("omissionTrackingService not configured");
        }
        return omissionTrackingService;
    }

    /**
     * 获取生命周期服务。
     *
     * @return 生命周期服务
     */
    private LifecycleService requireLifecycleService() {
        if (lifecycleService == null) {
            throw new UnsupportedOperationException("lifecycleService not configured");
        }
        return lifecycleService;
    }

    /**
     * 获取链接增强服务。
     *
     * @return 链接增强服务
     */
    private LinkEnhancementService requireLinkEnhancementService() {
        if (linkEnhancementService == null) {
            throw new UnsupportedOperationException("linkEnhancementService not configured");
        }
        return linkEnhancementService;
    }

    /**
     * 获取统一编译应用门面。
     *
     * @return 编译应用门面
     */
    private CompileApplicationFacade requireCompileApplicationFacade() {
        if (compileApplicationFacade == null) {
            throw new UnsupportedOperationException("compileApplicationFacade not configured");
        }
        return compileApplicationFacade;
    }

    /**
     * 获取 inspect 服务。
     *
     * @return inspect 服务
     */
    private InspectService requireInspectService() {
        if (inspectService == null) {
            throw new UnsupportedOperationException("inspectService not configured");
        }
        return inspectService;
    }

    /**
     * 获取 inspection 答案导入服务。
     *
     * @return inspection 答案导入服务
     */
    private InspectionAnswerImportService requireInspectionAnswerImportService() {
        if (inspectionAnswerImportService == null) {
            throw new UnsupportedOperationException("inspectionAnswerImportService not configured");
        }
        return inspectionAnswerImportService;
    }

    /**
     * 获取文章纠错服务。
     *
     * @return 文章纠错服务
     */
    private ArticleCorrectionService requireArticleCorrectionService() {
        if (articleCorrectionService == null) {
            throw new UnsupportedOperationException("articleCorrectionService not configured");
        }
        return articleCorrectionService;
    }

    /**
     * 获取传播执行服务。
     *
     * @return 传播执行服务
     */
    private PropagateExecutionService requirePropagateExecutionService() {
        if (propagateExecutionService == null) {
            throw new UnsupportedOperationException("propagateExecutionService not configured");
        }
        return propagateExecutionService;
    }

    /**
     * 获取传播服务。
     *
     * @return 传播服务
     */
    private PropagationService requirePropagationService() {
        if (propagationService == null) {
            throw new UnsupportedOperationException("propagationService not configured");
        }
        return propagationService;
    }

    /**
     * 获取快照服务。
     *
     * @return 快照服务
     */
    private SnapshotService requireSnapshotService() {
        if (snapshotService == null) {
            throw new UnsupportedOperationException("snapshotService not configured");
        }
        return snapshotService;
    }

    /**
     * 获取历史服务。
     *
     * @return 历史服务
     */
    private HistoryService requireHistoryService() {
        if (historyService == null) {
            throw new UnsupportedOperationException("historyService not configured");
        }
        return historyService;
    }

    /**
     * 获取资料源服务。
     *
     * @return 资料源服务
     */
    private SourceService requireSourceService() {
        if (sourceService == null) {
            throw new UnsupportedOperationException("sourceService not configured");
        }
        return sourceService;
    }

    /**
     * 获取资料源同步工作流服务。
     *
     * @return 资料源同步工作流服务
     */
    private SourceSyncWorkflowService requireSourceSyncWorkflowService() {
        if (sourceSyncWorkflowService == null) {
            throw new UnsupportedOperationException("sourceSyncWorkflowService not configured");
        }
        return sourceSyncWorkflowService;
    }

    /**
     * 将字符串转义为 JSON 字符串值（含双引号），处理 null 值与特殊字符。
     *
     * @param value 原始字符串
     * @return JSON 字符串表达
     */
    private String jsonString(String value) {
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
