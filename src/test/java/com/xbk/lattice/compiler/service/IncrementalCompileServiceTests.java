package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.CompilerProperties;
import com.xbk.lattice.compiler.domain.MergedConcept;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.governance.repo.RepoSnapshotService;
import com.xbk.lattice.infra.persistence.ArticleChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.RepoSnapshotRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IncrementalCompileService 测试
 *
 * 职责：验证增量编译的直命中、下游传播与最小刷新行为
 *
 * @author xiexu
 */
class IncrementalCompileServiceTests {

    /**
     * 验证概念命中已有文章时，会在保留旧来源的前提下增强旧文章。
     *
     * @param tempDir 临时目录
     * @throws IOException IO 异常
     */
    @Test
    void shouldEnhanceExistingArticleWhenConceptIdAlreadyExists(@TempDir Path tempDir) throws IOException {
        FakeArticleJdbcRepository articleJdbcRepository = new FakeArticleJdbcRepository();
        articleJdbcRepository.upsert(createExistingArticle(
                "payment-timeout",
                "Payment Timeout",
                List.of("payment/base.json"),
                "现有支付超时规则",
                """
                        # Payment Timeout

                        现有支付超时规则

                        ## Timeout Rules
                        - retry=3
                        """
        ));
        FakeArticleChunkJdbcRepository articleChunkJdbcRepository = new FakeArticleChunkJdbcRepository();
        articleChunkJdbcRepository.replaceChunks("payment-timeout", List.of("retry=3"));
        articleJdbcRepository.clearUpsertHistory();
        articleChunkJdbcRepository.clearReplaceHistory();
        FakeSourceFileJdbcRepository sourceFileJdbcRepository = new FakeSourceFileJdbcRepository();
        RecordingSynthesisArtifactsService synthesisArtifactsService = new RecordingSynthesisArtifactsService();
        RecordingRepoSnapshotService repoSnapshotService = new RecordingRepoSnapshotService();
        IncrementalCompileService incrementalCompileService = new IncrementalCompileService(
                createCompilerProperties(),
                null,
                null,
                null,
                synthesisArtifactsService,
                articleJdbcRepository,
                articleChunkJdbcRepository,
                sourceFileJdbcRepository
        );
        incrementalCompileService.setRepoSnapshotService(repoSnapshotService);

        Path paymentDir = Files.createDirectories(tempDir.resolve("payment"));
        Files.writeString(
                paymentDir.resolve("update.json"),
                "{"
                        + "\"concepts\":["
                        + "{\"id\":\"payment-timeout\",\"title\":\"Payment Timeout\",\"description\":\"补充支付超时补偿策略\","
                        + "\"snippets\":[\"manual-review\"],"
                        + "\"sections\":[{\"heading\":\"Compensation\",\"content\":[\"manual-review\",\"retry=5\"],\"sources\":[\"payment/update.json#compensation\"]}]}"
                        + "]"
                        + "}",
                StandardCharsets.UTF_8
        );

        CompileResult compileResult = incrementalCompileService.incrementalCompile(tempDir);

        ArticleRecord updatedArticle = articleJdbcRepository.findByConceptId("payment-timeout").orElseThrow();
        assertThat(compileResult.getPersistedCount()).isEqualTo(1);
        assertThat(updatedArticle.getSourcePaths()).contains("payment/base.json", "payment/update.json");
        assertThat(updatedArticle.getContent()).contains("payment/base.json");
        assertThat(updatedArticle.getContent()).contains("payment/update.json");
        assertThat(updatedArticle.getContent()).contains("## 增量更新");
        assertThat(updatedArticle.getContent()).contains("manual-review");
        assertThat(String.join("\n", articleChunkJdbcRepository.findChunkTexts("payment-timeout"))).contains("## 增量更新");
        assertThat(String.join("\n", articleChunkJdbcRepository.findChunkTexts("payment-timeout"))).contains("manual-review");
        assertThat(articleJdbcRepository.getUpsertedConceptIds()).containsExactly("payment-timeout");
        assertThat(articleChunkJdbcRepository.getReplacedConceptIds()).containsExactly("payment-timeout");
        assertThat(synthesisArtifactsService.getLastConcepts()).hasSize(1);
        assertThat(repoSnapshotService.getSnapshotCount()).isEqualTo(1);
        assertThat(repoSnapshotService.getLastTriggerEvent()).isEqualTo("compile.incremental");
    }

