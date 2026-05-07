package com.xbk.lattice.infra.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AnswerFeedbackJdbcRepository 测试
 *
 * 职责：验证答案反馈队列表和审计表的持久化能力
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
class AnswerFeedbackJdbcRepositoryTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AnswerFeedbackJdbcRepository answerFeedbackJdbcRepository;

    @Autowired
    private AnswerFeedbackAuditJdbcRepository answerFeedbackAuditJdbcRepository;

    /**
     * 验证 手动 DDL 已创建答案反馈表。
     */
    @Test
    void shouldCreateAnswerFeedbackTablesByManualDdl() {
        Integer feedbackTableCount = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from information_schema.tables
                        where table_schema = 'lattice'
                          and table_name = 'pending_answer_feedback'
                        """,
                Integer.class
        );
        Integer auditTableCount = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from information_schema.tables
                        where table_schema = 'lattice'
                          and table_name = 'answer_feedback_audits'
                        """,
                Integer.class
        );

        assertThat(feedbackTableCount).isEqualTo(1);
        assertThat(auditTableCount).isEqualTo(1);
    }

    /**
     * 验证答案反馈可保存、筛选、更新并写审计。
     */
    @Test
    void shouldSaveFilterUpdateAndAuditAnswerFeedback() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.answer_feedback_audits RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.pending_answer_feedback RESTART IDENTITY CASCADE");
        OffsetDateTime now = OffsetDateTime.parse("2026-05-05T12:00:00+08:00");

        AnswerFeedbackRecord savedRecord = answerFeedbackJdbcRepository.save(new AnswerFeedbackRecord(
                0L,
                "query-1",
                "接口是什么",
                "这是接口说明",
                "answer_problem",
                "答案混入无关内容",
                List.of("article-1"),
                List.of("docs/api.md"),
                "tester",
                "PENDING",
                "",
                null,
                null,
                now,
                now,
                "{}"
        ));
        answerFeedbackAuditJdbcRepository.save(new AnswerFeedbackAuditRecord(
                0L,
                savedRecord.getId(),
                "CREATE",
                null,
                "PENDING",
                "答案混入无关内容",
                "tester",
                now,
                "{}"
        ));

        List<AnswerFeedbackRecord> pendingRecords = answerFeedbackJdbcRepository.findAll("PENDING", 10);
        AnswerFeedbackRecord updatedRecord = answerFeedbackJdbcRepository.updateStatus(
                savedRecord.getId(),
                "RESOLVED",
                "已补回归",
                "handler",
                now.plusMinutes(5)
        );
        answerFeedbackAuditJdbcRepository.save(new AnswerFeedbackAuditRecord(
                0L,
                savedRecord.getId(),
                "RESOLVE",
                "PENDING",
                "RESOLVED",
                "已补回归",
                "handler",
                now.plusMinutes(5),
                "{}"
        ));

        assertThat(savedRecord.getId()).isGreaterThan(0L);
        assertThat(pendingRecords).hasSize(1);
        assertThat(pendingRecords.get(0).getArticleKeys()).containsExactly("article-1");
        assertThat(pendingRecords.get(0).getSourcePaths()).containsExactly("docs/api.md");
        assertThat(updatedRecord.getStatus()).isEqualTo("RESOLVED");
        assertThat(answerFeedbackJdbcRepository.countByStatus("PENDING")).isZero();
        assertThat(answerFeedbackAuditJdbcRepository.findByFeedbackId(savedRecord.getId())).hasSize(2);
    }
}
