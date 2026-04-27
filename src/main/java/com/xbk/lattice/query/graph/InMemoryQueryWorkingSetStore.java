package com.xbk.lattice.query.graph;

import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.query.citation.CitationCheckReport;
import com.xbk.lattice.query.citation.ClaimSegment;
import com.xbk.lattice.query.citation.QueryAnswerAuditSnapshot;
import com.xbk.lattice.query.domain.ReviewResult;
import com.xbk.lattice.query.evidence.domain.AnswerProjectionBundle;
import com.xbk.lattice.query.service.QueryArticleHit;
import com.xbk.lattice.query.service.RetrievalStrategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版问答图工作集存储
 *
 * 职责：为 Query Graph 提供进程内工作集外置存储
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
@ConditionalOnProperty(
        prefix = "lattice.query.working-set",
        name = "store",
        havingValue = "in-memory",
        matchIfMissing = true
)
public class InMemoryQueryWorkingSetStore implements QueryWorkingSetStore {

    private final Map<String, Object> store = new ConcurrentHashMap<String, Object>();

    /**
     * 保存检索命中分组。
     *
     * @param queryId 查询标识
     * @param hitGroups 命中分组
     * @return 工作集引用
     */
    @Override
    public String saveRetrievedHitGroups(String queryId, List<List<QueryArticleHit>> hitGroups) {
        String ref = buildRef(queryId, "retrieved-hit-groups");
        List<List<QueryArticleHit>> copiedHitGroups = new ArrayList<List<QueryArticleHit>>();
        for (List<QueryArticleHit> hitGroup : hitGroups) {
            copiedHitGroups.add(new ArrayList<QueryArticleHit>(hitGroup));
        }
        store.put(ref, copiedHitGroups);
        return ref;
    }

    /**
     * 读取检索命中分组。
     *
     * @param ref 工作集引用
     * @return 命中分组
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<List<QueryArticleHit>> loadRetrievedHitGroups(String ref) {
        if (!hasRef(ref)) {
            return Collections.emptyList();
        }
        Object value = store.get(ref);
        if (!(value instanceof List<?>)) {
            return Collections.emptyList();
        }
        List<?> listValue = (List<?>) value;
        List<List<QueryArticleHit>> hitGroups = new ArrayList<List<QueryArticleHit>>();
        for (Object item : listValue) {
            if (item instanceof List<?>) {
                List<?> hitGroup = (List<?>) item;
                hitGroups.add(new ArrayList<QueryArticleHit>((List<QueryArticleHit>) hitGroup));
            }
        }
        return hitGroups;
    }

    /**
     * 保存单路检索命中。
     *
     * @param queryId 查询标识
     * @param channel 通道路由
     * @param hits 命中列表
     * @return 工作集引用
     */
    @Override
    public String saveHits(String queryId, String channel, List<QueryArticleHit> hits) {
        String ref = buildRef(queryId, "hits-" + channel);
        store.put(ref, new ArrayList<QueryArticleHit>(hits));
        return ref;
    }

    /**
     * 读取单路检索命中。
     *
     * @param ref 工作集引用
     * @return 命中列表
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<QueryArticleHit> loadHits(String ref) {
        if (!hasRef(ref)) {
            return Collections.emptyList();
        }
        Object value = store.get(ref);
        if (!(value instanceof List<?>)) {
            return Collections.emptyList();
        }
        return new ArrayList<QueryArticleHit>((List<QueryArticleHit>) value);
    }

    /**
     * 保存融合命中结果。
     *
     * @param queryId 查询标识
     * @param fusedHits 融合命中
     * @return 工作集引用
     */
    @Override
    public String saveFusedHits(String queryId, List<QueryArticleHit> fusedHits) {
        String ref = buildRef(queryId, "fused-hits");
        store.put(ref, new ArrayList<QueryArticleHit>(fusedHits));
        return ref;
    }

    /**
     * 读取融合命中结果。
     *
     * @param ref 工作集引用
     * @return 融合命中
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<QueryArticleHit> loadFusedHits(String ref) {
        if (!hasRef(ref)) {
            return Collections.emptyList();
        }
        Object value = store.get(ref);
        if (!(value instanceof List<?>)) {
            return Collections.emptyList();
        }
        List<?> listValue = (List<?>) value;
        return new ArrayList<QueryArticleHit>((List<QueryArticleHit>) listValue);
    }

    /**
     * 保存检索策略。
     *
     * @param queryId 查询标识
     * @param retrievalStrategy 检索策略
     * @return 工作集引用
     */
    @Override
    public String saveRetrievalStrategy(String queryId, RetrievalStrategy retrievalStrategy) {
        String ref = buildRef(queryId, "retrieval-strategy");
        store.put(ref, retrievalStrategy);
        return ref;
    }

    /**
     * 读取检索策略。
     *
     * @param ref 工作集引用
     * @return 检索策略
     */
    @Override
    public RetrievalStrategy loadRetrievalStrategy(String ref) {
        if (!hasRef(ref)) {
            return null;
        }
        Object value = store.get(ref);
        if (value instanceof RetrievalStrategy) {
            return (RetrievalStrategy) value;
        }
        return null;
    }

    /**
     * 保存答案草稿。
     *
     * @param queryId 查询标识
     * @param answer 答案内容
     * @return 工作集引用
     */
    @Override
    public String saveAnswer(String queryId, String answer) {
        String ref = buildRef(queryId, "draft-answer");
        store.put(ref, answer);
        return ref;
    }