    /**
     * 验证未命中已有文章时，会新建文章并刷新合成产物。
     *
     * @param tempDir 临时目录
     * @throws IOException IO 异常
     */
    @Test
    void shouldCreateNewArticleWhenNoExistingArticleMatches(@TempDir Path tempDir) throws IOException {
        FakeArticleJdbcRepository articleJdbcRepository = new FakeArticleJdbcRepository();
        FakeArticleChunkJdbcRepository articleChunkJdbcRepository = new FakeArticleChunkJdbcRepository();
        FakeSourceFileJdbcRepository sourceFileJdbcRepository = new FakeSourceFileJdbcRepository();
        RecordingSynthesisArtifactsService synthesisArtifactsService = new RecordingSynthesisArtifactsService();
        IncrementalCompileService incrementalCompileService = new IncrementalCompileService(
                createCompilerProperties(),
                null,
                null,
                null,
                synthesisArtifactsService,
                articleJdbcRepository,
                articleChunkJdbcRepository,
                sourceFileJdbcRepository
        );

        Path refundDir = Files.createDirectories(tempDir.resolve("refund"));
        Files.writeString(
                refundDir.resolve("new.json"),
                "{"
                        + "\"concepts\":["
                        + "{\"id\":\"refund-status\",\"title\":\"Refund Status\",\"description\":\"退款状态流转说明\","
                        + "\"snippets\":[\"refund-created\"],"
                        + "\"sections\":[{\"heading\":\"Status Flow\",\"content\":[\"refund-created\",\"refund-paid\"],\"sources\":[\"refund/new.json#status-flow\"]}]}"
                        + "]"
                        + "}",
                StandardCharsets.UTF_8
        );

        CompileResult compileResult = incrementalCompileService.incrementalCompile(tempDir);

        ArticleRecord createdArticle = articleJdbcRepository.findByConceptId("refund-status").orElseThrow();
        assertThat(compileResult.getPersistedCount()).isEqualTo(1);
        assertThat(createdArticle.getTitle()).isEqualTo("Refund Status");
        assertThat(createdArticle.getSourcePaths()).containsExactly("refund/new.json");
        assertThat(createdArticle.getContent()).contains("# Refund Status");
        assertThat(createdArticle.getContent()).contains("refund/new.json");
        assertThat(String.join("\n", articleChunkJdbcRepository.findChunkTexts("refund-status"))).contains("# Refund Status");
        assertThat(String.join("\n", articleChunkJdbcRepository.findChunkTexts("refund-status"))).contains("refund-created");
        assertThat(articleJdbcRepository.getUpsertedConceptIds()).containsExactly("refund-status");
        assertThat(articleChunkJdbcRepository.getReplacedConceptIds()).containsExactly("refund-status");
        assertThat(synthesisArtifactsService.getLastConcepts()).hasSize(1);
    }

    /**
     * 验证源文件直命中的聚合文章会被增强，同时新增概念仍会创建独立文章。
     *
     * @param tempDir 临时目录
     * @throws IOException IO 异常
     */
    @Test
    void shouldEnhanceDirectSourceHitArticleAndStillCreateNewConceptArticle(@TempDir Path tempDir) throws IOException {
        FakeArticleJdbcRepository articleJdbcRepository = new FakeArticleJdbcRepository();
        articleJdbcRepository.upsert(createExistingArticle(
                "knowledge-overview",
                "Knowledge Overview",
                List.of("payment/update.json"),
                "现有总览",
                """
                        # Knowledge Overview

                        现有总览
                        """
        ));
        FakeArticleChunkJdbcRepository articleChunkJdbcRepository = new FakeArticleChunkJdbcRepository();
        articleChunkJdbcRepository.replaceChunks("knowledge-overview", List.of("overview"));
        articleJdbcRepository.clearUpsertHistory();
        articleChunkJdbcRepository.clearReplaceHistory();
        FakeSourceFileJdbcRepository sourceFileJdbcRepository = new FakeSourceFileJdbcRepository();
        RecordingSynthesisArtifactsService synthesisArtifactsService = new RecordingSynthesisArtifactsService();
        IncrementalCompileService incrementalCompileService = new IncrementalCompileService(
                createCompilerProperties(),
                null,
                null,
                null,
                synthesisArtifactsService,
                articleJdbcRepository,
                articleChunkJdbcRepository,
                sourceFileJdbcRepository
        );

        Path paymentDir = Files.createDirectories(tempDir.resolve("payment"));
        Files.writeString(
                paymentDir.resolve("update.json"),
                "{"
                        + "\"concepts\":["
                        + "{\"id\":\"refund-status\",\"title\":\"Refund Status\",\"description\":\"退款状态补充说明\","
                        + "\"snippets\":[\"refund-created\"],"
                        + "\"sections\":[{\"heading\":\"Refund Flow\",\"content\":[\"refund-created\",\"refund-paid\"],\"sources\":[\"payment/update.json#refund-flow\"]}]}"
                        + "]"
                        + "}",
                StandardCharsets.UTF_8
        );

        CompileResult compileResult = incrementalCompileService.incrementalCompile(tempDir);

        ArticleRecord updatedArticle = articleJdbcRepository.findByConceptId("knowledge-overview").orElseThrow();
        ArticleRecord createdArticle = articleJdbcRepository.findByConceptId("refund-status").orElseThrow();
        assertThat(compileResult.getPersistedCount()).isEqualTo(2);
        assertThat(updatedArticle.getSourcePaths()).containsExactly("payment/update.json");
        assertThat(updatedArticle.getContent()).contains("## 增量更新");
        assertThat(updatedArticle.getContent()).contains("Refund Status");
        assertThat(updatedArticle.getContent()).contains("refund-paid");
        assertThat(createdArticle.getSourcePaths()).containsExactly("payment/update.json");
        assertThat(createdArticle.getContent()).contains("# Refund Status");
        assertThat(articleJdbcRepository.getUpsertedConceptIds()).containsExactly("knowledge-overview", "refund-status");
        assertThat(articleChunkJdbcRepository.getReplacedConceptIds()).containsExactly("knowledge-overview", "refund-status");
    }

