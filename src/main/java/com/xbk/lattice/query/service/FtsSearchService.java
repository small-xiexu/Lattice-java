package com.xbk.lattice.query.service;

import com.xbk.lattice.query.service.mapper.ArticleFtsSearchMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * FTS 检索服务
 *
 * 职责：提供最小全文检索能力
 *
 * @author xiexu
 */
@Service
public class FtsSearchService {

    private final ArticleFtsSearchMapper articleFtsSearchMapper;

    private final FtsConfigResolver ftsConfigResolver;

    /**
     * 创建 FTS 检索服务。
     *
     * @param articleFtsSearchMapper 文章 FTS Mapper
     */
    @Autowired
    public FtsSearchService(ArticleFtsSearchMapper articleFtsSearchMapper) {
        this(articleFtsSearchMapper, new FtsConfigResolver());
    }

    /**
     * 创建 FTS 检索服务。
     *
     * @param articleFtsSearchMapper 文章 FTS Mapper
     * @param ftsConfigResolver FTS 配置解析器
     */
    public FtsSearchService(ArticleFtsSearchMapper articleFtsSearchMapper, FtsConfigResolver ftsConfigResolver) {
        this.articleFtsSearchMapper = articleFtsSearchMapper;
        this.ftsConfigResolver = ftsConfigResolver;
    }

    /**
     * 执行全文检索。
     *
     * @param question 查询问题
     * @param limit 返回数量
     * @return 命中文章
     */
    public List<QueryArticleHit> search(String question, int limit) {
        if (articleFtsSearchMapper == null) {
            return List.of();
        }
        if (question == null || question.isBlank()) {
            return List.of();
        }

        String tsConfig = ftsConfigResolver.resolveArticleTsConfig();
        return articleFtsSearchMapper.search(
                tsConfig,
                question,
                limit
        );
    }
}
