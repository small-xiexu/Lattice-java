package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.LexicalSearchTokenBudget;
import com.xbk.lattice.query.service.mapper.RefKeySearchMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 关键引用词检索服务
 *
 * 职责：提供最小引用词/业务码检索能力
 *
 * @author xiexu
 */
@Service
public class RefKeySearchService {

    private final RefKeySearchMapper refKeySearchMapper;

    /**
     * 创建关键引用词检索服务。
     *
     * @param refKeySearchMapper 关键引用词检索 Mapper
     */
    @Autowired
    public RefKeySearchService(RefKeySearchMapper refKeySearchMapper) {
        this.refKeySearchMapper = refKeySearchMapper;
    }

    /**
     * 执行关键引用词检索。
     *
     * @param question 查询问题
     * @param limit 返回数量
     * @return 命中文章
     */
    public List<QueryArticleHit> search(String question, int limit) {
        if (refKeySearchMapper == null) {
            return List.of();
        }
        List<String> queryTokens = QueryTokenExtractor.extract(question);
        if (queryTokens.isEmpty()) {
            return List.of();
        }
        List<String> likeTokens = LexicalSearchTokenBudget.selectLikeTokens(
                LexicalSearchTokenBudget.normalize(queryTokens)
        );
        List<String> likePatterns = likeTokens.stream()
                .map(this::likePattern)
                .toList();
        return refKeySearchMapper.search(likePatterns, limit <= 0 ? 5 : limit);
    }

    /**
     * 构造 LIKE 匹配模式。
     *
     * @param queryToken 查询 token
     * @return LIKE 模式
     */
    private String likePattern(String queryToken) {
        return "%" + escapeLikePattern(queryToken) + "%";
    }

    /**
     * 转义 LIKE 模式中的通配符。
     *
     * @param value 原始值
     * @return 转义后的 LIKE 片段
     */
    private String escapeLikePattern(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

}
