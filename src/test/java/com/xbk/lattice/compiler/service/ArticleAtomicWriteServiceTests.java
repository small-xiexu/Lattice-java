package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.graph.ArticleReviewEnvelope;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ArticleAtomicWriteService 测试
 *
 * 职责：验证编译图文章写入事务入口的调用边界
 *
 * @author xiexu
 */
class ArticleAtomicWriteServiceTests {

    /**
     * 验证原子写入入口声明事务。
     *
     * @throws NoSuchMethodException 方法不存在时抛出
     */
    @Test
    void shouldDeclareTransactionalBoundary() throws NoSuchMethodException {
        Method method = ArticleAtomicWriteService.class.getDeclaredMethod(
                "persistArticlesAtomic",
                String.class,
                List.class,
                Long.class,
                String.class,
                Map.class
        );

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.rollbackFor()).contains(Exception.class);
    }

    /**
     * 验证文章与分块按同一入口顺序写入。
     */
    @Test
    void shouldPersistArticlesAndRebuildChunksThroughSingleEntry() {
        RecordingArticlePersistSupport articlePersistSupport = new RecordingArticlePersistSupport(false);
        ArticleAtomicWriteService articleAtomicWriteService = new ArticleAtomicWriteService(articlePersistSupport);

        int persistedCount = articleAtomicWriteService.persistArticlesAtomic(
                "job-1",
                List.of(reviewEnvelope("payment-timeout")),
                1L,
                "source",
                Map.of("docs/payment.md", 10L)
        );

        assertThat(persistedCount).isEqualTo(1);
        assertThat(articlePersistSupport.getOperations()).containsExactly("persist", "rebuild");
    }

    /**
     * 验证分块重建失败会阻断整个写入入口。
     */
    @Test
    void shouldPropagateChunkRebuildFailure() {
        RecordingArticlePersistSupport articlePersistSupport = new RecordingArticlePersistSupport(true);
        ArticleAtomicWriteService articleAtomicWriteService = new ArticleAtomicWriteService(articlePersistSupport);

        assertThatThrownBy(() -> articleAtomicWriteService.persistArticlesAtomic(
                "job-1",
                List.of(reviewEnvelope("payment-timeout")),
                1L,
                "source",
                Map.of("docs/payment.md", 10L)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("chunk rebuild failed");
        assertThat(articlePersistSupport.getOperations()).containsExactly("persist", "rebuild");
    }

    private static ArticleReviewEnvelope reviewEnvelope(String conceptId) {
        ArticleReviewEnvelope articleReviewEnvelope = new ArticleReviewEnvelope();
        articleReviewEnvelope.setArticle(new ArticleRecord(
                conceptId,
                "Payment Timeout",
                "# Payment Timeout\n\nretry=3",
                "ACTIVE",
                OffsetDateTime.now(),
                List.of("docs/payment.md"),
                "{}"
        ));
        return articleReviewEnvelope;
    }

    /**
     * 记录文章写入支撑服务调用顺序。
     *
     * 职责：为原子写入服务提供无数据库依赖的替身
     *
     * @author xiexu
     */
    private static class RecordingArticlePersistSupport extends ArticlePersistSupport {

        private final boolean failOnRebuild;

        private final List<String> operations = new ArrayList<String>();

        private RecordingArticlePersistSupport(boolean failOnRebuild) {
            super(null, null, null, (CompilationWalStore) null, null, null, null);
            this.failOnRebuild = failOnRebuild;
        }

        /**
         * 记录文章落库调用。
         *
         * @param jobId 作业标识
         * @param reviewedArticles 审查后文章集合
         * @param sourceId 资料源主键
         * @param sourceCode 资料源编码
         * @param sourceFileIdsByPath 源文件主键映射
         * @return 已落库文章数
         */
        @Override
        public int persistArticles(
                String jobId,
                List<ArticleReviewEnvelope> reviewedArticles,
                Long sourceId,
                String sourceCode,
                Map<String, Long> sourceFileIdsByPath
        ) {
            operations.add("persist");
            return reviewedArticles == null ? 0 : reviewedArticles.size();
        }

        /**
         * 记录 chunk 重建调用。
         *
         * @param reviewedArticles 已落库文章集合
         */
        @Override
        public void rebuildArticleChunks(List<ArticleReviewEnvelope> reviewedArticles) {
            operations.add("rebuild");
            if (failOnRebuild) {
                throw new IllegalStateException("chunk rebuild failed");
            }
        }

        private List<String> getOperations() {
            return operations;
        }
    }
}
