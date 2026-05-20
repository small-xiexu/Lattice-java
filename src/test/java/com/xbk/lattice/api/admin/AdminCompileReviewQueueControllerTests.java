package com.xbk.lattice.api.admin;

import com.xbk.lattice.infra.persistence.CompileArticleReviewQueueJdbcRepository;
import com.xbk.lattice.infra.persistence.CompileArticleReviewQueueRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminCompileReviewQueueController 测试
 *
 * 职责：验证编译人工确认队列后台 API 的列表、详情与驳回动作
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.query.search.vector.enabled=false"
})
@AutoConfigureMockMvc
class AdminCompileReviewQueueControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CompileArticleReviewQueueJdbcRepository compileArticleReviewQueueJdbcRepository;

    /**
     * 验证列表与详情 API 返回 needs_human_review 草稿字段。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldExposeCompileReviewQueueListAndDetailApis() throws Exception {
        long id = preparePendingQueueRecord("job-api-list", "concept-api-list");

        mockMvc.perform(get("/api/v1/admin/compile/review-queue")
                        .param("status", "needs_human_review"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].jobId").value("job-api-list"))
                .andExpect(jsonPath("$.items[0].reviewStatus").value("needs_human_review"))
                .andExpect(jsonPath("$.items[0].reviewRoute").value("llm"));

        mockMvc.perform(get("/api/v1/admin/compile/review-queue/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.articleKey").value("source-api--concept-api-list"))
                .andExpect(jsonPath("$.content").value(containsString("review_status: needs_human_review")))
                .andExpect(jsonPath("$.reviewIssuesJson").value(containsString("GROUNDING")));
    }

    /**
     * 验证驳回 API 不写入正式 articles，仅更新队列状态。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldRejectQueueDraftWithoutPublishingArticleViaApi() throws Exception {
        long id = preparePendingQueueRecord("job-api-reject", "concept-api-reject");

        mockMvc.perform(post("/api/v1/admin/compile/review-queue/" + id + "/reject")
                        .contentType(APPLICATION_JSON)
                        .content("{"
                                + "\"reviewedBy\":\"reviewer\","
                                + "\"comment\":\"拒绝发布\","
                                + "\"expectedReviewStatus\":\"needs_human_review\""
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.reviewStatus").value("rejected"))
                .andExpect(jsonPath("$.item.reviewedBy").value("reviewer"))
                .andExpect(jsonPath("$.previousReviewStatus").value("needs_human_review"))
                .andExpect(jsonPath("$.auditId").isNumber());

        Integer articleCount = jdbcTemplate.queryForObject(
                "select count(*) from lattice.articles where article_key = 'source-api--concept-api-reject'",
                Integer.class
        );
        Integer rejectedCount = jdbcTemplate.queryForObject(
                "select count(*) from lattice.compile_article_review_queue where id = ? and review_status = 'rejected'",
                Integer.class,
                id
        );
        Integer auditCount = jdbcTemplate.queryForObject(
                "select count(*) from lattice.article_review_audits where action = 'compile_review_queue_reject'",
                Integer.class
        );

        assertThat(articleCount).isZero();
        assertThat(rejectedCount).isEqualTo(1);
        assertThat(auditCount).isEqualTo(1);
    }

    private long preparePendingQueueRecord(String jobId, String conceptId) {
        compileArticleReviewQueueJdbcRepository.list(null, 1);
        jdbcTemplate.execute("TRUNCATE TABLE lattice.compile_article_review_queue RESTART IDENTITY");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.article_review_audits RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.articles CASCADE");
        CompileArticleReviewQueueRecord queueRecord = new CompileArticleReviewQueueRecord(
                0L,
                jobId,
                null,
                "source-api",
                conceptId,
                "source-api--" + conceptId,
                "API Queue Draft",
                """
                        ---
                        title: "API Queue Draft"
                        summary: "Generic summary"
                        sources: ["docs/source.md"]
                        review_status: needs_human_review
                        ---

                        # API Queue Draft
                        """,
                "ACTIVE",
                OffsetDateTime.parse("2026-05-20T08:00:00+08:00"),
                List.of("docs/source.md"),
                "{}",
                "needs_human_review",
                "llm",
                "llm",
                "[{\"severity\":\"HIGH\",\"category\":\"GROUNDING\",\"description\":\"缺少来源\"}]",
                1,
                1,
                null,
                null,
                null,
                null,
                null,
                null
        );
        compileArticleReviewQueueJdbcRepository.upsertPending(queueRecord);
        return compileArticleReviewQueueJdbcRepository.list("needs_human_review", 1).get(0).getId();
    }
}
