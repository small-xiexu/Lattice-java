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
 * MCP 查询工具实现。
 *
 * 职责：承接问答、待确认反馈、检索和知识条目读取工具逻辑。
 *
 * @author xiexu
 */
abstract class QueryMcpTools extends LatticeMcpDependencySupport {

    /**
     * 向知识库发起查询，返回答案、来源数量与待确认查询标识。
     *
     * @param question 查询问题
     * @return JSON 字符串，包含 answer / queryId / reviewStatus / sourceCount
     */
    @McpTool(name = "lattice_query", description = "Query the Lattice knowledge base and return an answer with source count and a queryId for the pending review lifecycle")
    protected String query(@McpToolParam(description = "The question to ask the knowledge base") String question) {
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
    protected String queryPending() {
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
    protected String correct(
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
    protected String confirm(@McpToolParam(description = "The queryId of the pending query to confirm") String queryId) {
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
    protected String discard(@McpToolParam(description = "The queryId of the pending query to discard") String queryId) {
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
    protected String search(
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
    protected String get(@McpToolParam(description = "The articleKey, conceptId, or file path to fetch") String id) {
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
}
