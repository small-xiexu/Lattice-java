package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.CompilerProperties;
import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.compiler.domain.AnalyzedConcept;
import com.xbk.lattice.compiler.domain.ConceptSection;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.compiler.domain.SourceBatch;
import com.xbk.lattice.compiler.node.AnalyzeNode;
import com.xbk.lattice.llm.service.LlmCallResult;
import com.xbk.lattice.llm.service.LlmClient;
import com.xbk.lattice.query.service.RedisKeyValueStore;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AnalyzeNode 测试
 *
 * 职责：验证最小分析节点输出稳定的标题、来源和摘要
 *
 * @author xiexu
 */
class AnalyzeNodeTests {

    /**
     * 验证分析节点会把带噪音的分组键收敛为稳定 conceptId。
     */
    @Test
    void shouldNormalizeNoisyGroupKeyIntoStableConceptId() {
        AnalyzeNode analyzeNode = new AnalyzeNode();
        List<SourceBatch> sourceBatches = Arrays.asList(
                new SourceBatch("batch-1", "  Payment__Service---Core  ", Arrays.asList(
                        RawSource.text("payment/a.md", "snippet-a", "md", 9L)
                ))
        );

        List<AnalyzedConcept> analyzedConcepts = analyzeNode.analyze("  Payment__Service---Core  ", sourceBatches);

        assertThat(analyzedConcepts).hasSize(1);
        assertThat(analyzedConcepts.get(0).getConceptId()).isEqualTo("payment-service-core");
        assertThat(analyzedConcepts.get(0).getTitle()).isEqualTo("Payment Service Core");
        assertThat(analyzedConcepts.get(0).getTitleSource()).isEqualTo("GROUP_KEY");
    }

    /**
     * 验证分析节点会清理空白片段，避免把噪音写入后续链路。
     */
    @Test
    void shouldTrimSnippetsAndDropBlankEntries() {
        AnalyzeNode analyzeNode = new AnalyzeNode();
        List<SourceBatch> sourceBatches = Arrays.asList(
                new SourceBatch("batch-1", "payment-service", Arrays.asList(
                        RawSource.text("payment/c.md", " refund-flow ", "md", 11L),
                        RawSource.text("payment/a.md", "   ", "md", 3L),
                        RawSource.text("payment/b.md", "\norder-flow\n", "md", 12L)
                ))
        );

        List<AnalyzedConcept> analyzedConcepts = analyzeNode.analyze("payment-service", sourceBatches);

        assertThat(analyzedConcepts).hasSize(1);
        assertThat(analyzedConcepts.get(0).getSnippets()).containsExactly("order-flow", "refund-flow");
    }

    /**
     * 验证分析节点会优先采用结构化 JSON 中的概念结果，而不是退回分组级单概念。
     */
    @Test
    void shouldPreferStructuredJsonConceptsWhenPayloadIsValid() {
        AnalyzeNode analyzeNode = new AnalyzeNode();
        String jsonPayload = "{"
                + "\"concepts\":["
                + "{\"id\":\"payment_timeout\",\"title\":\"Payment Timeout\",\"description\":\" Handles payment timeout recovery \","
                + "\"snippets\":[\" order timeout \",\"refund timeout\"],"
                + "\"sections\":[{\"heading\":\" Timeout Rules \",\"content\":[\" retry=3 \",\"interval=30s\"],\"sources\":[\"payment/rules.md#timeout-rules\"]}]},"
                + "{\"id\":\"payment-channel\",\"title\":\"Payment Channel\",\"description\":\"Routes payment through enabled channels\","
                + "\"snippets\":[\"wechat-pay\",\"   \"],"
                + "\"sections\":[{\"heading\":\"Available Channels\",\"content\":[\"wechat\",\"alipay\"]}]}"
                + "]"
                + "}";
        List<SourceBatch> sourceBatches = Arrays.asList(
                new SourceBatch("batch-1", "payment-service", Arrays.asList(
                        RawSource.text("payment/analyze.json", jsonPayload, "json", 128L)
                ))
        );

        List<AnalyzedConcept> analyzedConcepts = analyzeNode.analyze("payment-service", sourceBatches);

        assertThat(analyzedConcepts).hasSize(2);
        assertThat(analyzedConcepts.get(0).getConceptId()).isEqualTo("payment-timeout");
        assertThat(analyzedConcepts.get(0).getTitle()).isEqualTo("Payment Timeout");
        assertThat(analyzedConcepts.get(0).getDescription()).isEqualTo("Handles payment timeout recovery");
        assertThat(analyzedConcepts.get(0).getSourcePaths()).containsExactly("payment/analyze.json");
        assertThat(analyzedConcepts.get(0).getSnippets()).containsExactly("order timeout", "refund timeout");
        assertThat(analyzedConcepts.get(0).getSections()).containsExactly(
                new ConceptSection(
                        "Timeout Rules",
                        Arrays.asList("retry=3", "interval=30s"),
                        Arrays.asList("payment/rules.md#timeout-rules")
                )
        );
        assertThat(analyzedConcepts.get(1).getConceptId()).isEqualTo("payment-channel");
        assertThat(analyzedConcepts.get(1).getTitle()).isEqualTo("Payment Channel");
        assertThat(analyzedConcepts.get(1).getDescription()).isEqualTo("Routes payment through enabled channels");
        assertThat(analyzedConcepts.get(1).getSourcePaths()).containsExactly("payment/analyze.json");
        assertThat(analyzedConcepts.get(1).getSnippets()).containsExactly("wechat-pay");
        assertThat(analyzedConcepts.get(1).getSections()).containsExactly(
                new ConceptSection(
                        "Available Channels",
                        Arrays.asList("wechat", "alipay"),
                        Arrays.asList("payment/analyze.json#Available Channels")
                )
        );
    }