    /**
     * 验证直命中后会沿依赖图传播到下游文章，且无关文章不会被刷新。
     *
     * @param tempDir 临时目录
     * @throws IOException IO 异常
     */
    @Test
    void shouldPropagateDirectHitToDownstreamArticlesWithoutRefreshingUnrelatedArticles(@TempDir Path tempDir) throws IOException {
        FakeArticleJdbcRepository articleJdbcRepository = new FakeArticleJdbcRepository();
        articleJdbcRepository.upsert(createExistingArticle(
                "payment-timeout",
                "Payment Timeout",
                List.of("payment/base.json"),
                "现有支付超时规则",
                """
                        # Payment Timeout

                        现有支付超时规则
                        """
        ));
        articleJdbcRepository.upsert(createExistingArticle(
                "payment-overview",
                "Payment Overview",
                List.of("overview/index.md"),
                List.of("payment-timeout"),
                "支付总览",
                """
                        # Payment Overview

                        依赖 [[payment-timeout]]
                        """
        ));
        articleJdbcRepository.upsert(createExistingArticle(
                "refund-policy",
                "Refund Policy",
                List.of("refund/base.json"),
                "退款规则",
                """
                        # Refund Policy

                        原有退款规则
                        """
        ));
        FakeArticleChunkJdbcRepository articleChunkJdbcRepository = new FakeArticleChunkJdbcRepository();
        articleChunkJdbcRepository.replaceChunks("payment-timeout", List.of("timeout"));
        articleChunkJdbcRepository.replaceChunks("payment-overview", List.of("overview"));
        articleChunkJdbcRepository.replaceChunks("refund-policy", List.of("refund"));
        articleJdbcRepository.clearUpsertHistory();
        articleChunkJdbcRepository.clearReplaceHistory();
        FakeSourceFileJdbcRepository sourceFileJdbcRepository = new FakeSourceFileJdbcRepository();
        RecordingSynthesisArtifactsService synthesisArtifactsService = new RecordingSynthesisArtifactsService();
        IncrementalCompileService incrementalCompileService = new IncrementalCompileService(
                createCompilerProperties(),
                null,
                null,
                null,
                synthesisArtifactsService,
                articleJdbcRepository,
                articleChunkJdbcRepository,
                sourceFileJdbcRepository
        );

        String unrelatedContent = articleJdbcRepository.findByConceptId("refund-policy").orElseThrow().getContent();

        Path paymentDir = Files.createDirectories(tempDir.resolve("payment"));
        Files.writeString(
                paymentDir.resolve("update.json"),
                "{"
                        + "\"concepts\":["
                        + "{\"id\":\"payment-timeout\",\"title\":\"Payment Timeout\",\"description\":\"补充支付超时补偿策略\","
                        + "\"snippets\":[\"manual-review\"],"
                        + "\"sections\":[{\"heading\":\"Compensation\",\"content\":[\"manual-review\",\"retry=5\"],\"sources\":[\"payment/update.json#compensation\"]}]}"
                        + "]"
                        + "}",
                StandardCharsets.UTF_8
        );

        CompileResult compileResult = incrementalCompileService.incrementalCompile(tempDir);

        ArticleRecord directHitArticle = articleJdbcRepository.findByConceptId("payment-timeout").orElseThrow();
        ArticleRecord downstreamArticle = articleJdbcRepository.findByConceptId("payment-overview").orElseThrow();
        ArticleRecord unrelatedArticle = articleJdbcRepository.findByConceptId("refund-policy").orElseThrow();
        assertThat(compileResult.getPersistedCount()).isEqualTo(2);
        assertThat(directHitArticle.getContent()).contains("manual-review");
        assertThat(downstreamArticle.getContent()).contains("manual-review");
        assertThat(downstreamArticle.getSourcePaths()).contains("overview/index.md", "payment/update.json");
        assertThat(unrelatedArticle.getContent()).isEqualTo(unrelatedContent);
        assertThat(articleJdbcRepository.getUpsertedConceptIds()).containsExactly("payment-timeout", "payment-overview");
        assertThat(articleChunkJdbcRepository.getReplacedConceptIds()).containsExactly("payment-timeout", "payment-overview");
    }

