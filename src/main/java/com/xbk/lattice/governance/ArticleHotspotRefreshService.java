package com.xbk.lattice.governance;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleUsageStatsJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleUsageStatsRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 文章热点刷新服务
 *
 * 职责：基于通用命中、引用和反馈信号生成热点待抽检队列
 *
 * @author xiexu
 */
@Service
public class ArticleHotspotRefreshService {

    public static final int DEFAULT_HEAT_SCORE_THRESHOLD = 3;

    private static final int DEFAULT_LIMIT = 200;

    private static final int RETRIEVAL_WEIGHT = 1;

    private static final int CITATION_WEIGHT = 2;

    private static final int FEEDBACK_WEIGHT = 3;

    private static final int MANUAL_WEIGHT = 3;

    private static final String RISK_REASON_HOTSPOT_UNVERIFIED = "hotspot_unverified";

    private final ArticleUsageStatsJdbcRepository articleUsageStatsJdbcRepository;

    private final ArticleJdbcRepository articleJdbcRepository;

    /**
     * 创建文章热点刷新服务。
     *
     * @param articleUsageStatsJdbcRepository 文章使用统计仓储
     * @param articleJdbcRepository 文章仓储
     */
    public ArticleHotspotRefreshService(
            ArticleUsageStatsJdbcRepository articleUsageStatsJdbcRepository,
            ArticleJdbcRepository articleJdbcRepository
    ) {
        this.articleUsageStatsJdbcRepository = articleUsageStatsJdbcRepository;
        this.articleJdbcRepository = articleJdbcRepository;
    }

    /**
     * 按默认阈值刷新热点待抽检队列。
     *
     * @return 刷新结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ArticleHotspotRefreshResult refresh() {
        return refresh(DEFAULT_HEAT_SCORE_THRESHOLD, DEFAULT_LIMIT);
    }

    /**
     * 按指定阈值刷新热点待抽检队列。
     *
     * @param heatScoreThreshold 热度阈值
     * @param limit 最大候选数
     * @return 刷新结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ArticleHotspotRefreshResult refresh(int heatScoreThreshold, int limit) {
        int safeThreshold = heatScoreThreshold <= 0 ? DEFAULT_HEAT_SCORE_THRESHOLD : heatScoreThreshold;
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, DEFAULT_LIMIT);
        int rebuiltStatsCount = articleUsageStatsJdbcRepository.rebuildStats(
                RETRIEVAL_WEIGHT,
                CITATION_WEIGHT,
                FEEDBACK_WEIGHT,
                MANUAL_WEIGHT
        );
        List<ArticleUsageStatsRecord> candidates = articleUsageStatsJdbcRepository.findHotspotCandidates(
                safeThreshold,
                safeLimit
        );
        int updatedArticleCount = articleJdbcRepository.markHotspotPendingVerification(
                collectArticleKeys(candidates),
                RISK_REASON_HOTSPOT_UNVERIFIED
        );
        return new ArticleHotspotRefreshResult(
                rebuiltStatsCount,
                candidates.size(),
                updatedArticleCount,
                safeThreshold,
                candidates
        );
    }

    /**
     * 收集热点候选文章唯一键。
     *
     * @param candidates 热点候选
     * @return 文章唯一键
     */
    private List<String> collectArticleKeys(List<ArticleUsageStatsRecord> candidates) {
        List<String> articleKeys = new ArrayList<String>();
        for (ArticleUsageStatsRecord candidate : candidates) {
            if (candidate == null || candidate.getArticleKey() == null || candidate.getArticleKey().isBlank()) {
                continue;
            }
            articleKeys.add(candidate.getArticleKey());
        }
        return articleKeys;
    }
}
