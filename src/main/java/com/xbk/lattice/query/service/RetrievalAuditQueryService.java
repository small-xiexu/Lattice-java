package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.QueryRetrievalAuditJdbcRepository;
import com.xbk.lattice.infra.persistence.QueryRetrievalChannelHitView;
import com.xbk.lattice.infra.persistence.QueryRetrievalRunView;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 检索审计查询服务
 *
 * 职责：为管理侧提供 retrieval audit 的读取聚合能力
 *
 * @author xiexu
 */
@Service
public class RetrievalAuditQueryService {

    private static final int DEFAULT_HISTORY_LIMIT = 5;

    private static final int DEFAULT_RECENT_LIMIT = 20;

    private static final int MAX_HISTORY_LIMIT = 20;

    private static final int MAX_RECENT_LIMIT = 100;

    private final QueryRetrievalAuditJdbcRepository queryRetrievalAuditJdbcRepository;

    /**
     * 创建检索审计查询服务。
     *
     * @param queryRetrievalAuditJdbcRepository 检索审计仓储
     */
    public RetrievalAuditQueryService(QueryRetrievalAuditJdbcRepository queryRetrievalAuditJdbcRepository) {
        this.queryRetrievalAuditJdbcRepository = queryRetrievalAuditJdbcRepository;
    }

    /**
     * 按 queryId 读取最新检索审计快照。
     *
     * @param queryId 查询标识
     * @param historyLimit 历史数量
     * @return 检索审计快照
     */
    public RetrievalAuditSnapshot getLatestSnapshot(String queryId, int historyLimit) {
        String effectiveQueryId = normalizeQueryId(queryId);
        int safeHistoryLimit = sanitizeHistoryLimit(historyLimit);
        Optional<QueryRetrievalRunView> latestRunOptional = queryRetrievalAuditJdbcRepository.findLatestRunByQueryId(effectiveQueryId);
        if (latestRunOptional.isEmpty()) {
            return RetrievalAuditSnapshot.empty(effectiveQueryId);
        }
        List<QueryRetrievalRunView> runHistory = queryRetrievalAuditJdbcRepository.findRunsByQueryId(
                effectiveQueryId,
                safeHistoryLimit
        );
        QueryRetrievalRunView latestRun = latestRunOptional.get();
        List<QueryRetrievalChannelHitView> channelHits = queryRetrievalAuditJdbcRepository.findChannelHitsByRunId(
                latestRun.getRunId()
        );
        return new RetrievalAuditSnapshot(effectiveQueryId, latestRun, runHistory, channelHits);
    }

    /**
     * 查询最近若干次检索审计。
     *
     * @param limit 返回数量
     * @return 最近检索审计
     */
    public List<QueryRetrievalRunView> listRecentRuns(int limit) {
        return queryRetrievalAuditJdbcRepository.findRecentRuns(sanitizeRecentLimit(limit));
    }

    /**
     * 归一化 queryId。
     *
     * @param queryId 查询标识
     * @return 归一化 queryId
     */
    private String normalizeQueryId(String queryId) {
        if (queryId == null || queryId.isBlank()) {
            throw new IllegalArgumentException("queryId不能为空");
        }
        return queryId.trim();
    }

    /**
     * 归一化历史数量。
     *
     * @param historyLimit 历史数量
     * @return 安全历史数量
     */
    private int sanitizeHistoryLimit(int historyLimit) {
        if (historyLimit <= 0) {
            return DEFAULT_HISTORY_LIMIT;
        }
        return Math.min(historyLimit, MAX_HISTORY_LIMIT);
    }

    /**
     * 归一化 recent runs 数量。
     *
     * @param limit 返回数量
     * @return 安全数量
     */
    private int sanitizeRecentLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_RECENT_LIMIT;
        }
        return Math.min(limit, MAX_RECENT_LIMIT);
    }
}