    /**
     * 验证分析节点会从截断 JSON 中抢救出已完整输出的概念。
     */
    @Test
    void shouldSalvageCompletedConceptsFromTruncatedJsonPayload() {
        AnalyzeNode analyzeNode = new AnalyzeNode();
        String truncatedJsonPayload = "{"
                + "\"concepts\":["
                + "{\"id\":\"payment-timeout\",\"title\":\"Payment Timeout\",\"description\":\"Timeout description\",\"snippets\":[\"timeout-a\"]},"
                + "{\"id\":\"refund-status\",\"title\":\"Refund Status\",\"description\":\"Refund state machine\",\"snippets\":[\"refund-created\",\"refund-paid\"]},"
                + "{\"id\":\"broken\",\"title\":\"Broken";
        List<SourceBatch> sourceBatches = Arrays.asList(
                new SourceBatch("batch-1", "payment-service", Arrays.asList(
                        RawSource.text("payment/truncated.json", truncatedJsonPayload, "json", 128L)
                ))
        );

        List<AnalyzedConcept> analyzedConcepts = analyzeNode.analyze("payment-service", sourceBatches);

        assertThat(analyzedConcepts).hasSize(2);
        assertThat(analyzedConcepts.get(0).getConceptId()).isEqualTo("payment-timeout");
        assertThat(analyzedConcepts.get(0).getTitle()).isEqualTo("Payment Timeout");
        assertThat(analyzedConcepts.get(0).getDescription()).isEqualTo("Timeout description");
        assertThat(analyzedConcepts.get(0).getSnippets()).containsExactly("timeout-a");
        assertThat(analyzedConcepts.get(1).getConceptId()).isEqualTo("refund-status");
        assertThat(analyzedConcepts.get(1).getTitle()).isEqualTo("Refund Status");
        assertThat(analyzedConcepts.get(1).getDescription()).isEqualTo("Refund state machine");
        assertThat(analyzedConcepts.get(1).getSnippets()).containsExactly("refund-created", "refund-paid");
    }

    /**
     * 验证分析节点会为每个批次生成概念，并保留来源与片段。
     */
    @Test
    void shouldAnalyzeBatchesIntoStableConcepts() {
        AnalyzeNode analyzeNode = new AnalyzeNode();
        List<SourceBatch> sourceBatches = Arrays.asList(
                new SourceBatch("batch-1", "payment-service", Arrays.asList(
                        RawSource.text("payment/a.md", "snippet-a", "md", 9L),
                        RawSource.text("payment/b.md", "snippet-b", "md", 9L)
                ))
        );

        List<AnalyzedConcept> analyzedConcepts = analyzeNode.analyze("payment-service", sourceBatches);

        assertThat(analyzedConcepts).hasSize(1);
        assertThat(analyzedConcepts.get(0).getConceptId()).isEqualTo("payment-service");
        assertThat(analyzedConcepts.get(0).getTitle()).isEqualTo("2 份资料概览");
        assertThat(analyzedConcepts.get(0).getTitleSource()).isEqualTo("NEUTRAL_MULTI_SOURCE");
        assertThat(analyzedConcepts.get(0).getSourcePaths()).containsExactly("payment/a.md", "payment/b.md");
        assertThat(analyzedConcepts.get(0).getSnippets()).containsExactly("snippet-a", "snippet-b");
        assertThat(analyzedConcepts.get(0).getSections()).isEmpty();
    }