    /**
     * 验证仅通过 related 关联的文章不会被纳入增量传播，避免把旁路资料误并入正文事实。
     *
     * @param tempDir 临时目录
     * @throws IOException IO 异常
     */
    @Test
    void shouldIgnoreRelatedOnlyArticlesDuringIncrementalPropagation(@TempDir Path tempDir) throws IOException {
        FakeArticleJdbcRepository articleJdbcRepository = new FakeArticleJdbcRepository();
        articleJdbcRepository.upsert(createExistingArticle(
                "ops",
                "Ops",
                List.of("ops/postmortem.md"),
                List.of(),
                List.of("payments"),
                "运行时观察",
                """
                        # Ops

                        观察支付超时与熔断阈值
                        """
        ));
        articleJdbcRepository.upsert(createExistingArticle(
                "payments",
                "Payments",
                List.of("payments/gateway-config.yaml"),
                List.of(),
                List.of("ops"),
                "支付配置",
                """
                        # Payments

                        当前支付配置
                        """
        ));
        FakeArticleChunkJdbcRepository articleChunkJdbcRepository = new FakeArticleChunkJdbcRepository();
        articleChunkJdbcRepository.replaceChunks("ops", List.of("ops"));
        articleChunkJdbcRepository.replaceChunks("payments", List.of("payments"));
        articleJdbcRepository.clearUpsertHistory();
        articleChunkJdbcRepository.clearReplaceHistory();
        FakeSourceFileJdbcRepository sourceFileJdbcRepository = new FakeSourceFileJdbcRepository();
        RecordingSynthesisArtifactsService synthesisArtifactsService = new RecordingSynthesisArtifactsService();
        IncrementalCompileService incrementalCompileService = new IncrementalCompileService(
                createCompilerProperties(),
                null,
                null,
                null,
                synthesisArtifactsService,
                articleJdbcRepository,
                articleChunkJdbcRepository,
                sourceFileJdbcRepository
        );

        String paymentsContent = articleJdbcRepository.findByConceptId("payments").orElseThrow().getContent();

        Path opsDir = Files.createDirectories(tempDir.resolve("ops"));
        Files.writeString(
                opsDir.resolve("postmortem.json"),
                """
                        {
                          "concepts": [
                            {
                              "id": "ops",
                              "title": "Ops",
                              "description": "补充最新熔断阈值",
                              "snippets": ["failure-rate-threshold=51"],
                              "sections": [
                                {
                                  "heading": "Current Values",
                                  "content": ["failure-rate-threshold=51", "observation-window=21m"],
                                  "sources": ["ops/postmortem.json#current-values"]
                                }
                              ]
                            }
                          ]
                        }
                        """.trim(),
                StandardCharsets.UTF_8
        );

        CompileResult compileResult = incrementalCompileService.incrementalCompile(tempDir);

        ArticleRecord opsArticle = articleJdbcRepository.findByConceptId("ops").orElseThrow();
        ArticleRecord paymentsArticle = articleJdbcRepository.findByConceptId("payments").orElseThrow();
        assertThat(compileResult.getPersistedCount()).isEqualTo(1);
        assertThat(opsArticle.getContent()).contains("failure-rate-threshold=51");
        assertThat(paymentsArticle.getContent()).isEqualTo(paymentsContent);
        assertThat(paymentsArticle.getSourcePaths()).containsExactly("payments/gateway-config.yaml");
        assertThat(articleJdbcRepository.getUpsertedConceptIds()).containsExactly("ops");
        assertThat(articleChunkJdbcRepository.getReplacedConceptIds()).containsExactly("ops");
    }

