package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.compiler.domain.ConceptSection;
import com.xbk.lattice.compiler.domain.MergedConcept;
import com.xbk.lattice.compiler.node.CompileArticleNode;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import com.xbk.lattice.llm.service.LlmCallResult;
import com.xbk.lattice.llm.service.LlmClient;
import com.xbk.lattice.query.domain.ReviewIssue;
import com.xbk.lattice.query.domain.ReviewResult;
import com.xbk.lattice.query.service.RedisKeyValueStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CompileArticleNode 审查流程测试
 *
 * 职责：验证编译侧单轮审查、修复与状态收敛行为
 *
 * @author xiexu
 */
class CompileArticleReviewFlowTests {

    /**
     * 验证审查通过时，文章状态会更新为 passed。
     */
    @Test
    void shouldMarkArticleAsPassedWhenReviewPasses() {
        CompileArticleNode compileArticleNode = new CompileArticleNode(
                createLlmGateway("", "{}"),
                new FakeSourceFileJdbcRepository(),
                new DocumentSectionSelector(),
                new StubArticleReviewerGateway(ReviewResult.passed(), true),
                new StubReviewFixService(null)
        );

        ArticleRecord articleRecord = compileArticleNode.compile(createMergedConcept());

        assertThat(articleRecord.getReviewStatus()).isEqualTo("passed");
        assertThat(articleRecord.getContent()).contains("review_status: passed");
    }

    /**
     * 验证审查发现问题且修复成功时，文章状态会收敛为 passed。
     */
    @Test
    void shouldMarkArticleAsPassedWhenFixSucceeds() {
        CompileArticleNode compileArticleNode = new CompileArticleNode(
                createLlmGateway("", "{}"),
                new FakeSourceFileJdbcRepository(),
                new DocumentSectionSelector(),
                new StubArticleReviewerGateway(
                        ReviewResult.issuesFound(List.of(new ReviewIssue("HIGH", "MISSING_REF", "缺少 retry=3"))),
                        true
                ),
                new StubReviewFixService("""
                        ---
                        title: "Payment Timeout"
                        summary: "Handles payment timeout recovery"
                        referential_keywords: ["retry=3"]
                        sources: ["payment/analyze.json"]
                        depends_on: []
                        related: []
                        confidence: medium
                        review_status: passed
                        ---

                        # Payment Timeout
                        """)
        );

        ArticleRecord articleRecord = compileArticleNode.compile(createMergedConcept());

        assertThat(articleRecord.getReviewStatus()).isEqualTo("passed");
        assertThat(articleRecord.getContent()).contains("review_status: passed");
    }

    /**
     * 验证审查发现问题且修复失败时，文章状态会收敛为 needs_human_review。
     */
    @Test
    void shouldMarkArticleAsNeedsHumanReviewWhenFixFails() {
        CompileArticleNode compileArticleNode = new CompileArticleNode(
                createLlmGateway("", "{}"),
                new FakeSourceFileJdbcRepository(),
                new DocumentSectionSelector(),
                new StubArticleReviewerGateway(
                        ReviewResult.issuesFound(List.of(new ReviewIssue("HIGH", "MISSING_REF", "缺少 retry=3"))),
                        true
                ),
                new StubReviewFixService(null)
        );

        ArticleRecord articleRecord = compileArticleNode.compile(createMergedConcept());

        assertThat(articleRecord.getReviewStatus()).isEqualTo("needs_human_review");
        assertThat(articleRecord.getContent()).contains("review_status: needs_human_review");
    }

    /**
     * 创建测试用概念。
     *
     * @return 合并概念
     */
    private MergedConcept createMergedConcept() {
        return new MergedConcept(
                "payment-timeout",
                "Payment Timeout",
                "Handles payment timeout recovery",
                List.of("payment/analyze.json"),
                List.of("timeout-a"),
                List.of(new ConceptSection(
                        "Timeout Rules",
                        Arrays.asList("retry=3", "interval=30s"),
                        Arrays.asList("payment/analyze.json#timeout-rules")
                ))
        );
    }

    /**
     * 创建测试用 LLM 网关。
     *
     * @param compileResponse 编译返回
     * @param reviewResponse 审查返回
     * @return LLM 网关
     */
    private LlmGateway createLlmGateway(String compileResponse, String reviewResponse) {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setCompileModel("openai");
        llmProperties.setReviewerModel("anthropic");
        llmProperties.setBudgetUsd(10.0D);
        llmProperties.setCacheTtlSeconds(3600L);
        llmProperties.setCacheKeyPrefix("llm:test:");
        return new LlmGateway(
                new StaticLlmClient(compileResponse),
                new StaticLlmClient(reviewResponse),
                new NoopRedisKeyValueStore(),
                llmProperties
        );
    }

    /**
     * 固定返回结果的 LLM 客户端。
     *
     * @author xiexu
     */
    private static class StaticLlmClient implements LlmClient {

        private final String content;

        private StaticLlmClient(String content) {
            this.content = content;
        }

        @Override
        public LlmCallResult call(String systemPrompt, String userPrompt) {
            return new LlmCallResult(content, 100, 50);
        }
    }

    /**
     * 空操作 Redis 存储。
     *
     * @author xiexu
     */
    private static class NoopRedisKeyValueStore implements RedisKeyValueStore {

        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public void set(String key, String value, Duration ttl) {
        }

        @Override
        public Long getExpire(String key) {
            return null;
        }
    }

    /**
     * 源文件仓储测试替身。
     *
     * @author xiexu
     */
    private static class FakeSourceFileJdbcRepository extends SourceFileJdbcRepository {

        private final Map<String, SourceFileRecord> records = new LinkedHashMap<String, SourceFileRecord>();

        private FakeSourceFileJdbcRepository() {
            super(null);
            records.put(
                    "payment/analyze.json",
                    new SourceFileRecord(
                            "payment/analyze.json",
                            "retry=3",
                            "json",
                            20L,
                            "retry=3\ninterval=30s",
                            "{}",
                            true,
                            "payment/analyze.json"
                    )
            );
        }

        @Override
        public Optional<SourceFileRecord> findByPath(String filePath) {
            return Optional.ofNullable(records.get(filePath));
        }
    }

    /**
     * 文章审查网关测试替身。
     *
     * @author xiexu
     */
    private static class StubArticleReviewerGateway extends ArticleReviewerGateway {

        private final ReviewResult reviewResult;

        private final boolean enabled;

        private StubArticleReviewerGateway(ReviewResult reviewResult, boolean enabled) {
            super(null, null, new LlmProperties(), new RuleBasedArticleReviewer());
            this.reviewResult = reviewResult;
            this.enabled = enabled;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public ReviewResult review(String articleContent, String sourceContents) {
            return reviewResult;
        }
    }

    /**
     * 修复服务测试替身。
     *
     * @author xiexu
     */
    private static class StubReviewFixService extends ReviewFixService {

        private final String fixedContent;

        private StubReviewFixService(String fixedContent) {
            super(null);
            this.fixedContent = fixedContent;
        }

        @Override
        public String applyFix(String articleContent, List<ReviewIssue> reviewIssues, String sourceContents) {
            return fixedContent;
        }
    }
}
