package com.xbk.lattice.api.query;

import com.xbk.lattice.query.service.KnowledgeSearchService;
import com.xbk.lattice.query.service.QueryArticleHit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索控制器
 *
 * 职责：暴露不生成答案的融合搜索入口
 *
 * @author xiexu
 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final KnowledgeSearchService knowledgeSearchService;

    /**
     * 创建搜索控制器。
     *
     * @param knowledgeSearchService 知识搜索服务
     */
    public SearchController(KnowledgeSearchService knowledgeSearchService) {
        this.knowledgeSearchService = knowledgeSearchService;
    }

    /**
     * 执行多路融合搜索。
     *
     * @param question 查询词
     * @param limit 返回数量
     * @return 搜索结果
     */
    @GetMapping
    public SearchResponse search(
            @RequestParam String question,
            @RequestParam(defaultValue = "5") int limit
    ) {
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("question 不能为空");
        }
        List<QueryArticleHit> hits = knowledgeSearchService.search(question, limit);
        List<SearchHitResponse> items = new ArrayList<SearchHitResponse>();
        for (QueryArticleHit hit : hits) {
            items.add(new SearchHitResponse(
                    hit.getEvidenceType().name(),
                    hit.getSourceId(),
                    hit.getArticleKey(),
                    hit.getConceptId(),
                    hit.getTitle(),
                    hit.getContent(),
                    hit.getMetadataJson(),
                    hit.getSourcePaths(),
                    hit.getScore()
            ));
        }
        return new SearchResponse(items.size(), items);
    }
}