    /**
     * 验证整目录增量时会先滤掉未变化文件，只刷新真实受影响文章。
     *
     * @param tempDir 临时目录
     * @throws IOException IO 异常
     */
    @Test
    void shouldIgnoreUnchangedFilesWhenIncrementalDirectoryContainsMixedChanges(@TempDir Path tempDir) throws IOException {
        FakeArticleJdbcRepository articleJdbcRepository = new FakeArticleJdbcRepository();
        articleJdbcRepository.upsert(createExistingArticle(
                "payment-timeout",
                "Payment Timeout",
                List.of("payment/base.json"),
                "现有支付超时规则",
                """
                        # Payment Timeout

                        现有支付超时规则
                        """
        ));
        articleJdbcRepository.upsert(createExistingArticle(
                "payment-overview",
                "Payment Overview",
                List.of("overview/index.md"),
                List.of("payment-timeout"),
                "支付总览",
                """
                        # Payment Overview

                        依赖 [[payment-timeout]]
                        """
        ));
        articleJdbcRepository.upsert(createExistingArticle(
                "refund-policy",
                "Refund Policy",
                List.of("refund/base.json"),
                "退款规则",
                """
                        # Refund Policy

                        原有退款规则
                        """
        ));
        FakeArticleChunkJdbcRepository articleChunkJdbcRepository = new FakeArticleChunkJdbcRepository();
        articleChunkJdbcRepository.replaceChunks("payment-timeout", List.of("timeout"));
        articleChunkJdbcRepository.replaceChunks("payment-overview", List.of("overview"));
        articleChunkJdbcRepository.replaceChunks("refund-policy", List.of("refund"));
        articleJdbcRepository.clearUpsertHistory();
        articleChunkJdbcRepository.clearReplaceHistory();

        String unchangedRefundContent = """
                {
                  "concepts": [
                    {
                      "id": "refund-policy",
                      "title": "Refund Policy",
                      "description": "退款规则",
                      "snippets": ["refund-window=7"],
                      "sections": [
                        {
                          "heading": "Refund Rules",
                          "content": ["refund-window=7"],
                          "sources": ["refund/base.json#refund-rules"]
                        }
                      ]
                    }
                  ]
                }
                """.trim();
        String changedPaymentBaseline = """
                {
                  "concepts": [
                    {
                      "id": "payment-timeout",
                      "title": "Payment Timeout",
                      "description": "现有支付超时规则",
                      "snippets": ["retry=3"],
                      "sections": [
                        {
                          "heading": "Timeout Rules",
                          "content": ["retry=3"],
                          "sources": ["payment/base.json#timeout-rules"]
                        }
                      ]
                    }
                  ]
                }
                """.trim();
        FakeSourceFileJdbcRepository sourceFileJdbcRepository = new FakeSourceFileJdbcRepository();
        sourceFileJdbcRepository.upsert(new SourceFileRecord(
                "payment/base.json",
                changedPaymentBaseline,
                "json",
                changedPaymentBaseline.getBytes(StandardCharsets.UTF_8).length,
                changedPaymentBaseline,
                "{}",
                false,
                "payment/base.json"
        ));
        sourceFileJdbcRepository.upsert(new SourceFileRecord(
                "refund/base.json",
                unchangedRefundContent,
                "json",
                unchangedRefundContent.getBytes(StandardCharsets.UTF_8).length,
                unchangedRefundContent,
                "{}",
                false,
                "refund/base.json"
        ));
        RecordingSynthesisArtifactsService synthesisArtifactsService = new RecordingSynthesisArtifactsService();
        IncrementalCompileService incrementalCompileService = new IncrementalCompileService(
                createCompilerProperties(),
                null,
                null,
                null,
                synthesisArtifactsService,
                articleJdbcRepository,
                articleChunkJdbcRepository,
                sourceFileJdbcRepository
        );

        Path paymentDir = Files.createDirectories(tempDir.resolve("payment"));
        Path refundDir = Files.createDirectories(tempDir.resolve("refund"));
        Files.writeString(
                paymentDir.resolve("base.json"),
                """
                        {
                          "concepts": [
                            {
                              "id": "payment-timeout",
                              "title": "Payment Timeout",
                              "description": "补充支付超时补偿策略",
                              "snippets": ["manual-review"],
                              "sections": [
                                {
                                  "heading": "Compensation",
                                  "content": ["manual-review", "retry=5"],
                                  "sources": ["payment/base.json#compensation"]
                                }
                              ]
                            }
                          ]
                        }
                        """.trim(),
                StandardCharsets.UTF_8
        );
        Files.writeString(
                refundDir.resolve("base.json"),
                unchangedRefundContent,
                StandardCharsets.UTF_8
        );

        String unrelatedContent = articleJdbcRepository.findByConceptId("refund-policy").orElseThrow().getContent();

        CompileResult compileResult = incrementalCompileService.incrementalCompile(tempDir);

        ArticleRecord directHitArticle = articleJdbcRepository.findByConceptId("payment-timeout").orElseThrow();
        ArticleRecord downstreamArticle = articleJdbcRepository.findByConceptId("payment-overview").orElseThrow();
        ArticleRecord unrelatedArticle = articleJdbcRepository.findByConceptId("refund-policy").orElseThrow();
        assertThat(compileResult.getPersistedCount()).isEqualTo(2);
        assertThat(directHitArticle.getContent()).contains("manual-review");
        assertThat(downstreamArticle.getContent()).contains("manual-review");
        assertThat(unrelatedArticle.getContent()).isEqualTo(unrelatedContent);
        assertThat(articleJdbcRepository.getUpsertedConceptIds()).containsExactly("payment-timeout", "payment-overview");
        assertThat(articleChunkJdbcRepository.getReplacedConceptIds()).containsExactly("payment-timeout", "payment-overview");
    }

