package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.ArticleUsageStatsMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文章使用热度统计 JDBC 仓储
 *
 * 职责：从通用检索、引用和反馈审计中刷新文章级热度统计
 *
 * @author xiexu
 */
@Repository
public class ArticleUsageStatsJdbcRepository {

    private final ArticleUsageStatsMapper articleUsageStatsMapper;

    /**
     * 创建文章使用热度统计 JDBC 仓储。
     *
     * @param articleUsageStatsMapper 文章使用热度统计 Mapper
     */
    public ArticleUsageStatsJdbcRepository(ArticleUsageStatsMapper articleUsageStatsMapper) {
        this.articleUsageStatsMapper = articleUsageStatsMapper;
    }

    /**
     * 重建文章使用热度统计。
     *
     * @param retrievalWeight 检索命中权重
     * @param citationWeight 答案引用权重
     * @param feedbackWeight 答案反馈权重
     * @param manualWeight 人工标记权重
     * @return 刷新后的统计记录数
     */
    public int rebuildStats(int retrievalWeight, int citationWeight, int feedbackWeight, int manualWeight) {
        articleUsageStatsMapper.truncateStats();
        return articleUsageStatsMapper.rebuildStats(retrievalWeight, citationWeight, feedbackWeight, manualWeight);
    }

    /**
     * 查询热度达到阈值的文章统计。
     *
     * @param heatScoreThreshold 热度阈值
     * @param limit 返回上限
     * @return 热点统计列表
     */
    public List<ArticleUsageStatsRecord> findHotspotCandidates(int heatScoreThreshold, int limit) {
        int safeLimit = limit <= 0 ? 50 : Math.min(limit, 500);
        return articleUsageStatsMapper.findHotspotCandidates(heatScoreThreshold, safeLimit);
    }

    /**
     * 查询全部统计记录。
     *
     * @param limit 返回上限
     * @return 统计列表
     */
    public List<ArticleUsageStatsRecord> findAll(int limit) {
        int safeLimit = limit <= 0 ? 50 : Math.min(limit, 500);
        return articleUsageStatsMapper.findAll(safeLimit);
    }
}
