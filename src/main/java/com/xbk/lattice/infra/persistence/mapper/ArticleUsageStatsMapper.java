package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.ArticleUsageStatsRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文章使用热度统计 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 article_usage_stats 表
 *
 * @author xiexu
 */
@Mapper
public interface ArticleUsageStatsMapper {

    /**
     * 清空文章使用热度统计。
     */
    void truncateStats();

    /**
     * 重建文章使用热度统计。
     *
     * @param retrievalWeight 检索命中权重
     * @param citationWeight 答案引用权重
     * @param feedbackWeight 答案反馈权重
     * @param manualWeight 人工标记权重
     * @return 写入记录数
     */
    int rebuildStats(
            @Param("retrievalWeight") int retrievalWeight,
            @Param("citationWeight") int citationWeight,
            @Param("feedbackWeight") int feedbackWeight,
            @Param("manualWeight") int manualWeight
    );

    /**
     * 查询热度达到阈值的文章统计。
     *
     * @param heatScoreThreshold 热度阈值
     * @param limit 返回上限
     * @return 热点统计列表
     */
    List<ArticleUsageStatsRecord> findHotspotCandidates(
            @Param("heatScoreThreshold") int heatScoreThreshold,
            @Param("limit") int limit
    );

    /**
     * 查询全部统计记录。
     *
     * @param limit 返回上限
     * @return 统计列表
     */
    List<ArticleUsageStatsRecord> findAll(@Param("limit") int limit);
}