    /**
     * 验证分析节点在缺少结构化 JSON 时会调用 LLM，并解析代码块中的概念结果。
     */
    @Test
    void shouldUseLlmGatewayWhenStructuredPayloadIsAbsent() {
        LlmGateway llmGateway = new LlmGateway(
                new StaticLlmClient("""
                        ```json
                        {
                          "concepts":[
                            {
                              "id":"payment-timeout",
                              "title":"支付超时",
                              "description":"处理支付超时后的恢复策略",
                              "sources":[{"path":"payment/order.md","location":"Page 1"}],
                              "relationships":[]
                            }
                          ],
                          "controversies":[],
                          "gaps":[]
                        }
                        ```
                        """),
                new StaticLlmClient("{}"),
                new NoopRedisKeyValueStore(),
                createLlmProperties()
        );
        AnalyzeNode analyzeNode = new AnalyzeNode(llmGateway);
        List<SourceBatch> sourceBatches = Arrays.asList(
                new SourceBatch("batch-1", "payment-service", Arrays.asList(
                        RawSource.text("payment/order.md", "timeout retry rule", "md", 18L)
                ))
        );

        List<AnalyzedConcept> analyzedConcepts = analyzeNode.analyze("payment-service", sourceBatches);

        assertThat(analyzedConcepts).hasSize(1);
        assertThat(analyzedConcepts.get(0).getConceptId()).isEqualTo("payment-timeout");
        assertThat(analyzedConcepts.get(0).getTitle()).isEqualTo("支付超时");
        assertThat(analyzedConcepts.get(0).getDescription()).isEqualTo("处理支付超时后的恢复策略");
        assertThat(analyzedConcepts.get(0).getSourcePaths()).containsExactly("payment/order.md");
        assertThat(analyzedConcepts.get(0).getAnalysisMode()).isEqualTo("LLM");
        assertThat(analyzedConcepts.get(0).getFailureReason()).isNull();
        assertThat(analyzedConcepts.get(0).getTitleSource()).isNull();
    }

    /**
     * 验证 LLM 返回无法解析时，分析节点会回退为最小概念。
     */
    @Test
    void shouldFallbackToMinimalConceptWhenLlmResultIsUnparseable() {
        LlmGateway llmGateway = new LlmGateway(
                new StaticLlmClient("not-json-response"),
                new StaticLlmClient("{}"),
                new NoopRedisKeyValueStore(),
                createLlmProperties()
        );
        AnalyzeNode analyzeNode = new AnalyzeNode(llmGateway);
        List<SourceBatch> sourceBatches = Arrays.asList(
                new SourceBatch("batch-1", "payment-service", Arrays.asList(
                        RawSource.text("payment/a.md", "snippet-a", "md", 9L),
                        RawSource.text("payment/b.md", "snippet-b", "md", 9L)
                ))
        );

        List<AnalyzedConcept> analyzedConcepts = analyzeNode.analyze("payment-service", sourceBatches);

        assertThat(analyzedConcepts).hasSize(1);
        assertThat(analyzedConcepts.get(0).getConceptId()).isEqualTo("payment-service");
        assertThat(analyzedConcepts.get(0).getTitle()).isEqualTo("2 份资料概览");
        assertThat(analyzedConcepts.get(0).getSnippets()).containsExactly("snippet-a", "snippet-b");
        assertThat(analyzedConcepts.get(0).getAnalysisMode()).isEqualTo("FALLBACK");
        assertThat(analyzedConcepts.get(0).getFailureReason()).isEqualTo("EMPTY_RESULT");
        assertThat(analyzedConcepts.get(0).getTitleSource()).isEqualTo("NEUTRAL_MULTI_SOURCE");
    }