    /**
     * 验证 metadata JSON 仅存在格式差异时，不应误判为源文件变化。
     */
    @Test
    void shouldIgnoreMetadataJsonFormattingDifferencesWhenFilteringChangedSources() {
        FakeArticleJdbcRepository articleJdbcRepository = new FakeArticleJdbcRepository();
        FakeArticleChunkJdbcRepository articleChunkJdbcRepository = new FakeArticleChunkJdbcRepository();
        FakeSourceFileJdbcRepository sourceFileJdbcRepository = new FakeSourceFileJdbcRepository();
        sourceFileJdbcRepository.upsert(new SourceFileRecord(
                null,
                1L,
                "integrations/guide.pdf",
                "integrations/guide.pdf",
                null,
                "preview",
                "pdf",
                1024L,
                "=== Page: 1 ===\ncontent",
                "{\"pageCount\": 3}",
                true,
                "integrations/guide.pdf"
        ));
        IncrementalCompileService incrementalCompileService = new IncrementalCompileService(
                createCompilerProperties(),
                null,
                null,
                null,
                new RecordingSynthesisArtifactsService(),
                articleJdbcRepository,
                articleChunkJdbcRepository,
                sourceFileJdbcRepository
        );

        List<RawSource> changedRawSources = incrementalCompileService.filterChangedRawSources(List.of(
                RawSource.parsed(
                        1L,
                        "integrations/guide.pdf",
                        "=== Page: 1 ===\ncontent",
                        "pdf",
                        1024L,
                        "{\"pageCount\":3}",
                        true,
                        "integrations/guide.pdf",
                        "document-extract",
                        "apache-pdfbox"
                )
        ));

        assertThat(changedRawSources).isEmpty();
    }

    /**
     * 创建编译配置。
     *
     * @return 编译配置
     */
    private CompilerProperties createCompilerProperties() {
        CompilerProperties compilerProperties = new CompilerProperties();
        compilerProperties.setIngestMaxChars(4096);
        compilerProperties.setBatchMaxChars(4096);
        return compilerProperties;
    }

    /**
     * 创建已有文章。
     *
     * @param conceptId 概念标识
     * @param title 标题
     * @param sourcePaths 来源路径
     * @param summary 摘要
     * @param body 主体
     * @return 文章记录
     */
    private ArticleRecord createExistingArticle(
            String conceptId,
            String title,
            List<String> sourcePaths,
            String summary,
            String body
    ) {
        return createExistingArticle(conceptId, title, sourcePaths, List.of(), summary, body);
    }

