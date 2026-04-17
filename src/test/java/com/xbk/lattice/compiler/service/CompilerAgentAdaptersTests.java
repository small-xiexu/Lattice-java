package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.agent.AgentModelRouter;
import com.xbk.lattice.compiler.agent.DefaultFixerAgent;
import com.xbk.lattice.compiler.agent.DefaultReviewerAgent;
import com.xbk.lattice.compiler.agent.DefaultWriterAgent;
import com.xbk.lattice.compiler.agent.FixTask;
import com.xbk.lattice.compiler.agent.FixerResult;
import com.xbk.lattice.compiler.agent.ReviewTask;
import com.xbk.lattice.compiler.agent.ReviewerResult;
import com.xbk.lattice.compiler.agent.WriterResult;
import com.xbk.lattice.compiler.agent.WriterTask;
import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.compiler.model.ConceptSection;
import com.xbk.lattice.compiler.model.MergedConcept;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import com.xbk.lattice.query.service.RedisKeyValueStore;
import com.xbk.lattice.query.service.ReviewIssue;
import com.xbk.lattice.query.service.ReviewResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 编译侧 Agent 适配器测试
 *
 * 职责：验证 Writer / Reviewer / Fixer 默认适配器与角色路由元信息
 *
 * @author xiexu
 */
class CompilerAgentAdaptersTests {

    /**
     * 验证 AgentModelRouter 会暴露角色路由标签。
     */
    @Test
    void shouldExposeRoleRoutesFromLlmGateway() {
        LlmProperties llmProperties = createProperties(true);
        AgentModelRouter agentModelRouter = new AgentModelRouter(createLlmGateway("", "{}", llmProperties));

        assertThat(agentModelRouter.routeForWriterAgent()).isEqualTo("openai");
        assertThat(agentModelRouter.routeForReviewerAgent()).isEqualTo("anthropic");
        assertThat(agentModelRouter.routeForFixerAgent()).isEqualTo("openai");
    }

    /**
     * 验证 review 关闭时，Reviewer 路由会收敛为 rule-based。
     */
    @Test
    void shouldFallbackReviewerRouteToRuleBasedWhenReviewIsDisabled() {
        LlmProperties llmProperties = createProperties(false);
        AgentModelRouter agentModelRouter = new AgentModelRouter(createLlmGateway("", "{}", llmProperties));

        assertThat(agentModelRouter.routeForReviewerAgent()).isEqualTo("rule-based");
    }

    /**
     * 验证 WriterAgent 会生成草稿并回填编译路由。
     */
    @Test
    void shouldGenerateDraftWithWriterRoute() {
        LlmProperties llmProperties = createProperties(true);
        CompileArticleNode compileArticleNode = new CompileArticleNode(
                createLlmGateway("", "{}", llmProperties),
                new FakeSourceFileJdbcRepository(),
                new DocumentSectionSelector(),
                new StubArticleReviewerGateway(ReviewResult.passed(), true),
                new StubReviewFixService(null)
        );
        DefaultWriterAgent writerAgent = new DefaultWriterAgent(
                compileArticleNode,
                new AgentModelRouter(createLlmGateway("", "{}", llmProperties))
        );

        WriterResult writerResult = writerAgent.write(new WriterTask(createMergedConcept(), Path.of(".")));

        assertThat(writerResult.getAgentRole()).isEqualTo("WriterAgent");
        assertThat(writerResult.getModelRoute()).isEqualTo("openai");
        assertThat(writerResult.getArticleRecord()).isNotNull();
        assertThat(writerResult.getArticleRecord().getReviewStatus()).isEqualTo("pending");
    }