    /**
     * 验证 LLM 超时等调用失败时，分析节点会保留失败原因并继续落到 fallback。
     */
    @Test
    void shouldCaptureFailureReasonWhenLlmAnalyzeFails() {
        LlmGateway llmGateway = new LlmGateway(
                new ThrowingLlmClient(new ResourceAccessException("read timed out", new SocketTimeoutException("read timed out"))),
                new StaticLlmClient("{}"),
                new NoopRedisKeyValueStore(),
                createLlmProperties()
        );
        AnalyzeNode analyzeNode = new AnalyzeNode(llmGateway);
        List<SourceBatch> sourceBatches = Arrays.asList(
                new SourceBatch("batch-1", "payment-service", Arrays.asList(
                        RawSource.text("payment/a.md", "snippet-a", "md", 9L)
                ))
        );

        List<AnalyzedConcept> analyzedConcepts = analyzeNode.analyze("payment-service", sourceBatches);

        assertThat(analyzedConcepts).hasSize(1);
        assertThat(analyzedConcepts.get(0).getAnalysisMode()).isEqualTo("FALLBACK");
        assertThat(analyzedConcepts.get(0).getFailureReason()).isEqualTo("TIMEOUT");
        assertThat(analyzedConcepts.get(0).getTitle()).isEqualTo("Payment Service");
        assertThat(analyzedConcepts.get(0).getTitleSource()).isEqualTo("GROUP_KEY");
    }

    /**
     * 验证小型单文件资料会生成轻量概念，而不是直接退回目录级 fallback。
     */
    @Test
    void shouldBuildLightweightConceptForSmallSingleSourceDocument() {
        AnalyzeNode analyzeNode = new AnalyzeNode(null, null, createDocumentTopicCompilerProperties());
        String content = """
                # 服务探针与事件响应协同手册

                当前资料记录了服务探针在事件响应中的职责与边界。
                它说明了 readiness、liveness 和 startup probe 的区别，以及如何配合人工确认。
                还补充了告警分流、人工确认和回滚步骤要同步更新到操作手册。
                """.trim();
        List<SourceBatch> sourceBatches = Arrays.asList(
                new SourceBatch("batch-1", "01_markdown", Arrays.asList(
                        RawSource.extracted(
                                "01_markdown/probe-and-incident-operations.md",
                                content,
                                "md",
                                content.length(),
                                "{\"documentTitle\":\"服务探针与事件响应协同手册\"}",
                                false,
                                "01_markdown/probe-and-incident-operations.md"
                        )
                ))
        );

        List<AnalyzedConcept> analyzedConcepts = analyzeNode.analyze("01_markdown", sourceBatches);

        assertThat(analyzedConcepts).hasSize(1);
        assertThat(analyzedConcepts.get(0).getTitle()).isEqualTo("服务探针与事件响应协同手册");
        assertThat(analyzedConcepts.get(0).getAnalysisMode()).isEqualTo("LIGHTWEIGHT_SMALL_DOC");
        assertThat(analyzedConcepts.get(0).getTitleSource()).isEqualTo("DOCUMENT_TITLE");
        assertThat(analyzedConcepts.get(0).getDescription()).contains("服务探针在事件响应中的职责与边界");
        assertThat(analyzedConcepts.get(0).getSections()).hasSize(1);
        assertThat(analyzedConcepts.get(0).getSections().get(0).getContentLines())
                .anyMatch(line -> line.contains("readiness、liveness 和 startup probe"));
    }

    /**
     * 验证多来源小型结构化资料会逐源生成轻量概念，而不是退回目录级占位概念。
     */
    @Test
    void shouldCreatePerSourceLightweightConceptsForSmallStructuredBatch() {
        AnalyzeNode analyzeNode = new AnalyzeNode(null, null, createDocumentTopicCompilerProperties());
        String grpcYaml = """
                apiVersion: v1
                kind: Pod
                metadata:
                  name: grpc-health
                spec:
                  containers:
                    - name: app
                      livenessProbe:
                        grpc:
                          port: 2379
                """.trim();
        String httpYaml = """
                apiVersion: v1
                kind: Pod
                metadata:
                  name: http-health
                spec:
                  containers:
                    - name: app
                      readinessProbe:
                        httpGet:
                          path: /healthz
                          port: 18082
                """.trim();
        List<SourceBatch> sourceBatches = Arrays.asList(
                new SourceBatch("batch-1", "02_structured", Arrays.asList(
                        RawSource.text("02_structured/grpc-liveness.yaml", grpcYaml, "yaml", grpcYaml.length()),
                        RawSource.text("02_structured/http-liveness.yaml", httpYaml, "yaml", httpYaml.length())
                ))
        );

        List<AnalyzedConcept> analyzedConcepts = analyzeNode.analyze("02_structured", sourceBatches);

        assertThat(analyzedConcepts).hasSize(2);
        assertThat(analyzedConcepts)
                .extracting(AnalyzedConcept::getTitle)
                .containsExactly("Grpc Liveness", "Http Liveness");
        assertThat(analyzedConcepts)
                .extracting(AnalyzedConcept::getAnalysisMode)
                .containsOnly("LIGHTWEIGHT_SMALL_DOC");
        assertThat(analyzedConcepts)
                .extracting(AnalyzedConcept::getTitleSource)
                .containsOnly("FILE_STEM");
        assertThat(analyzedConcepts)
                .extracting(AnalyzedConcept::getConceptId)
                .allMatch(conceptId -> !"02-structured".equals(conceptId));
    }

