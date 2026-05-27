package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.domain.ConceptSection;
import com.xbk.lattice.compiler.domain.MergedConcept;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import com.xbk.lattice.source.domain.KnowledgeSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ArticleTitleProfileSupport 测试
 *
 * 职责：验证来源标题优先级、规则模式与 LLM 兜底触发条件
 *
 * @author xiexu
 */
class ArticleTitleProfileSupportTests {

    /**
     * 验证来源标题优先取单文件 documentTitle，其次才回退资料源级标题提示。
     */
    @Test
    void shouldPreferDocumentTitleOverBundleHints() {
        MergedConcept mergedConcept = new MergedConcept(
                "quality-progress-and-lessons",
                "下一步计划",
                "从长文档中识别出的专题：下一步计划",
                List.of("docs/quality-progress-and-lessons.md"),
                List.of("Dashboard 状态摘要接入人工确认队列"),
                List.of(new ConceptSection(
                        "台账要求",
                        List.of("每完成一个事项立即回写", "未验证事项不得勾选完成"),
                        List.of("docs/quality-progress-and-lessons.md#台账要求")
                ))
        );
        SourceFileRecord sourceFileRecord = new SourceFileRecord(
                1L,
                7L,
                "docs/quality-progress-and-lessons.md",
                "docs/quality-progress-and-lessons.md",
                null,
                "Dashboard 状态摘要接入人工确认队列",
                "md",
                200L,
                "Dashboard 状态摘要接入人工确认队列\n台账要求\n联动范围",
                "{\"documentTitle\":\"质量打磨阶段进展\"}",
                false,
                "docs/quality-progress-and-lessons.md"
        );
        KnowledgeSource knowledgeSource = new KnowledgeSource(
                7L,
                "quality",
                "Quality Source",
                "UPLOAD",
                "document",
                "ACTIVE",
                "PRIVATE",
                "MANUAL",
                "{}",
                "{\"bundleSummary\":{\"displayName\":\"Quality Bundle\",\"titleHints\":[\"Quality Hint\"]}}",
                null,
                null,
                null,
                null,
                null,
                null
        );

        ArticleTitleProfileSupport.TitleProfile titleProfile = ArticleTitleProfileSupport.resolve(
                mergedConcept,
                List.of(sourceFileRecord),
                knowledgeSource
        );

        assertThat(titleProfile.getSourceTitle()).isEqualTo("质量打磨阶段进展");
        assertThat(titleProfile.getRepresentativeTitle()).isEqualTo("台账要求");
        assertThat(titleProfile.getTitleGenerationMode()).isEqualTo("RULE_BASED");
        assertThat(titleProfile.getTitleGenerationConfidence()).isEqualTo("LOW");
        assertThat(ArticleTitleProfileSupport.shouldUseLlmFallback(titleProfile)).isTrue();
    }

    /**
     * 验证来源标题会在缺少 documentTitle 时回退到资料源 displayName。
     */
    @Test
    void shouldFallbackToBundleDisplayNameWhenDocumentTitleMissing() {
        MergedConcept mergedConcept = new MergedConcept(
                "payment-retry-policy",
                "Retry Policy",
                "汇总支付重试策略与约束",
                List.of("payments/PaymentRetryPolicy.java"),
                List.of("retry window", "retry count"),
                List.of(new ConceptSection(
                        "Retry Policy",
                        List.of("Retry count is 3", "Retry interval is 30s"),
                        List.of("payments/PaymentRetryPolicy.java#retry")
                ))
        );
        SourceFileRecord sourceFileRecord = new SourceFileRecord(
                2L,
                8L,
                "payments/PaymentRetryPolicy.java",
                "payments/PaymentRetryPolicy.java",
                null,
                "Retry count is 3",
                "java",
                120L,
                "Retry count is 3\nRetry interval is 30s",
                "{}",
                false,
                "payments/PaymentRetryPolicy.java"
        );
        KnowledgeSource knowledgeSource = new KnowledgeSource(
                8L,
                "payments",
                "Payments Source",
                "UPLOAD",
                "document",
                "ACTIVE",
                "PRIVATE",
                "MANUAL",
                "{}",
                "{\"bundleSummary\":{\"displayName\":\"Payments Knowledge Base\",\"titleHints\":[\"Payments Hint\"]}}",
                null,
                null,
                null,
                null,
                null,
                null
        );

        ArticleTitleProfileSupport.TitleProfile titleProfile = ArticleTitleProfileSupport.resolve(
                mergedConcept,
                List.of(sourceFileRecord),
                knowledgeSource
        );

        assertThat(titleProfile.getSourceTitle()).isEqualTo("Payments Knowledge Base");
        assertThat(titleProfile.getRepresentativeTitle()).isEqualTo("Retry Policy");
        assertThat(titleProfile.getTitleGenerationMode()).isEqualTo("ANCHOR_DIRECT");
        assertThat(ArticleTitleProfileSupport.shouldUseLlmFallback(titleProfile)).isFalse();
    }

    /**
     * 验证模型标题候选会被清洗为单行有效标题。
     */
    @Test
    void shouldNormalizeGeneratedTitleCandidate() {
        String candidate = ArticleTitleProfileSupport.normalizeGeneratedTitleCandidate(
                "标题：Dashboard 状态摘要接入与台账回写要求\n补充说明"
        );

        assertThat(candidate).isEqualTo("Dashboard 状态摘要接入与台账回写要求");
    }
}