    /**
     * 创建已有文章。
     *
     * @param conceptId 概念标识
     * @param title 标题
     * @param sourcePaths 来源路径
     * @param dependsOn 依赖概念
     * @param summary 摘要
     * @param body 主体
     * @return 文章记录
     */
    private ArticleRecord createExistingArticle(
            String conceptId,
            String title,
            List<String> sourcePaths,
            List<String> dependsOn,
            String summary,
            String body
    ) {
        return createExistingArticle(conceptId, title, sourcePaths, dependsOn, List.of(), summary, body);
    }

    /**
     * 创建已有文章。
     *
     * @param conceptId 概念标识
     * @param title 标题
     * @param sourcePaths 来源路径
     * @param dependsOn 依赖概念
     * @param related 相关概念
     * @param summary 摘要
     * @param body 主体
     * @return 文章记录
     */
    private ArticleRecord createExistingArticle(
            String conceptId,
            String title,
            List<String> sourcePaths,
            List<String> dependsOn,
            List<String> related,
            String summary,
            String body
    ) {
        return new ArticleRecord(
                conceptId,
                title,
                buildArticleContent(title, summary, sourcePaths, dependsOn, related, body),
                "ACTIVE",
                OffsetDateTime.now(),
                sourcePaths,
                "{\"incremental\":false}",
                summary,
                List.of(),
                dependsOn,
                related,
                "medium",
                "pending"
        );
    }

    /**
     * 构建文章 Markdown。
     *
     * @param title 标题
     * @param summary 摘要
     * @param sourcePaths 来源路径
     * @param dependsOn 依赖概念
     * @param related 相关概念
     * @param body 主体
     * @return Markdown 内容
     */
    private String buildArticleContent(
            String title,
            String summary,
            List<String> sourcePaths,
            List<String> dependsOn,
            List<String> related,
            String body
    ) {
        return """
                ---
                title: "%s"
                summary: "%s"
                referential_keywords: []
                sources: %s
                depends_on: %s
                related: %s
                confidence: medium
                compiled_at: "%s"
                review_status: pending
                ---

                %s
                """.formatted(
                title,
                summary,
                formatYamlList(sourcePaths),
                formatYamlList(dependsOn),
                formatYamlList(related),
                OffsetDateTime.now(),
                body
        ).trim();
    }

    /**
     * 格式化 YAML 行内列表。
     *
     * @param values 值列表
     * @return YAML 行内列表
     */
    private String formatYamlList(List<String> values) {
        List<String> escaped = new ArrayList<String>();
        for (String value : values) {
            escaped.add("\"" + value + "\"");
        }
        return "[" + String.join(", ", escaped) + "]";
    }

    /**
     * 文章仓储测试替身。
     *
     * @author xiexu
     */
    private static class FakeArticleJdbcRepository extends ArticleJdbcRepository {

        private final Map<String, ArticleRecord> records = new LinkedHashMap<String, ArticleRecord>();

        private final List<String> upsertedConceptIds = new ArrayList<String>();

        private FakeArticleJdbcRepository() {
            super(null);
        }

        @Override
        public void upsert(ArticleRecord articleRecord) {
            records.put(articleRecord.getConceptId(), articleRecord);
            upsertedConceptIds.add(articleRecord.getConceptId());
        }

        @Override
        public Optional<ArticleRecord> findByConceptId(String conceptId) {
            return Optional.ofNullable(records.get(conceptId));
        }

        @Override
        public Optional<ArticleRecord> findByArticleKey(String articleKey) {
            return Optional.ofNullable(records.get(articleKey));
        }

        @Override
        public Optional<ArticleRecord> findBySourceIdAndConceptId(Long sourceId, String conceptId) {
            return findByConceptId(conceptId);
        }

        @Override
        public List<ArticleRecord> findAll() {
            return new ArrayList<ArticleRecord>(records.values());
        }

        private void clearUpsertHistory() {
            upsertedConceptIds.clear();
        }

        private List<String> getUpsertedConceptIds() {
            return upsertedConceptIds;
        }
    }

    /**
     * 文章 chunk 仓储测试替身。
     *
     * @author xiexu
     */
    private static class FakeArticleChunkJdbcRepository extends ArticleChunkJdbcRepository {

        private final Map<String, List<String>> chunks = new LinkedHashMap<String, List<String>>();

        private final List<String> replacedConceptIds = new ArrayList<String>();

        private FakeArticleChunkJdbcRepository() {
            super(null);
        }

