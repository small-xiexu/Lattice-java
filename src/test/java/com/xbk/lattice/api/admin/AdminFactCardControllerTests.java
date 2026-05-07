package com.xbk.lattice.api.admin;

import com.xbk.lattice.infra.persistence.FactCardJdbcRepository;
import com.xbk.lattice.infra.persistence.FactCardRecord;
import com.xbk.lattice.infra.persistence.SourceFileChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileChunkRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.evidence.domain.FactCardReviewStatus;
import com.xbk.lattice.query.evidence.domain.FactCardType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminFactCardController 测试
 *
 * 职责：验证管理侧可查看结构化证据卡统计、列表与明细
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.query.cache.store=in-memory"
})
@AutoConfigureMockMvc
class AdminFactCardControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FactCardJdbcRepository factCardJdbcRepository;

    @Autowired
    private SourceFileJdbcRepository sourceFileJdbcRepository;

    @Autowired
    private SourceFileChunkJdbcRepository sourceFileChunkJdbcRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 验证管理侧可查看 Fact Card 统计摘要。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldExposeFactCardSummary() throws Exception {
        resetFactCards();
        factCardJdbcRepository.upsert(factCard("fc:summary-valid", FactCardType.FACT_ENUM, AnswerShape.ENUM,
                FactCardReviewStatus.VALID, List.of(11L), 0.91D));
        factCardJdbcRepository.upsert(factCard("fc:summary-low", FactCardType.FACT_SEQUENCE, AnswerShape.SEQUENCE,
                FactCardReviewStatus.LOW_CONFIDENCE, List.of(), 0.42D));

        mockMvc.perform(get("/api/v1/admin/fact-cards/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.countByCardType.FACT_ENUM").value(1))
                .andExpect(jsonPath("$.countByCardType.FACT_SEQUENCE").value(1))
                .andExpect(jsonPath("$.countByReviewStatus.valid").value(1))
                .andExpect(jsonPath("$.countByReviewStatus.low_confidence").value(1))
                .andExpect(jsonPath("$.sourceReferenceMissingCount").value(1))
                .andExpect(jsonPath("$.lowConfidenceCount").value(1));
    }

    /**
     * 验证管理侧可查看 Fact Card 列表。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldExposeFactCardList() throws Exception {
        resetFactCards();
        factCardJdbcRepository.upsert(factCard("fc:list-a", FactCardType.FACT_ENUM, AnswerShape.ENUM,
                FactCardReviewStatus.VALID, List.of(21L), 0.90D));
        factCardJdbcRepository.upsert(factCard("fc:list-b", FactCardType.FACT_POLICY, AnswerShape.POLICY,
                FactCardReviewStatus.INCOMPLETE, List.of(22L), 0.70D));

        mockMvc.perform(get("/api/v1/admin/fact-cards").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.items[0].cardId").exists())
                .andExpect(jsonPath("$.items[0].cardType").exists())
                .andExpect(jsonPath("$.items[0].itemsJson").exists())
                .andExpect(jsonPath("$.items[0].sourceChunkIds").isArray());
    }

    /**
     * 验证管理侧可查看 Fact Card 明细。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldExposeFactCardDetail() throws Exception {
        resetFactCards();
        FactCardRecord saved = factCardJdbcRepository.upsert(factCard(
                "fc:detail",
                FactCardType.FACT_STATUS,
                AnswerShape.STATUS,
                FactCardReviewStatus.CONFLICT,
                List.of(31L, 32L),
                0.55D
        ));
        Long firstSourceChunkId = saved.getSourceChunkIds().get(0);

        mockMvc.perform(get("/api/v1/admin/fact-cards/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.cardId").value("fc:detail"))
                .andExpect(jsonPath("$.cardType").value("FACT_STATUS"))
                .andExpect(jsonPath("$.answerShape").value("STATUS"))
                .andExpect(jsonPath("$.sourceFilePath").value("fact-card-admin/fc-detail.md"))
                .andExpect(jsonPath("$.claim").value("测试结论"))
                .andExpect(jsonPath("$.evidenceText").value("alpha: enabled"))
                .andExpect(jsonPath("$.sourceChunkIds[0]").value(firstSourceChunkId))
                .andExpect(jsonPath("$.reviewStatus").value("conflict"));
    }

    /**
     * 清理 Fact Card 测试数据。
     */
    private void resetFactCards() {
        factCardJdbcRepository.deleteAll();
    }

    /**
     * 构造 Fact Card 测试数据。
     *
     * @param cardId 稳定标识
     * @param factCardType 卡类型
     * @param answerShape 答案形态
     * @param reviewStatus 审查状态
     * @param sourceChunkIds source chunk 主键
     * @param confidence 置信度
     * @return Fact Card 记录
     */
    private FactCardRecord factCard(
            String cardId,
            FactCardType factCardType,
            AnswerShape answerShape,
            FactCardReviewStatus reviewStatus,
            List<Long> sourceChunkIds,
            double confidence
    ) {
        SourceFileRecord sourceFileRecord = saveSourceFile(cardId);
        List<Long> effectiveSourceChunkIds = sourceChunkIds == null || sourceChunkIds.isEmpty()
                ? List.of()
                : replaceSourceChunkIds(sourceFileRecord, sourceChunkIds.size());
        return new FactCardRecord(
                cardId,
                sourceFileRecord.getSourceId(),
                sourceFileRecord.getId(),
                factCardType,
                answerShape,
                "测试结构化证据卡",
                "测试结论",
                "{\"items\":[{\"label\":\"alpha\",\"value\":\"enabled\"}]}",
                "alpha: enabled",
                effectiveSourceChunkIds,
                List.of(),
                confidence,
                reviewStatus,
                "hash-" + cardId.replace(":", "-")
        );
    }

    /**
     * 保存测试源文件。
     *
     * @param cardId 证据卡标识
     * @return 源文件记录
     */
    private SourceFileRecord saveSourceFile(String cardId) {
        return sourceFileJdbcRepository.upsert(new SourceFileRecord(
                "fact-card-admin/" + cardId.replace(":", "-") + ".md",
                "Fact Card Admin Fixture",
                "md",
                32L,
                "alpha: enabled",
                "{}",
                true,
                "fact-card-admin/" + cardId.replace(":", "-") + ".md"
        ));
    }

    /**
     * 创建 source chunk 并返回真实主键。
     *
     * @param sourceFileRecord 源文件记录
     * @param count chunk 数量
     * @return source chunk 主键
     */
    private List<Long> replaceSourceChunkIds(SourceFileRecord sourceFileRecord, int count) {
        List<SourceFileChunkRecord> chunkRecords = new java.util.ArrayList<SourceFileChunkRecord>();
        for (int index = 0; index < count; index++) {
            chunkRecords.add(new SourceFileChunkRecord(
                    sourceFileRecord.getId(),
                    sourceFileRecord.getFilePath(),
                    index,
                    "alpha: enabled",
                    true
            ));
        }
        sourceFileChunkJdbcRepository.replaceChunks(
                sourceFileRecord.getId(),
                sourceFileRecord.getFilePath(),
                chunkRecords
        );
        return jdbcTemplate.queryForList(
                """
                        select id
                        from source_file_chunks
                        where source_file_id = ?
                        order by chunk_index asc
                        """,
                Long.class,
                sourceFileRecord.getId()
        );
    }
}
