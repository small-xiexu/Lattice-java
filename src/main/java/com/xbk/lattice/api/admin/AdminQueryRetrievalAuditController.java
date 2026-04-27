package com.xbk.lattice.api.admin;

import com.xbk.lattice.infra.persistence.QueryRetrievalChannelHitView;
import com.xbk.lattice.infra.persistence.QueryRetrievalRunView;
import com.xbk.lattice.query.service.RetrievalAuditQueryService;
import com.xbk.lattice.query.service.RetrievalAuditSnapshot;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理侧 Query 检索审计控制器
 *
 * 职责：暴露 retrieval audit 的最近 runs 与按 queryId 详情查询接口
 *
 * @author xiexu
 */
@RestController
@Profile("jdbc")
@RequestMapping("/api/v1/admin/query/retrieval")
public class AdminQueryRetrievalAuditController {

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
                runView.getRetrievalMode(),
                runView.isRewriteApplied(),
                runView.getRewriteAuditRef(),
                runView.getRetrievalStrategyRef(),
                runView.getFusedHitCount(),
                runView.getChannelCount(),
                runView.getCreatedAt() == null ? null : runView.getCreatedAt().toString()
        );
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
                channelHitView.getSourcePathsJson(),
                channelHitView.getMetadataJson(),
                channelHitView.getCreatedAt() == null ? null : channelHitView.getCreatedAt().toString()
        );
    }
}
