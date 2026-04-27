package com.xbk.lattice.compiler.graph;

import com.xbk.lattice.infra.persistence.CompileJobStepJdbcRepository;
import com.xbk.lattice.infra.persistence.CompileJobStepRecord;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GraphStepLogger 测试
 *
 * 职责：验证步骤日志会生成稳定句柄，并基于 stepExecutionId 回写状态
 *
 * @author xiexu
 */
class GraphStepLoggerTests {

    /**
     * 验证步骤开始与成功更新会复用 stepExecutionId + sequenceNo。
     */
    @Test
    void shouldCreateAndUpdateStepByStepExecutionHandle() {
        CapturingCompileJobStepJdbcRepository repository = new CapturingCompileJobStepJdbcRepository();
        GraphStepLogger graphStepLogger = new GraphStepLogger(repository);
        CompileGraphState state = new CompileGraphState();
        state.setJobId("job-001");
        state.setCompileMode("full");
        state.setSourceDir("/tmp/source");
        state.setRawSourcesRef("job-001:raw_sources:v1");
        state.setGroupedSourcesRef("job-001:grouped_sources:v1");
        state.setSourceBatchesRef("job-001:source_batches:v1");
        state.setAnalyzedConceptsRef("job-001:analyzed_concepts:v1");
        state.setMergedConceptsRef("job-001:merged_concepts:v1");
        state.setEnhancementConceptsRef("job-001:enhancement_concepts:v1");
        state.setConceptsToCreateRef("job-001:concepts_to_create:v1");
        state.setDraftArticlesRef("job-001:draft_articles:v1");
        state.setReviewPartitionRef("job-001:review_partition:v1");
        state.setAcceptedArticlesRef("job-001:accepted_articles:v1");
        state.setNeedsHumanReviewArticlesRef("job-001:needs_human_review_articles:v1");
        state.setAstExtractReportRef("job-001:ast_extract_report:v1");
        state.setReviewRoute("rule-based");

        StepExecutionHandle handle = graphStepLogger.beforeStep("review_articles", state, 1_000L);
        state.setAcceptedCount(1);
        graphStepLogger.afterStep(handle, "review_articles", state, 2_000L);

        assertThat(handle).isNotNull();
        assertThat(handle.getSequenceNo()).isEqualTo(1);
        assertThat(handle.getStepExecutionId()).isNotBlank();
        assertThat(repository.createdRecord).isNotNull();
        assertThat(repository.createdRecord.getStepExecutionId()).isEqualTo(handle.getStepExecutionId());
        assertThat(repository.createdRecord.getAgentRole()).isEqualTo("ReviewerAgent");
        assertThat(repository.createdRecord.getModelRoute()).isEqualTo("rule-based");
        assertThat(repository.createdRecord.getInputSummary()).contains("rawSourcesRef=job-001:raw_sources:v1");
        assertThat(repository.createdRecord.getInputSummary()).contains("astExtractReportRef=job-001:ast_extract_report:v1");
        assertThat(repository.succeededStepExecutionId).isEqualTo(handle.getStepExecutionId());
        assertThat(repository.succeededSequenceNo).isEqualTo(handle.getSequenceNo());
        assertThat(repository.succeededOutputSummary).contains("acceptedCount=1");
        assertThat(repository.succeededOutputSummary).contains("acceptedRef=job-001:accepted_articles:v1");
        assertThat(repository.succeededOutputSummary).contains("needsHumanReviewRef=job-001:needs_human_review_articles:v1");
    }

    /**
     * 捕获调用参数的步骤仓储替身。
     *
     * @author xiexu
     */
    private static class CapturingCompileJobStepJdbcRepository extends CompileJobStepJdbcRepository {

        private CompileJobStepRecord createdRecord;

        private String succeededStepExecutionId;

        private int succeededSequenceNo;

        private String succeededOutputSummary;

        private CapturingCompileJobStepJdbcRepository() {
            super(null);
        }

        @Override
        public void createRunningStep(CompileJobStepRecord compileJobStepRecord) {
            this.createdRecord = compileJobStepRecord;
        }

        @Override
        public void markSucceeded(
                String stepExecutionId,
                int sequenceNo,
                String summary,
                String outputSummary,
                OffsetDateTime finishedAt
        ) {
            this.succeededStepExecutionId = stepExecutionId;
            this.succeededSequenceNo = sequenceNo;
            this.succeededOutputSummary = outputSummary;
        }
    }
}
