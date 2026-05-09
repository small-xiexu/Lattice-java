package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.graph.ArticleReviewEnvelope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 文章原子写入服务
 *
 * 职责：在编译图写节点中把文章主表、来源关联与文章分块写入同一数据库事务
 *
 * @author xiexu
 */
@Service
public class ArticleAtomicWriteService {

    private final ArticlePersistSupport articlePersistSupport;

    /**
     * 创建文章原子写入服务。
     *
     * @param articlePersistSupport 文章落库支撑服务
     */
    public ArticleAtomicWriteService(ArticlePersistSupport articlePersistSupport) {
        this.articlePersistSupport = articlePersistSupport;
    }

    /**
     * 原子写入文章、来源关联与文章分块。
     *
     * @param jobId 作业标识
     * @param reviewedArticles 审查后文章集合
     * @return 已落库文章数
     */
    @Transactional(rollbackFor = Exception.class)
    public int persistArticlesAtomic(String jobId, List<ArticleReviewEnvelope> reviewedArticles) {
        return persistArticlesAtomic(
                jobId,
                reviewedArticles,
                null,
                null,
                Collections.<String, Long>emptyMap()
        );
    }

    /**
     * 原子写入文章、来源关联与文章分块。
     *
     * @param jobId 作业标识
     * @param reviewedArticles 审查后文章集合
     * @param sourceId 资料源主键
     * @param sourceCode 资料源编码
     * @param sourceFileIdsByPath 源文件主键映射
     * @return 已落库文章数
     */
    @Transactional(rollbackFor = Exception.class)
    public int persistArticlesAtomic(
            String jobId,
            List<ArticleReviewEnvelope> reviewedArticles,
            Long sourceId,
            String sourceCode,
            Map<String, Long> sourceFileIdsByPath
    ) {
        int persistedCount = articlePersistSupport.persistArticles(
                jobId,
                reviewedArticles,
                sourceId,
                sourceCode,
                sourceFileIdsByPath
        );
        if (persistedCount > 0) {
            articlePersistSupport.rebuildArticleChunks(reviewedArticles);
        }
        return persistedCount;
    }
}
