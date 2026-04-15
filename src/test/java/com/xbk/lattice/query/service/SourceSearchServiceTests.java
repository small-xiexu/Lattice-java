package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.SourceFileChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileChunkRecord;
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
        SourceSearchService sourceSearchService = new SourceSearchService(
                new FakeSourceFileChunkJdbcRepository(List.of(
                        new SourceFileChunkRecord(
                                "payment/context.md",
                                0,
                                "支付超时后，重试间隔固定为30s。",
                                true
                        )
                ))
        );

        List<QueryArticleHit> hits = sourceSearchService.search("重试间隔是多少", 5);

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

        private final List<SourceFileChunkRecord> records;

        /**
         * 创建源文件分块仓储替身。
         *
         * @param records 预置分块
         */
        private FakeSourceFileChunkJdbcRepository(List<SourceFileChunkRecord> records) {
            super(new JdbcTemplate());
            this.records = records;
        }

        /**
         * 返回预置分块记录。
         *
         * @return 分块记录列表
         */
        @Override
        public List<SourceFileChunkRecord> findAll() {
            return records;
        }
    }
}