        @Override
        public void replaceChunks(String conceptId, List<String> chunkTexts) {
            chunks.put(conceptId, new ArrayList<String>(chunkTexts));
            replacedConceptIds.add(conceptId);
        }

        @Override
        public void replaceChunks(String articleKey, String conceptId, List<String> chunkTexts) {
            chunks.put(conceptId, new ArrayList<String>(chunkTexts));
            replacedConceptIds.add(conceptId);
        }

        @Override
        public void replaceChunksFromContent(String conceptId, String content) {
            chunks.put(conceptId, List.of(content));
            replacedConceptIds.add(conceptId);
        }

        @Override
        public void replaceChunksFromContent(String articleKey, String conceptId, String content) {
            chunks.put(conceptId, List.of(content));
            replacedConceptIds.add(conceptId);
        }

        @Override
        public List<String> findChunkTexts(String conceptId) {
            return chunks.getOrDefault(conceptId, List.of());
        }

        private void clearReplaceHistory() {
            replacedConceptIds.clear();
        }

        private List<String> getReplacedConceptIds() {
            return replacedConceptIds;
        }
    }

    /**
     * 源文件仓储测试替身。
     *
     * @author xiexu
     */
    private static class FakeSourceFileJdbcRepository extends SourceFileJdbcRepository {

        private final Map<String, SourceFileRecord> recordsByPath = new LinkedHashMap<String, SourceFileRecord>();

        private final Map<String, SourceFileRecord> recordsBySourcePath = new LinkedHashMap<String, SourceFileRecord>();

        private FakeSourceFileJdbcRepository() {
            super(null);
        }

        @Override
        public SourceFileRecord upsert(SourceFileRecord sourceFileRecord) {
            recordsByPath.put(sourceFileRecord.getFilePath(), sourceFileRecord);
            if (sourceFileRecord.getRelativePath() != null) {
                recordsByPath.put(sourceFileRecord.getRelativePath(), sourceFileRecord);
            }
            if (sourceFileRecord.getSourceId() != null && sourceFileRecord.getRelativePath() != null) {
                recordsBySourcePath.put(buildSourcePathKey(sourceFileRecord.getSourceId(), sourceFileRecord.getRelativePath()), sourceFileRecord);
            }
            return sourceFileRecord;
        }

        @Override
        public Optional<SourceFileRecord> findByPath(String filePath) {
            return Optional.ofNullable(recordsByPath.get(filePath));
        }

        @Override
        public Optional<SourceFileRecord> findBySourceIdAndRelativePath(Long sourceId, String relativePath) {
            return Optional.ofNullable(recordsBySourcePath.get(buildSourcePathKey(sourceId, relativePath)));
        }

        private String buildSourcePathKey(Long sourceId, String relativePath) {
            return sourceId + "::" + relativePath;
        }
    }

    /**
     * 合成产物服务测试替身。
     *
     * @author xiexu
     */
    private static class RecordingSynthesisArtifactsService extends SynthesisArtifactsService {

        private List<MergedConcept> lastConcepts = List.of();

        private RecordingSynthesisArtifactsService() {
            super(null, null);
        }

        @Override
        public void generateAll(List<MergedConcept> mergedConcepts) {
            lastConcepts = new ArrayList<MergedConcept>(mergedConcepts);
        }

        /**
         * 记录带作用域的合成产物刷新输入。
         *
         * @param scopeId 作用域标识
         * @param mergedConcepts 合并概念列表
         */
        @Override
        public void generateAll(String scopeId, List<MergedConcept> mergedConcepts) {
            lastConcepts = new ArrayList<MergedConcept>(mergedConcepts);
        }

        private List<MergedConcept> getLastConcepts() {
            return lastConcepts;
        }
    }

    /**
     * 整库快照服务测试替身。
     *
     * @author xiexu
     */
    private static class RecordingRepoSnapshotService extends RepoSnapshotService {

        private int snapshotCount;

        private String lastTriggerEvent;

        private RecordingRepoSnapshotService() {
            super(null, null, null, null);
        }

        @Override
        public RepoSnapshotRecord snapshot(String triggerEvent, String description, String gitCommit) {
            snapshotCount++;
            lastTriggerEvent = triggerEvent;
            return new RepoSnapshotRecord(1L, null, triggerEvent, gitCommit, description, 0);
        }

        private int getSnapshotCount() {
            return snapshotCount;
        }

        private String getLastTriggerEvent() {
            return lastTriggerEvent;
        }
    }
}
