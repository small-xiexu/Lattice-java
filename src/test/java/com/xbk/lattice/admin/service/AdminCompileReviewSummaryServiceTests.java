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
        String detail = summaryService.buildStepDetail(response);

        assertThat(response).isNotNull();
        assertThat(response.getRequestedReviewMode()).isEqualTo("LLM");
        assertThat(response.getReviewRoute()).isEqualTo("anthropic");
        assertThat(response.getReviewModeLabel()).isEqualTo("LLM 审查");
        assertThat(detail).contains("reviewMode=LLM");
        assertThat(detail).contains("model_route=anthropic");
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