    /**
     * 验证小资料轻量概念阈值会遵循编译配置，而不是写死在 AnalyzeNode 内部。
     */
    @Test
    void shouldHonorLightweightThresholdsFromCompilerProperties() {
        CompilerProperties compilerProperties = createDocumentTopicCompilerProperties();
        compilerProperties.getDocumentTopics().setLightweightMinTotalChars(500);
        AnalyzeNode analyzeNode = new AnalyzeNode(null, null, compilerProperties);
        String content = """
                # 轻量资料

                第一条说明。
                第二条说明。
                第三条说明。
                """.trim();
        List<SourceBatch> sourceBatches = Arrays.asList(
                new SourceBatch("batch-1", "small-doc", Arrays.asList(
                        RawSource.extracted(
                                "small-doc/brief.md",
                                content,
                                "md",
                                content.length(),
                                "{\"documentTitle\":\"轻量资料\"}",
                                false,
                                "small-doc/brief.md"
                        )
                ))
        );

        List<AnalyzedConcept> analyzedConcepts = analyzeNode.analyze("small-doc", sourceBatches);

        assertThat(analyzedConcepts).hasSize(1);
        assertThat(analyzedConcepts.get(0).getTitle()).isEqualTo("轻量资料");
        assertThat(analyzedConcepts.get(0).getAnalysisMode()).isEqualTo("FALLBACK");
        assertThat(analyzedConcepts.get(0).getTitleSource()).isEqualTo("DOCUMENT_TITLE");
    }

    /**
     * 验证单来源 fallback 会在缺少 documentTitle 时优先使用可读文件 stem，而不是目录分组名。
     */
    @Test
    void shouldPreferReadableFileStemForSingleSourceFallback() {
        CompilerProperties compilerProperties = createDocumentTopicCompilerProperties();
        compilerProperties.getDocumentTopics().setLightweightMinTotalChars(500);
        AnalyzeNode analyzeNode = new AnalyzeNode(null, null, compilerProperties);
        List<SourceBatch> sourceBatches = Arrays.asList(
                new SourceBatch("batch-1", "04_office", Arrays.asList(
                        RawSource.text(
                                "04_office/incident-response-checklists-lite.xlsx",
                                "checklist",
                                "xlsx",
                                9L
                        )
                ))
        );

        List<AnalyzedConcept> analyzedConcepts = analyzeNode.analyze("04_office", sourceBatches);

        assertThat(analyzedConcepts).hasSize(1);
        assertThat(analyzedConcepts.get(0).getAnalysisMode()).isEqualTo("FALLBACK");
        assertThat(analyzedConcepts.get(0).getTitle()).isEqualTo("Incident Response Checklists Lite");
        assertThat(analyzedConcepts.get(0).getTitleSource()).isEqualTo("FILE_STEM");
    }

    /**
     * 验证长结构化文档在缺少结构化结果时，会按专题标题拆成多个概念。
     */
    @Test
    void shouldSplitLongStructuredDocumentIntoTopicConcepts() {
        AnalyzeNode analyzeNode = new AnalyzeNode(null, null, createDocumentTopicCompilerProperties());
        String content = buildLongStructuredDocument();
        List<SourceBatch> sourceBatches = Arrays.asList(
                new SourceBatch("batch-1", "platform-guide", Arrays.asList(
                        RawSource.text("docs/test/platform-guide.pdf", content, "pdf", content.length())
                ))
        );

        List<AnalyzedConcept> analyzedConcepts = analyzeNode.analyze("platform-guide", sourceBatches);

        assertThat(analyzedConcepts).hasSize(4);
        assertThat(analyzedConcepts)
                .extracting(AnalyzedConcept::getTitle)
                .containsExactly("Overview", "Storage Layout", "Runtime Refresh", "Acceptance Flow");
        assertThat(analyzedConcepts.get(0).getConceptId()).isEqualTo("platform-guide-overview");
        assertThat(analyzedConcepts.get(2).getConceptId()).isEqualTo("platform-guide-runtime-refresh");
        assertThat(analyzedConcepts.get(2).getSourcePaths()).containsExactly("docs/test/platform-guide.pdf");
        assertThat(analyzedConcepts.get(2).getAnalysisMode()).isEqualTo("TOPIC");
        assertThat(analyzedConcepts.get(2).getSections()).isNotEmpty();
        assertThat(analyzedConcepts.get(2).getSections().get(0)).isEqualTo(
                new ConceptSection(
                        "Runtime Refresh",
                        analyzedConcepts.get(2).getSections().get(0).getContentLines(),
                        Arrays.asList("docs/test/platform-guide.pdf#Page 3")
                )
        );
        assertThat(analyzedConcepts.get(2).getSections().get(0).getContentLines())
                .anyMatch(line -> line.contains("runtime_refresh_token"));
    }

