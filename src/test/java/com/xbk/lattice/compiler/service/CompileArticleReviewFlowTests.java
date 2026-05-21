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
     * 验证修复稿误改 sources 时，会强制保留原始来源路径。
     */
    @Test
    void shouldPreserveOriginalSourcePathsWhenFixerRewritesSourcesAsTitle() {
        CompileArticleNode compileArticleNode = new CompileArticleNode(
                createLlmGateway("", "{}"),
                new FakeSourceFileJdbcRepository(),
                new DocumentSectionSelector(),
                new StubArticleReviewerGateway(
                        ReviewResult.issuesFound(List.of(new ReviewIssue("HIGH", "EMPTY_SOURCES", "源文件正文为空"))),
                        true
                ),
                new StubReviewFixService("""
                        ---
                        title: "项目全流程真实验收手册"
                        summary: "真实验收结果汇总"
                        referential_keywords: ["compile"]
                        sources: ["项目端到端验收手册"]
                        depends_on: []
                        related: []
                        confidence: high
                        review_status: pending
                        ---

                        # 项目全流程真实验收手册
                        """)
        );

        ArticleRecord fixedArticle = compileArticleNode.replaceReviewStatus(
                createChineseArticleRecord(),
                "pending",
                """
                        ---
                        title: "项目全流程真实验收手册"
                        summary: "真实验收结果汇总"
                        referential_keywords: ["compile"]
                        sources: ["项目端到端验收手册"]
                        depends_on: []
                        related: []
                        confidence: high
                        review_status: pending
                        ---

                        # 项目全流程真实验收手册
                        """
        );

        assertThat(fixedArticle.getSourcePaths()).containsExactly("项目全流程真实验收手册.md");
        assertThat(fixedArticle.getContent()).contains("sources:");
        assertThat(fixedArticle.getContent()).contains("\"项目全流程真实验收手册.md\"");
        assertThat(fixedArticle.getContent()).doesNotContain("\"项目端到端验收手册\"");
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
     * 验证审查超时不会再被当成自动通过，而是收敛为 needs_human_review。
     */
    @Test
    void shouldMarkArticleAsNeedsHumanReviewWhenReviewTimesOut() {
        CompileArticleNode compileArticleNode = new CompileArticleNode(
                createLlmGateway("", "{}"),
                new FakeSourceFileJdbcRepository(),
                new DocumentSectionSelector(),
                new StubArticleReviewerGateway(ReviewResult.timeoutFallback(), true),
                new StubReviewFixService(null)
        );

        ArticleRecord articleRecord = compileArticleNode.compile(createMergedConcept());

        assertThat(articleRecord.getReviewStatus()).isEqualTo("needs_human_review");
        assertThat(articleRecord.getContent()).contains("review_status: needs_human_review");
    }

    /**
     * 验证 fallback 文章不再包含关键事实速览，正文直接以原始章节内容开始。
     */
    @Test
    void shouldPlaceFactHighlightsBeforeDetailedSectionsInFallbackMarkdown() {
        CompileArticleNode compileArticleNode = new CompileArticleNode(
                null,
                new FakeSourceFileJdbcRepository(),
                new DocumentSectionSelector(),
                null,
                null
        );

        ArticleRecord articleRecord = compileArticleNode.compileDraft(new MergedConcept(
                "migration-facts",
                "Migration Facts",
                "总结迁移关键事实。",
                List.of("docs/migration.md"),
                List.of("dpfm-callback-service"),
                List.of(
                        new ConceptSection(
                                "配置分裂",
                                Arrays.asList(
                                        "externalSrkitTypeCodeList = [22, 26, 43, 37]",
                                        "fc-digital 硬编码 = [\"22\", \"26\"]"
                                ),
                                Arrays.asList("docs/migration.md#配置分裂")
                        ),
                        new ConceptSection(
                                "灰度批次",
                                Arrays.asList(
                                        "第一批：场景6",
                                        "第二批：场景7"
                                ),
                                Arrays.asList("docs/migration.md#灰度批次")
                        )
                )
        ), null);

        String body = com.xbk.lattice.article.service.ArticleMarkdownSupport.extractBody(articleRecord.getContent());
        assertThat(body).doesNotContain("## 关键事实速览");
        assertThat(body).contains("## 配置分裂");
        assertThat(body).contains("## 灰度批次");
        assertThat(body).contains("externalSrkitTypeCodeList = [22, 26, 43, 37]");
        assertThat(body).contains("第一批：场景6");
    }

    /**
     * 验证审查与修复阶段接收 sourceRef 相关片段，而不是完整来源全文前缀。
     */
    @Test
    void shouldPassRelevantSourcePayloadToReviewerAndFixer() {
        FakeSourceFileJdbcRepository sourceFileJdbcRepository = new FakeSourceFileJdbcRepository();
        sourceFileJdbcRepository.putRecord(new SourceFileRecord(
                "docs/compile.md",
                "compile",
                "md",
                12000L,
                buildLongCompileSource(),
                "{}",
                false,
                "docs/compile.md"
        ));
        StubArticleReviewerGateway reviewerGateway = new StubArticleReviewerGateway(
                ReviewResult.issuesFound(List.of(new ReviewIssue("HIGH", "MISSING_REF", "缺少 reviewer payload"))),
                true
        );
        StubReviewFixService reviewFixService = new StubReviewFixService("""
                ---
                title: "Compile Runtime Gate"
                summary: "desc"
                referential_keywords: ["reviewer-route"]
                sources: ["docs/compile.md"]
                depends_on: []
                related: []
                confidence: medium
                review_status: pending
                ---

                # Compile Runtime Gate
                """
        );
        CompileArticleNode compileArticleNode = new CompileArticleNode(
                createLlmGateway("""
                        ---
                        title: "Compile Runtime Gate"
                        summary: "desc"
                        referential_keywords: ["reviewer-route"]
                        sources: ["docs/compile.md"]
                        depends_on: []
                        related: []
                        confidence: medium
                        compiled_at: "2026-04-16T11:00:00+08:00"
                        review_status: pending
                        ---

                        # Compile Runtime Gate
                        """, "{}"),
                sourceFileJdbcRepository,
                new DocumentSectionSelector(),
                reviewerGateway,
                reviewFixService
        );

        compileArticleNode.compile(new MergedConcept(
                "compile-runtime-gate",
                "Compile Runtime Gate",
                "desc",
                List.of("docs/compile.md"),
                List.of("reviewer-route"),
                List.of(new ConceptSection(
                        "Runtime Gate",
                        List.of("reviewer-route = openai", "fixer-route = openai"),
                        List.of("docs/compile.md#Runtime Gate")
                ))
        ));

        assertThat(reviewerGateway.getLastSourceContents()).contains("## Runtime Gate");
        assertThat(reviewerGateway.getLastSourceContents()).contains("reviewer-route = openai");
        assertThat(reviewerGateway.getLastSourceContents()).doesNotContain("NOISE-LINE-80");
        assertThat(reviewerGateway.getLastSourceContents()).doesNotContain("UNRELATED-MARKER");
        assertThat(reviewFixService.getLastSourceContents()).contains("## Runtime Gate");
        assertThat(reviewFixService.getLastSourceContents()).contains("fixer-route = openai");
        assertThat(reviewFixService.getLastSourceContents()).doesNotContain("NOISE-LINE-80");
        assertThat(reviewFixService.getLastSourceContents()).doesNotContain("UNRELATED-MARKER");
    }

    /**
     * 验证图编排审查入口可从文章引用中恢复 sourceRef 并构建相关片段。
     */
    @Test
    void shouldBuildReviewPayloadFromArticleSourceRefs() {
        FakeSourceFileJdbcRepository sourceFileJdbcRepository = new FakeSourceFileJdbcRepository();
        sourceFileJdbcRepository.putRecord(new SourceFileRecord(
                "docs/review.md",
                "review",
                "md",
                12000L,
                buildLongReviewSource(),
                "{}",
                false,
                "docs/review.md"
        ));
        CompileArticleNode compileArticleNode = new CompileArticleNode(
                null,
                sourceFileJdbcRepository,
                new DocumentSectionSelector(),
                null,
                null
        );
        ArticleRecord articleRecord = new ArticleRecord(
                "review-payload",
                "Review Payload",
                """
                        ---
                        title: "Review Payload"
                        summary: "desc"
                        sources: ["docs/review.md"]
                        review_status: pending
                        ---

                        # Review Payload

                        需要核验相关片段。[→ docs/review.md, Relevant Section]
                        """,
                "ACTIVE",
                java.time.OffsetDateTime.now(),
                List.of("docs/review.md"),
                "{}",
                "desc",
                List.of("payload-marker"),
                List.of(),
                List.of(),
                "medium",
                "pending"
        );

        String sourceContents = compileArticleNode.buildReviewSourceContents(articleRecord);

        assertThat(sourceContents).contains("## Relevant Section");
        assertThat(sourceContents).contains("payload-marker = selected");
        assertThat(sourceContents).doesNotContain("NOISE-LINE-80");
        assertThat(sourceContents).doesNotContain("UNRELATED-MARKER");
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
     * 构建长来源正文。
     *
     * @return 长来源正文
     */
    private String buildLongCompileSource() {
        return """
                # Prelude
                %s
                ## Runtime Gate
                reviewer-route = openai
                fixer-route = openai
                ## Unrelated Section
                UNRELATED-MARKER
                """.formatted(buildNoiseLines());
    }

    /**
     * 构建长审查来源正文。
     *
     * @return 长审查来源正文
     */
    private String buildLongReviewSource() {
        return """
                # Intro
                %s
                ## Relevant Section
                payload-marker = selected
                ## Tail
                UNRELATED-MARKER
                """.formatted(buildNoiseLines());
    }

    /**
     * 构建干扰行。
     *
     * @return 干扰行文本
     */
    private String buildNoiseLines() {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < 120; index++) {
            builder.append("NOISE-LINE-").append(index).append(": unrelated prefix").append("\n");
        }
        return builder.toString();
    }

    /**
     * 创建中文测试文章。
     *
     * @return 测试文章
     */
    private ArticleRecord createChineseArticleRecord() {
        return new ArticleRecord(
                "项目全流程真实验收手册",
                "项目全流程真实验收手册",
                """
                        ---
                        title: "项目全流程真实验收手册"
                        summary: "真实验收结果汇总"
                        referential_keywords: ["compile"]
                        sources: ["项目全流程真实验收手册.md"]
                        depends_on: []
                        related: []
                        confidence: high
                        review_status: pending
                        ---

                        # 项目全流程真实验收手册
                        """,
                "ACTIVE",
                java.time.OffsetDateTime.now(),
                List.of("项目全流程真实验收手册.md"),
                "{}",
                "真实验收结果汇总",
                List.of("compile"),
                List.of(),
                List.of(),
                "high",
                "pending"
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

        @Override
        public void deleteByPrefix(String keyPrefix) {
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

        /**
         * 写入测试源文件记录。
         *
         * @param sourceFileRecord 源文件记录
         */
        private void putRecord(SourceFileRecord sourceFileRecord) {
            records.put(sourceFileRecord.getFilePath(), sourceFileRecord);
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

        private String lastSourceContents;

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
            this.lastSourceContents = sourceContents;
            return reviewResult;
        }

        /**
         * 获取最近一次审查来源正文。
         *
         * @return 来源正文
         */
        private String getLastSourceContents() {
            return lastSourceContents;
        }
    }

    /**
     * 修复服务测试替身。
     *
     * @author xiexu
     */
    private static class StubReviewFixService extends ReviewFixService {

        private final String fixedContent;

        private String lastSourceContents;

        private StubReviewFixService(String fixedContent) {
            super(null);
            this.fixedContent = fixedContent;
        }

        @Override
        public String applyFix(String articleContent, List<ReviewIssue> reviewIssues, String sourceContents) {
            this.lastSourceContents = sourceContents;
            return fixedContent;
        }

        /**
         * 获取最近一次修复来源正文。
         *
         * @return 来源正文
         */
        private String getLastSourceContents() {
            return lastSourceContents;
        }
    }
}
