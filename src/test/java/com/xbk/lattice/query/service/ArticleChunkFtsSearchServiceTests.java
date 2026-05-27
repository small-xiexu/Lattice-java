package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.ArticleChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.LexicalSearchRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Article Chunk FTS 检索服务测试
 *
 * 职责：验证 article chunk lexical 命中的身份与展示信息
 *
 * @author xiexu
 */
class ArticleChunkFtsSearchServiceTests {

    /**
     * 验证 chunk 命中会保留 chunk identity、chunkIndex 与章节锚点。
     */
    @Test
    void shouldExposeChunkIdentityAndSectionAnchor() {
        ArticleChunkFtsSearchService searchService = new ArticleChunkFtsSearchService(
                new FakeArticleChunkJdbcRepository()
        );

        List<QueryArticleHit> hits = searchService.search("Deployment Plan", 5);

        assertThat(hits).hasSize(1);
        QueryArticleHit hit = hits.get(0);
        assertThat(hit.getTitle()).isEqualTo("Release Guide / Deployment Plan");
        assertThat(hit.getMetadataJson()).contains("\"chunkIndex\":2");
        assertThat(hit.getMetadataJson()).contains("\"chunkIdentity\":\"ARTICLE_CHUNK:release-guide#2\"");
        assertThat(hit.getMetadataJson()).contains("\"sectionAnchor\":\"Deployment Plan\"");
        assertThat(hit.getMetadataJson()).contains("\"channel\":\"article_chunk_fts\"");
    }

    /**
     * 固定 article chunk lexical 结果的仓储替身。
     *
     * @author xiexu
     */
    private static class FakeArticleChunkJdbcRepository extends ArticleChunkJdbcRepository {

        /**
         * 创建仓储替身。
         */
        private FakeArticleChunkJdbcRepository() {
            super(null);
        }

        /**
         * 返回固定 lexical 命中。
         *
         * @param question 查询问题
         * @param queryTokens 查询 token
         * @param limit 返回数量
         * @param tsConfig FTS 配置
         * @return 命中列表
         */
        @Override
        public List<LexicalSearchRecord> searchLexical(
                String question,
                List<String> queryTokens,
                int limit,
                String tsConfig
        ) {
            return List.of(new LexicalSearchRecord(
                    11L,
                    "release-guide",
                    "release-concept",
                    "Release Guide",
                    "## Deployment Plan\n- execute rollout\n- verify rollback",
                    "{\"titleProfile\":{\"anchorTitle\":\"Release Guide\"}}",
                    "passed",
                    List.of("docs/release.md"),
                    Integer.valueOf(2),
                    null,
                    12.0D
            ));
        }
    }
}