    /**
     * 验证长文档专题拆分会跳过 fenced code block 内的 PlantUML / plain 文本，避免把图节点误拆成专题。
     */
    @Test
    void shouldIgnoreHeadingsInsideFencedCodeBlocks() {
        CompilerProperties compilerProperties = createDocumentTopicCompilerProperties();
        compilerProperties.getDocumentTopics().setMediumDocumentMinChars(200);
        compilerProperties.getDocumentTopics().setMinHeadingsForMediumDocument(2);
        AnalyzeNode analyzeNode = new AnalyzeNode(null, null, compilerProperties);
        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append("=== Page: 1 ===\n");
        contentBuilder.append("1. 正式主题\n");
        for (int index = 0; index < 60; index++) {
            contentBuilder.append("这里是主题说明，第 ").append(index + 1).append(" 条。\n");
        }
        contentBuilder.append("\n```plain\n");
        contentBuilder.append("actor \"D OMS\" as OMS\n");
        contentBuilder.append("Participant \"S4\" as S4\n");
        contentBuilder.append("alt 卡号以8开头\n");
        contentBuilder.append("MQ -> Consumer : CAC同步\n");
        contentBuilder.append("```\n\n");
        contentBuilder.append("=== Page: 2 ===\n");
        contentBuilder.append("2. 第二主题\n");
        for (int index = 0; index < 60; index++) {
            contentBuilder.append("这里是第二主题说明，第 ").append(index + 1).append(" 条。\n");
        }
        String content = contentBuilder.toString();
        List<SourceBatch> sourceBatches = Arrays.asList(
                new SourceBatch("batch-1", "code-fence-guide", Arrays.asList(
                        RawSource.text("docs/test/code-fence-guide.md", content, "md", content.length())
                ))
        );

        List<AnalyzedConcept> analyzedConcepts = analyzeNode.analyze("code-fence-guide", sourceBatches);

        assertThat(analyzedConcepts).hasSize(2);
        assertThat(analyzedConcepts)
                .extracting(AnalyzedConcept::getTitle)
                .containsExactly("正式主题", "第二主题");
    }

    /**
     * 验证专题正文中的子标题与列表块会保留成多个结构化 section。
     */
    @Test
    void shouldSplitTopicBodyIntoStructuredSections() {
        CompilerProperties compilerProperties = createDocumentTopicCompilerProperties();
        compilerProperties.getDocumentTopics().setMediumDocumentMinChars(200);
        compilerProperties.getDocumentTopics().setMinHeadingsForMediumDocument(2);
        AnalyzeNode analyzeNode = new AnalyzeNode(null, null, compilerProperties);
        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append("=== Page: 1 ===\n");
        contentBuilder.append("1. 灰度策略\n");
        for (int index = 0; index < 30; index++) {
            contentBuilder.append("这里是灰度背景说明，第 ").append(index + 1).append(" 条。\n");
        }
        contentBuilder.append("- **第一批：场景6**\n");
        contentBuilder.append("第二批：场景7\n");
        contentBuilder.append("第三批：场景2/4\n");
        contentBuilder.append("第四批：场景1/3\n");
        for (int index = 0; index < 30; index++) {
            contentBuilder.append("这里是灰度补充说明，第 ").append(index + 1).append(" 条。\n");
        }
        contentBuilder.append("=== Page: 2 ===\n");
        contentBuilder.append("2. 第二主题\n");
        for (int index = 0; index < 60; index++) {
            contentBuilder.append("这里是第二主题说明，第 ").append(index + 1).append(" 条。\n");
        }
        List<SourceBatch> sourceBatches = Arrays.asList(
                new SourceBatch("batch-1", "sectioned-guide", Arrays.asList(
                        RawSource.text("docs/test/sectioned-guide.md", contentBuilder.toString(), "md", contentBuilder.length())
                ))
        );

        List<AnalyzedConcept> analyzedConcepts = analyzeNode.analyze("sectioned-guide", sourceBatches);

        assertThat(analyzedConcepts).hasSize(2);
        List<ConceptSection> sections = analyzedConcepts.get(0).getSections();
        assertThat(sections).hasSizeGreaterThanOrEqualTo(2);
        assertThat(sections)
                .extracting(ConceptSection::getHeading)
                .contains("灰度策略", "- **第一批：场景6**");
        assertThat(sections.stream()
                .filter(section -> "- **第一批：场景6**".equals(section.getHeading()))
                .findFirst()
                .orElseThrow()
                .getContentLines()).contains("第二批：场景7", "第三批：场景2/4");
    }