    /**
     * 验证 ReviewerAgent 会返回审查结果并回填审查路由。
     */
    @Test
    void shouldReviewArticleWithReviewerRoute() {
        LlmProperties llmProperties = createProperties(true);
        DefaultReviewerAgent reviewerAgent = new DefaultReviewerAgent(
                new StubArticleReviewerGateway(
                        ReviewResult.issuesFound(List.of(new ReviewIssue("HIGH", "MISSING_REF", "缺少 retry=3"))),
                        true
                ),
                new AgentModelRouter(createLlmGateway("", "{}", llmProperties))
        );

        ReviewerResult reviewerResult = reviewerAgent.review(new ReviewTask(createDraftArticle(), "retry=3"));

        assertThat(reviewerResult.getAgentRole()).isEqualTo("ReviewerAgent");
        assertThat(reviewerResult.getModelRoute()).isEqualTo("anthropic");
        assertThat(reviewerResult.getReviewResult().isPass()).isFalse();
    }

    /**
     * 验证 FixerAgent 会返回修复结果并回填修复路由。
     */
    @Test
    void shouldFixArticleWithFixerRoute() {
        LlmProperties llmProperties = createProperties(true);
        DefaultFixerAgent fixerAgent = new DefaultFixerAgent(
                new StubReviewFixService("""
                        ---
                        title: "Payment Timeout"
                        summary: "Handles payment timeout recovery"
                        referential_keywords: ["retry=3"]
                        sources: ["payment/analyze.json"]
                        depends_on: []
                        related: []
                        confidence: medium
                        review_status: pending
                        ---

                        # Payment Timeout
                        """),
                new AgentModelRouter(createLlmGateway("", "{}", llmProperties))
        );

        FixerResult fixerResult = fixerAgent.fix(new FixTask(
                createDraftArticle(),
                List.of(new ReviewIssue("HIGH", "MISSING_REF", "缺少 retry=3")),
                "retry=3"
        ));

        assertThat(fixerResult.getAgentRole()).isEqualTo("FixerAgent");
        assertThat(fixerResult.getModelRoute()).isEqualTo("openai");
        assertThat(fixerResult.isFixed()).isTrue();
        assertThat(fixerResult.getFixedContent()).contains("review_status: pending");
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
     * 创建测试用草稿文章。
     *
     * @return 草稿文章
     */
    private ArticleRecord createDraftArticle() {
        return new ArticleRecord(
                "payment-timeout",
                "Payment Timeout",
                """
                        ---
                        title: "Payment Timeout"
                        summary: "Handles payment timeout recovery"
                        referential_keywords: ["timeout-a"]
                        sources: ["payment/analyze.json"]
                        depends_on: []
                        related: []
                        confidence: medium
                        review_status: pending
                        ---

                        # Payment Timeout
                        """,
                "ACTIVE",
                java.time.OffsetDateTime.now(),
                List.of("payment/analyze.json"),
                "{}",
                "Handles payment timeout recovery",
                List.of("timeout-a"),
                List.of(),
                List.of(),
                "medium",
                "pending"
        );
    }

    /**
     * 创建测试用 LLM 网关。
     *
     * @param compileResponse 编译返回
     * @param reviewResponse 审查返回
     * @param llmProperties LLM 配置
     * @return LLM 网关
     */
    private LlmGateway createLlmGateway(String compileResponse, String reviewResponse, LlmProperties llmProperties) {
        return new LlmGateway(
                new StaticLlmClient(compileResponse),
                new StaticLlmClient(reviewResponse),
                new NoopRedisKeyValueStore(),
                llmProperties
        );
    }

    /**
     * 创建默认配置。
     *
     * @param reviewEnabled 是否启用真实审查
     * @return LLM 配置
     */
    private LlmProperties createProperties(boolean reviewEnabled) {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setCompileModel("openai");
        llmProperties.setReviewerModel("anthropic");
        llmProperties.setBudgetUsd(10.0D);
        llmProperties.setCacheTtlSeconds(3600L);
        llmProperties.setCacheKeyPrefix("llm:test:");
        llmProperties.setReviewEnabled(reviewEnabled);
        return llmProperties;
    }

    /**
     * 固定返回结果的 LLM 客户端。
     *
     * 职责：为测试提供稳定输出
     *
     * @author xiexu
     */
    private static class StaticLlmClient implements LlmClient {

        private final String content;

        /**
         * 创建固定返回结果的 LLM 客户端。
         *
         * @param content 返回内容
         */
        private StaticLlmClient(String content) {
            this.content = content;
        }

        /**
         * 执行固定返回调用。
         *
         * @param systemPrompt 系统提示词
         * @param userPrompt 用户提示词
         * @return 调用结果
         */
        @Override
        public LlmCallResult call(String systemPrompt, String userPrompt) {
            return new LlmCallResult(content, 100, 50);
        }
    }

    /**
     * 空操作 Redis 存储。
     *
     * 职责：屏蔽缓存副作用
     *
     * @author xiexu
     */
    private static class NoopRedisKeyValueStore implements RedisKeyValueStore {

        /**
         * 读取缓存值。
         *
         * @param key 缓存键
         * @return 缓存值
         */
        @Override
        public String get(String key) {
            return null;
        }

        /**
         * 写入缓存值。
         *
         * @param key 缓存键
         * @param value 缓存值
         * @param ttl 过期时间
         */
        @Override
        public void set(String key, String value, Duration ttl) {
        }

        /**
         * 读取过期时间。
         *
         * @param key 缓存键
         * @return 过期时间
         */
        @Override
        public Long getExpire(String key) {
            return null;
        }
    }

    /**
     * 源文件仓储测试替身。
     *
     * 职责：返回固定来源正文
     *
     * @author xiexu
     */
    private static class FakeSourceFileJdbcRepository extends SourceFileJdbcRepository {

        private final Map<String, SourceFileRecord> records = new LinkedHashMap<String, SourceFileRecord>();

        /**
         * 创建源文件仓储测试替身。
         */
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

        /**
         * 按路径读取源文件。
         *
         * @param filePath 文件路径
         * @return 源文件
         */
        @Override
        public Optional<SourceFileRecord> findByPath(String filePath) {
            return Optional.ofNullable(records.get(filePath));
        }
    }

    /**
     * 文章审查网关测试替身。
     *
     * 职责：返回固定审查结果
     *
     * @author xiexu
     */
    private static class StubArticleReviewerGateway extends ArticleReviewerGateway {

        private final ReviewResult reviewResult;

        private final boolean enabled;

        /**
         * 创建审查网关测试替身。
         *
         * @param reviewResult 固定审查结果
         * @param enabled 是否启用
         */
        private StubArticleReviewerGateway(ReviewResult reviewResult, boolean enabled) {
            super(null, null, new LlmProperties(), new RuleBasedArticleReviewer());
            this.reviewResult = reviewResult;
            this.enabled = enabled;
        }

        /**
         * 返回是否启用。
         *
         * @return 是否启用
         */
        @Override
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 执行固定审查。
         *
         * @param articleContent 文章内容
         * @param sourceContents 来源正文
         * @return 审查结果
         */
        @Override
        public ReviewResult review(String articleContent, String sourceContents) {
            return reviewResult;
        }
    }

    /**
     * 修复服务测试替身。
     *
     * 职责：返回固定修复结果
     *
     * @author xiexu
     */
    private static class StubReviewFixService extends ReviewFixService {

        private final String fixedContent;

        /**
         * 创建修复服务测试替身。
         *
         * @param fixedContent 固定修复内容
         */
        private StubReviewFixService(String fixedContent) {
            super(null);
            this.fixedContent = fixedContent;
        }

        /**
         * 执行固定修复。
         *
         * @param articleContent 原始文章
         * @param reviewIssues 审查问题
         * @param sourceContents 来源正文
         * @return 修复结果
         */
        @Override
        public String applyFix(String articleContent, List<ReviewIssue> reviewIssues, String sourceContents) {
            return fixedContent;
        }
    }
}
