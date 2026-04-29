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
     * 验证 source chunk 命中后会补充同文件邻近 chunk，避免表格证据被 chunk 边界截断。
     */
    @Test
    void shouldAppendNeighborSourceChunksForContext() {
        LexicalSearchRecord anchorRecord = new LexicalSearchRecord(
                null,
                "manual.pdf#8",
                "manual.pdf",
                "manual.pdf",
                "=== Table: page 3 block 1 ===\ntable_row: Risk | Impact | Mitigation",
                "{\"filePath\":\"manual.pdf\",\"chunkIndex\":8,\"verbatim\":true}",
                List.of("manual.pdf"),
                Integer.valueOf(8),
                Boolean.TRUE,
                6.0D
        );
        LexicalSearchRecord neighborRecord = new LexicalSearchRecord(
                null,
                "manual.pdf#9",
                "manual.pdf",
                "manual.pdf",
                "table_row: Capacity | Latency | Throttle writes",
                "{\"filePath\":\"manual.pdf\",\"chunkIndex\":9,\"verbatim\":true}",
                List.of("manual.pdf"),
                Integer.valueOf(9),
                Boolean.TRUE,
                1.0D
        );
        SourceChunkFtsSearchService sourceChunkFtsSearchService = new SourceChunkFtsSearchService(
                new FakeSourceFileChunkJdbcRepository(List.of(anchorRecord), List.of(neighborRecord))
        );

        List<QueryArticleHit> hits = sourceChunkFtsSearchService.search("表格有哪些风险", 5);

        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).getContent()).contains("Risk | Impact | Mitigation");
        assertThat(hits.get(1).getContent()).contains("Capacity | Latency | Throttle writes");
    }

    /**
     * 源文件分块仓储替身。
     *
     * @author xiexu
     */
    private static class FakeSourceFileChunkJdbcRepository extends SourceFileChunkJdbcRepository {

        private final List<LexicalSearchRecord> records;

        private final List<LexicalSearchRecord> neighborRecords;

        /**
         * 创建源文件分块仓储替身。
         *
         * @param records 预置分块
         */
        private FakeSourceFileChunkJdbcRepository(List<LexicalSearchRecord> records) {
            this(records, List.of());
        }

        /**
         * 创建源文件分块仓储替身。
         *
         * @param records 预置分块
         * @param neighborRecords 预置邻近分块
         */
        private FakeSourceFileChunkJdbcRepository(
                List<LexicalSearchRecord> records,
                List<LexicalSearchRecord> neighborRecords
        ) {
            super(new JdbcTemplate());
            this.records = records;
            this.neighborRecords = neighborRecords;
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

        /**
         * 返回预置邻近分块记录。
         *
         * @param filePath 文件路径
         * @param chunkIndex 当前 chunk 序号
         * @param radius 邻近半径
         * @param limit 返回数量
         * @return 邻近分块记录
         */
        @Override
        public List<LexicalSearchRecord> findNeighborChunks(String filePath, int chunkIndex, int radius, int limit) {
            return neighborRecords;
        }
    }
}