    /**
     * 验证长结构化文档会优先按自身标题拆分，不被 LLM 单概念结果收敛回大文章。
     */
    @Test
    void shouldPreferDocumentTopicsBeforeLlmForLongStructuredDocument() {
        LlmGateway llmGateway = new LlmGateway(
                new StaticLlmClient("{\"concepts\":[{\"id\":\"one-big-topic\",\"title\":\"One Big Topic\"}]}"),
                new StaticLlmClient("{}"),
                new NoopRedisKeyValueStore(),
                createLlmProperties()
        );
        AnalyzeNode analyzeNode = new AnalyzeNode(llmGateway, null, createDocumentTopicCompilerProperties());
        String content = buildLongStructuredDocument();
        List<SourceBatch> sourceBatches = Arrays.asList(
                new SourceBatch("batch-1", "platform-guide", Arrays.asList(
                        RawSource.text("docs/test/platform-guide.pdf", content, "pdf", content.length())
                ))
        );

        List<AnalyzedConcept> analyzedConcepts = analyzeNode.analyze("platform-guide", sourceBatches);

        assertThat(analyzedConcepts).hasSize(4);
        assertThat(analyzedConcepts)
                .extracting(AnalyzedConcept::getConceptId)
                .doesNotContain("one-big-topic");
    }

    /**
     * 验证长文档专题拆分可以通过配置关闭。
     */
    @Test
    void shouldAllowDisablingDocumentTopicExtraction() {
        CompilerProperties compilerProperties = createDocumentTopicCompilerProperties();
        compilerProperties.getDocumentTopics().setEnabled(false);
        AnalyzeNode analyzeNode = new AnalyzeNode(null, null, compilerProperties);
        String content = buildLongStructuredDocument();
        List<SourceBatch> sourceBatches = Arrays.asList(
                new SourceBatch("batch-1", "platform-guide", Arrays.asList(
                        RawSource.text("docs/test/platform-guide.pdf", content, "pdf", content.length())
                ))
        );

        List<AnalyzedConcept> analyzedConcepts = analyzeNode.analyze("platform-guide", sourceBatches);

        assertThat(analyzedConcepts).hasSize(1);
        assertThat(analyzedConcepts.get(0).getConceptId()).isEqualTo("platform-guide");
        assertThat(analyzedConcepts.get(0).getTitle()).isEqualTo("Platform Guide");
    }

    /**
     * 验证过多专题的长文档会被收敛为单个 overview concept，避免过度进入 Writer。
     */
    @Test
    void shouldCollapseOverFragmentedDocumentTopicsIntoOverviewConcept() {
        AnalyzeNode analyzeNode = new AnalyzeNode(null, null, createDocumentTopicCompilerProperties());
        String content = buildOverFragmentedStructuredDocument();
        List<SourceBatch> sourceBatches = Arrays.asList(
                new SourceBatch("batch-1", "ops-handbook", Arrays.asList(
                        RawSource.text("docs/test/ops-handbook.pdf", content, "pdf", content.length())
                ))
        );

        List<AnalyzedConcept> analyzedConcepts = analyzeNode.analyze("ops-handbook", sourceBatches);

        assertThat(analyzedConcepts).hasSize(1);
        assertThat(analyzedConcepts.get(0).getConceptId()).isEqualTo("document-overview-docs-test-ops-handbook");
        assertThat(analyzedConcepts.get(0).getTitle()).isEqualTo("Document Overview - ops-handbook");
        assertThat(analyzedConcepts.get(0).getSourcePaths()).containsExactly("docs/test/ops-handbook.pdf");
        assertThat(analyzedConcepts.get(0).getSnippets()).contains(
                "Source path: docs/test/ops-handbook.pdf",
                "Topic count: 10",
                "Route: collapse over-fragmented document topics into one overview concept"
        );
        assertThat(String.join("\n", analyzedConcepts.get(0).getSnippets()))
                .contains("Representative topics:");
    }

