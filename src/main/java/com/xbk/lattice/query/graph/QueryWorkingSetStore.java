package com.xbk.lattice.query.graph;

import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.query.citation.CitationCheckReport;
import com.xbk.lattice.query.citation.ClaimSegment;
import com.xbk.lattice.query.citation.QueryAnswerAuditSnapshot;
import com.xbk.lattice.query.domain.ReviewResult;
import com.xbk.lattice.query.evidence.domain.AnswerProjectionBundle;
import com.xbk.lattice.query.service.QueryArticleHit;

import java.util.List;

/**
 * 问答图工作集存储
 *
 * 职责：为 Query Graph 节点提供大对象的外置读写能力
 *
 * @author xiexu
 */
public interface QueryWorkingSetStore {

    /**
     * 保存检索命中分组。
     *
     * @param queryId 查询标识
     * @param hitGroups 命中分组
     * @return 工作集引用
     */
    String saveRetrievedHitGroups(String queryId, List<List<QueryArticleHit>> hitGroups);

    /**
     * 读取检索命中分组。
     *
     * @param ref 工作集引用
     * @return 命中分组
     */
    List<List<QueryArticleHit>> loadRetrievedHitGroups(String ref);

    /**
     * 保存单路检索命中。
     *
     * @param queryId 查询标识
     * @param channel 通道路由
     * @param hits 命中列表
     * @return 工作集引用
     */
    String saveHits(String queryId, String channel, List<QueryArticleHit> hits);

    /**
     * 读取单路检索命中。
     *
     * @param ref 工作集引用
     * @return 命中列表
     */
    List<QueryArticleHit> loadHits(String ref);

    /**
     * 保存融合命中结果。
     *
     * @param queryId 查询标识
     * @param fusedHits 融合命中
     * @return 工作集引用
     */
    String saveFusedHits(String queryId, List<QueryArticleHit> fusedHits);

    /**
     * 读取融合命中结果。
     *
     * @param ref 工作集引用
     * @return 融合命中
     */
    List<QueryArticleHit> loadFusedHits(String ref);

    /**
     * 保存答案草稿。
     *
     * @param queryId 查询标识
     * @param answer 答案内容
     * @return 工作集引用
     */
    String saveAnswer(String queryId, String answer);

    /**
     * 读取答案草稿。
     *
     * @param ref 工作集引用
     * @return 答案内容
     */
    String loadAnswer(String ref);

    /**
     * 保存审查结果。
     *
     * @param queryId 查询标识
     * @param reviewResult 审查结果
     * @return 工作集引用
     */
    String saveReviewResult(String queryId, ReviewResult reviewResult);

    /**
     * 读取审查结果。
     *
     * @param ref 工作集引用
     * @return 审查结果
     */
    ReviewResult loadReviewResult(String ref);

    /**
     * 保存 claim 分段结果。
     *
     * @param queryId 查询标识
     * @param claimSegments claim 分段
     * @return 工作集引用
     */
    String saveClaimSegments(String queryId, List<ClaimSegment> claimSegments);

    /**
     * 读取 claim 分段结果。
     *
     * @param ref 工作集引用
     * @return claim 分段
     */
    List<ClaimSegment> loadClaimSegments(String ref);

    /**
     * 保存 Citation 检查报告。
     *
     * @param queryId 查询标识
     * @param report Citation 检查报告
     * @return 工作集引用
     */
    String saveCitationCheckReport(String queryId, CitationCheckReport report);

    /**
     * 读取 Citation 检查报告。
     *
     * @param ref 工作集引用
     * @return Citation 检查报告
     */
    CitationCheckReport loadCitationCheckReport(String ref);

    /**
     * 保存答案审计快照。
     *
     * @param queryId 查询标识
     * @param answerAuditSnapshot 答案审计快照
     * @return 工作集引用
     */
    String saveAnswerAudit(String queryId, QueryAnswerAuditSnapshot answerAuditSnapshot);

    /**
     * 读取答案审计快照。
     *
     * @param ref 工作集引用
     * @return 答案审计快照
     */
    QueryAnswerAuditSnapshot loadAnswerAudit(String ref);

    /**
     * 保存答案投影白名单。
     *
     * @param queryId 查询标识
     * @param answerProjectionBundle 答案投影包
     * @return 工作集引用
     */
    String saveAnswerProjectionBundle(String queryId, AnswerProjectionBundle answerProjectionBundle);

    /**
     * 读取答案投影白名单。
     *
     * @param ref 工作集引用
     * @return 答案投影包
     */
    AnswerProjectionBundle loadAnswerProjectionBundle(String ref);

    /**
     * 保存查询响应。
     *
     * @param queryId 查询标识
     * @param queryResponse 查询响应
     * @return 工作集引用
     */
    String saveResponse(String queryId, QueryResponse queryResponse);

    /**
     * 读取查询响应。
     *
     * @param ref 工作集引用
     * @return 查询响应
     */
    QueryResponse loadResponse(String ref);

    /**
     * 删除指定请求的全部工作集。
     *
     * @param queryId 查询标识
     */
    void deleteByQueryId(String queryId);
}
