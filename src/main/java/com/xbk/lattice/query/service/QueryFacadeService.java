package com.xbk.lattice.query.service;

import com.xbk.lattice.api.query.QueryArticleResponse;
import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.api.query.QuerySourceResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询门面服务
 *
 * 职责：串联最小查询闭环的检索、融合和答案生成
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class QueryFacadeService {

    private static final int TOP_K = 5;

    private final FtsSearchService ftsSearchService;

    private final RefKeySearchService refKeySearchService;

    private final RrfFusionService rrfFusionService;

    private final AnswerGenerationService answerGenerationService;

    /**
     * 创建查询门面服务。
     *
     * @param ftsSearchService FTS 检索服务
     * @param refKeySearchService 引用词检索服务
     * @param rrfFusionService RRF 融合服务
     * @param answerGenerationService 答案生成服务
     */
    public QueryFacadeService(
            FtsSearchService ftsSearchService,
            RefKeySearchService refKeySearchService,
            RrfFusionService rrfFusionService,
            AnswerGenerationService answerGenerationService
    ) {
        this.ftsSearchService = ftsSearchService;
        this.refKeySearchService = refKeySearchService;
        this.rrfFusionService = rrfFusionService;
        this.answerGenerationService = answerGenerationService;
    }

    /**
     * 执行最小知识查询。
     *
     * @param question 查询问题
     * @return 查询响应
     */
    public QueryResponse query(String question) {
        List<QueryArticleHit> ftsHits = ftsSearchService.search(question, TOP_K);
        List<QueryArticleHit> refKeyHits = refKeySearchService.search(question, TOP_K);
        List<QueryArticleHit> fusedHits = rrfFusionService.fuse(ftsHits, refKeyHits, TOP_K);
        if (fusedHits.isEmpty()) {
            return new QueryResponse("未找到相关知识", List.of(), List.of());
        }

        QueryArticleHit topHit = fusedHits.get(0);
        String answer = answerGenerationService.generate(question, topHit);
        return new QueryResponse(
                answer,
                toSourceResponses(fusedHits),
                toArticleResponses(fusedHits)
        );
    }

    /**
     * 转换来源响应。
     *
     * @param fusedHits 融合结果
     * @return 来源响应列表
     */
    private List<QuerySourceResponse> toSourceResponses(List<QueryArticleHit> fusedHits) {
        List<QuerySourceResponse> sourceResponses = new ArrayList<QuerySourceResponse>();
        for (QueryArticleHit fusedHit : fusedHits) {
            sourceResponses.add(new QuerySourceResponse(
                    fusedHit.getConceptId(),
                    fusedHit.getTitle(),
                    fusedHit.getSourcePaths()
            ));
        }
        return sourceResponses;
    }

    /**
     * 转换文章响应。
     *
     * @param fusedHits 融合结果
     * @return 文章响应列表
     */
    private List<QueryArticleResponse> toArticleResponses(List<QueryArticleHit> fusedHits) {
        List<QueryArticleResponse> articleResponses = new ArrayList<QueryArticleResponse>();
        for (QueryArticleHit fusedHit : fusedHits) {
            articleResponses.add(new QueryArticleResponse(
                    fusedHit.getConceptId(),
                    fusedHit.getTitle()
            ));
        }
        return articleResponses;
    }
}
