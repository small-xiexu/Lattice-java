package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.LexicalSearchRecord;
import com.xbk.lattice.infra.persistence.SourceFileChunkJdbcRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SourceSearchService 测试
 *
 * 职责：验证源文件分块能够被中文语义问句检索到
 *
 * @author xiexu
 */
class SourceSearchServiceTests {

    /**
     * 验证纯中文问句可以命中源文件分块中的中文事实。
     */
    @Test
    void shouldMatchSourceChunkForChineseSemanticQuestion() {
        SourceChunkFtsSearchService sourceChunkFtsSearchService = new SourceChunkFtsSearchService(
                new FakeSourceFileChunkJdbcRepository(List.of(
                        new LexicalSearchRecord(
                                null,
                                "payment/context.md#0",
                                "payment/context.md",
                                "payment/context.md",
                                "支付超时后，重试间隔固定为30s。",
                                "{\"filePath\":\"payment/context.md\",\"chunkIndex\":0,\"verbatim\":true}",
                                List.of("payment/context.md"),
                                Integer.valueOf(0),
                                Boolean.TRUE,
                                6.0D
                        )
                ))
        );

        List<QueryArticleHit> hits = sourceChunkFtsSearchService.search("重试间隔是多少", 5);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getEvidenceType()).isEqualTo(QueryEvidenceType.SOURCE);
        assertThat(hits.get(0).getSourcePaths()).containsExactly("payment/context.md");
    }

    /**
     * 源文件分块仓储替身。
     *
     * @author xiexu
     */
    private static class FakeSourceFileChunkJdbcRepository extends SourceFileChunkJdbcRepository {

        private final List<LexicalSearchRecord> records;

        /**
         * 创建源文件分块仓储替身。
         *
         * @param records 预置分块
         */
        private FakeSourceFileChunkJdbcRepository(List<LexicalSearchRecord> records) {
            super(new JdbcTemplate());
            this.records = records;
        }

        /**
         * 返回预置分块记录。
         *
         * @param question 查询问题
         * @param queryTokens 查询 token
         * @param limit 返回数量
         * @param tsConfig FTS 配置
         * @return 分块记录列表
         */
        @Override
        public List<LexicalSearchRecord> searchLexical(
                String question,
                List<String> queryTokens,
                int limit,
                String tsConfig
        ) {
            return records;
        }
    }
}
