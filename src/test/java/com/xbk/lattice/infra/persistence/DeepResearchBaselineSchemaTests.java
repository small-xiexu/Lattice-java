package com.xbk.lattice.infra.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deep Research baseline schema 测试
 *
 * 职责：验证 v2.6 baseline DDL 的建表结果与关键约束
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
class DeepResearchBaselineSchemaTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 验证 v2.6 baseline DDL 已创建 Deep Research 与 Citation 所需表。
     */
    @Test
    void shouldCreateV26DeepResearchTablesByManualDdl() {
        List<String> tableNames = jdbcTemplate.queryForList(
                """
                        select table_name
                        from information_schema.tables
                        where table_schema = 'lattice'
                          and table_name in (
                              'deep_research_runs',
                              'deep_research_tasks',
                              'deep_research_task_hits',
                              'deep_research_findings',
                              'deep_research_evidence_anchors',
                              'deep_research_evidence_anchor_validations',
                              'deep_research_answer_projections',
                              'query_answer_audits',
                              'query_answer_claims',
                              'query_answer_citations'
                          )
                        order by table_name
                        """,
                String.class
        );

        assertThat(tableNames)
                .contains("deep_research_runs")
                .contains("deep_research_tasks")
                .contains("deep_research_task_hits")
                .contains("deep_research_findings")
                .contains("deep_research_evidence_anchors")
                .contains("deep_research_evidence_anchor_validations")
                .contains("deep_research_answer_projections")
                .contains("query_answer_audits")
                .contains("query_answer_claims")
                .contains("query_answer_citations");
    }

    /**
     * 验证 final audit 与 ACTIVE projection literal 的同 run 约束可生效。
     */
    @Test
    void shouldRejectCrossRunAuditBindingAndDuplicateActiveProjectionLiteral() {
        Long runId1 = insertRun("dr-schema-q1");
        Long runId2 = insertRun("dr-schema-q2");
        Long auditId1 = insertAudit("dr-schema-q1", 1, runId1);

        bindFinalAudit(runId1, auditId1);

        assertThatThrownBy(() -> bindFinalAudit(runId2, auditId1))
                .isInstanceOf(DataIntegrityViolationException.class);

        insertTask(runId1, "task-1");
        insertTask(runId1, "task-2");
        insertArticleAnchor(runId1, "task-1", "ev#1", "article-a", "chunk-a", "quote-a", "hash-a");
        insertArticleAnchor(runId1, "task-2", "ev#2", "article-b", "chunk-b", "quote-b", "hash-b");
        insertProjection(runId1, auditId1, 1, "ev#1", "[[payment-routing]]", "article-a", "ACTIVE", 0, null);
        insertProjection(runId1, auditId1, 2, "ev#2", "[[payment-routing]]", "article-b", "REPLACED", 1, 1);

        assertThatThrownBy(() -> insertProjection(
                runId1,
                auditId1,
                3,
                "ev#2",
                "[[payment-routing]]",
                "article-b",
                "ACTIVE",
                2,
                2
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * 验证 SOURCE_FILE 锚点的字段组合与行号配对约束会拒绝非法输入。
     */
    @Test
    void shouldRejectInvalidSourceFileAnchorCombinations() {
        Long runId = insertRun("dr-anchor-q1");
        insertTask(runId, "task-source-file");

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                        insert into deep_research_evidence_anchors (
                            anchor_id, run_id, task_id, source_type, source_id, chunk_id,
                            path, line_start, line_end, quote_text, retrieval_score, content_hash
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                "ev#sf-1",
                runId,
                "task-source-file",
                "SOURCE_FILE",
                "docs/payment.md",
                null,
                "docs/other.md",
                Integer.valueOf(10),
                Integer.valueOf(12),
                "支付路由配置",
                Double.valueOf(0.8D),
                "hash-source-file-path-mismatch"
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                        insert into deep_research_evidence_anchors (
                            anchor_id, run_id, task_id, source_type, source_id, chunk_id,
                            path, line_start, line_end, quote_text, retrieval_score, content_hash
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                "ev#sf-2",
                runId,
                "task-source-file",
                "SOURCE_FILE",
                "docs/payment.md",
                null,
                "docs/payment.md",
                Integer.valueOf(10),
                null,
                "支付路由配置",
                Double.valueOf(0.8D),
                "hash-source-file-line-pair"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * 插入最小 Deep Research run 记录。
     *
     * @param queryId 查询标识
     * @return run 主键
     */
    private Long insertRun(String queryId) {
        return jdbcTemplate.queryForObject(
                """
                        insert into deep_research_runs (
                            query_id, question, route_reason, plan_json, layer_count, task_count,
                            llm_call_count, citation_coverage, partial_answer, has_conflicts
                        )
                        values (?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)
                        returning run_id
                        """,
                Long.class,
                queryId,
                "问题-" + queryId,
                "complexity_gate",
                "{\"layers\":[]}",
                Integer.valueOf(1),
                Integer.valueOf(2),
                Integer.valueOf(0),
                Double.valueOf(0.0D),
                Boolean.FALSE,
                Boolean.FALSE
        );
    }

    /**
     * 插入最小答案审计记录。
     *
     * @param queryId 查询标识
     * @param answerVersion 答案版本
     * @param deepResearchRunId Deep Research run 主键
     * @return audit 主键
     */
    private Long insertAudit(String queryId, int answerVersion, Long deepResearchRunId) {
        return jdbcTemplate.queryForObject(
                """
                        insert into query_answer_audits (
                            query_id, answer_version, question, answer_markdown, answer_outcome,
                            generation_mode, review_status, citation_coverage, unsupported_claim_count,
                            verified_citation_count, demoted_citation_count, skipped_citation_count,
                            cacheable, route_type, model_snapshot_json, deep_research_run_id
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                        returning audit_id
                        """,
                Long.class,
                queryId,
                Integer.valueOf(answerVersion),
                "问题-" + queryId,
                "答案 [[payment-routing]]",
                "SUCCESS",
                "LLM",
                null,
                Double.valueOf(1.0D),
                Integer.valueOf(0),
                Integer.valueOf(1),
                Integer.valueOf(0),
                Integer.valueOf(0),
                Boolean.FALSE,
                "deep_research",
                "{}",
                deepResearchRunId
        );
    }

    /**
     * 绑定 run 的 final answer audit。
     *
     * @param runId run 主键
     * @param auditId audit 主键
     */
    private void bindFinalAudit(Long runId, Long auditId) {
        jdbcTemplate.update(
                "update deep_research_runs set final_answer_audit_id = ? where run_id = ?",
                auditId,
                runId
        );
    }

    /**
     * 插入最小任务记录。
     *
     * @param runId run 主键
     * @param taskId 任务标识
     */
    private void insertTask(Long runId, String taskId) {
        jdbcTemplate.update(
                """
                        insert into deep_research_tasks (
                            task_id, run_id, layer_index, task_type, question,
                            expected_fact_schema_json, preferred_upstream_task_ids_json, status,
                            llm_call_count, timed_out
                        )
                        values (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)
                        """,
                taskId,
                runId,
                Integer.valueOf(0),
                "FACT_SLOT",
                "请确认支付路由配置",
                "[\"maxAttempts\"]",
                "[]",
                "SUCCEEDED",
                Integer.valueOf(1),
                Boolean.FALSE
        );
    }

    /**
     * 插入 ARTICLE 类型锚点。
     *
     * @param runId run 主键
     * @param taskId 任务标识
     * @param anchorId 锚点标识
     * @param articleKey 文章键
     * @param chunkId chunk 标识
     * @param quoteText 摘录文本
     * @param contentHash 内容哈希
     */
    private void insertArticleAnchor(
            Long runId,
            String taskId,
            String anchorId,
            String articleKey,
            String chunkId,
            String quoteText,
            String contentHash
    ) {
        jdbcTemplate.update(
                """
                        insert into deep_research_evidence_anchors (
                            anchor_id, run_id, task_id, source_type, source_id, chunk_id,
                            path, line_start, line_end, quote_text, retrieval_score, content_hash
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                anchorId,
                runId,
                taskId,
                "ARTICLE",
                articleKey,
                chunkId,
                null,
                null,
                null,
                quoteText,
                Double.valueOf(0.9D),
                contentHash
        );
    }

    /**
     * 插入最终出站 projection。
     *
     * @param runId run 主键
     * @param answerAuditId audit 主键
     * @param projectionOrdinal 投影顺序号
     * @param anchorId 锚点标识
     * @param citationLiteral 最终字面量
     * @param targetKey 最终目标键
     * @param status 投影状态
     * @param repairRound repair 轮次
     * @param repairedFromProjectionOrdinal 回指的历史 projection
     */
    private void insertProjection(
            Long runId,
            Long answerAuditId,
            int projectionOrdinal,
            String anchorId,
            String citationLiteral,
            String targetKey,
            String status,
            int repairRound,
            Integer repairedFromProjectionOrdinal
    ) {
        jdbcTemplate.update(
                """
                        insert into deep_research_answer_projections (
                            run_id, answer_audit_id, projection_ordinal, anchor_id, citation_literal,
                            source_type, target_key, status, repair_round, repaired_from_projection_ordinal
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                runId,
                answerAuditId,
                Integer.valueOf(projectionOrdinal),
                anchorId,
                citationLiteral,
                "ARTICLE",
                targetKey,
                status,
                Integer.valueOf(repairRound),
                repairedFromProjectionOrdinal
        );
    }
}