    /**
     * 构建测试用专题拆分配置。
     *
     * @return 编译配置
     */
    private CompilerProperties createDocumentTopicCompilerProperties() {
        CompilerProperties compilerProperties = new CompilerProperties();
        CompilerProperties.DocumentTopics documentTopics = compilerProperties.getDocumentTopics();
        documentTopics.setPageMarkerPattern("^===\\s*Page:\\s*(\\d+)\\s*===$");
        documentTopics.setHeadingBoundaryPattern("^[：:\\-—\\s]+|[：:\\-—\\s]+$");
        documentTopics.setIgnoredLinePrefixes(Arrays.asList("table_row:", "==="));
        documentTopics.setHeadingTerminalPunctuations(Arrays.asList("。", "；", ";", "，", ","));
        documentTopics.setBodyTerminalPunctuations(Arrays.asList("。", "."));
        documentTopics.setHeadingPatterns(Arrays.asList(
                new CompilerProperties.HeadingPatternRule(
                        "numeric",
                        "^(\\d+(?:\\.\\d+){0,4})[、.．\\s]+(.+?)\\s*$",
                        2,
                        1,
                        1,
                        "numeric-depth"
                )
        ));
        return compilerProperties;
    }

    /**
     * 构建长结构化文档样本。
     *
     * @return 长结构化文档
     */
    private String buildLongStructuredDocument() {
        StringBuilder builder = new StringBuilder();
        appendTopic(builder, 1, "Overview", "Describe module boundary and ownership for source_alpha.");
        appendTopic(builder, 2, "Storage Layout", "Describe table_alpha, table_beta and identifier mapping.");
        appendTopic(builder, 3, "Runtime Refresh", "Describe runtime_refresh_token and refresh window handling.");
        appendTopic(builder, 4, "Acceptance Flow", "Describe verification batches and acceptance checkpoints.");
        return builder.toString();
    }

    /**
     * 构建过度专题化的长文档样本。
     *
     * @return 长文档样本
     */
    private String buildOverFragmentedStructuredDocument() {
        StringBuilder builder = new StringBuilder();
        for (int index = 1; index <= 10; index++) {
            appendTopic(
                    builder,
                    index,
                    "Topic " + index,
                    "Describe operational checklist item " + index + " and its evidence chain."
            );
        }
        return builder.toString();
    }

    /**
     * 追加专题内容。
     *
     * @param builder 内容构建器
     * @param page 页码
     * @param title 标题
     * @param line 内容行
     */
    private void appendTopic(StringBuilder builder, int page, String title, String line) {
        builder.append("=== Page: ").append(page).append(" ===").append("\n");
        builder.append(page).append(". ").append(title).append("\n");
        for (int index = 0; index < 80; index++) {
            builder.append(line).append(" 第 ").append(index + 1).append(" 条。").append("\n");
        }
        builder.append("\n");
    }

    /**
     * 创建测试用 LLM 配置。
     *
     * @return LLM 配置
     */
    private LlmProperties createLlmProperties() {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setCompileModel("openai");
        llmProperties.setReviewerModel("anthropic");
        llmProperties.setBudgetUsd(10.0D);
        llmProperties.setCacheTtlSeconds(3600L);
        llmProperties.setCacheKeyPrefix("llm:test:");
        return llmProperties;
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
     * 固定抛异常的 LLM 客户端。
     *
     * @author xiexu
     */
    private static class ThrowingLlmClient implements LlmClient {

        private final RuntimeException runtimeException;

        private ThrowingLlmClient(RuntimeException runtimeException) {
            this.runtimeException = runtimeException;
        }

        @Override
        public LlmCallResult call(String systemPrompt, String userPrompt) {
            throw runtimeException;
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
        public void set(String key, String value, java.time.Duration ttl) {
        }

        @Override
        public Long getExpire(String key) {
            return null;
        }

        @Override
        public void deleteByPrefix(String keyPrefix) {
        }
    }

}
