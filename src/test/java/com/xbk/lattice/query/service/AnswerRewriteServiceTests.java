package com.xbk.lattice.query.service;

import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.domain.GenerationMode;
import com.xbk.lattice.query.domain.ModelExecutionStatus;
import com.xbk.lattice.query.domain.QueryAnswerPayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AnswerRewriteService 测试
 *
 * 职责：验证审查反馈重写支路的模型不可用 fallback 行为
 *
 * @author xiexu
 */
class AnswerRewriteServiceTests {

    private final AnswerGenerationService support = new AnswerGenerationService();

    private final AnswerPostProcessor answerPostProcessor = new AnswerPostProcessor(support);

    private final AnswerRewriteService answerRewriteService = new AnswerRewriteService(
            support,
            new AnswerPromptBuilder(support),
            new AnswerPayloadParser(support, answerPostProcessor),
            new AnswerLlmInvoker(null)
    );

    /**
     * 验证模型不可用时会回落到规则拼装答案。
     */
    @Test
    void shouldReturnRuleBasedFallbackWhenLlmUnavailable() {
        QueryAnswerPayload answerPayload = answerRewriteService.rewriteFromReviewPayload(
                "query-1",
                "query",
                "rewrite",
                "timeout 配置是多少",
                "旧答案",
                "请补齐 timeout 的明确值",
                List.of()
        );

        assertThat(answerPayload.getGenerationMode()).isEqualTo(GenerationMode.RULE_BASED);
        assertThat(answerPayload.getModelExecutionStatus()).isEqualTo(ModelExecutionStatus.SKIPPED);
        assertThat(answerPayload.getAnswerOutcome()).isEqualTo(AnswerOutcome.PARTIAL_ANSWER);
        assertThat(answerPayload.getAnswerMarkdown()).contains("# 查询回答");
    }

    /**
     * 验证文本入口复用结构化载荷中的 Markdown。
     */
    @Test
    void shouldReturnMarkdownFromRewritePayload() {
        String answerMarkdown = answerRewriteService.rewriteFromReviewFeedback(
                "query-1",
                "query",
                "rewrite",
                "timeout 配置是多少",
                "旧答案",
                "请补齐 timeout 的明确值",
                List.of()
        );

        assertThat(answerMarkdown).contains("# 查询回答");
    }
}
