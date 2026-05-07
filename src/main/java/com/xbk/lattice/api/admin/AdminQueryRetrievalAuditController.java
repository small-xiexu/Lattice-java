package com.xbk.lattice.api.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.infra.persistence.QueryRetrievalChannelHitView;
import com.xbk.lattice.infra.persistence.QueryRetrievalRunView;
import com.xbk.lattice.query.service.RetrievalAuditQueryService;
import com.xbk.lattice.query.service.RetrievalAuditSnapshot;
import com.xbk.lattice.query.service.RetrievalChannelRun;
import com.xbk.lattice.query.service.RetrievalChannelRunStatus;
import com.xbk.lattice.query.service.RetrievalStrategyResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理侧 Query 检索审计控制器
 *
 * 职责：暴露 retrieval audit 的最近 runs 与按 queryId 详情查询接口
 *
 * @author xiexu
 */
@RestController
@RequestMapping("/api/v1/admin/query/retrieval")
public class AdminQueryRetrievalAuditController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private static final TypeReference<LinkedHashMap<String, RetrievalChannelRun>> CHANNEL_RUNS_TYPE =
            new TypeReference<LinkedHashMap<String, RetrievalChannelRun>>() {
            };

    private static final List<String> CHANNEL_DISPLAY_ORDER = List.of(
            RetrievalStrategyResolver.CHANNEL_FTS,
            RetrievalStrategyResolver.CHANNEL_ARTICLE_CHUNK_FTS,
            RetrievalStrategyResolver.CHANNEL_REFKEY,
            RetrievalStrategyResolver.CHANNEL_SOURCE,
            RetrievalStrategyResolver.CHANNEL_SOURCE_CHUNK_FTS,
            RetrievalStrategyResolver.CHANNEL_FACT_CARD_FTS,
            RetrievalStrategyResolver.CHANNEL_FACT_CARD_VECTOR,
            RetrievalStrategyResolver.CHANNEL_CONTRIBUTION,
            RetrievalStrategyResolver.CHANNEL_GRAPH,
            RetrievalStrategyResolver.CHANNEL_ARTICLE_VECTOR,
            RetrievalStrategyResolver.CHANNEL_CHUNK_VECTOR
    );

    private final RetrievalAuditQueryService retrievalAuditQueryService;

    /**
     * 创建管理侧 Query 检索审计控制器。
     *
     * @param retrievalAuditQueryService 检索审计查询服务
     */
    public AdminQueryRetrievalAuditController(RetrievalAuditQueryService retrievalAuditQueryService) {
        this.retrievalAuditQueryService = retrievalAuditQueryService;
    }

    /**
     * 按 queryId 查看最新一次检索审计详情。
     *
     * @param queryId 查询标识
     * @param historyLimit 历史数量
     * @return 检索审计详情
     */
    @GetMapping("/audits/latest")
    public AdminQueryRetrievalAuditDetailResponse getLatestAudit(
            @RequestParam String queryId,
            @RequestParam(defaultValue = "5") int historyLimit
    ) {
        RetrievalAuditSnapshot snapshot = retrievalAuditQueryService.getLatestSnapshot(queryId, historyLimit);
        List<AdminQueryRetrievalAuditRunResponse> runHistory = new ArrayList<AdminQueryRetrievalAuditRunResponse>();
        for (QueryRetrievalRunView runView : snapshot.getRunHistory()) {
            runHistory.add(toRunResponse(runView));
        }
        List<AdminQueryRetrievalChannelHitResponse> channelHits = new ArrayList<AdminQueryRetrievalChannelHitResponse>();
        for (QueryRetrievalChannelHitView channelHitView : snapshot.getChannelHits()) {
            channelHits.add(toChannelHitResponse(channelHitView));
        }
        return new AdminQueryRetrievalAuditDetailResponse(
                snapshot.getQueryId(),
                snapshot.isFound(),
                toRunResponse(snapshot.getLatestRun()),
                runHistory.size(),
                runHistory,
                channelHits.size(),
                channelHits
        );
    }

    /**
     * 查看最近若干次检索审计。
     *
     * @param limit 返回数量
     * @return 检索审计列表
     */
    @GetMapping("/audits/recent")
    public AdminQueryRetrievalAuditListResponse listRecentRuns(@RequestParam(defaultValue = "20") int limit) {
        List<AdminQueryRetrievalAuditRunResponse> items = new ArrayList<AdminQueryRetrievalAuditRunResponse>();
        for (QueryRetrievalRunView runView : retrievalAuditQueryService.listRecentRuns(limit)) {
            items.add(toRunResponse(runView));
        }
        return new AdminQueryRetrievalAuditListResponse(items.size(), items);
    }

    /**
     * 映射 run 响应。
     *
     * @param runView run 视图
     * @return 管理侧响应
     */
    private AdminQueryRetrievalAuditRunResponse toRunResponse(QueryRetrievalRunView runView) {
        if (runView == null) {
            return null;
        }
        return new AdminQueryRetrievalAuditRunResponse(
                runView.getRunId(),
                runView.getQueryId(),
                runView.getQuestion(),
                runView.getNormalizedQuestion(),
                runView.getRetrievalQuestion(),
                runView.getVersionTag(),
                runView.getStrategyTag(),
                runView.getQuestionTypeTag(),
                runView.getAnswerShape(),
                runView.getRetrievalMode(),
                runView.isRewriteApplied(),
                runView.getRewriteAuditRef(),
                runView.getRetrievalStrategyRef(),
                runView.getFusedHitCount(),
                runView.getChannelCount(),
                runView.getFactCardHitCount(),
                runView.getSourceChunkHitCount(),
                runView.getCoverageStatus(),
                runView.getChannelRunSummaryJson(),
                toChannelRunResponses(runView.getChannelRunSummaryJson()),
                runView.getCreatedAt() == null ? null : runView.getCreatedAt().toString()
        );
    }

    /**
     * 映射通道运行摘要响应。
     *
     * @param channelRunSummaryJson 通道运行摘要 JSON
     * @return 通道运行摘要响应
     */
    private List<AdminQueryRetrievalChannelRunResponse> toChannelRunResponses(String channelRunSummaryJson) {
        Map<String, RetrievalChannelRun> channelRuns = parseChannelRuns(channelRunSummaryJson);
        List<AdminQueryRetrievalChannelRunResponse> responses =
                new ArrayList<AdminQueryRetrievalChannelRunResponse>();
        for (String channelName : CHANNEL_DISPLAY_ORDER) {
            if (channelRuns.containsKey(channelName)) {
                responses.add(toChannelRunResponse(channelName, channelRuns.remove(channelName)));
            }
        }
        for (Map.Entry<String, RetrievalChannelRun> entry : channelRuns.entrySet()) {
            responses.add(toChannelRunResponse(entry.getKey(), entry.getValue()));
        }
        return responses;
    }

    /**
     * 映射单个通道运行摘要响应。
     *
     * @param fallbackChannelName 兜底通道名称
     * @param channelRun 通道运行摘要
     * @return 通道运行响应
     */
    private AdminQueryRetrievalChannelRunResponse toChannelRunResponse(
            String fallbackChannelName,
            RetrievalChannelRun channelRun
    ) {
        if (channelRun == null) {
            return new AdminQueryRetrievalChannelRunResponse(
                    defaultString(fallbackChannelName, ""),
                    "",
                    0L,
                    0,
                    "",
                    "",
                    false,
                    false
            );
        }
        String channelName = defaultString(channelRun.getChannelName(), fallbackChannelName);
        String status = channelRun.getStatus() == null ? "" : channelRun.getStatus().name();
        return new AdminQueryRetrievalChannelRunResponse(
                channelName,
                status,
                channelRun.getDurationMillis(),
                channelRun.getHitCount(),
                channelRun.getSkippedReason(),
                channelRun.getErrorSummary(),
                channelRun.getStatus() == RetrievalChannelRunStatus.TIMEOUT,
                channelRun.getStatus() == RetrievalChannelRunStatus.SUCCESS && channelRun.getHitCount() == 0
        );
    }

    /**
     * 解析通道运行摘要 JSON。
     *
     * @param channelRunSummaryJson 通道运行摘要 JSON
     * @return 通道运行摘要
     */
    private Map<String, RetrievalChannelRun> parseChannelRuns(String channelRunSummaryJson) {
        if (channelRunSummaryJson == null || channelRunSummaryJson.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(channelRunSummaryJson, CHANNEL_RUNS_TYPE);
        }
        catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    /**
     * 返回默认字符串。
     *
     * @param value 原始值
     * @param fallback 默认值
     * @return 字符串
     */
    private String defaultString(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback == null ? "" : fallback;
        }
        return value;
    }

    /**
     * 映射通道命中响应。
     *
     * @param channelHitView 通道命中视图
     * @return 管理侧响应
     */
    private AdminQueryRetrievalChannelHitResponse toChannelHitResponse(QueryRetrievalChannelHitView channelHitView) {
        return new AdminQueryRetrievalChannelHitResponse(
                channelHitView.getHitId(),
                channelHitView.getRunId(),
                channelHitView.getChannelName(),
                channelHitView.getHitRank(),
                channelHitView.getFusedRank(),
                channelHitView.isIncludedInFused(),
                channelHitView.getChannelWeight(),
                channelHitView.getEvidenceType(),
                channelHitView.getArticleKey(),
                channelHitView.getConceptId(),
                channelHitView.getTitle(),
                channelHitView.getScore(),
                channelHitView.getFactCardId(),
                channelHitView.getCardType(),
                channelHitView.getReviewStatus(),
                channelHitView.getConfidence(),
                channelHitView.getSourceChunkIdsJson(),
                channelHitView.getSourcePathsJson(),
                channelHitView.getMetadataJson(),
                channelHitView.getCreatedAt() == null ? null : channelHitView.getCreatedAt().toString()
        );
    }
}
