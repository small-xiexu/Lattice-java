package com.xbk.lattice.admin.service;

import com.xbk.lattice.api.admin.AdminCompileReviewSummaryResponse;
import com.xbk.lattice.infra.persistence.CompileJobStepJdbcRepository;
import com.xbk.lattice.infra.persistence.CompileJobStepRecord;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 管理侧编译审查摘要服务测试。
 *
 * 职责：验证 job 级审查模式与实际审查路由会展示到后台摘要
 *
 * @author xiexu
 */
class AdminCompileReviewSummaryServiceTests {

    /**
     * 验证审查摘要返回请求模式和实际路由。
     */
    @Test
    void shouldExposeRequestedReviewModeAndActualReviewRoute() {
        AdminCompileReviewSummaryService summaryService = new AdminCompileReviewSummaryService(
                new StubCompileJobStepJdbcRepository(List.of(reviewStep()))
        );

        AdminCompileReviewSummaryResponse response = summaryService.resolve("job-review-mode");
        String detail = summaryService.buildStepDetail(
                response,
                AdminProcessingTaskDisplayStatus.SUCCEEDED.getCode(),
                "review_articles"
        );

        assertThat(response).isNotNull();
        assertThat(response.getRequestedReviewMode()).isEqualTo("LLM");
        assertThat(response.getReviewRoute()).isEqualTo("anthropic");
        assertThat(response.getReviewModeLabel()).isEqualTo("LLM 审查");
        assertThat(detail).isEqualTo("未发现需要修复的问题");
        assertThat(detail).doesNotContain("review_articles");
        assertThat(detail).doesNotContain("reviewMode");
        assertThat(detail).doesNotContain("model_route");
        assertThat(detail).doesNotContain("acceptedCount");
        assertThat(detail).doesNotContain("pendingReviewCount");
        assertThat(detail).doesNotContain("needsHumanReviewCount");
    }

    /**
     * 验证默认步骤详情保留用户文案而不是内部审计字段。
     */
    @Test
    void shouldBuildHumanReadableStepDetailForReviewOutcomes() {
        AdminCompileReviewSummaryService summaryService = new AdminCompileReviewSummaryService(
                new StubCompileJobStepJdbcRepository(List.of(reviewStep()))
        );
        AdminCompileReviewSummaryResponse needsHumanReviewSummary = new AdminCompileReviewSummaryResponse(
                true,
                "review_articles",
                "ReviewerAgent",
                "LLM",
                "anthropic",
                "LLM 审查",
                Integer.valueOf(0),
                Integer.valueOf(0),
                Integer.valueOf(1),
                false,
                null,
                Integer.valueOf(0),
                null,
                "未触发自动修复：无 fixable issue",
                null
        );
        AdminCompileReviewSummaryResponse fixedSummary = new AdminCompileReviewSummaryResponse(
                true,
                "review_articles",
                "ReviewerAgent",
                "LLM",
                "anthropic",
                "LLM 审查",
                Integer.valueOf(1),
                Integer.valueOf(0),
                Integer.valueOf(0),
                true,
                "fix_review_issues",
                Integer.valueOf(1),
                "openai",
                "已触发自动修复",
                null
        );
        AdminCompileReviewSummaryResponse completedSummary = new AdminCompileReviewSummaryResponse(
                true,
                "review_articles",
                "ReviewerAgent",
                "LLM",
                "anthropic",
                "LLM 审查",
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null
        );

        assertThat(summaryService.buildStepDetail(
                needsHumanReviewSummary,
                AdminProcessingTaskDisplayStatus.SUCCEEDED.getCode(),
                "review_articles"
        )).isEqualTo("质量检查后需要人工确认");
        assertThat(summaryService.buildStepDetail(
                fixedSummary,
                AdminProcessingTaskDisplayStatus.SUCCEEDED.getCode(),
                "fix_review_issues"
        )).isEqualTo("已根据检查结果完成修正");
        assertThat(summaryService.buildStepDetail(
                completedSummary,
                AdminProcessingTaskDisplayStatus.SUCCEEDED.getCode(),
                "review_articles"
        )).isEqualTo("质量检查已完成");
    }

    /**
     * 验证 RUNNING 状态下优先展示进行中语义，而非终态总结。
     */
    @Test
    void shouldShowInProgressSemanticsWhenTaskIsRunning() {
        AdminCompileReviewSummaryService summaryService = new AdminCompileReviewSummaryService(
                new StubCompileJobStepJdbcRepository(List.of(reviewStep()))
        );
        AdminCompileReviewSummaryResponse reviewOnlySummary = new AdminCompileReviewSummaryResponse(
                true,
                "review_articles",
                "ReviewerAgent",
                "LLM",
                "anthropic",
                "LLM 审查",
                Integer.valueOf(0),
                Integer.valueOf(0),
                Integer.valueOf(0),
                false,
                null,
                Integer.valueOf(0),
                null,
                "未触发自动修复：无 fixable issue",
                null
        );
        AdminCompileReviewSummaryResponse fixTriggeredSummary = new AdminCompileReviewSummaryResponse(
                true,
                "review_articles",
                "ReviewerAgent",
                "LLM",
                "anthropic",
                "LLM 审查",
                Integer.valueOf(1),
                Integer.valueOf(0),
                Integer.valueOf(0),
                true,
                "fix_review_issues",
                Integer.valueOf(1),
                "openai",
                "已触发自动修复",
                null
        );

        assertThat(summaryService.buildStepDetail(
                reviewOnlySummary,
                AdminProcessingTaskDisplayStatus.RUNNING.getCode(),
                "review_articles"
        ))
                .isEqualTo("正在检查内容质量");
        assertThat(summaryService.buildStepDetail(
                fixTriggeredSummary,
                AdminProcessingTaskDisplayStatus.RUNNING.getCode(),
                "fix_review_issues"
        ))
                .isEqualTo("已发现待修复问题，正在自动修正");
        assertThat(summaryService.buildStepDetail(
                fixTriggeredSummary,
                AdminProcessingTaskDisplayStatus.RUNNING.getCode(),
                "review_articles"
        ))
                .isEqualTo("正在检查内容质量");
    }

    private CompileJobStepRecord reviewStep() {
        return new CompileJobStepRecord(
                "job-review-mode",
                "step-1",
                "review_articles",
                "ReviewerAgent",
                "anthropic",
                1,
                "succeeded",
                "review_articles conceptCount=1, reviewMode=LLM, pendingReviewCount=0, "
                        + "acceptedCount=1, needsHumanReviewCount=0, persistedCount=0, "
                        + "fixAttemptCount=0, nothingToDo=false",
                "mode=full, reviewMode=LLM, sourceDir=/tmp/source",
                "pendingReviewCount=0, acceptedCount=1, needsHumanReviewCount=0",
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    /**
     * 固定返回步骤列表的仓储替身。
     *
     * @author xiexu
     */
    private static class StubCompileJobStepJdbcRepository extends CompileJobStepJdbcRepository {

        private final List<CompileJobStepRecord> records;

        /**
         * 创建固定步骤仓储替身。
         *
         * @param records 步骤记录
         */
        private StubCompileJobStepJdbcRepository(List<CompileJobStepRecord> records) {
            super(null);
            this.records = records;
        }

        /**
         * 返回固定步骤记录。
         *
         * @param jobId 作业标识
         * @return 步骤记录
         */
        @Override
        public List<CompileJobStepRecord> findByJobId(String jobId) {
            return records;
        }
    }
}
