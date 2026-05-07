package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.evidence.domain.FactCardReviewStatus;
import com.xbk.lattice.query.evidence.domain.FactCardType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FactCardJdbcRepository 测试
 *
 * 职责：验证事实证据卡 schema、持久化、去重和检索能力
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key"
})
class FactCardJdbcRepositoryTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SourceFileJdbcRepository sourceFileJdbcRepository;

    @Autowired
    private SourceFileChunkJdbcRepository sourceFileChunkJdbcRepository;

    @Autowired
    private FactCardJdbcRepository factCardJdbcRepository;

    @Autowired
    private FactCardVectorJdbcRepository factCardVectorJdbcRepository;

    /**
     * 验证 手动 DDL 已创建事实证据卡主表。
     */
    @Test
    void shouldCreateFactCardsTableByManualDdl() {
        Integer count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from information_schema.tables
                        where table_schema = 'lattice'
                          and table_name = 'fact_cards'
                        """,
                Integer.class
        );

        assertThat(count).isEqualTo(1);
    }

    /**
     * 验证事实证据卡表包含最小必要索引与唯一约束。
     */
    @Test
    void shouldCreateFactCardIndexesByManualDdl() {
        List<String> indexNames = jdbcTemplate.queryForList(
                """
                        select indexname
                        from pg_indexes
                        where schemaname = 'lattice'
                          and tablename = 'fact_cards'
                        order by indexname asc
                        """,
                String.class
        );

        assertThat(indexNames)
                .contains("uk_fact_cards_card_id")
                .contains("idx_fact_cards_source_file_id")
                .contains("idx_fact_cards_card_type")
                .contains("idx_fact_cards_answer_shape")
                .contains("idx_fact_cards_review_status")
                .contains("idx_fact_cards_content_hash")
                .contains("idx_fact_cards_search_tsv");
    }

    /**
     * 验证事实证据卡可保存并按稳定标识读取。
     */
    @Test
    void shouldSaveAndLoadFactCardRecord() {
        resetFactCards();
        FactCardRecord factCardRecord = sampleFactCard("fc:save-load", "hash-save-load");

        FactCardRecord saved = factCardJdbcRepository.upsert(factCardRecord);
        Optional<FactCardRecord> loaded = factCardJdbcRepository.findByCardId("fc:save-load");

        assertThat(saved.getId()).isNotNull();
        assertThat(loaded).isPresent();
        assertThat(loaded.orElseThrow().getCardType()).isEqualTo(FactCardType.FACT_ENUM);
        assertThat(loaded.orElseThrow().getAnswerShape()).isEqualTo(AnswerShape.ENUM);
        assertThat(loaded.orElseThrow().getItemsJson()).contains("retry");
        assertThat(loaded.orElseThrow().getEvidenceText()).contains("retry=3");
        assertThat(loaded.orElseThrow().getSourceChunkIds()).hasSize(1);
        assertThat(loaded.orElseThrow().getConfidence()).isEqualTo(0.92D);
        assertThat(loaded.orElseThrow().getReviewStatus()).isEqualTo(FactCardReviewStatus.VALID);
    }

    /**
     * 验证重复 card_id 会更新原记录而不是插入重复卡。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldUpdateExistingFactCardByCardId() throws Exception {
        resetFactCards();
        FactCardRecord original = sampleFactCard("fc:upsert", "hash-original");
        FactCardRecord firstSaved = factCardJdbcRepository.upsert(original);

        Thread.sleep(10L);
        FactCardRecord updated = new FactCardRecord(
                "fc:upsert",
                original.getSourceId(),
                original.getSourceFileId(),
                FactCardType.FACT_COMPARE,
                AnswerShape.COMPARE,
                "重试策略对照",
                "重试策略从 2 次调整为 3 次。",
                "{\"items\":[{\"before\":\"2\",\"after\":\"3\"}]}",
                "retry before=2 after=3",
                original.getSourceChunkIds(),
                List.of(),
                0.88D,
                FactCardReviewStatus.INCOMPLETE,
                "hash-updated"
        );

        FactCardRecord secondSaved = factCardJdbcRepository.upsert(updated);

        assertThat(secondSaved.getId()).isEqualTo(firstSaved.getId());
        assertThat(secondSaved.getCreatedAt()).isEqualTo(firstSaved.getCreatedAt());
        assertThat(secondSaved.getUpdatedAt()).isAfter(firstSaved.getUpdatedAt());
        assertThat(factCardJdbcRepository.countAll()).isEqualTo(1);
        assertThat(factCardJdbcRepository.findByCardId("fc:upsert").orElseThrow().getCardType())
                .isEqualTo(FactCardType.FACT_COMPARE);
    }

    /**
     * 验证可按源文件删除全部事实证据卡。
     */
    @Test
    void shouldDeleteFactCardsBySourceFileId() {
        resetFactCards();
        FactCardRecord factCardRecord = sampleFactCard("fc:delete-source", "hash-delete-source");
        FactCardRecord saved = factCardJdbcRepository.upsert(factCardRecord);

        int deleted = factCardJdbcRepository.deleteBySourceFileId(saved.getSourceFileId());

        assertThat(deleted).isEqualTo(1);
        assertThat(factCardJdbcRepository.findByCardId("fc:delete-source")).isEmpty();
    }

    /**
     * 验证可按事实卡类型统计数量。
     */
    @Test
    void shouldCountFactCardsByCardType() {
        resetFactCards();
        factCardJdbcRepository.upsert(sampleFactCard("fc:count-enum", "hash-count-enum"));
        FactCardRecord sequenceCard = new FactCardRecord(
                "fc:count-sequence",
                null,
                null,
                FactCardType.FACT_SEQUENCE,
                AnswerShape.SEQUENCE,
                "处理步骤",
                "步骤需要按顺序执行。",
                "{\"steps\":[1,2]}",
                "1. upload\n2. compile",
                List.of(),
                List.of(),
                0.70D,
                FactCardReviewStatus.LOW_CONFIDENCE,
                "hash-count-sequence"
        );
        factCardJdbcRepository.upsert(sequenceCard);

        Map<FactCardType, Integer> counts = factCardJdbcRepository.countByCardType();

        assertThat(counts.get(FactCardType.FACT_ENUM)).isGreaterThanOrEqualTo(1);
        assertThat(counts.get(FactCardType.FACT_SEQUENCE)).isGreaterThanOrEqualTo(1);
    }

    /**
     * 验证 fact card 可通过数据库侧 lexical 检索命中结构化字段与原文证据。
     */
    @Test
    void shouldSearchFactCardsByLexicalIndex() {
        resetFactCards();
        factCardJdbcRepository.upsert(sampleFactCard("fc:search", "hash-search"));

        List<LexicalSearchRecord> hits = factCardJdbcRepository.searchLexical(
                "retry interval",
                List.of("retry", "interval"),
                5,
                "simple"
        );

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).getItemKey()).isEqualTo("fc:search");
        assertThat(hits.get(0).getContent()).contains("retry");
        assertThat(hits.get(0).getMetadataJson()).contains("sourceChunkIds");
        assertThat(hits.get(0).getReviewStatus()).isEqualTo("valid");
        assertThat(hits.get(0).getSourcePaths()).containsExactly("fact-card/manual.md");
    }

    /**
     * 验证 fact card 向量表在 pgvector 启用时可写入读取，未启用时仓储会跳过。
     */
    @Test
    void shouldSaveAndLoadFactCardVectorWhenPgvectorEnabled() {
        resetFactCards();
        FactCardRecord savedCard = factCardJdbcRepository.upsert(sampleFactCard("fc:vector", "hash-vector-card"));
        if (!factCardVectorTableExists()) {
            factCardVectorJdbcRepository.upsert(new FactCardVectorRecord(
                    savedCard.getId(),
                    savedCard.getCardId(),
                    savedCard.getCardType(),
                    savedCard.getAnswerShape(),
                    1L,
                    3,
                    "test",
                    "hash-vector",
                    new float[] {0.1F, 0.2F, 0.3F},
                    OffsetDateTime.now()
            ));
            assertThat(factCardVectorJdbcRepository.countAll()).isZero();
            return;
        }

        Long modelProfileId = ensureEmbeddingModelProfile();
        factCardVectorJdbcRepository.alignEmbeddingColumnDimensions(3);
        FactCardVectorRecord vectorRecord = new FactCardVectorRecord(
                savedCard.getId(),
                savedCard.getCardId(),
                savedCard.getCardType(),
                savedCard.getAnswerShape(),
                modelProfileId,
                3,
                "test",
                "hash-vector",
                new float[] {0.1F, 0.2F, 0.3F},
                OffsetDateTime.now()
        );

        factCardVectorJdbcRepository.upsert(vectorRecord);
        Optional<FactCardVectorRecord> loaded = factCardVectorJdbcRepository.findByFactCardId(savedCard.getId());

        assertThat(loaded).isPresent();
        assertThat(loaded.orElseThrow().getCardId()).isEqualTo(savedCard.getCardId());
        assertThat(loaded.orElseThrow().getEmbedding()).containsExactly(0.1F, 0.2F, 0.3F);
        assertThat(factCardVectorJdbcRepository.countAll()).isGreaterThanOrEqualTo(1);
        assertThat(factCardVectorJdbcRepository.findEmbeddingColumnType()).isPresent();
    }

    /**
     * 清理事实证据卡测试数据。
     */
    private void resetFactCards() {
        factCardJdbcRepository.deleteAll();
    }

    /**
     * 构造测试用事实证据卡。
     *
     * @param cardId 证据卡稳定标识
     * @param contentHash 内容哈希
     * @return 事实证据卡记录
     */
    private FactCardRecord sampleFactCard(String cardId, String contentHash) {
        SourceFileRecord sourceFileRecord = sourceFileJdbcRepository.upsert(new SourceFileRecord(
                "fact-card/manual.md",
                "Fact Card Manual",
                "md",
                64L,
                """
                        # Fact Card Manual

                        - retry=3
                        - interval=30s
                        """,
                "{}",
                true,
                "fact-card/manual.md"
        ));
        sourceFileChunkJdbcRepository.replaceChunks(
                sourceFileRecord.getId(),
                sourceFileRecord.getFilePath(),
                List.of(new SourceFileChunkRecord(
                        sourceFileRecord.getId(),
                        sourceFileRecord.getFilePath(),
                        0,
                        "retry=3\ninterval=30s",
                        true
                ))
        );
        Long chunkId = jdbcTemplate.queryForObject(
                """
                        select id
                        from source_file_chunks
                        where source_file_id = ?
                          and chunk_index = 0
                        """,
                Long.class,
                sourceFileRecord.getId()
        );
        return new FactCardRecord(
                cardId,
                sourceFileRecord.getSourceId(),
                sourceFileRecord.getId(),
                FactCardType.FACT_ENUM,
                AnswerShape.ENUM,
                "重试参数枚举",
                "重试次数为 3，间隔为 30s。",
                "{\"items\":[{\"name\":\"retry\",\"value\":\"3\"},{\"name\":\"interval\",\"value\":\"30s\"}]}",
                "retry=3\ninterval=30s",
                List.of(chunkId),
                List.of(),
                0.92D,
                FactCardReviewStatus.VALID,
                contentHash
        );
    }

    /**
     * 判断 fact card 向量索引表是否存在。
     *
     * @return 向量索引表是否存在
     */
    private boolean factCardVectorTableExists() {
        Integer count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from information_schema.tables
                        where table_schema = 'lattice'
                          and table_name = 'fact_card_vector_index'
                        """,
                Integer.class
        );
        return count != null && count.intValue() > 0;
    }

    /**
     * 确保测试用 embedding 模型配置存在。
     *
     * @return 模型配置主键
     */
    private Long ensureEmbeddingModelProfile() {
        jdbcTemplate.update(
                """
                        insert into llm_provider_connections (
                            connection_code, provider_type, base_url, api_key_ciphertext, api_key_mask
                        )
                        values ('fact-card-test-openai', 'OPENAI', 'https://api.openai.com', 'test', 'test')
                        on conflict (connection_code) do nothing
                        """
        );
        Long connectionId = jdbcTemplate.queryForObject(
                "select id from llm_provider_connections where connection_code = 'fact-card-test-openai'",
                Long.class
        );
        jdbcTemplate.update(
                """
                        insert into llm_model_profiles (
                            model_code, connection_id, model_name, model_kind, expected_dimensions
                        )
                        values ('fact-card-test-embedding', ?, 'text-embedding-test', 'EMBEDDING', 3)
                        on conflict (model_code) do nothing
                        """,
                connectionId
        );
        return jdbcTemplate.queryForObject(
                "select id from llm_model_profiles where model_code = 'fact-card-test-embedding'",
                Long.class
        );
    }
}