    /**
     * 读取答案草稿。
     *
     * @param ref 工作集引用
     * @return 答案内容
     */
    @Override
    public String loadAnswer(String ref) {
        if (!hasRef(ref)) {
            return null;
        }
        Object value = store.get(ref);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 保存审查结果。
     *
     * @param queryId 查询标识
     * @param reviewResult 审查结果
     * @return 工作集引用
     */
    @Override
    public String saveReviewResult(String queryId, ReviewResult reviewResult) {
        String ref = buildRef(queryId, "review-result");
        store.put(ref, reviewResult);
        return ref;
    }

    /**
     * 读取审查结果。
     *
     * @param ref 工作集引用
     * @return 审查结果
     */
    @Override
    public ReviewResult loadReviewResult(String ref) {
        if (!hasRef(ref)) {
            return null;
        }
        Object value = store.get(ref);
        if (value instanceof ReviewResult) {
            return (ReviewResult) value;
        }
        return null;
    }

    /**
     * 保存 claim 分段结果。
     *
     * @param queryId 查询标识
     * @param claimSegments claim 分段
     * @return 工作集引用
     */
    @Override
    public String saveClaimSegments(String queryId, List<ClaimSegment> claimSegments) {
        String ref = buildRef(queryId, "claim-segments");
        store.put(ref, new ArrayList<ClaimSegment>(claimSegments));
        return ref;
    }

    /**
     * 读取 claim 分段结果。
     *
     * @param ref 工作集引用
     * @return claim 分段
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<ClaimSegment> loadClaimSegments(String ref) {
        if (!hasRef(ref)) {
            return Collections.emptyList();
        }
        Object value = store.get(ref);
        if (!(value instanceof List<?>)) {
            return Collections.emptyList();
        }
        return new ArrayList<ClaimSegment>((List<ClaimSegment>) value);
    }

    /**
     * 保存 Citation 检查报告。
     *
     * @param queryId 查询标识
     * @param report Citation 检查报告
     * @return 工作集引用
     */
    @Override
    public String saveCitationCheckReport(String queryId, CitationCheckReport report) {
        String ref = buildRef(queryId, "citation-check-report");
        store.put(ref, report);
        return ref;
    }

    /**
     * 读取 Citation 检查报告。
     *
     * @param ref 工作集引用
     * @return Citation 检查报告
     */
    @Override
    public CitationCheckReport loadCitationCheckReport(String ref) {
        if (!hasRef(ref)) {
            return null;
        }
        Object value = store.get(ref);
        if (value instanceof CitationCheckReport) {
            return (CitationCheckReport) value;
        }
        return null;
    }

    /**
     * 保存答案审计快照。
     *
     * @param queryId 查询标识
     * @param answerAuditSnapshot 答案审计快照
     * @return 工作集引用
     */
    @Override
    public String saveAnswerAudit(String queryId, QueryAnswerAuditSnapshot answerAuditSnapshot) {
        String ref = buildRef(queryId, "answer-audit");
        store.put(ref, answerAuditSnapshot);
        return ref;
    }

    /**
     * 读取答案审计快照。
     *
     * @param ref 工作集引用
     * @return 答案审计快照
     */
    @Override
    public QueryAnswerAuditSnapshot loadAnswerAudit(String ref) {
        if (!hasRef(ref)) {
            return null;
        }
        Object value = store.get(ref);
        if (value instanceof QueryAnswerAuditSnapshot) {
            return (QueryAnswerAuditSnapshot) value;
        }
        return null;
    }

    /**
     * 保存答案投影白名单。
     *
     * @param queryId 查询标识
     * @param answerProjectionBundle 答案投影包
     * @return 工作集引用
     */
    @Override
    public String saveAnswerProjectionBundle(String queryId, AnswerProjectionBundle answerProjectionBundle) {
        String ref = buildRef(queryId, "answer-projection-bundle");
        store.put(ref, answerProjectionBundle);
        return ref;
    }

    /**
     * 读取答案投影白名单。
     *
     * @param ref 工作集引用
     * @return 答案投影包
     */
    @Override
    public AnswerProjectionBundle loadAnswerProjectionBundle(String ref) {
        if (!hasRef(ref)) {
            return null;
        }
        Object value = store.get(ref);
        if (value instanceof AnswerProjectionBundle) {
            return (AnswerProjectionBundle) value;
        }
        return null;
    }

    /**
     * 保存查询响应。
     *
     * @param queryId 查询标识
     * @param queryResponse 查询响应
     * @return 工作集引用
     */
    @Override
    public String saveResponse(String queryId, QueryResponse queryResponse) {
        String ref = buildRef(queryId, "response");
        store.put(ref, queryResponse);
        return ref;
    }

    /**
     * 读取查询响应。
     *
     * @param ref 工作集引用
     * @return 查询响应
     */
    @Override
    public QueryResponse loadResponse(String ref) {
        if (!hasRef(ref)) {
            return null;
        }
        Object value = store.get(ref);
        if (value instanceof QueryResponse) {
            return (QueryResponse) value;
        }
        return null;
    }

    /**
     * 删除指定请求的全部工作集。
     *
     * @param queryId 查询标识
     */
    @Override
    public void deleteByQueryId(String queryId) {
        String keyPrefix = queryId + ":";
        for (String key : store.keySet()) {
            if (key.startsWith(keyPrefix)) {
                store.remove(key);
            }
        }
    }

    /**
     * 构建工作集引用。
     *
     * @param queryId 查询标识
     * @param suffix 后缀
     * @return 工作集引用
     */
    private String buildRef(String queryId, String suffix) {
        return queryId + ":" + suffix;
    }

    private boolean hasRef(String ref) {
        return ref != null && !ref.isBlank();
    }
}
