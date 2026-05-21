package com.xbk.lattice.admin.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AdminProcessingTaskPresentationResolver 测试
 *
 * 职责：验证 source-run / processing-tasks 在人工确认发布场景下的业务语义展示
 *
 * @author xiexu
 */
class AdminProcessingTaskPresentationResolverTests {

    private final AdminProcessingTaskPresentationResolver resolver =
            new AdminProcessingTaskPresentationResolver();

    /**
     * 验证全部待人工确认时会展示为待人工确认。
     */
    @Test
    void shouldRenderPendingHumanReviewWhenNothingPublished() {
        AdminProcessingTaskPresentation presentation = resolver.resolve(
                AdminProcessingTaskPresentationResolver.TASK_TYPE_SOURCE_SYNC,
                "SUCCEEDED",
                "finalize_job",
                1,
                1,
                "编译完成",
                null,
                "处理成功，资料已写入知识库",
                null,
                9L,
                3,
                0,
                0
        );

        assertThat(presentation.getDisplayStatusLabel()).isEqualTo("待人工确认");
        assertThat(presentation.getCompletionNotice()).isEqualTo("草稿尚未入库，需人工确认后才能发布");
        assertThat(presentation.getReasonSummary()).isEqualTo("质量检查已完成，等待人工确认后决定是否入库");
        assertThat(presentation.isRequiresManualAction()).isTrue();
        assertThat(presentation.getCurrentStepLabel()).isEqualTo("等待人工确认");
    }

    /**
     * 验证部分已发布、部分待确认时会继续展示为待人工确认。
     */
    @Test
    void shouldRenderPendingHumanReviewWhenPartiallyPublished() {
        AdminProcessingTaskPresentation presentation = resolver.resolve(
                AdminProcessingTaskPresentationResolver.TASK_TYPE_SOURCE_SYNC,
                "SUCCEEDED",
                "finalize_job",
                1,
                1,
                "编译完成",
                null,
                "处理成功，资料已写入知识库",
                null,
                9L,
                2,
                1,
                0
        );

        assertThat(presentation.getDisplayStatusLabel()).isEqualTo("待人工确认");
        assertThat(presentation.getCompletionNotice()).isEqualTo("部分内容已入库，其余仍待人工确认");
        assertThat(presentation.getReasonSummary()).isEqualTo("已入库 1 篇，仍有 2 篇待人工确认");
        assertThat(presentation.isRequiresManualAction()).isTrue();
    }

    /**
     * 验证部分已发布、部分已驳回且无待确认时会展示为已处理。
     */
    @Test
    void shouldRenderProcessedWhenPartiallyPublishedAndRejected() {
        AdminProcessingTaskPresentation presentation = resolver.resolve(
                AdminProcessingTaskPresentationResolver.TASK_TYPE_SOURCE_SYNC,
                "SUCCEEDED",
                "finalize_job",
                1,
                1,
                "编译完成",
                null,
                "处理成功，资料已写入知识库",
                null,
                9L,
                0,
                2,
                1
        );

        assertThat(presentation.getDisplayStatusLabel()).isEqualTo("已处理");
        assertThat(presentation.getCompletionNotice()).isEqualTo("本次草稿已处理完成，但只有部分内容进入知识库");
        assertThat(presentation.getReasonSummary()).isEqualTo("已入库 2 篇，已驳回 1 篇");
        assertThat(presentation.isRequiresManualAction()).isFalse();
    }

    /**
     * 验证全部已发布时会展示为已完成。
     */
    @Test
    void shouldRenderCompletedWhenAllDraftsPublished() {
        AdminProcessingTaskPresentation presentation = resolver.resolve(
                AdminProcessingTaskPresentationResolver.TASK_TYPE_SOURCE_SYNC,
                "SUCCEEDED",
                "finalize_job",
                1,
                1,
                "编译完成",
                null,
                "处理成功，资料已写入知识库",
                null,
                9L,
                0,
                4,
                0
        );

        assertThat(presentation.getDisplayStatusLabel()).isEqualTo("已完成");
        assertThat(presentation.getCompletionNotice()).isEqualTo("资料已正式发布到知识库");
        assertThat(presentation.getReasonSummary()).isEqualTo("资料已正式发布到知识库");
        assertThat(presentation.isRequiresManualAction()).isFalse();
    }

    /**
     * 验证全部已驳回时会展示为未入库。
     */
    @Test
    void shouldRenderNotPublishedWhenAllDraftsRejected() {
        AdminProcessingTaskPresentation presentation = resolver.resolve(
                AdminProcessingTaskPresentationResolver.TASK_TYPE_SOURCE_SYNC,
                "SUCCEEDED",
                "finalize_job",
                1,
                1,
                "编译完成",
                null,
                "处理成功，资料已写入知识库",
                null,
                9L,
                0,
                0,
                3
        );

        assertThat(presentation.getDisplayStatusLabel()).isEqualTo("未入库");
        assertThat(presentation.getCompletionNotice()).isEqualTo("本次草稿已全部驳回，未进入正式知识库");
        assertThat(presentation.getReasonSummary()).isEqualTo("本次草稿已全部驳回，未进入正式知识库");
        assertThat(presentation.isRequiresManualAction()).isFalse();
    }
}
