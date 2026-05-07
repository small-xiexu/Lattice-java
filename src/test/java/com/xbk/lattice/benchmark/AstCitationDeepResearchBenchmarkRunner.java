package com.xbk.lattice.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.file.FileSystemSaver;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.xbk.lattice.compiler.ast.domain.AstEntity;
import com.xbk.lattice.compiler.ast.domain.AstEntityType;
import com.xbk.lattice.compiler.ast.domain.AstFact;
import com.xbk.lattice.compiler.ast.domain.AstGraphExtractReport;
import com.xbk.lattice.compiler.ast.domain.AstRelation;
import com.xbk.lattice.compiler.graph.ArticleReviewEnvelope;
import com.xbk.lattice.compiler.graph.CompileGraphState;
import com.xbk.lattice.compiler.graph.CompileGraphStateMapper;
import com.xbk.lattice.compiler.graph.CompileWorkingSetProperties;
import com.xbk.lattice.compiler.graph.RedisCompileWorkingSetStore;
import com.xbk.lattice.compiler.graph.ReviewPartition;
import com.xbk.lattice.compiler.graph.node.CaptureRepoSnapshotNode;
import com.xbk.lattice.compiler.graph.node.FinalizeJobNode;
import com.xbk.lattice.compiler.graph.node.GenerateSynthesisArtifactsNode;
import com.xbk.lattice.compiler.graph.node.PersistArticlesNode;
import com.xbk.lattice.compiler.graph.node.RebuildArticleChunksNode;
import com.xbk.lattice.compiler.graph.node.RefreshVectorIndexNode;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.compiler.domain.SourceBatch;
import com.xbk.lattice.compiler.service.ArticlePersistSupport;
import com.xbk.lattice.compiler.service.CompilationWalStore;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ArticleSourceRefRecord;
import com.xbk.lattice.infra.persistence.ArticleSourceRefJdbcRepository;
import com.xbk.lattice.infra.persistence.GraphEntityJdbcRepository;
import com.xbk.lattice.infra.persistence.GraphFactJdbcRepository;
import com.xbk.lattice.infra.persistence.GraphRelationJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.query.citation.Citation;
import com.xbk.lattice.query.citation.CitationCheckReport;
import com.xbk.lattice.query.citation.CitationCheckService;
import com.xbk.lattice.query.citation.CitationExtractor;
import com.xbk.lattice.query.citation.CitationSourceType;
import com.xbk.lattice.query.citation.CitationValidationResult;
import com.xbk.lattice.query.citation.CitationValidationStatus;
import com.xbk.lattice.query.citation.CitationValidator;
import com.xbk.lattice.query.citation.ClaimSegment;
import com.xbk.lattice.query.deepresearch.domain.EvidenceCard;
import com.xbk.lattice.query.deepresearch.domain.EvidenceLedger;
import com.xbk.lattice.query.deepresearch.domain.DeepResearchAuditSnapshot;
import com.xbk.lattice.query.deepresearch.domain.DeepResearchSynthesisResult;
import com.xbk.lattice.query.deepresearch.domain.LayerSummary;
import com.xbk.lattice.query.deepresearch.domain.LayeredResearchPlan;
import com.xbk.lattice.query.deepresearch.domain.ResearchLayer;
import com.xbk.lattice.query.deepresearch.domain.ResearchTask;
import com.xbk.lattice.query.deepresearch.graph.DeepResearchGraphDefinitionFactory;
import com.xbk.lattice.query.deepresearch.graph.DeepResearchState;
import com.xbk.lattice.query.deepresearch.graph.DeepResearchStateMapper;
import com.xbk.lattice.query.deepresearch.store.DeepResearchWorkingSetProperties;
import com.xbk.lattice.query.deepresearch.store.RedisDeepResearchWorkingSetStore;
import com.xbk.lattice.query.deepresearch.service.DeepResearchExecutionContext;
import com.xbk.lattice.query.deepresearch.service.DeepResearchExecutionRegistry;
import com.xbk.lattice.query.deepresearch.service.DeepResearchResearcherService;
import com.xbk.lattice.query.service.ArticleChunkFtsSearchService;
import com.xbk.lattice.query.service.ChunkVectorSearchService;
import com.xbk.lattice.query.service.ContributionSearchService;
import com.xbk.lattice.query.service.FactCardFtsSearchService;
import com.xbk.lattice.query.service.FactCardVectorSearchService;
import com.xbk.lattice.query.evidence.domain.EvidenceAnchor;
import com.xbk.lattice.query.evidence.domain.EvidenceAnchorSourceType;
import com.xbk.lattice.query.evidence.domain.AnswerProjection;
import com.xbk.lattice.query.evidence.domain.AnswerProjectionBundle;
import com.xbk.lattice.query.evidence.domain.FactFinding;
import com.xbk.lattice.query.evidence.domain.FactValueType;
import com.xbk.lattice.query.evidence.domain.FindingSupportLevel;
import com.xbk.lattice.query.evidence.domain.ProjectionCitationFormat;
import com.xbk.lattice.query.evidence.domain.ProjectionStatus;
import com.xbk.lattice.query.deepresearch.service.DeepResearchSynthesizer;
import com.xbk.lattice.query.domain.ReviewResult;
import com.xbk.lattice.query.graph.QueryAnswerProjectionBuilder;
import com.xbk.lattice.query.graph.QueryGraphConditions;
import com.xbk.lattice.query.graph.QueryGraphDefinitionFactory;
import com.xbk.lattice.query.graph.QueryGraphState;
import com.xbk.lattice.query.graph.QueryGraphStateMapper;
import com.xbk.lattice.query.service.FtsSearchService;
import com.xbk.lattice.query.service.GraphSearchService;
import com.xbk.lattice.query.service.KnowledgeSearchService;
import com.xbk.lattice.query.service.AnswerShapeClassifier;
import com.xbk.lattice.query.service.QueryIntentClassifier;
import com.xbk.lattice.query.service.QueryArticleHit;
import com.xbk.lattice.query.service.QueryCacheStore;
import com.xbk.lattice.query.service.QueryReviewProperties;
import com.xbk.lattice.query.service.QuerySearchProperties;
import com.xbk.lattice.query.graph.QueryWorkingSetProperties;
import com.xbk.lattice.query.graph.RedisQueryWorkingSetStore;
import com.xbk.lattice.query.service.QueryRetrievalSettingsService;
import com.xbk.lattice.query.service.QueryRetrievalSettingsState;
import com.xbk.lattice.query.service.QueryRewriteService;
import com.xbk.lattice.query.service.RedisKeyValueStore;
import com.xbk.lattice.query.service.RefKeySearchService;
import com.xbk.lattice.query.service.RetrievalQueryContext;
import com.xbk.lattice.query.service.RetrievalStrategy;
import com.xbk.lattice.query.service.RetrievalStrategyResolver;
import com.xbk.lattice.query.service.ReviewResultParser;
import com.xbk.lattice.query.service.ReviewerAgent;
import com.xbk.lattice.query.service.ReviewerGateway;
import com.xbk.lattice.query.service.RrfFusionService;
import com.xbk.lattice.query.service.SourceChunkFtsSearchService;
import com.xbk.lattice.query.service.SourceSearchService;
import com.xbk.lattice.query.service.VectorSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AST / Citation / Deep Research 对标 benchmark runner
 *
 * 职责：读取共享题集，执行 Java shadow、TS shadow，并产出 gap report
 */
class AstCitationDeepResearchBenchmarkRunner {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private static final String STATUS_LEADING = "LEADING";

    private static final String STATUS_NOT_LEADING = "NOT_LEADING";

    private static final String STATUS_AVAILABLE = "AVAILABLE";

    private static final String STATUS_JAVA_ONLY = "JAVA_ONLY";

    private static final String STATUS_MISSING_DATASET = "MISSING_DATASET";

    private static final String STATUS_NOT_INCLUDED = "NOT_INCLUDED";

    private static final Path PROJECT_ROOT = Path.of(System.getProperty("user.dir"));

    private static final Path DATASET_PATH = PROJECT_ROOT.resolve(
            "src/test/resources/benchmarks/ast-citation-deepresearch/shared-dataset.json"
    );

    private static final Path TS_RUNNER_PATH = PROJECT_ROOT.resolve(
            ".claude/benchmarks/ast-citation-deepresearch/ts-shadow-runner.ts"
    );

    private static final Path REPORT_DIR = PROJECT_ROOT.resolve(
            "target/benchmark-reports/ast-citation-deepresearch"
    );

    private static final Path LOCAL_NPM_CACHE = PROJECT_ROOT.resolve(".codex/npm-cache");

    private static final String BENCHMARK_DB_URL = System.getProperty(
            "lattice.benchmark.jdbc.url",
            "jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge"
    );

    private static final String BENCHMARK_DB_USERNAME = System.getProperty(
            "lattice.benchmark.jdbc.username",
            "postgres"
    );

    private static final String BENCHMARK_DB_PASSWORD = System.getProperty(
            "lattice.benchmark.jdbc.password",
            "postgres"
    );

    @Test
    void shouldGenerateGapReport() throws Exception {
        Files.createDirectories(REPORT_DIR);
        JsonNode dataset = OBJECT_MAPPER.readTree(Files.readString(DATASET_PATH, StandardCharsets.UTF_8));

        ObjectNode javaResult = runJavaShadow(dataset);
        Path javaOutputPath = REPORT_DIR.resolve("java-shadow-result.json");
        Files.writeString(javaOutputPath, OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(javaResult));

        Path tsOutputPath = REPORT_DIR.resolve("ts-shadow-result.json");
        ObjectNode tsResult = runTsShadow(tsOutputPath);

        ObjectNode realQuestionReplayBenchmark = runRealQuestionReplayBenchmark(
                dataset.withArray("realQuestionReplayScenarios")
        );
        ObjectNode resumeRecoveryBenchmark = runResumeRecoveryBenchmark();
        ObjectNode comparison = compare(
                javaResult,
                tsResult,
                realQuestionReplayBenchmark,
                resumeRecoveryBenchmark
        );
        Path comparisonOutputPath = REPORT_DIR.resolve("gap-report.json");
        Files.writeString(
                comparisonOutputPath,
                OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(comparison)
        );

        String markdownReport = renderMarkdownReport(dataset, javaResult, tsResult, comparison);
        Files.writeString(REPORT_DIR.resolve("gap-report.md"), markdownReport, StandardCharsets.UTF_8);

        assertThat(javaResult.withArray("scenarios")).isNotEmpty();
        assertThat(tsResult.withArray("scenarios")).isNotEmpty();
        assertThat(markdownReport).contains("对标结论");
    }

    @Test
    void shouldGenerateResumeRecoveryReport() throws Exception {
        Files.createDirectories(REPORT_DIR);

        ObjectNode resumeRecoveryBenchmark = runResumeRecoveryBenchmark();
        Path reportPath = REPORT_DIR.resolve("resume-recovery-report.json");
        Files.writeString(
                reportPath,
                OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(resumeRecoveryBenchmark),
                StandardCharsets.UTF_8
        );

        assertThat(resumeRecoveryBenchmark.path("scenarioCount").asInt()).isEqualTo(3);
        assertThat(resumeRecoveryBenchmark.path("checkpointRecoveryReady").asBoolean()).isTrue();
        assertThat(resumeRecoveryBenchmark.path("endToEndRecoveryReady").asBoolean()).isTrue();
    }

    private ObjectNode runJavaShadow(JsonNode dataset) {
        ArrayNode scenarioResults = OBJECT_MAPPER.createArrayNode();
        for (JsonNode scenario : dataset.withArray("citationScenarios")) {
            scenarioResults.add(runJavaCitationScenario(scenario));
        }
        for (JsonNode scenario : dataset.withArray("deepResearchScenarios")) {
            scenarioResults.add(runJavaDeepResearchScenario(scenario));
        }
        if (dataset.withArray("retrievalScenarios").size() > 0) {
            try (RetrievalBenchmarkHarness retrievalBenchmarkHarness = new RetrievalBenchmarkHarness()) {
                for (JsonNode scenario : dataset.withArray("retrievalScenarios")) {
                    scenarioResults.add(retrievalBenchmarkHarness.runScenario(scenario));
                }
            }
        }
        for (JsonNode scenario : dataset.withArray("graphScenarios")) {
            scenarioResults.add(runJavaGraphScenario(scenario));
        }

        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("runner", "java-shadow");
        payload.put("version", dataset.path("version").asText(""));
        payload.set("scenarios", scenarioResults);
        payload.set("metrics", summarizeMetrics(scenarioResults));
        return payload;
    }

    private ObjectNode runTsShadow(Path outputPath) throws Exception {
        Path tsRepoRoot = Path.of(System.getProperty("lattice.ts.repo", "/Users/sxie/xbk/Lattice"));
        Files.createDirectories(LOCAL_NPM_CACHE);
        ProcessBuilder processBuilder = new ProcessBuilder(
                "npx",
                "-y",
                "tsx",
                TS_RUNNER_PATH.toString(),
                tsRepoRoot.toString(),
                DATASET_PATH.toString(),
                outputPath.toString()
        );
        processBuilder.directory(PROJECT_ROOT.toFile());
        processBuilder.redirectErrorStream(true);
        processBuilder.environment().put("npm_config_cache", LOCAL_NPM_CACHE.toString());
        Process process = processBuilder.start();

        StringBuilder outputBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                outputBuilder.append(line).append('\n');
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("TS shadow runner failed: " + outputBuilder);
        }
        return (ObjectNode) OBJECT_MAPPER.readTree(Files.readString(outputPath, StandardCharsets.UTF_8));
    }

    private ObjectNode runJavaCitationScenario(JsonNode scenario) {
        CitationCheckService citationCheckService = createCitationCheckService(scenario);
        CitationCheckReport report = citationCheckService.check(scenario.path("answer").asText(""));
        int totalClaims = report.getClaimSegments().size();
        int coveredClaims = coveredClaimCount(report);
        ObjectNode result = OBJECT_MAPPER.createObjectNode();
        result.put("id", scenario.path("id").asText(""));
        result.put("kind", "citation");
        result.put("totalClaims", totalClaims);
        result.put("unsupportedClaims", report.getUnsupportedClaimCount());
        result.put("coveredClaims", coveredClaims);
        result.put("verifiedCitations", report.getVerifiedCount());
        result.put("demotedCitations", report.getDemotedCount());
        result.put("unsupportedClaimRate", totalClaims == 0 ? 0.0D : report.getUnsupportedClaimCount() * 1.0D / totalClaims);
        putNullableDouble(
                result,
                "citationPrecision",
                report.getVerifiedCount() + report.getDemotedCount() == 0
                        ? null
                        : report.getVerifiedCount() * 1.0D / (report.getVerifiedCount() + report.getDemotedCount())
        );
        result.put("citationCoverage", totalClaims == 0 ? 0.0D : coveredClaims * 1.0D / totalClaims);
        result.putNull("multiHopCompleteness");
        result.put("matchedExpectedClaims", 0);
        result.put("expectedClaims", 0);
        result.put("conflictSurfaced", false);
        ArrayNode notes = result.putArray("notes");
        for (CitationValidationResult validationResult : report.getResults()) {
            notes.add(validationResult.getStatus().name().toLowerCase(Locale.ROOT) + ":" + validationResult.getTargetKey());
        }
        return result;
    }

    private ObjectNode runJavaDeepResearchScenario(JsonNode scenario) {
        CitationCheckService citationCheckService = createCitationCheckService(scenario);
        DeepResearchSynthesizer deepResearchSynthesizer = new DeepResearchSynthesizer(citationCheckService);

        EvidenceLedger evidenceLedger = new EvidenceLedger();
        for (JsonNode cardNode : scenario.withArray("evidenceCards")) {
            evidenceLedger.addCard(toEvidenceCard(cardNode));
        }

        List<LayerSummary> layerSummaries = new ArrayList<LayerSummary>();
        for (JsonNode layerSummaryNode : scenario.withArray("layerSummaries")) {
            LayerSummary layerSummary = new LayerSummary();
            layerSummary.setLayerIndex(layerSummaryNode.path("layerIndex").asInt(0));
            layerSummary.setSummaryMarkdown(layerSummaryNode.path("summaryMarkdown").asText(""));
            layerSummaries.add(layerSummary);
        }

        var synthesisResult = deepResearchSynthesizer.synthesize(
                scenario.path("question").asText(""),
                layerSummaries,
                evidenceLedger
        );
        CitationCheckReport report = synthesisResult.getCitationCheckReport();
        int totalClaims = report == null ? 0 : report.getClaimSegments().size();
        int coveredClaims = coveredClaimCount(report);
        int expectedClaims = scenario.withArray("expectedClaims").size();
        int matchedExpectedClaims = countMatchedExpectedClaims(
                synthesisResult.getAnswerMarkdown(),
                scenario.withArray("expectedClaims")
        );

        ObjectNode result = OBJECT_MAPPER.createObjectNode();
        result.put("id", scenario.path("id").asText(""));
        result.put("kind", "deepresearch");
        result.put("totalClaims", totalClaims);
        result.put("unsupportedClaims", report == null ? 0 : report.getUnsupportedClaimCount());
        result.put("coveredClaims", coveredClaims);
        result.put("verifiedCitations", report == null ? 0 : report.getVerifiedCount());
        result.put("demotedCitations", report == null ? 0 : report.getDemotedCount());
        result.put("unsupportedClaimRate", totalClaims == 0 ? 0.0D : (report.getUnsupportedClaimCount() * 1.0D / totalClaims));
        putNullableDouble(
                result,
                "citationPrecision",
                report == null || report.getVerifiedCount() + report.getDemotedCount() == 0
                        ? null
                        : report.getVerifiedCount() * 1.0D / (report.getVerifiedCount() + report.getDemotedCount())
        );
        result.put("citationCoverage", totalClaims == 0 ? 0.0D : coveredClaims * 1.0D / totalClaims);
        putNullableDouble(
                result,
                "multiHopCompleteness",
                expectedClaims == 0 ? null : matchedExpectedClaims * 1.0D / expectedClaims
        );
        result.put("matchedExpectedClaims", matchedExpectedClaims);
        result.put("expectedClaims", expectedClaims);
        result.put("conflictSurfaced", synthesisResult.getAnswerMarkdown().contains("## 冲突提示"));
        ArrayNode notes = result.putArray("notes");
        if (synthesisResult.isPartialAnswer()) {
            notes.add("partial_answer");
        }
        if (synthesisResult.isHasConflicts()) {
            notes.add("conflict_detected");
        }
        return result;
    }

    private ObjectNode runJavaGraphScenario(JsonNode scenario) {
        GraphSearchService graphSearchService = new GraphSearchService(
                new BenchmarkGraphEntityJdbcRepository(scenario.withArray("entities")),
                new BenchmarkGraphFactJdbcRepository(scenario.withArray("facts")),
                new BenchmarkGraphRelationJdbcRepository(scenario.withArray("relations")),
                new BenchmarkArticleSourceRefJdbcRepository(scenario.path("articleKeysBySourceFileId")),
                new BenchmarkGraphSourceFileJdbcRepository(scenario.path("sourceFiles"))
        );

        List<QueryArticleHit> hits = graphSearchService.search(scenario.path("question").asText(""), 5);
        String factsBlock = graphSearchService.buildFactsBlock(hits);
        int expectedFacts = scenario.withArray("expectedFactsBlockContains").size();
        int acceptedFacts = 0;
        for (JsonNode expectedFactNode : scenario.withArray("expectedFactsBlockContains")) {
            if (factsBlock.contains(expectedFactNode.asText(""))) {
                acceptedFacts++;
            }
        }

        ObjectNode result = OBJECT_MAPPER.createObjectNode();
        result.put("id", scenario.path("id").asText(""));
        result.put("kind", "graph");
        result.put("totalClaims", 0);
        result.put("unsupportedClaims", 0);
        result.put("coveredClaims", 0);
        result.put("verifiedCitations", 0);
        result.put("demotedCitations", 0);
        result.putNull("unsupportedClaimRate");
        result.putNull("citationPrecision");
        result.putNull("citationCoverage");
        result.putNull("multiHopCompleteness");
        result.put("matchedExpectedClaims", 0);
        result.put("expectedClaims", 0);
        result.put("conflictSurfaced", false);
        result.put("acceptedFacts", acceptedFacts);
        result.put("expectedFacts", expectedFacts);
        result.put("graphFactAcceptedRate", expectedFacts == 0 ? 0.0D : acceptedFacts * 1.0D / expectedFacts);
        ArrayNode notes = result.putArray("notes");
        if (!hits.isEmpty()) {
            notes.add("top_hit=" + hits.get(0).getTitle());
        }
        notes.add("factsBlock=" + factsBlock);
        return result;
    }

    private ObjectNode compare(
            ObjectNode javaResult,
            ObjectNode tsResult,
            ObjectNode realQuestionReplayBenchmark,
            ObjectNode resumeRecoveryBenchmark
    ) {
        Map<String, JsonNode> javaScenarioMap = indexScenarios(javaResult.withArray("scenarios"));
        Map<String, JsonNode> tsScenarioMap = indexScenarios(tsResult.withArray("scenarios"));

        ArrayNode scenarioComparisons = OBJECT_MAPPER.createArrayNode();
        ArrayNode graphComparisons = OBJECT_MAPPER.createArrayNode();
        ArrayNode retrievalComparisons = OBJECT_MAPPER.createArrayNode();
        Map<String, Integer> taxonomyCounts = new LinkedHashMap<String, Integer>();
        ArrayNode nonComparableScenarios = OBJECT_MAPPER.createArrayNode();
        for (Map.Entry<String, JsonNode> entry : javaScenarioMap.entrySet()) {
            String scenarioId = entry.getKey();
            JsonNode javaScenario = entry.getValue();
            JsonNode tsScenario = tsScenarioMap.get(scenarioId);
            String scenarioKind = javaScenario.path("kind").asText("");
            if (tsScenario == null) {
                ObjectNode nonComparable = OBJECT_MAPPER.createObjectNode();
                nonComparable.put("id", scenarioId);
                nonComparable.put("kind", scenarioKind);
                nonComparable.put("reason", scenarioKind + " scenario currently has no TS shadow counterpart");
                nonComparableScenarios.add(nonComparable);
                continue;
            }
            if ("graph".equals(scenarioKind)) {
                ObjectNode graphComparison = OBJECT_MAPPER.createObjectNode();
                graphComparison.put("id", scenarioId);
                graphComparison.put("kind", "graph");
                putNullableDouble(
                        graphComparison,
                        "acceptedRateDelta",
                        nullableDifference(javaScenario, tsScenario, "graphFactAcceptedRate")
                );
                graphComparison.put("javaAcceptedFacts", javaScenario.path("acceptedFacts").asInt(0));
                graphComparison.put("javaExpectedFacts", javaScenario.path("expectedFacts").asInt(0));
                graphComparison.put("tsAcceptedFacts", tsScenario.path("acceptedFacts").asInt(0));
                graphComparison.put("tsExpectedFacts", tsScenario.path("expectedFacts").asInt(0));
                ArrayNode gaps = graphComparison.putArray("gaps");
                if (isLower(javaScenario, tsScenario, "graphFactAcceptedRate")) {
                    gaps.add("graph_fact_gap");
                    taxonomyCounts.merge("graph_fact_gap", 1, Integer::sum);
                }
                graphComparisons.add(graphComparison);
                continue;
            }
            if ("retrieval".equals(scenarioKind)) {
                ObjectNode retrievalComparison = OBJECT_MAPPER.createObjectNode();
                retrievalComparison.put("id", scenarioId);
                retrievalComparison.put("kind", "retrieval");
                putNullableDouble(
                        retrievalComparison,
                        "recallAt5Delta",
                        nullableDifference(javaScenario, tsScenario, "retrievalRecallAt5")
                );
                putNullableDouble(
                        retrievalComparison,
                        "recallAt10Delta",
                        nullableDifference(javaScenario, tsScenario, "retrievalRecallAt10")
                );
                putNullableDouble(
                        retrievalComparison,
                        "mrrDelta",
                        nullableDifference(javaScenario, tsScenario, "reciprocalRank")
                );
                retrievalComparison.put(
                        "javaFirstRelevantRank",
                        nullableIntValue(javaScenario.path("firstRelevantRank"))
                );
                retrievalComparison.put(
                        "tsFirstRelevantRank",
                        nullableIntValue(tsScenario.path("firstRelevantRank"))
                );
                ArrayNode gaps = retrievalComparison.putArray("gaps");
                if (isLower(javaScenario, tsScenario, "retrievalRecallAt10")) {
                    gaps.add("retrieval_recall_gap");
                    taxonomyCounts.merge("retrieval_recall_gap", 1, Integer::sum);
                }
                if (isLower(javaScenario, tsScenario, "reciprocalRank")) {
                    gaps.add("retrieval_rank_gap");
                    taxonomyCounts.merge("retrieval_rank_gap", 1, Integer::sum);
                }
                retrievalComparisons.add(retrievalComparison);
                continue;
            }
            ObjectNode scenarioComparison = OBJECT_MAPPER.createObjectNode();
            scenarioComparison.put("id", scenarioId);
            scenarioComparison.put("kind", scenarioKind);
            scenarioComparison.put("unsupportedDelta", javaScenario.path("unsupportedClaimRate").asDouble(0.0D)
                    - tsScenario.path("unsupportedClaimRate").asDouble(0.0D));
            putNullableDouble(
                    scenarioComparison,
                    "citationPrecisionDelta",
                    nullableDifference(javaScenario, tsScenario, "citationPrecision")
            );
            putNullableDouble(
                    scenarioComparison,
                    "citationCoverageDelta",
                    nullableDifference(javaScenario, tsScenario, "citationCoverage")
            );
            putNullableDouble(
                    scenarioComparison,
                    "multiHopDelta",
                    nullableDifference(javaScenario, tsScenario, "multiHopCompleteness")
            );
            boolean javaConflict = javaScenario.path("conflictSurfaced").asBoolean(false);
            boolean tsConflict = tsScenario.path("conflictSurfaced").asBoolean(false);
            scenarioComparison.put("javaConflictSurfaced", javaConflict);
            scenarioComparison.put("tsConflictSurfaced", tsConflict);
            ArrayNode gaps = scenarioComparison.putArray("gaps");
            if (javaScenario.path("unsupportedClaimRate").asDouble(0.0D) > tsScenario.path("unsupportedClaimRate").asDouble(0.0D)
                    || isLower(javaScenario, tsScenario, "citationPrecision")) {
                gaps.add("citation_error");
                taxonomyCounts.merge("citation_error", 1, Integer::sum);
            }
            if (isLower(javaScenario, tsScenario, "multiHopCompleteness")) {
                gaps.add("multi_hop_missing");
                taxonomyCounts.merge("multi_hop_missing", 1, Integer::sum);
            }
            if (!javaConflict && tsConflict) {
                gaps.add("conflict_not_surfaced");
                taxonomyCounts.merge("conflict_not_surfaced", 1, Integer::sum);
            }
            scenarioComparisons.add(scenarioComparison);
        }

        ObjectNode comparison = OBJECT_MAPPER.createObjectNode();
        comparison.set("javaMetrics", javaResult.path("metrics"));
        comparison.set("tsMetrics", tsResult.path("metrics"));
        comparison.set("scenarioComparisons", scenarioComparisons);
        comparison.set("graphComparisons", graphComparisons);
        comparison.set("retrievalComparisons", retrievalComparisons);
        comparison.set("nonComparableScenarios", nonComparableScenarios);
        ObjectNode taxonomyNode = comparison.putObject("topGaps");
        taxonomyCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .forEach(entry -> taxonomyNode.put(entry.getKey(), entry.getValue()));
        boolean sharedBenchmarkOutperform = javaResult.path("metrics").path("unsupportedClaimRate").asDouble(Double.MAX_VALUE)
                < tsResult.path("metrics").path("unsupportedClaimRate").asDouble(Double.MAX_VALUE)
                && !isLower(javaResult.path("metrics"), tsResult.path("metrics"), "citationPrecision")
                && !isLower(javaResult.path("metrics"), tsResult.path("metrics"), "multiHopCompleteness")
                && !isLower(javaResult.path("metrics"), tsResult.path("metrics"), "graphFactAcceptedRate")
                && !isLower(javaResult.path("metrics"), tsResult.path("metrics"), "retrievalRecallAt5")
                && !isLower(javaResult.path("metrics"), tsResult.path("metrics"), "retrievalRecallAt10")
                && !isLower(javaResult.path("metrics"), tsResult.path("metrics"), "mrr");
        comparison.put("sharedBenchmarkOutperformOriginal", sharedBenchmarkOutperform);

        ObjectNode evidenceMatrix = comparison.putObject("evidenceMatrix");
        evidenceMatrix.put("sharedBenchmark", sharedBenchmarkOutperform ? STATUS_LEADING : STATUS_NOT_LEADING);
        evidenceMatrix.put(
                "graphStructuredFacts",
                resolveMetricStatus(javaResult.path("metrics"), tsResult.path("metrics"), "graphFactAcceptedRate")
        );
        evidenceMatrix.put("retrievalAuditDashboard", STATUS_AVAILABLE);
        evidenceMatrix.put(
                "retrievalRecallAt5",
                resolveMetricStatus(javaResult.path("metrics"), tsResult.path("metrics"), "retrievalRecallAt5")
        );
        evidenceMatrix.put(
                "retrievalRecallAt10",
                resolveMetricStatus(javaResult.path("metrics"), tsResult.path("metrics"), "retrievalRecallAt10")
        );
        evidenceMatrix.put(
                "mrr",
                resolveMetricStatus(javaResult.path("metrics"), tsResult.path("metrics"), "mrr")
        );
        evidenceMatrix.put("realQuestionReplay", resolveRealQuestionReplayStatus(realQuestionReplayBenchmark));
        evidenceMatrix.put("resumeRecovery", resolveResumeRecoveryStatus(resumeRecoveryBenchmark));
        evidenceMatrix.put("defaultConfigBenefit", resolveDefaultConfigBenefitStatus(realQuestionReplayBenchmark));
        comparison.set("realQuestionReplayBenchmark", realQuestionReplayBenchmark);
        comparison.set("resumeRecoveryBenchmark", resumeRecoveryBenchmark);

        ArrayNode blockingGaps = comparison.putArray("blockingGaps");
        if (!sharedBenchmarkOutperform) {
            blockingGaps.add("shared_benchmark_not_leading");
        }
        appendMetricBlockingGap(blockingGaps, javaResult.path("metrics"), tsResult.path("metrics"), "graphFactAcceptedRate");
        appendMetricBlockingGap(blockingGaps, javaResult.path("metrics"), tsResult.path("metrics"), "retrievalRecallAt5");
        appendMetricBlockingGap(blockingGaps, javaResult.path("metrics"), tsResult.path("metrics"), "retrievalRecallAt10");
        appendMetricBlockingGap(blockingGaps, javaResult.path("metrics"), tsResult.path("metrics"), "mrr");
        appendReplayBlockingGap(blockingGaps, realQuestionReplayBenchmark);
        appendResumeRecoveryBlockingGap(blockingGaps, resumeRecoveryBenchmark);
        appendDefaultConfigBlockingGap(blockingGaps, realQuestionReplayBenchmark);
        if (!nonComparableScenarios.isEmpty()) {
            blockingGaps.add("graph_shadow_not_comparable");
        }

        boolean comprehensiveOutperformOriginal = sharedBenchmarkOutperform && blockingGaps.isEmpty();
        comparison.put("comprehensiveOutperformOriginal", comprehensiveOutperformOriginal);
        comparison.put(
                "verdictSummary",
                comprehensiveOutperformOriginal
                        ? "当前证据足以支持 Java 版全面超过原版。"
                        : "当前只能证明共享 benchmark 维度是否领先，尚不足以宣称 Java 版已全面超过原版。"
        );
        return comparison;
    }

    private String renderMarkdownReport(
            JsonNode dataset,
            ObjectNode javaResult,
            ObjectNode tsResult,
            ObjectNode comparison
    ) {
        StringBuilder reportBuilder = new StringBuilder();
        reportBuilder.append("# AST / Citation / Deep Research 对标报告").append("\n\n");
        reportBuilder.append("- 数据集版本：").append(dataset.path("version").asText("")).append("\n");
        reportBuilder.append("- Java runner：").append(javaResult.path("runner").asText("")).append("\n");
        reportBuilder.append("- TS runner：").append(tsResult.path("runner").asText("")).append("\n\n");

        reportBuilder.append("## 对标结论").append("\n\n");
        reportBuilder.append("- unsupportedClaimRate：Java=")
                .append(formatNumber(javaResult.path("metrics").path("unsupportedClaimRate")))
                .append("，TS=")
                .append(formatNumber(tsResult.path("metrics").path("unsupportedClaimRate")))
                .append("\n");
        reportBuilder.append("- citationPrecision：Java=")
                .append(formatNumber(javaResult.path("metrics").path("citationPrecision")))
                .append("，TS=")
                .append(formatNumber(tsResult.path("metrics").path("citationPrecision")))
                .append("\n");
        reportBuilder.append("- citationCoverage：Java=")
                .append(formatNumber(javaResult.path("metrics").path("citationCoverage")))
                .append("，TS=")
                .append(formatNumber(tsResult.path("metrics").path("citationCoverage")))
                .append("\n");
        reportBuilder.append("- multiHopCompleteness：Java=")
                .append(formatNumber(javaResult.path("metrics").path("multiHopCompleteness")))
                .append("，TS=")
                .append(formatNumber(tsResult.path("metrics").path("multiHopCompleteness")))
                .append("\n");
        reportBuilder.append("- graphFactAcceptedRate：Java=")
                .append(formatNumber(javaResult.path("metrics").path("graphFactAcceptedRate")))
                .append("，TS=")
                .append(formatNumber(tsResult.path("metrics").path("graphFactAcceptedRate")))
                .append("\n");
        reportBuilder.append("- retrievalRecall@5：Java=")
                .append(formatNumber(javaResult.path("metrics").path("retrievalRecallAt5")))
                .append("，TS=")
                .append(formatNumber(tsResult.path("metrics").path("retrievalRecallAt5")))
                .append("\n");
        reportBuilder.append("- retrievalRecall@10：Java=")
                .append(formatNumber(javaResult.path("metrics").path("retrievalRecallAt10")))
                .append("，TS=")
                .append(formatNumber(tsResult.path("metrics").path("retrievalRecallAt10")))
                .append("\n");
        reportBuilder.append("- MRR：Java=")
                .append(formatNumber(javaResult.path("metrics").path("mrr")))
                .append("，TS=")
                .append(formatNumber(tsResult.path("metrics").path("mrr")))
                .append("\n");
        reportBuilder.append("- 共享 benchmark 是否领先原版：")
                .append(comparison.path("sharedBenchmarkOutperformOriginal").asBoolean(false) ? "是" : "否")
                .append("\n");
        reportBuilder.append("- 是否已有足够证据宣称“全面超越原版”：")
                .append(comparison.path("comprehensiveOutperformOriginal").asBoolean(false) ? "是" : "否")
                .append("\n");
        reportBuilder.append("- 判定说明：")
                .append(comparison.path("verdictSummary").asText(""))
                .append("\n\n");

        reportBuilder.append("## 证据矩阵").append("\n\n");
        comparison.path("evidenceMatrix").fields().forEachRemaining(entry -> reportBuilder
                .append("- ")
                .append(entry.getKey())
                .append("：")
                .append(entry.getValue().asText(""))
                .append("\n"));
        reportBuilder.append("\n");

        reportBuilder.append("## 逐题差异").append("\n\n");
        reportBuilder.append("| 场景 | 类型 | unsupported Δ | precision Δ | coverage Δ | multi-hop Δ | gaps |").append("\n");
        reportBuilder.append("| --- | --- | --- | --- | --- | --- | --- |").append("\n");
        for (JsonNode scenarioComparison : comparison.withArray("scenarioComparisons")) {
            String gaps = joinArray(scenarioComparison.withArray("gaps"));
            reportBuilder.append("| ")
                    .append(scenarioComparison.path("id").asText(""))
                    .append(" | ")
                    .append(scenarioComparison.path("kind").asText(""))
                    .append(" | ")
                    .append(formatNumber(scenarioComparison.path("unsupportedDelta")))
                    .append(" | ")
                    .append(formatNumber(scenarioComparison.path("citationPrecisionDelta")))
                    .append(" | ")
                    .append(formatNumber(scenarioComparison.path("citationCoverageDelta")))
                    .append(" | ")
                    .append(formatNumber(scenarioComparison.path("multiHopDelta")))
                    .append(" | ")
                    .append(gaps.isBlank() ? "-" : gaps)
                    .append(" |").append("\n");
        }
        reportBuilder.append("\n");

        reportBuilder.append("## Graph 对比").append("\n\n");
        reportBuilder.append("| 场景 | acceptedRate Δ | Java accepted | TS accepted | gaps |").append("\n");
        reportBuilder.append("| --- | --- | --- | --- | --- |").append("\n");
        for (JsonNode graphComparison : comparison.withArray("graphComparisons")) {
            String gaps = joinArray(graphComparison.withArray("gaps"));
            reportBuilder.append("| ")
                    .append(graphComparison.path("id").asText(""))
                    .append(" | ")
                    .append(formatNumber(graphComparison.path("acceptedRateDelta")))
                    .append(" | ")
                    .append(graphComparison.path("javaAcceptedFacts").asInt())
                    .append("/")
                    .append(graphComparison.path("javaExpectedFacts").asInt())
                    .append(" | ")
                    .append(graphComparison.path("tsAcceptedFacts").asInt())
                    .append("/")
                    .append(graphComparison.path("tsExpectedFacts").asInt())
                    .append(" | ")
                    .append(gaps.isBlank() ? "-" : gaps)
                    .append(" |").append("\n");
        }
        reportBuilder.append("\n");

        reportBuilder.append("## Retrieval 对比").append("\n\n");
        reportBuilder.append("| 场景 | recall@5 Δ | recall@10 Δ | MRR Δ | gaps |").append("\n");
        reportBuilder.append("| --- | --- | --- | --- | --- |").append("\n");
        for (JsonNode retrievalComparison : comparison.withArray("retrievalComparisons")) {
            String gaps = joinArray(retrievalComparison.withArray("gaps"));
            reportBuilder.append("| ")
                    .append(retrievalComparison.path("id").asText(""))
                    .append(" | ")
                    .append(formatNumber(retrievalComparison.path("recallAt5Delta")))
                    .append(" | ")
                    .append(formatNumber(retrievalComparison.path("recallAt10Delta")))
                    .append(" | ")
                    .append(formatNumber(retrievalComparison.path("mrrDelta")))
                    .append(" | ")
                    .append(gaps.isBlank() ? "-" : gaps)
                    .append(" |").append("\n");
        }
        reportBuilder.append("\n");

        JsonNode replayBenchmark = comparison.path("realQuestionReplayBenchmark");
        reportBuilder.append("## 真实问题回放（Java 默认配置 vs 保守基线）").append("\n\n");
        reportBuilder.append("- 场景数：").append(replayBenchmark.path("scenarioCount").asInt(0)).append("\n");
        reportBuilder.append("- current recall@10：")
                .append(formatNumber(replayBenchmark.path("currentRecallAt10")))
                .append("，baseline recall@10：")
                .append(formatNumber(replayBenchmark.path("baselineRecallAt10")))
                .append("\n");
        reportBuilder.append("- current top1 支撑率：")
                .append(formatNumber(replayBenchmark.path("currentSupportAt1")))
                .append("，baseline top1 支撑率：")
                .append(formatNumber(replayBenchmark.path("baselineSupportAt1")))
                .append("\n");
        reportBuilder.append("- current top3 支撑率：")
                .append(formatNumber(replayBenchmark.path("currentSupportAt3")))
                .append("，baseline top3 支撑率：")
                .append(formatNumber(replayBenchmark.path("baselineSupportAt3")))
                .append("\n");
        reportBuilder.append("- replay 是否领先：")
                .append(replayBenchmark.path("leading").asBoolean(false) ? "是" : "否")
                .append("\n");
        reportBuilder.append("- 默认配置收益是否领先保守基线：")
                .append(replayBenchmark.path("defaultBenefitLeading").asBoolean(false) ? "是" : "否")
                .append("\n\n");
        reportBuilder.append("| 场景 | current recall@10 | baseline recall@10 | current top1 支撑 | baseline top1 支撑 | current top hits | baseline top hits |").append("\n");
        reportBuilder.append("| --- | --- | --- | --- | --- | --- | --- |").append("\n");
        for (JsonNode replayScenario : replayBenchmark.withArray("scenarios")) {
            reportBuilder.append("| ")
                    .append(replayScenario.path("id").asText(""))
                    .append(" | ")
                    .append(formatNumber(replayScenario.path("currentRecallAt10")))
                    .append(" | ")
                    .append(formatNumber(replayScenario.path("baselineRecallAt10")))
                    .append(" | ")
                    .append(formatNumber(replayScenario.path("currentSupportAt1")))
                    .append(" | ")
                    .append(formatNumber(replayScenario.path("baselineSupportAt1")))
                    .append(" | ")
                    .append(replayScenario.path("currentTopHits").asText(""))
                    .append(" | ")
                    .append(replayScenario.path("baselineTopHits").asText(""))
                    .append(" |").append("\n");
        }
        reportBuilder.append("\n");

        JsonNode resumeRecoveryBenchmark = comparison.path("resumeRecoveryBenchmark");
        reportBuilder.append("## 恢复能力基准（Graph checkpoint + resume smoke）").append("\n\n");
        reportBuilder.append("- 场景数：").append(resumeRecoveryBenchmark.path("scenarioCount").asInt(0)).append("\n");
        reportBuilder.append("- query resume success rate：")
                .append(formatNumber(resumeRecoveryBenchmark.path("queryResumeSuccessRate")))
                .append("\n");
        reportBuilder.append("- deep research resume success rate：")
                .append(formatNumber(resumeRecoveryBenchmark.path("deepResearchResumeSuccessRate")))
                .append("\n");
        reportBuilder.append("- compile resume success rate：")
                .append(formatNumber(resumeRecoveryBenchmark.path("compileResumeSuccessRate")))
                .append("\n");
        reportBuilder.append("- checkpoint 级恢复是否已就绪：")
                .append(resumeRecoveryBenchmark.path("checkpointRecoveryReady").asBoolean(false) ? "是" : "否")
                .append("\n");
        reportBuilder.append("- 端到端恢复是否已就绪：")
                .append(resumeRecoveryBenchmark.path("endToEndRecoveryReady").asBoolean(false) ? "是" : "否")
                .append("\n");
        if (!resumeRecoveryBenchmark.withArray("scenarios").isEmpty()) {
            reportBuilder.append("\n");
            reportBuilder.append("| 场景 | interrupt 节点 | resume 下一节点 | success | 说明 |").append("\n");
            reportBuilder.append("| --- | --- | --- | --- | --- |").append("\n");
            for (JsonNode resumeScenario : resumeRecoveryBenchmark.withArray("scenarios")) {
                reportBuilder.append("| ")
                        .append(resumeScenario.path("id").asText(""))
                        .append(" | ")
                        .append(resumeScenario.path("interruptedNode").asText(""))
                        .append(" | ")
                        .append(resumeScenario.path("resumeNextNode").asText(""))
                        .append(" | ")
                        .append(resumeScenario.path("success").asBoolean(false) ? "是" : "否")
                        .append(" | ")
                        .append(joinArray(resumeScenario.withArray("notes")))
                        .append(" |").append("\n");
            }
            reportBuilder.append("\n");
        }

        reportBuilder.append("## 阻塞项").append("\n\n");
        if (comparison.withArray("blockingGaps").isEmpty()) {
            reportBuilder.append("- 当前没有阻止“全面超越”判定的缺口。").append("\n");
        }
        else {
            for (JsonNode blockingGap : comparison.withArray("blockingGaps")) {
                reportBuilder.append("- ").append(blockingGap.asText("")).append("\n");
            }
        }
        reportBuilder.append("\n");

        reportBuilder.append("## Top Gaps").append("\n\n");
        if (comparison.path("topGaps").isEmpty()) {
            reportBuilder.append("- 当前共享题集上未发现 Java 相比原版退化的 Top gap。").append("\n");
        }
        else {
            comparison.path("topGaps").fields().forEachRemaining(entry -> reportBuilder
                    .append("- ")
                    .append(entry.getKey())
                    .append("：")
                    .append(entry.getValue().asInt())
                    .append(" 个场景").append("\n"));
        }
        reportBuilder.append("\n");

        reportBuilder.append("## Retrieval Audit 对接").append("\n\n");
        reportBuilder.append("- 单题下钻接口：`GET /api/v1/admin/query/retrieval/audits/latest?queryId=<queryId>&historyLimit=5`").append("\n");
        reportBuilder.append("- recent runs 接口：`GET /api/v1/admin/query/retrieval/audits/recent?limit=20`").append("\n");
        reportBuilder.append("- 用法：当共享 benchmark 暴露具体 gap 后，可按 queryId 回看通道命中、fused rank、未入融合候选与策略标签。").append("\n\n");

        reportBuilder.append("## Graph 备注").append("\n\n");
        for (JsonNode scenario : javaResult.withArray("scenarios")) {
            if (!"graph".equals(scenario.path("kind").asText(""))) {
                continue;
            }
            reportBuilder.append("- ")
                    .append(scenario.path("id").asText(""))
                    .append("：accepted=")
                    .append(scenario.path("acceptedFacts").asInt())
                    .append("/")
                    .append(scenario.path("expectedFacts").asInt())
                    .append("，")
                    .append(joinArray(scenario.withArray("notes")))
                    .append("\n");
        }
        if (!comparison.withArray("nonComparableScenarios").isEmpty()) {
            reportBuilder.append("\n");
            for (JsonNode nonComparableScenario : comparison.withArray("nonComparableScenarios")) {
                reportBuilder.append("- 非同构场景 ")
                        .append(nonComparableScenario.path("id").asText(""))
                        .append("：")
                        .append(nonComparableScenario.path("reason").asText(""))
                        .append("\n");
            }
        }
        return reportBuilder.toString();
    }

    private String resolveMetricStatus(JsonNode javaMetrics, JsonNode tsMetrics, String fieldName) {
        if (javaMetrics.path(fieldName).isNull() || tsMetrics.path(fieldName).isNull()) {
            return STATUS_MISSING_DATASET;
        }
        return isLower(javaMetrics, tsMetrics, fieldName) ? STATUS_NOT_LEADING : STATUS_LEADING;
    }

    private void appendMetricBlockingGap(
            ArrayNode blockingGaps,
            JsonNode javaMetrics,
            JsonNode tsMetrics,
            String fieldName
    ) {
        if (javaMetrics.path(fieldName).isNull() || tsMetrics.path(fieldName).isNull()) {
            blockingGaps.add(fieldName + "_missing_dataset");
            return;
        }
        if (isLower(javaMetrics, tsMetrics, fieldName)) {
            blockingGaps.add(fieldName + "_not_leading");
        }
    }

    private String resolveRealQuestionReplayStatus(JsonNode replayBenchmark) {
        if (replayBenchmark == null || replayBenchmark.path("scenarioCount").asInt(0) == 0) {
            return STATUS_NOT_INCLUDED;
        }
        return replayBenchmark.path("leading").asBoolean(false) ? STATUS_LEADING : STATUS_NOT_LEADING;
    }

    private String resolveDefaultConfigBenefitStatus(JsonNode replayBenchmark) {
        if (replayBenchmark == null || replayBenchmark.path("scenarioCount").asInt(0) == 0) {
            return STATUS_NOT_INCLUDED;
        }
        return replayBenchmark.path("defaultBenefitLeading").asBoolean(false) ? STATUS_LEADING : STATUS_NOT_LEADING;
    }

    private String resolveResumeRecoveryStatus(JsonNode resumeRecoveryBenchmark) {
        if (resumeRecoveryBenchmark == null || resumeRecoveryBenchmark.path("scenarioCount").asInt(0) == 0) {
            return STATUS_NOT_INCLUDED;
        }
        if (resumeRecoveryBenchmark.path("endToEndRecoveryReady").asBoolean(false)) {
            return STATUS_LEADING;
        }
        return resumeRecoveryBenchmark.path("checkpointRecoveryReady").asBoolean(false)
                ? STATUS_AVAILABLE
                : STATUS_NOT_LEADING;
    }

    private void appendReplayBlockingGap(ArrayNode blockingGaps, JsonNode replayBenchmark) {
        if (replayBenchmark == null || replayBenchmark.path("scenarioCount").asInt(0) == 0) {
            blockingGaps.add("real_question_replay_missing");
            return;
        }
        if (!replayBenchmark.path("leading").asBoolean(false)) {
            blockingGaps.add("real_question_replay_not_leading");
        }
    }

    private void appendDefaultConfigBlockingGap(ArrayNode blockingGaps, JsonNode replayBenchmark) {
        if (replayBenchmark == null || replayBenchmark.path("scenarioCount").asInt(0) == 0) {
            blockingGaps.add("default_config_gain_not_benchmarked");
            return;
        }
        if (!replayBenchmark.path("defaultBenefitLeading").asBoolean(false)) {
            blockingGaps.add("default_config_gain_not_leading");
        }
    }

    private void appendResumeRecoveryBlockingGap(ArrayNode blockingGaps, JsonNode resumeRecoveryBenchmark) {
        if (resumeRecoveryBenchmark == null || resumeRecoveryBenchmark.path("scenarioCount").asInt(0) == 0) {
            blockingGaps.add("resume_recovery_benchmark_missing");
            return;
        }
        if (!resumeRecoveryBenchmark.path("checkpointRecoveryReady").asBoolean(false)) {
            blockingGaps.add("resume_recovery_success_rate_below_threshold");
            return;
        }
        if (!resumeRecoveryBenchmark.path("endToEndRecoveryReady").asBoolean(false)) {
            blockingGaps.add("resume_recovery_end_to_end_missing");
        }
    }

    private Integer nullableIntValue(JsonNode jsonNode) {
        if (jsonNode == null || jsonNode.isNull() || jsonNode.isMissingNode()) {
            return null;
        }
        return Integer.valueOf(jsonNode.asInt());
    }

    private List<String> splitRetrievalChunks(String body) {
        List<String> chunks = new ArrayList<String>();
        String[] segments = body == null ? new String[0] : body.split("\\n\\s*\\n");
        for (String segment : segments) {
            if (segment != null && !segment.isBlank()) {
                chunks.add(segment.trim());
            }
        }
        if (chunks.isEmpty() && body != null && !body.isBlank()) {
            chunks.add(body.trim());
        }
        return chunks;
    }

    private CitationCheckService createCitationCheckService(JsonNode scenario) {
        return new CitationCheckService(
                new CitationExtractor(),
                new CitationValidator(
                        new BenchmarkArticleJdbcRepository(scenario.path("articles")),
                        new BenchmarkSourceFileJdbcRepository(scenario.path("sourceFiles"))
                )
        );
    }

    private EvidenceCard toEvidenceCard(JsonNode cardNode) {
        EvidenceCard evidenceCard = new EvidenceCard();
        evidenceCard.setEvidenceId(cardNode.path("evidenceId").asText(""));
        evidenceCard.setLayerIndex(cardNode.path("layerIndex").asInt(0));
        evidenceCard.setTaskId(cardNode.path("taskId").asText(""));
        evidenceCard.setScope(cardNode.path("scope").asText(""));
        for (JsonNode anchorNode : cardNode.withArray("evidenceAnchors")) {
            evidenceCard.getEvidenceAnchors().add(toEvidenceAnchor(anchorNode));
        }
        for (JsonNode findingNode : cardNode.withArray("factFindings")) {
            evidenceCard.getFactFindings().add(toFactFinding(findingNode));
        }
        int legacyFindingIndex = 0;
        for (JsonNode legacyFindingNode : cardNode.withArray("findings")) {
            legacyFindingIndex++;
            String anchorId = resolveLegacyAnchorId(evidenceCard, legacyFindingIndex);
            evidenceCard.getEvidenceAnchors().add(toLegacyEvidenceAnchor(legacyFindingNode, anchorId));
            evidenceCard.getFactFindings().add(toLegacyFactFinding(cardNode, legacyFindingNode, anchorId));
        }
        for (JsonNode gapNode : cardNode.withArray("gaps")) {
            evidenceCard.getGaps().add(gapNode.asText(""));
        }
        for (JsonNode relatedLeadNode : cardNode.withArray("relatedLeads")) {
            evidenceCard.getRelatedLeads().add(relatedLeadNode.asText(""));
        }
        for (JsonNode articleKeyNode : cardNode.withArray("selectedArticleKeys")) {
            evidenceCard.getSelectedArticleKeys().add(articleKeyNode.asText(""));
        }
        return evidenceCard;
    }

    private EvidenceAnchor toEvidenceAnchor(JsonNode anchorNode) {
        EvidenceAnchor evidenceAnchor = new EvidenceAnchor();
        evidenceAnchor.setAnchorId(anchorNode.path("anchorId").asText(""));
        evidenceAnchor.setSourceType(parseEvidenceAnchorSourceType(anchorNode.path("sourceType").asText("")));
        evidenceAnchor.setSourceId(anchorNode.path("sourceId").asText(""));
        evidenceAnchor.setPath(anchorNode.path("path").asText(null));
        evidenceAnchor.setLineStart(nullableInt(anchorNode.path("lineStart")));
        evidenceAnchor.setLineEnd(nullableInt(anchorNode.path("lineEnd")));
        evidenceAnchor.setChunkId(anchorNode.path("chunkId").asText(null));
        evidenceAnchor.setQuoteText(anchorNode.path("quoteText").asText(""));
        evidenceAnchor.setRetrievalScore(anchorNode.path("retrievalScore").asDouble(0.0D));
        return evidenceAnchor;
    }

    private FactFinding toFactFinding(JsonNode findingNode) {
        FactFinding factFinding = new FactFinding();
        factFinding.setFindingId(findingNode.path("findingId").asText(""));
        factFinding.setSubject(findingNode.path("subject").asText(""));
        factFinding.setPredicate(findingNode.path("predicate").asText(""));
        factFinding.setQualifier(findingNode.path("qualifier").asText(""));
        factFinding.setFactKey(findingNode.path("factKey").asText(factFinding.expectedFactKey()));
        factFinding.setValueText(findingNode.path("valueText").asText(""));
        factFinding.setValueType(parseFactValueType(findingNode.path("valueType").asText("STRING")));
        factFinding.setUnit(findingNode.path("unit").asText(null));
        factFinding.setClaimText(findingNode.path("claimText").asText(""));
        factFinding.setConfidence(findingNode.path("confidence").asDouble(0.0D));
        factFinding.setSupportLevel(parseFindingSupportLevel(findingNode.path("supportLevel").asText("DIRECT")));
        for (JsonNode anchorIdNode : findingNode.withArray("anchorIds")) {
            factFinding.getAnchorIds().add(anchorIdNode.asText(""));
        }
        return factFinding;
    }

    private EvidenceAnchorSourceType parseEvidenceAnchorSourceType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if ("SOURCE".equalsIgnoreCase(value.trim())) {
            return EvidenceAnchorSourceType.SOURCE_FILE;
        }
        return EvidenceAnchorSourceType.valueOf(value.trim());
    }

    private FactValueType parseFactValueType(String value) {
        if (value == null || value.isBlank()) {
            return FactValueType.STRING;
        }
        return FactValueType.valueOf(value.trim());
    }

    private FindingSupportLevel parseFindingSupportLevel(String value) {
        if (value == null || value.isBlank()) {
            return FindingSupportLevel.DIRECT;
        }
        return FindingSupportLevel.valueOf(value.trim());
    }

    private Integer nullableInt(JsonNode jsonNode) {
        if (jsonNode == null || jsonNode.isMissingNode() || jsonNode.isNull()) {
            return null;
        }
        return Integer.valueOf(jsonNode.asInt());
    }

    private int coveredClaimCount(CitationCheckReport report) {
        if (report == null || report.getClaimSegments() == null) {
            return 0;
        }
        return Math.max(0, report.getClaimSegments().size() - report.getUnsupportedClaimCount());
    }

    private String resolveLegacyAnchorId(EvidenceCard evidenceCard, int legacyFindingIndex) {
        String evidenceId = evidenceCard.getEvidenceId();
        if (legacyFindingIndex == 1 && evidenceId != null && evidenceId.matches("ev#\\d+")) {
            return evidenceId;
        }
        int stableHash = Math.abs(Objects.hash(
                evidenceId,
                evidenceCard.getTaskId(),
                evidenceCard.getScope(),
                Integer.valueOf(legacyFindingIndex)
        ));
        return "ev#" + stableHash;
    }

    private EvidenceAnchor toLegacyEvidenceAnchor(JsonNode findingNode, String anchorId) {
        EvidenceAnchor evidenceAnchor = new EvidenceAnchor();
        EvidenceAnchorSourceType sourceType = parseEvidenceAnchorSourceType(findingNode.path("sourceType").asText("ARTICLE"));
        evidenceAnchor.setAnchorId(anchorId);
        evidenceAnchor.setSourceType(sourceType);
        evidenceAnchor.setSourceId(findingNode.path("sourceId").asText(""));
        if (sourceType == EvidenceAnchorSourceType.SOURCE_FILE) {
            evidenceAnchor.setPath(evidenceAnchor.getSourceId());
        }
        else {
            evidenceAnchor.setChunkId(findingNode.path("chunkId").asText(null));
        }
        evidenceAnchor.setQuoteText(findingNode.path("quote").asText(""));
        evidenceAnchor.setRetrievalScore(Math.max(0.8D, findingNode.path("confidence").asDouble(0.0D)));
        return evidenceAnchor;
    }

    private FactFinding toLegacyFactFinding(JsonNode cardNode, JsonNode findingNode, String anchorId) {
        LegacyClaimParts legacyClaimParts = deriveLegacyClaimParts(
                findingNode.path("claim").asText(""),
                cardNode.path("scope").asText(""),
                cardNode.path("taskId").asText("")
        );
        FactFinding factFinding = new FactFinding();
        factFinding.setFindingId(cardNode.path("evidenceId").asText("") + "-legacy-" + anchorId);
        factFinding.setSubject(legacyClaimParts.getSubject());
        factFinding.setPredicate(legacyClaimParts.getPredicate());
        factFinding.setQualifier(legacyClaimParts.getQualifier());
        factFinding.setFactKey(factFinding.expectedFactKey());
        factFinding.setValueText(legacyClaimParts.getValueText());
        factFinding.setValueType(FactValueType.STRING);
        factFinding.setClaimText(findingNode.path("claim").asText(""));
        factFinding.setConfidence(Math.max(0.8D, findingNode.path("confidence").asDouble(0.0D)));
        factFinding.setSupportLevel(FindingSupportLevel.DIRECT);
        factFinding.getAnchorIds().add(anchorId);
        return factFinding;
    }

    private LegacyClaimParts deriveLegacyClaimParts(String claim, String scope, String taskId) {
        String normalizedClaim = claim == null ? "" : claim.trim();
        if (normalizedClaim.isEmpty()) {
            String subject = fallbackSubject(scope, taskId);
            return new LegacyClaimParts(subject, "states", "legacy_benchmark", subject);
        }
        for (LegacySeparator separator : LegacySeparator.values()) {
            int index = normalizedClaim.indexOf(separator.literal());
            if (index > 0) {
                String subject = normalizedClaim.substring(0, index).trim();
                String valueText = normalizedClaim.substring(index + separator.literal().length()).trim();
                if (!subject.isEmpty() && !valueText.isEmpty()) {
                    return new LegacyClaimParts(subject, separator.predicate(), "legacy_benchmark", valueText);
                }
            }
        }
        String subject = fallbackSubject(scope, taskId);
        return new LegacyClaimParts(subject, "states", "legacy_benchmark", normalizedClaim);
    }

    private String fallbackSubject(String scope, String taskId) {
        if (scope != null && !scope.isBlank()) {
            return scope.trim();
        }
        if (taskId != null && !taskId.isBlank()) {
            return taskId.trim();
        }
        return "benchmark";
    }

    private int countMatchedExpectedClaims(String answerMarkdown, ArrayNode expectedClaims) {
        int matched = 0;
        for (JsonNode expectedClaim : expectedClaims) {
            if (answerMarkdown != null && answerMarkdown.contains(expectedClaim.asText(""))) {
                matched++;
            }
        }
        return matched;
    }

    private Map<String, JsonNode> indexScenarios(ArrayNode scenarios) {
        Map<String, JsonNode> scenarioMap = new LinkedHashMap<String, JsonNode>();
        for (JsonNode scenario : scenarios) {
            scenarioMap.put(scenario.path("id").asText(""), scenario);
        }
        return scenarioMap;
    }

    private ObjectNode summarizeMetrics(ArrayNode scenarios) {
        int totalClaims = 0;
        int unsupportedClaims = 0;
        int coveredClaims = 0;
        int verifiedCitations = 0;
        int demotedCitations = 0;
        int expectedClaims = 0;
        int matchedExpectedClaims = 0;
        int expectedFacts = 0;
        int acceptedFacts = 0;
        int retrievalExpectedTargets = 0;
        int retrievalMatchedTargetsAt5 = 0;
        int retrievalMatchedTargetsAt10 = 0;
        int retrievalScenarioCount = 0;
        double retrievalReciprocalRankSum = 0.0D;

        for (JsonNode scenario : scenarios) {
            String kind = scenario.path("kind").asText("");
            if ("retrieval".equals(kind)) {
                retrievalExpectedTargets += scenario.path("expectedTargets").asInt(0);
                retrievalMatchedTargetsAt5 += scenario.path("matchedTargetsAt5").asInt(0);
                retrievalMatchedTargetsAt10 += scenario.path("matchedTargetsAt10").asInt(0);
                retrievalScenarioCount++;
                retrievalReciprocalRankSum += scenario.path("reciprocalRank").asDouble(0.0D);
                continue;
            }
            if (!"graph".equals(kind)) {
                totalClaims += scenario.path("totalClaims").asInt(0);
                unsupportedClaims += scenario.path("unsupportedClaims").asInt(0);
                coveredClaims += scenario.path("coveredClaims").asInt(0);
                verifiedCitations += scenario.path("verifiedCitations").asInt(0);
                demotedCitations += scenario.path("demotedCitations").asInt(0);
                expectedClaims += scenario.path("expectedClaims").asInt(0);
                matchedExpectedClaims += scenario.path("matchedExpectedClaims").asInt(0);
            }
            expectedFacts += scenario.path("expectedFacts").asInt(0);
            acceptedFacts += scenario.path("acceptedFacts").asInt(0);
        }

        ObjectNode metrics = OBJECT_MAPPER.createObjectNode();
        metrics.put("unsupportedClaimRate", totalClaims == 0 ? 0.0D : unsupportedClaims * 1.0D / totalClaims);
        putNullableDouble(
                metrics,
                "citationPrecision",
                verifiedCitations + demotedCitations == 0
                        ? null
                        : verifiedCitations * 1.0D / (verifiedCitations + demotedCitations)
        );
        metrics.put("citationCoverage", totalClaims == 0 ? 0.0D : coveredClaims * 1.0D / totalClaims);
        putNullableDouble(
                metrics,
                "multiHopCompleteness",
                expectedClaims == 0 ? null : matchedExpectedClaims * 1.0D / expectedClaims
        );
        putNullableDouble(
                metrics,
                "graphFactAcceptedRate",
                expectedFacts == 0 ? null : acceptedFacts * 1.0D / expectedFacts
        );
        putNullableDouble(
                metrics,
                "retrievalRecallAt5",
                retrievalExpectedTargets == 0 ? null : retrievalMatchedTargetsAt5 * 1.0D / retrievalExpectedTargets
        );
        putNullableDouble(
                metrics,
                "retrievalRecallAt10",
                retrievalExpectedTargets == 0 ? null : retrievalMatchedTargetsAt10 * 1.0D / retrievalExpectedTargets
        );
        putNullableDouble(
                metrics,
                "mrr",
                retrievalScenarioCount == 0 ? null : retrievalReciprocalRankSum / retrievalScenarioCount
        );
        return metrics;
    }

    private ObjectNode runRealQuestionReplayBenchmark(ArrayNode scenarios) {
        ObjectNode emptyResult = OBJECT_MAPPER.createObjectNode();
        emptyResult.put("scenarioCount", scenarios == null ? 0 : scenarios.size());
        emptyResult.putNull("currentRecallAt10");
        emptyResult.putNull("baselineRecallAt10");
        emptyResult.putNull("currentSupportAt1");
        emptyResult.putNull("baselineSupportAt1");
        emptyResult.putNull("currentSupportAt3");
        emptyResult.putNull("baselineSupportAt3");
        emptyResult.put("leading", false);
        emptyResult.put("defaultBenefitLeading", false);
        emptyResult.set("scenarios", OBJECT_MAPPER.createArrayNode());
        if (scenarios == null || scenarios.isEmpty()) {
            return emptyResult;
        }
        try (RealQuestionReplayHarness replayHarness = new RealQuestionReplayHarness()) {
            return replayHarness.run(scenarios);
        }
    }

    private ObjectNode runResumeRecoveryBenchmark() {
        ResumeRecoveryBenchmarkHarness benchmarkHarness = new ResumeRecoveryBenchmarkHarness();
        return benchmarkHarness.run();
    }

    private void putNullableDouble(ObjectNode node, String fieldName, Double value) {
        if (value == null) {
            node.putNull(fieldName);
            return;
        }
        node.put(fieldName, value);
    }

    private Double nullableDifference(JsonNode left, JsonNode right, String fieldName) {
        if (left.path(fieldName).isNull() || right.path(fieldName).isNull()) {
            return null;
        }
        return left.path(fieldName).asDouble(0.0D) - right.path(fieldName).asDouble(0.0D);
    }

    private boolean isLower(JsonNode left, JsonNode right, String fieldName) {
        if (left.path(fieldName).isNull() || right.path(fieldName).isNull()) {
            return false;
        }
        return left.path(fieldName).asDouble(0.0D) < right.path(fieldName).asDouble(0.0D);
    }

    private String formatNumber(JsonNode node) {
        if (node == null || node.isNull()) {
            return "N/A";
        }
        return String.format(Locale.ROOT, "%.3f", node.asDouble(0.0D));
    }

    private String joinArray(ArrayNode arrayNode) {
        List<String> items = new ArrayList<String>();
        for (JsonNode item : arrayNode) {
            items.add(item.asText(""));
        }
        return String.join("; ", items);
    }

    private class RetrievalBenchmarkHarness implements AutoCloseable {

        private final JdbcTemplate jdbcTemplate;

        private final KnowledgeSearchService knowledgeSearchService;

        private RetrievalBenchmarkHarness() {
            DriverManagerDataSource schemaDataSource = dataSource(withCurrentSchema(BENCHMARK_DB_URL, "lattice"));
            this.jdbcTemplate = new JdbcTemplate(schemaDataSource);

            QueryRetrievalSettingsService queryRetrievalSettingsService = new QueryRetrievalSettingsService();
            this.knowledgeSearchService = new KnowledgeSearchService(
                    new FtsSearchService(jdbcTemplate),
                    new ArticleChunkFtsSearchService(new ArticleChunkJdbcRepository(jdbcTemplate)),
                    new RefKeySearchService(jdbcTemplate),
                    new SourceSearchService(null),
                    new SourceChunkFtsSearchService(null),
                    new ContributionSearchService(null),
                    new GraphSearchService(),
                    new VectorSearchService(),
                    new ChunkVectorSearchService(),
                    new RrfFusionService(queryRetrievalSettingsService),
                    queryRetrievalSettingsService,
                    new QueryRewriteService(),
                    new QueryIntentClassifier(),
                    new RetrievalStrategyResolver(),
                    null
            );
        }

        private ObjectNode runScenario(JsonNode scenario) {
            resetData();
            insertArticles(scenario.withArray("articles"), scenario.path("id").asText("retrieval"));

            String question = scenario.path("question").asText("");
            RetrievalQueryContext retrievalQueryContext = knowledgeSearchService.prepareContext(
                    "retrieval-benchmark:" + scenario.path("id").asText(""),
                    question
            );
            List<QueryArticleHit> hits = knowledgeSearchService.search(retrievalQueryContext, 10);
            List<String> expectedArticleKeys = new ArrayList<String>();
            for (JsonNode expectedArticleKeyNode : scenario.withArray("expectedArticleKeys")) {
                expectedArticleKeys.add(expectedArticleKeyNode.asText(""));
            }

            ObjectNode result = OBJECT_MAPPER.createObjectNode();
            result.put("id", scenario.path("id").asText(""));
            result.put("kind", "retrieval");
            result.put("totalClaims", 0);
            result.put("unsupportedClaims", 0);
            result.put("coveredClaims", 0);
            result.put("verifiedCitations", 0);
            result.put("demotedCitations", 0);
            result.putNull("unsupportedClaimRate");
            result.putNull("citationPrecision");
            result.putNull("citationCoverage");
            result.putNull("multiHopCompleteness");
            result.put("matchedExpectedClaims", 0);
            result.put("expectedClaims", 0);
            result.put("conflictSurfaced", false);
            result.put("expectedTargets", expectedArticleKeys.size());

            int matchedTargetsAt5 = matchTargets(expectedArticleKeys, hits, 5);
            int matchedTargetsAt10 = matchTargets(expectedArticleKeys, hits, 10);
            Integer firstRelevantRank = findFirstRelevantRank(expectedArticleKeys, hits);
            result.put("matchedTargetsAt5", matchedTargetsAt5);
            result.put("matchedTargetsAt10", matchedTargetsAt10);
            putNullableDouble(
                    result,
                    "retrievalRecallAt5",
                    expectedArticleKeys.isEmpty() ? null : matchedTargetsAt5 * 1.0D / expectedArticleKeys.size()
            );
            putNullableDouble(
                    result,
                    "retrievalRecallAt10",
                    expectedArticleKeys.isEmpty() ? null : matchedTargetsAt10 * 1.0D / expectedArticleKeys.size()
            );
            result.put("reciprocalRank", firstRelevantRank == null ? 0.0D : 1.0D / firstRelevantRank);
            if (firstRelevantRank == null) {
                result.putNull("firstRelevantRank");
            }
            else {
                result.put("firstRelevantRank", firstRelevantRank.intValue());
            }
            ArrayNode notes = result.putArray("notes");
            notes.add("intent=" + retrievalQueryContext.getQueryIntent().name());
            notes.add("retrievalQuestion=" + retrievalQueryContext.getRetrievalQuestion());
            notes.add("top_hits=" + topArticleKeys(hits, 3));
            return result;
        }

        private void resetData() {
            jdbcTemplate.execute(
                    "TRUNCATE TABLE article_chunks, articles, source_file_chunks, source_files, contributions RESTART IDENTITY CASCADE"
            );
        }

        private void insertArticles(ArrayNode articles, String scenarioId) {
            for (JsonNode articleNode : articles) {
                String articleId = articleNode.path("id").asText("");
                String title = articleNode.path("title").asText(articleId);
                String summary = articleNode.path("summary").asText("");
                String body = articleNode.path("body").asText("");
                List<String> referentialKeywords = new ArrayList<String>();
                for (JsonNode keywordNode : articleNode.withArray("referentialKeywords")) {
                    referentialKeywords.add(keywordNode.asText(""));
                }
                String searchText = title + "\n" + summary + "\n" + body;
                String refkeyText = String.join(" ", referentialKeywords) + " " + articleId + " " + title;
                String sourcePathLiteral = toTextArrayLiteral(List.of("benchmarks/" + scenarioId + "/" + articleId + ".md"));
                String refkeyLiteral = toTextArrayLiteral(referentialKeywords);
                String articleSql = """
                        insert into articles (
                            source_id, article_key, concept_id, title, content, lifecycle, compiled_at,
                            source_paths, metadata_json, summary, referential_keywords, depends_on, related,
                            confidence, review_status, search_text, search_tsv, refkey_text
                        )
                        values (
                            ?, ?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP,
                            %s, ?::jsonb, ?, %s, ARRAY[]::TEXT[], ARRAY[]::TEXT[],
                            'high', 'approved', ?, to_tsvector('simple'::regconfig, ?), ?
                        )
                        returning id
                        """.formatted(sourcePathLiteral, refkeyLiteral);
                Long articlePk = jdbcTemplate.queryForObject(
                        articleSql,
                        Long.class,
                        null,
                        articleId,
                        articleId,
                        title,
                        body,
                        "{\"benchmark\":true}",
                        summary,
                        searchText,
                        searchText,
                        refkeyText
                );
                List<String> chunks = splitRetrievalChunks(body);
                for (int index = 0; index < chunks.size(); index++) {
                    String chunk = chunks.get(index);
                    jdbcTemplate.update(
                            """
                                    insert into article_chunks (article_id, chunk_text, chunk_index, search_tsv)
                                    values (?, ?, ?, to_tsvector('simple'::regconfig, ?))
                                    """,
                            articlePk,
                            chunk,
                            Integer.valueOf(index),
                            chunk
                    );
                }
            }
        }

        private int matchTargets(List<String> expectedArticleKeys, List<QueryArticleHit> hits, int limit) {
            int matched = 0;
            for (String expectedArticleKey : expectedArticleKeys) {
                if (containsArticleKey(hits, expectedArticleKey, limit)) {
                    matched++;
                }
            }
            return matched;
        }

        private boolean containsArticleKey(List<QueryArticleHit> hits, String expectedArticleKey, int limit) {
            int safeLimit = Math.min(limit, hits.size());
            for (int index = 0; index < safeLimit; index++) {
                QueryArticleHit hit = hits.get(index);
                if (expectedArticleKey.equals(hit.getArticleKey()) || expectedArticleKey.equals(hit.getConceptId())) {
                    return true;
                }
            }
            return false;
        }

        private Integer findFirstRelevantRank(List<String> expectedArticleKeys, List<QueryArticleHit> hits) {
            for (int index = 0; index < hits.size(); index++) {
                QueryArticleHit hit = hits.get(index);
                for (String expectedArticleKey : expectedArticleKeys) {
                    if (expectedArticleKey.equals(hit.getArticleKey()) || expectedArticleKey.equals(hit.getConceptId())) {
                        return Integer.valueOf(index + 1);
                    }
                }
            }
            return null;
        }

        private String topArticleKeys(List<QueryArticleHit> hits, int limit) {
            List<String> keys = new ArrayList<String>();
            int safeLimit = Math.min(limit, hits.size());
            for (int index = 0; index < safeLimit; index++) {
                QueryArticleHit hit = hits.get(index);
                String articleKey = hit.getArticleKey();
                if (articleKey == null || articleKey.isBlank()) {
                    articleKey = hit.getConceptId();
                }
                keys.add(articleKey == null ? "" : articleKey);
            }
            return String.join(",", keys);
        }

        private String toTextArrayLiteral(List<String> values) {
            if (values == null || values.isEmpty()) {
                return "ARRAY[]::TEXT[]";
            }
            StringBuilder literalBuilder = new StringBuilder("ARRAY[");
            for (int index = 0; index < values.size(); index++) {
                if (index > 0) {
                    literalBuilder.append(", ");
                }
                literalBuilder.append('\'')
                        .append(values.get(index).replace("'", "''"))
                        .append('\'');
            }
            literalBuilder.append("]::TEXT[]");
            return literalBuilder.toString();
        }

        private DriverManagerDataSource dataSource(String url) {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.postgresql.Driver");
            dataSource.setUrl(url);
            dataSource.setUsername(BENCHMARK_DB_USERNAME);
            dataSource.setPassword(BENCHMARK_DB_PASSWORD);
            return dataSource;
        }

        private String withCurrentSchema(String baseUrl, String schema) {
            String separator = baseUrl.contains("?") ? "&" : "?";
            return baseUrl + separator + "currentSchema=" + schema;
        }

        @Override
        public void close() {
        }
    }

    private static class FixedQueryRetrievalSettingsService extends QueryRetrievalSettingsService {

        private final QueryRetrievalSettingsState fixedState;

        private FixedQueryRetrievalSettingsService(QueryRetrievalSettingsState fixedState) {
            super();
            this.fixedState = fixedState;
        }

        @Override
        public QueryRetrievalSettingsState getCurrentState() {
            return fixedState;
        }

        @Override
        public QueryRetrievalSettingsState defaultState() {
            return fixedState;
        }
    }

    private class RealQuestionReplayHarness implements AutoCloseable {

        private final JdbcTemplate jdbcTemplate;

        private final SourceFileJdbcRepository sourceFileJdbcRepository;

        private final GraphEntityJdbcRepository graphEntityJdbcRepository;

        private final GraphFactJdbcRepository graphFactJdbcRepository;

        private final GraphRelationJdbcRepository graphRelationJdbcRepository;

        private final ArticleSourceRefJdbcRepository articleSourceRefJdbcRepository;

        private final KnowledgeSearchService currentKnowledgeSearchService;

        private final KnowledgeSearchService baselineKnowledgeSearchService;

        private RealQuestionReplayHarness() {
            DriverManagerDataSource schemaDataSource = dataSource(withCurrentSchema(BENCHMARK_DB_URL, "lattice"));
            this.jdbcTemplate = new JdbcTemplate(schemaDataSource);
            this.sourceFileJdbcRepository = new SourceFileJdbcRepository(jdbcTemplate);
            this.graphEntityJdbcRepository = new GraphEntityJdbcRepository(jdbcTemplate);
            this.graphFactJdbcRepository = new GraphFactJdbcRepository(jdbcTemplate);
            this.graphRelationJdbcRepository = new GraphRelationJdbcRepository(jdbcTemplate);
            this.articleSourceRefJdbcRepository = new ArticleSourceRefJdbcRepository(jdbcTemplate);
            QueryRetrievalSettingsState currentSettings = new QueryRetrievalSettingsService().defaultState();
            QueryRetrievalSettingsState conservativeBaseline = new QueryRetrievalSettingsState(
                    true,
                    false,
                    false,
                    1.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    QueryRetrievalSettingsState.DEFAULT_RRF_K
            );
            this.currentKnowledgeSearchService = createKnowledgeSearchService(currentSettings);
            this.baselineKnowledgeSearchService = createKnowledgeSearchService(conservativeBaseline);
        }

        private ObjectNode run(ArrayNode scenarios) {
            ArrayNode scenarioResults = OBJECT_MAPPER.createArrayNode();
            int totalExpectedTargets = 0;
            int currentMatchedTargetsAt10 = 0;
            int baselineMatchedTargetsAt10 = 0;
            int totalExpectedSnippets = 0;
            int currentMatchedSnippetsAt1 = 0;
            int baselineMatchedSnippetsAt1 = 0;
            int currentMatchedSnippetsAt3 = 0;
            int baselineMatchedSnippetsAt3 = 0;

            for (JsonNode scenario : scenarios) {
                resetData();
                String scenarioId = scenario.path("id").asText("replay");
                insertArticles(scenario.withArray("articles"), scenarioId);
                insertGraphSupport(scenario);

                String question = scenario.path("question").asText("");
                List<String> expectedArticleKeys = readStringArray(scenario.withArray("expectedArticleKeys"));
                List<String> expectedAnswerSnippets = readStringArray(scenario.withArray("expectedAnswerSnippets"));
                List<QueryArticleHit> currentHits = search(currentKnowledgeSearchService, question, scenarioId + ":current");
                List<QueryArticleHit> baselineHits = search(baselineKnowledgeSearchService, question, scenarioId + ":baseline");

                int currentMatchedAt10 = matchTargets(expectedArticleKeys, currentHits, 10);
                int baselineMatchedAt10 = matchTargets(expectedArticleKeys, baselineHits, 10);
                int currentSupportMatchedAt1 = matchExpectedSnippets(currentHits, expectedAnswerSnippets, 1);
                int baselineSupportMatchedAt1 = matchExpectedSnippets(baselineHits, expectedAnswerSnippets, 1);
                int currentSupportMatchedAt3 = matchExpectedSnippets(currentHits, expectedAnswerSnippets, 3);
                int baselineSupportMatchedAt3 = matchExpectedSnippets(baselineHits, expectedAnswerSnippets, 3);

                totalExpectedTargets += expectedArticleKeys.size();
                currentMatchedTargetsAt10 += currentMatchedAt10;
                baselineMatchedTargetsAt10 += baselineMatchedAt10;
                totalExpectedSnippets += expectedAnswerSnippets.size();
                currentMatchedSnippetsAt1 += currentSupportMatchedAt1;
                baselineMatchedSnippetsAt1 += baselineSupportMatchedAt1;
                currentMatchedSnippetsAt3 += currentSupportMatchedAt3;
                baselineMatchedSnippetsAt3 += baselineSupportMatchedAt3;

                ObjectNode scenarioResult = OBJECT_MAPPER.createObjectNode();
                scenarioResult.put("id", scenarioId);
                putNullableDouble(
                        scenarioResult,
                        "currentRecallAt10",
                        expectedArticleKeys.isEmpty() ? null : currentMatchedAt10 * 1.0D / expectedArticleKeys.size()
                );
                putNullableDouble(
                        scenarioResult,
                        "baselineRecallAt10",
                        expectedArticleKeys.isEmpty() ? null : baselineMatchedAt10 * 1.0D / expectedArticleKeys.size()
                );
                putNullableDouble(
                        scenarioResult,
                        "currentSupportAt1",
                        expectedAnswerSnippets.isEmpty() ? null : currentSupportMatchedAt1 * 1.0D / expectedAnswerSnippets.size()
                );
                putNullableDouble(
                        scenarioResult,
                        "baselineSupportAt1",
                        expectedAnswerSnippets.isEmpty() ? null : baselineSupportMatchedAt1 * 1.0D / expectedAnswerSnippets.size()
                );
                putNullableDouble(
                        scenarioResult,
                        "currentSupportAt3",
                        expectedAnswerSnippets.isEmpty() ? null : currentSupportMatchedAt3 * 1.0D / expectedAnswerSnippets.size()
                );
                putNullableDouble(
                        scenarioResult,
                        "baselineSupportAt3",
                        expectedAnswerSnippets.isEmpty() ? null : baselineSupportMatchedAt3 * 1.0D / expectedAnswerSnippets.size()
                );
                scenarioResult.put("currentTopHits", topArticleKeys(currentHits, 3));
                scenarioResult.put("baselineTopHits", topArticleKeys(baselineHits, 3));
                scenarioResults.add(scenarioResult);
            }

            ObjectNode result = OBJECT_MAPPER.createObjectNode();
            result.put("scenarioCount", scenarios.size());
            putNullableDouble(
                    result,
                    "currentRecallAt10",
                    totalExpectedTargets == 0 ? null : currentMatchedTargetsAt10 * 1.0D / totalExpectedTargets
            );
            putNullableDouble(
                    result,
                    "baselineRecallAt10",
                    totalExpectedTargets == 0 ? null : baselineMatchedTargetsAt10 * 1.0D / totalExpectedTargets
            );
            putNullableDouble(
                    result,
                    "currentSupportAt1",
                    totalExpectedSnippets == 0 ? null : currentMatchedSnippetsAt1 * 1.0D / totalExpectedSnippets
            );
            putNullableDouble(
                    result,
                    "baselineSupportAt1",
                    totalExpectedSnippets == 0 ? null : baselineMatchedSnippetsAt1 * 1.0D / totalExpectedSnippets
            );
            putNullableDouble(
                    result,
                    "currentSupportAt3",
                    totalExpectedSnippets == 0 ? null : currentMatchedSnippetsAt3 * 1.0D / totalExpectedSnippets
            );
            putNullableDouble(
                    result,
                    "baselineSupportAt3",
                    totalExpectedSnippets == 0 ? null : baselineMatchedSnippetsAt3 * 1.0D / totalExpectedSnippets
            );
            boolean leading = totalExpectedTargets > 0
                    && result.path("currentRecallAt10").asDouble(0.0D) >= result.path("baselineRecallAt10").asDouble(0.0D)
                    && result.path("currentSupportAt1").asDouble(0.0D) > result.path("baselineSupportAt1").asDouble(0.0D);
            boolean defaultBenefitLeading = totalExpectedTargets > 0
                    && (result.path("currentRecallAt10").asDouble(0.0D) > result.path("baselineRecallAt10").asDouble(0.0D)
                    || result.path("currentSupportAt1").asDouble(0.0D) > result.path("baselineSupportAt1").asDouble(0.0D)
                    || result.path("currentSupportAt3").asDouble(0.0D) > result.path("baselineSupportAt3").asDouble(0.0D));
            result.put("leading", leading);
            result.put("defaultBenefitLeading", defaultBenefitLeading);
            result.set("scenarios", scenarioResults);
            return result;
        }

        private KnowledgeSearchService createKnowledgeSearchService(QueryRetrievalSettingsState settings) {
            FixedQueryRetrievalSettingsService settingsService = new FixedQueryRetrievalSettingsService(settings);
            GraphSearchService graphSearchService = new GraphSearchService(
                    graphEntityJdbcRepository,
                    graphFactJdbcRepository,
                    graphRelationJdbcRepository,
                    articleSourceRefJdbcRepository,
                    sourceFileJdbcRepository
            );
            return new KnowledgeSearchService(
                    new FtsSearchService(jdbcTemplate),
                    new ArticleChunkFtsSearchService(new ArticleChunkJdbcRepository(jdbcTemplate)),
                    new RefKeySearchService(jdbcTemplate),
                    new SourceSearchService(null),
                    new SourceChunkFtsSearchService(null),
                    new ContributionSearchService(null),
                    graphSearchService,
                    new VectorSearchService(),
                    new ChunkVectorSearchService(),
                    new RrfFusionService(settingsService),
                    settingsService,
                    new QueryRewriteService(),
                    new QueryIntentClassifier(),
                    new RetrievalStrategyResolver(),
                    null
            );
        }

        private List<QueryArticleHit> search(KnowledgeSearchService knowledgeSearchService, String question, String queryId) {
            RetrievalQueryContext retrievalQueryContext = knowledgeSearchService.prepareContext(queryId, question);
            return knowledgeSearchService.search(retrievalQueryContext, 10);
        }

        private void resetData() {
            jdbcTemplate.execute(
                    "TRUNCATE TABLE graph_relations, graph_facts, graph_entities, article_source_refs, "
                            + "article_chunks, articles, source_file_chunks, source_files, contributions "
                            + "RESTART IDENTITY CASCADE"
            );
        }

        private void insertArticles(ArrayNode articles, String scenarioId) {
            for (JsonNode articleNode : articles) {
                String articleId = articleNode.path("id").asText("");
                String title = articleNode.path("title").asText(articleId);
                String summary = articleNode.path("summary").asText("");
                String body = articleNode.path("body").asText("");
                List<String> referentialKeywords = readStringArray(articleNode.withArray("referentialKeywords"));
                String searchText = title + "\n" + summary + "\n" + body;
                String refkeyText = String.join(" ", referentialKeywords) + " " + articleId + " " + title;
                String sourcePathLiteral = toTextArrayLiteral(List.of("benchmarks/" + scenarioId + "/" + articleId + ".md"));
                String refkeyLiteral = toTextArrayLiteral(referentialKeywords);
                String articleSql = """
                        insert into articles (
                            source_id, article_key, concept_id, title, content, lifecycle, compiled_at,
                            source_paths, metadata_json, summary, referential_keywords, depends_on, related,
                            confidence, review_status, search_text, search_tsv, refkey_text
                        )
                        values (
                            ?, ?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP,
                            %s, ?::jsonb, ?, %s, ARRAY[]::TEXT[], ARRAY[]::TEXT[],
                            'high', 'approved', ?, to_tsvector('simple'::regconfig, ?), ?
                        )
                        returning id
                        """.formatted(sourcePathLiteral, refkeyLiteral);
                Long articlePk = jdbcTemplate.queryForObject(
                        articleSql,
                        Long.class,
                        null,
                        articleId,
                        articleId,
                        title,
                        body,
                        "{\"benchmark\":true}",
                        summary,
                        searchText,
                        searchText,
                        refkeyText
                );
                List<String> chunks = splitRetrievalChunks(body);
                for (int index = 0; index < chunks.size(); index++) {
                    String chunk = chunks.get(index);
                    jdbcTemplate.update(
                            """
                                    insert into article_chunks (article_id, chunk_text, chunk_index, search_tsv)
                                    values (?, ?, ?, to_tsvector('simple'::regconfig, ?))
                                    """,
                            articlePk,
                            chunk,
                            Integer.valueOf(index),
                            chunk
                    );
                }
            }
        }

        private void insertGraphSupport(JsonNode scenario) {
            Map<Long, Long> actualSourceFileIds = insertSourceFiles(scenario.path("sourceFiles"));
            insertArticleSourceRefs(scenario.path("articleKeysBySourceFileId"), actualSourceFileIds);
            insertGraphEntities(scenario.withArray("graphEntities"), actualSourceFileIds);
            insertGraphFacts(scenario.withArray("graphFacts"));
            insertGraphRelations(scenario.withArray("graphRelations"));
        }

        private Map<Long, Long> insertSourceFiles(JsonNode sourceFilesNode) {
            Map<Long, Long> actualSourceFileIds = new LinkedHashMap<Long, Long>();
            if (sourceFilesNode == null || !sourceFilesNode.isObject()) {
                return actualSourceFileIds;
            }
            sourceFilesNode.fields().forEachRemaining(entry -> {
                JsonNode sourceFileNode = entry.getValue();
                long scenarioSourceFileId = sourceFileNode.path("id").asLong(0L);
                SourceFileRecord storedRecord = sourceFileJdbcRepository.upsert(new SourceFileRecord(
                        null,
                        sourceFileNode.path("sourceId").isMissingNode() ? 1L : Long.valueOf(sourceFileNode.path("sourceId").asLong(1L)),
                        sourceFileNode.path("filePath").asText(""),
                        sourceFileNode.path("relativePath").asText(sourceFileNode.path("filePath").asText("")),
                        null,
                        sourceFileNode.path("contentPreview").asText(""),
                        sourceFileNode.path("format").asText("TEXT"),
                        Long.valueOf(sourceFileNode.path("fileSize").asLong(0L)),
                        sourceFileNode.path("contentText").asText(""),
                        sourceFileNode.path("metadataJson").asText("{}"),
                        sourceFileNode.path("verbatim").asBoolean(false),
                        sourceFileNode.path("rawPath").asText(sourceFileNode.path("filePath").asText(""))
                ));
                actualSourceFileIds.put(Long.valueOf(scenarioSourceFileId), storedRecord.getId());
            });
            return actualSourceFileIds;
        }

        private void insertArticleSourceRefs(JsonNode mappingNode, Map<Long, Long> actualSourceFileIds) {
            if (mappingNode == null || !mappingNode.isObject()) {
                return;
            }
            Map<String, List<ArticleSourceRefRecord>> refsByArticleKey = new LinkedHashMap<String, List<ArticleSourceRefRecord>>();
            mappingNode.fields().forEachRemaining(entry -> {
                Long scenarioSourceFileId = Long.valueOf(Long.parseLong(entry.getKey()));
                Long actualSourceFileId = actualSourceFileIds.get(scenarioSourceFileId);
                if (actualSourceFileId == null) {
                    return;
                }
                for (JsonNode articleKeyNode : entry.getValue()) {
                    String articleKey = articleKeyNode.asText("");
                    if (articleKey.isBlank()) {
                        continue;
                    }
                    List<ArticleSourceRefRecord> refRecords = refsByArticleKey.computeIfAbsent(
                            articleKey,
                            ignored -> new ArrayList<ArticleSourceRefRecord>()
                    );
                    refRecords.add(new ArticleSourceRefRecord(
                            articleKey,
                            1L,
                            actualSourceFileId.longValue(),
                            "GRAPH",
                            "benchmark-graph"
                    ));
                }
            });
            for (Map.Entry<String, List<ArticleSourceRefRecord>> entry : refsByArticleKey.entrySet()) {
                articleSourceRefJdbcRepository.replaceRefs(entry.getKey(), entry.getValue());
            }
        }

        private void insertGraphEntities(ArrayNode entityNodes, Map<Long, Long> actualSourceFileIds) {
            for (JsonNode entityNode : entityNodes) {
                AstEntity entity = new AstEntity();
                entity.setId(entityNode.path("id").asText(""));
                entity.setCanonicalName(entityNode.path("canonicalName").asText(""));
                entity.setSimpleName(entityNode.path("simpleName").asText(""));
                entity.setEntityType(AstEntityType.valueOf(entityNode.path("entityType").asText("CLASS")));
                entity.setSystemLabel(entityNode.path("systemLabel").asText("benchmark"));
                Long actualSourceFileId = actualSourceFileIds.get(Long.valueOf(entityNode.path("sourceFileId").asLong(0L)));
                entity.setSourceFileId(actualSourceFileId);
                entity.setAnchorRef(entityNode.path("anchorRef").asText(""));
                entity.setResolutionStatus(entityNode.path("resolutionStatus").asText("RESOLVED"));
                entity.setMetadataJson(entityNode.path("metadataJson").asText("{}"));
                graphEntityJdbcRepository.upsert(entity);
            }
        }

        private void insertGraphFacts(ArrayNode factNodes) {
            for (JsonNode factNode : factNodes) {
                AstFact fact = new AstFact();
                fact.setEntityId(factNode.path("entityId").asText(""));
                fact.setPredicate(factNode.path("predicate").asText(""));
                fact.setValue(factNode.path("value").asText(""));
                fact.setSourceRef(factNode.path("sourceRef").asText(""));
                fact.setSourceStartLine(factNode.path("sourceStartLine").asInt(0));
                fact.setSourceEndLine(factNode.path("sourceEndLine").asInt(0));
                fact.setEvidenceExcerpt(factNode.path("evidenceExcerpt").asText(""));
                fact.setConfidence(factNode.path("confidence").asDouble(0.0D));
                fact.setExtractor(factNode.path("extractor").asText("benchmark"));
                graphFactJdbcRepository.insert(fact);
            }
        }

        private void insertGraphRelations(ArrayNode relationNodes) {
            for (JsonNode relationNode : relationNodes) {
                AstRelation relation = new AstRelation();
                relation.setSrcId(relationNode.path("srcId").asText(""));
                relation.setEdgeType(relationNode.path("edgeType").asText(""));
                relation.setDstId(relationNode.path("dstId").asText(""));
                relation.setSourceRef(relationNode.path("sourceRef").asText(""));
                relation.setSourceStartLine(relationNode.path("sourceStartLine").asInt(0));
                relation.setSourceEndLine(relationNode.path("sourceEndLine").asInt(0));
                relation.setConfidence(relationNode.path("confidence").asDouble(0.0D));
                relation.setExtractor(relationNode.path("extractor").asText("benchmark"));
                graphRelationJdbcRepository.insert(relation);
            }
        }

        private List<String> readStringArray(ArrayNode arrayNode) {
            List<String> values = new ArrayList<String>();
            for (JsonNode node : arrayNode) {
                values.add(node.asText(""));
            }
            return values;
        }

        private int matchTargets(List<String> expectedArticleKeys, List<QueryArticleHit> hits, int limit) {
            int matched = 0;
            for (String expectedArticleKey : expectedArticleKeys) {
                if (containsArticleKey(hits, expectedArticleKey, limit)) {
                    matched++;
                }
            }
            return matched;
        }

        private boolean containsArticleKey(List<QueryArticleHit> hits, String expectedArticleKey, int limit) {
            int safeLimit = Math.min(limit, hits.size());
            for (int index = 0; index < safeLimit; index++) {
                QueryArticleHit hit = hits.get(index);
                if (expectedArticleKey.equals(hit.getArticleKey()) || expectedArticleKey.equals(hit.getConceptId())) {
                    return true;
                }
            }
            return false;
        }

        private int matchExpectedSnippets(List<QueryArticleHit> hits, List<String> expectedSnippets, int limit) {
            String contentWindow = joinHitContent(hits, limit);
            int matched = 0;
            for (String expectedSnippet : expectedSnippets) {
                if (contentWindow.contains(expectedSnippet)) {
                    matched++;
                }
            }
            return matched;
        }

        private String joinHitContent(List<QueryArticleHit> hits, int limit) {
            StringBuilder contentBuilder = new StringBuilder();
            int safeLimit = Math.min(limit, hits.size());
            for (int index = 0; index < safeLimit; index++) {
                QueryArticleHit hit = hits.get(index);
                contentBuilder.append(hit.getTitle()).append("\n");
                contentBuilder.append(hit.getContent()).append("\n");
            }
            return contentBuilder.toString();
        }

        private String topArticleKeys(List<QueryArticleHit> hits, int limit) {
            List<String> keys = new ArrayList<String>();
            int safeLimit = Math.min(limit, hits.size());
            for (int index = 0; index < safeLimit; index++) {
                QueryArticleHit hit = hits.get(index);
                String articleKey = hit.getArticleKey();
                if (articleKey == null || articleKey.isBlank()) {
                    articleKey = hit.getConceptId();
                }
                keys.add(articleKey == null ? "" : articleKey);
            }
            return String.join(",", keys);
        }

        private String toTextArrayLiteral(List<String> values) {
            if (values == null || values.isEmpty()) {
                return "ARRAY[]::TEXT[]";
            }
            StringBuilder literalBuilder = new StringBuilder("ARRAY[");
            for (int index = 0; index < values.size(); index++) {
                if (index > 0) {
                    literalBuilder.append(", ");
                }
                literalBuilder.append('\'')
                        .append(values.get(index).replace("'", "''"))
                        .append('\'');
            }
            literalBuilder.append("]::TEXT[]");
            return literalBuilder.toString();
        }

        private DriverManagerDataSource dataSource(String url) {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.postgresql.Driver");
            dataSource.setUrl(url);
            dataSource.setUsername(BENCHMARK_DB_USERNAME);
            dataSource.setPassword(BENCHMARK_DB_PASSWORD);
            return dataSource;
        }

        private String withCurrentSchema(String baseUrl, String schema) {
            String separator = baseUrl.contains("?") ? "&" : "?";
            return baseUrl + separator + "currentSchema=" + schema;
        }

        @Override
        public void close() {
        }
    }

    private class ResumeRecoveryBenchmarkHarness {

        private static final int RESUME_TIMEOUT_MS = 60_000;

        private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        private final BenchmarkRedisKeyValueStore redisKeyValueStore = new BenchmarkRedisKeyValueStore();

        private ObjectNode run() {
            ObjectNode queryScenario = benchmarkQueryResume();
            ObjectNode deepResearchScenario = benchmarkDeepResearchResume();
            ObjectNode compileScenario = benchmarkCompileResume();

            ArrayNode scenarios = OBJECT_MAPPER.createArrayNode();
            scenarios.add(queryScenario);
            scenarios.add(deepResearchScenario);
            scenarios.add(compileScenario);

            double queryResumeSuccessRate = successRate(queryScenario);
            double deepResearchResumeSuccessRate = successRate(deepResearchScenario);
            double compileResumeSuccessRate = successRate(compileScenario);
            boolean checkpointRecoveryReady = queryResumeSuccessRate >= 0.9D
                    && deepResearchResumeSuccessRate >= 0.9D
                    && compileResumeSuccessRate >= 0.9D;
            boolean endToEndRecoveryReady = checkpointRecoveryReady
                    && queryScenario.path("success").asBoolean(false)
                    && deepResearchScenario.path("success").asBoolean(false)
                    && compileScenario.path("success").asBoolean(false);

            ObjectNode result = OBJECT_MAPPER.createObjectNode();
            result.put("scenarioCount", 3);
            result.put("queryResumeSuccessRate", queryResumeSuccessRate);
            result.put("deepResearchResumeSuccessRate", deepResearchResumeSuccessRate);
            result.put("compileResumeSuccessRate", compileResumeSuccessRate);
            result.put("checkpointRecoveryReady", checkpointRecoveryReady);
            result.put("endToEndRecoveryReady", endToEndRecoveryReady);
            result.set("scenarios", scenarios);
            return result;
        }

        private double successRate(ObjectNode scenario) {
            return scenario.path("success").asBoolean(false) ? 1.0D : 0.0D;
        }

        private ObjectNode benchmarkQueryResume() {
            ObjectNode scenario = newResumeScenario("query");
            String queryId = "resume-query-1";
            try {
                Path checkpointDir = prepareCheckpointDir("query");
                FileSystemSaver saver = FileSystemSaver.builder().targetFolder(checkpointDir).build();
                QueryResumeFixture initialFixture = createQueryResumeFixture();
                CompiledGraph interruptedGraph = compileQueryGraph(
                        initialFixture.queryGraphDefinitionFactory,
                        saver,
                        true
                );
                QueryGraphState initialState = new QueryGraphState();
                initialState.setQueryId(queryId);
                initialState.setQuestion("payment timeout retry=3 是什么配置");
                initialState.setRewriteAttemptCount(0);
                initialState.setMaxRewriteRounds(0);
                RunnableConfig initialConfig = RunnableConfig.builder().threadId(queryId).build();
                NodeOutput interruptionOutput = interruptedGraph.invokeAndGetOutput(
                        initialFixture.queryGraphStateMapper.toMap(initialState),
                        initialConfig
                ).orElse(null);
                StateSnapshot checkpoint = interruptedGraph.lastStateOf(initialConfig).orElse(null);

                QueryResumeFixture resumedFixture = createQueryResumeFixture();
                CompiledGraph resumedGraph = compileQueryGraph(
                        resumedFixture.queryGraphDefinitionFactory,
                        saver,
                        false
                );
                QueryGraphState resumedState = null;
                QueryResponse queryResponse = null;
                if (checkpoint != null) {
                    Optional<OverAllState> resumedStateOptional = resumedGraph.invoke(
                            resumeInput(checkpoint),
                            checkpoint.config().withResume()
                    );
                    if (resumedStateOptional.isPresent()) {
                        resumedState = resumedFixture.queryGraphStateMapper.fromMap(resumedStateOptional.get().data());
                        queryResponse = resumedFixture.queryWorkingSetStore.loadResponse(resumedState.getFinalResponseRef());
                    }
                }

                boolean success = interruptionOutput instanceof InterruptionMetadata
                        && checkpoint != null
                        && "fuse_candidates".equals(checkpoint.node())
                        && "answer_question".equals(checkpoint.next())
                        && resumedState != null
                        && queryResponse != null
                        && "PASSED".equals(queryResponse.getReviewStatus())
                        && queryResponse.getAnswer() != null
                        && queryResponse.getAnswer().contains("[[payment-timeout]]");

                scenario.put("interruptedNode", checkpoint == null ? "" : checkpoint.node());
                scenario.put("resumeNextNode", checkpoint == null ? "" : checkpoint.next());
                scenario.put("success", success);
                scenario.putArray("notes")
                        .add("finalResponseRef=" + (resumedState == null ? "" : nullToEmpty(resumedState.getFinalResponseRef())))
                        .add("answer=" + (queryResponse == null ? "" : nullToEmpty(queryResponse.getAnswer())));
                return scenario;
            }
            catch (Exception exception) {
                return failedResumeScenario(scenario, exception);
            }
        }

        private ObjectNode benchmarkDeepResearchResume() {
            ObjectNode scenario = newResumeScenario("deepresearch");
            String queryId = "resume-deep-1";
            try {
                Path checkpointDir = prepareCheckpointDir("deepresearch");
                FileSystemSaver saver = FileSystemSaver.builder().targetFolder(checkpointDir).build();
                LayeredResearchPlan plan = buildResumeDeepResearchPlan();
                DeepResearchResumeFixture initialFixture = createDeepResearchResumeFixture();
                initialFixture.executionRegistry.register(queryId, 4, RESUME_TIMEOUT_MS);

                DeepResearchState initialState = new DeepResearchState();
                initialState.setQueryId(queryId);
                initialState.setQuestion(plan.getRootQuestion());
                initialState.setRouteReason("deep_research_resume_benchmark");
                initialState.setPlanRef(initialFixture.deepResearchWorkingSetStore.savePlan(queryId, plan));
                initialState.setLlmCallBudgetRemaining(4);

                CompiledGraph interruptedGraph = compileDeepResearchGraph(
                        initialFixture.graphDefinitionFactory,
                        plan,
                        saver,
                        true
                );
                RunnableConfig initialConfig = RunnableConfig.builder().threadId(queryId).build();
                NodeOutput interruptionOutput = interruptedGraph.invokeAndGetOutput(
                        initialFixture.deepResearchStateMapper.toMap(initialState),
                        initialConfig
                ).orElse(null);
                StateSnapshot checkpoint = interruptedGraph.lastStateOf(initialConfig).orElse(null);
                DeepResearchState checkpointState = checkpoint == null
                        ? null
                        : initialFixture.deepResearchStateMapper.fromMap(checkpoint.state().data());

                DeepResearchResumeFixture resumedFixture = createDeepResearchResumeFixture();
                if (checkpointState != null) {
                    int remainingBudget = checkpointState.getLlmCallBudgetRemaining() <= 0
                            ? 1
                            : checkpointState.getLlmCallBudgetRemaining();
                    resumedFixture.executionRegistry.register(queryId, remainingBudget, RESUME_TIMEOUT_MS);
                }
                CompiledGraph resumedGraph = compileDeepResearchGraph(
                        resumedFixture.graphDefinitionFactory,
                        plan,
                        saver,
                        false
                );
                DeepResearchState resumedState = null;
                AnswerProjectionBundle projectionBundle = null;
                CitationCheckReport citationCheckReport = null;
                if (checkpoint != null) {
                    Optional<OverAllState> resumedStateOptional = resumedGraph.invoke(
                            resumeInput(checkpoint),
                            checkpoint.config().withResume()
                    );
                    if (resumedStateOptional.isPresent()) {
                        resumedState = resumedFixture.deepResearchStateMapper.fromMap(resumedStateOptional.get().data());
                        projectionBundle = resumedFixture.deepResearchWorkingSetStore.loadAnswerProjectionBundle(
                                resumedState.getProjectionRef()
                        );
                        citationCheckReport = resumedFixture.queryWorkingSetStore.loadCitationCheckReport(
                                resumedState.getCitationCheckReportRef()
                        );
                    }
                }

                boolean success = interruptionOutput instanceof InterruptionMetadata
                        && checkpoint != null
                        && "research_layer_1_task_retry".equals(checkpoint.node())
                        && "summarize_layer_1".equals(checkpoint.next())
                        && resumedState != null
                        && resumedState.getCurrentLayerIndex() == 2
                        && projectionBundle != null
                        && projectionBundle.getAnswerMarkdown() != null
                        && projectionBundle.getAnswerMarkdown().contains("[[payment-routing]]")
                        && citationCheckReport != null
                        && citationCheckReport.getVerifiedCount() > 0;

                scenario.put("interruptedNode", checkpoint == null ? "" : checkpoint.node());
                scenario.put("resumeNextNode", checkpoint == null ? "" : checkpoint.next());
                scenario.put("success", success);
                scenario.putArray("notes")
                        .add("layerSummaryCount=" + (resumedState == null ? 0 : resumedState.getLayerSummaryRefs().size()))
                        .add("projectionRef=" + (resumedState == null ? "" : nullToEmpty(resumedState.getProjectionRef())));
                return scenario;
            }
            catch (Exception exception) {
                return failedResumeScenario(scenario, exception);
            }
        }

        private ObjectNode benchmarkCompileResume() {
            ObjectNode scenario = newResumeScenario("compile");
            String jobId = "resume-compile-1";
            try {
                Path checkpointDir = prepareCheckpointDir("compile");
                FileSystemSaver saver = FileSystemSaver.builder().targetFolder(checkpointDir).build();
                RecordingCompileTailOperations operations = new RecordingCompileTailOperations();
                RedisCompileWorkingSetStore initialStore = createCompileWorkingSetStore();
                CompileGraphStateMapper compileGraphStateMapper = new CompileGraphStateMapper();

                ArticleReviewEnvelope articleReviewEnvelope = new ArticleReviewEnvelope();
                articleReviewEnvelope.setArticle(new ArticleRecord(
                        "payment-timeout",
                        "Payment Timeout",
                        """
                                # Payment Timeout

                                retry=3 [[payment-timeout]]
                                """.trim(),
                        "published",
                        OffsetDateTime.now(),
                        List.of("payment/timeouts.md"),
                        "{}"
                ));
                articleReviewEnvelope.setReviewResult(ReviewResult.passed());
                String acceptedArticlesRef = initialStore.saveAcceptedArticles(jobId, List.of(articleReviewEnvelope));

                CompileGraphState initialState = new CompileGraphState();
                initialState.setJobId(jobId);
                initialState.setSourceDir(REPORT_DIR.toString());
                initialState.setCompileMode("incremental");
                initialState.setSourceId(1L);
                initialState.setSourceCode("benchmark");
                initialState.setAcceptedArticlesRef(acceptedArticlesRef);
                initialState.setSourceFileIdsByPath(Map.of("payment/timeouts.md", Long.valueOf(101L)));
                initialState.setSynthesisRequired(true);
                initialState.setSnapshotRequired(true);

                ArticlePersistSupport firstSupport = new RecordingArticlePersistSupport(operations);
                CompiledGraph interruptedGraph = compileCompileTailGraph(
                        compileGraphStateMapper,
                        initialStore,
                        firstSupport,
                        saver,
                        true
                );
                RunnableConfig initialConfig = RunnableConfig.builder().threadId(jobId).build();
                NodeOutput interruptionOutput = interruptedGraph.invokeAndGetOutput(
                        compileGraphStateMapper.toMap(initialState),
                        initialConfig
                ).orElse(null);
                StateSnapshot checkpoint = interruptedGraph.lastStateOf(initialConfig).orElse(null);

                RedisCompileWorkingSetStore resumedStore = createCompileWorkingSetStore();
                ArticlePersistSupport resumedSupport = new RecordingArticlePersistSupport(operations);
                CompiledGraph resumedGraph = compileCompileTailGraph(
                        compileGraphStateMapper,
                        resumedStore,
                        resumedSupport,
                        saver,
                        false
                );
                CompileGraphState resumedState = null;
                if (checkpoint != null) {
                    Optional<OverAllState> resumedStateOptional = resumedGraph.invoke(
                            resumeInput(checkpoint),
                            checkpoint.config().withResume()
                    );
                    if (resumedStateOptional.isPresent()) {
                        resumedState = compileGraphStateMapper.fromMap(resumedStateOptional.get().data());
                    }
                }

                boolean success = interruptionOutput instanceof InterruptionMetadata
                        && checkpoint != null
                        && "persist_articles".equals(checkpoint.node())
                        && "rebuild_article_chunks".equals(checkpoint.next())
                        && resumedState != null
                        && resumedState.getPersistedCount() == 1
                        && operations.persistCalls == 1
                        && operations.rebuildCalls == 1
                        && operations.refreshCalls == 1
                        && operations.synthesisCalls == 1
                        && operations.snapshotCalls == 1;

                scenario.put("interruptedNode", checkpoint == null ? "" : checkpoint.node());
                scenario.put("resumeNextNode", checkpoint == null ? "" : checkpoint.next());
                scenario.put("success", success);
                scenario.putArray("notes")
                        .add("persistedCount=" + (resumedState == null ? 0 : resumedState.getPersistedCount()))
                        .add("ops=persist:" + operations.persistCalls
                                + ",rebuild:" + operations.rebuildCalls
                                + ",refresh:" + operations.refreshCalls
                                + ",synthesis:" + operations.synthesisCalls
                                + ",snapshot:" + operations.snapshotCalls);
                return scenario;
            }
            catch (Exception exception) {
                return failedResumeScenario(scenario, exception);
            }
        }

        private QueryResumeFixture createQueryResumeFixture() {
            RedisQueryWorkingSetStore queryWorkingSetStore = createQueryWorkingSetStore();
            QueryGraphStateMapper queryGraphStateMapper = new QueryGraphStateMapper();
            QueryReviewProperties queryReviewProperties = new QueryReviewProperties();
            queryReviewProperties.setRewriteEnabled(false);
            queryReviewProperties.setMaxRewriteRounds(0);
            QueryArticleHit articleHit = new QueryArticleHit(
                    1L,
                    "payment-timeout",
                    "payment-timeout",
                    "Payment Timeout",
                    "payment.timeout.retry=3",
                    "{\"description\":\"支付超时默认重试次数\"}",
                    List.of("payment/timeouts.md"),
                    9.8D
            );
            CitationExtractor citationExtractor = new CitationExtractor();
            CitationCheckService citationCheckService = new CitationCheckService(
                    citationExtractor,
                    new CitationValidator(
                            new CatalogArticleJdbcRepository(List.of(articleHit)),
                            new CatalogSourceFileJdbcRepository(List.of(articleHit))
                    )
            );
            QueryGraphDefinitionFactory queryGraphDefinitionFactory = new QueryGraphDefinitionFactory(
                    new FixedFtsSearchService(List.of(articleHit)),
                    new ArticleChunkFtsSearchService(null),
                    new RefKeySearchService(null),
                    new SourceSearchService(null),
                    new SourceChunkFtsSearchService(null),
                    new FactCardFtsSearchService(null),
                    new FactCardVectorSearchService(),
                    new ContributionSearchService(null),
                    new GraphSearchService(),
                    new VectorSearchService(),
                    new ChunkVectorSearchService(),
                    new RrfFusionService(),
                    new QueryRetrievalSettingsService(),
                    new QuerySearchProperties(),
                    new QueryRewriteService(),
                    new QueryIntentClassifier(),
                    new AnswerShapeClassifier(),
                    new RetrievalStrategyResolver(),
                    null,
                    new com.xbk.lattice.query.service.AnswerGenerationService(),
                    new NoopQueryCacheStore(),
                    new ReviewerAgent(new FixedReviewerGateway(), new ReviewResultParser()),
                    queryWorkingSetStore,
                    citationCheckService,
                    null,
                    queryGraphStateMapper,
                    new QueryGraphConditions(queryReviewProperties),
                    new QueryAnswerProjectionBuilder(citationExtractor)
            );
            return new QueryResumeFixture(
                    queryGraphDefinitionFactory,
                    queryGraphStateMapper,
                    queryWorkingSetStore
            );
        }

        private DeepResearchResumeFixture createDeepResearchResumeFixture() {
            RedisDeepResearchWorkingSetStore deepResearchWorkingSetStore = createDeepResearchWorkingSetStore();
            RedisQueryWorkingSetStore queryWorkingSetStore = createQueryWorkingSetStore();
            DeepResearchExecutionRegistry executionRegistry = new DeepResearchExecutionRegistry();
            DeepResearchStateMapper deepResearchStateMapper = new DeepResearchStateMapper();
            DeepResearchGraphDefinitionFactory graphDefinitionFactory = new DeepResearchGraphDefinitionFactory(
                    deepResearchStateMapper,
                    deepResearchWorkingSetStore,
                    queryWorkingSetStore,
                    executionRegistry,
                    new FixedDeepResearchResearcherService(),
                    new FixedDeepResearchSynthesizer()
            );
            return new DeepResearchResumeFixture(
                    graphDefinitionFactory,
                    deepResearchStateMapper,
                    deepResearchWorkingSetStore,
                    queryWorkingSetStore,
                    executionRegistry
            );
        }

        private RedisQueryWorkingSetStore createQueryWorkingSetStore() {
            QueryWorkingSetProperties properties = new QueryWorkingSetProperties();
            properties.setKeyPrefix("bench:query:ws:");
            properties.setTtlSeconds(180L);
            return new RedisQueryWorkingSetStore(redisKeyValueStore, objectMapper, properties);
        }

        private RedisDeepResearchWorkingSetStore createDeepResearchWorkingSetStore() {
            DeepResearchWorkingSetProperties properties = new DeepResearchWorkingSetProperties();
            properties.setKeyPrefix("bench:deep:ws:");
            properties.setTtlSeconds(240L);
            return new RedisDeepResearchWorkingSetStore(redisKeyValueStore, objectMapper, properties);
        }

        private RedisCompileWorkingSetStore createCompileWorkingSetStore() {
            CompileWorkingSetProperties properties = new CompileWorkingSetProperties();
            properties.setKeyPrefix("bench:compile:ws:");
            properties.setTtlSeconds(300L);
            return new RedisCompileWorkingSetStore(redisKeyValueStore, objectMapper, properties);
        }

        private CompiledGraph compileQueryGraph(
                QueryGraphDefinitionFactory queryGraphDefinitionFactory,
                FileSystemSaver saver,
                boolean interruptAfterFuseCandidates
        ) throws Exception {
            CompileConfig.Builder builder = CompileConfig.builder()
                    .saverConfig(SaverConfig.builder().register(saver).build());
            if (interruptAfterFuseCandidates) {
                builder.interruptAfter("fuse_candidates");
            }
            return queryGraphDefinitionFactory.build().compile(builder.build());
        }

        private CompiledGraph compileDeepResearchGraph(
                DeepResearchGraphDefinitionFactory graphDefinitionFactory,
                LayeredResearchPlan plan,
                FileSystemSaver saver,
                boolean interruptAfterResumeBoundary
        ) throws Exception {
            CompileConfig.Builder builder = CompileConfig.builder()
                    .saverConfig(SaverConfig.builder().register(saver).build());
            if (interruptAfterResumeBoundary) {
                builder.interruptAfter("research_layer_1_task_retry");
            }
            return graphDefinitionFactory.build(plan).compile(builder.build());
        }

        private CompiledGraph compileCompileTailGraph(
                CompileGraphStateMapper compileGraphStateMapper,
                RedisCompileWorkingSetStore compileWorkingSetStore,
                ArticlePersistSupport articlePersistSupport,
                FileSystemSaver saver,
                boolean interruptAfterPersist
        ) throws Exception {
            PersistArticlesNode persistArticlesNode = new PersistArticlesNode(
                    compileGraphStateMapper,
                    compileWorkingSetStore,
                    articlePersistSupport
            );
            RebuildArticleChunksNode rebuildArticleChunksNode = new RebuildArticleChunksNode(
                    compileGraphStateMapper,
                    compileWorkingSetStore,
                    articlePersistSupport
            );
            RefreshVectorIndexNode refreshVectorIndexNode = new RefreshVectorIndexNode(
                    compileGraphStateMapper,
                    compileWorkingSetStore,
                    articlePersistSupport
            );
            GenerateSynthesisArtifactsNode generateSynthesisArtifactsNode = new GenerateSynthesisArtifactsNode(
                    compileGraphStateMapper,
                    articlePersistSupport
            );
            CaptureRepoSnapshotNode captureRepoSnapshotNode = new CaptureRepoSnapshotNode(
                    compileGraphStateMapper,
                    articlePersistSupport
            );
            FinalizeJobNode finalizeJobNode = new FinalizeJobNode(
                    compileGraphStateMapper,
                    compileWorkingSetStore
            );

            StateGraph stateGraph = new StateGraph();
            stateGraph.addNode("persist_articles", AsyncNodeAction.node_async(persistArticlesNode::execute));
            stateGraph.addNode("rebuild_article_chunks", AsyncNodeAction.node_async(rebuildArticleChunksNode::execute));
            stateGraph.addNode("refresh_vector_index", AsyncNodeAction.node_async(refreshVectorIndexNode::execute));
            stateGraph.addNode("generate_synthesis_artifacts", AsyncNodeAction.node_async(generateSynthesisArtifactsNode::execute));
            stateGraph.addNode("capture_repo_snapshot", AsyncNodeAction.node_async(captureRepoSnapshotNode::execute));
            stateGraph.addNode("finalize_job", AsyncNodeAction.node_async(finalizeJobNode::execute));
            stateGraph.addEdge(StateGraph.START, "persist_articles");
            stateGraph.addEdge("persist_articles", "rebuild_article_chunks");
            stateGraph.addEdge("rebuild_article_chunks", "refresh_vector_index");
            stateGraph.addEdge("refresh_vector_index", "generate_synthesis_artifacts");
            stateGraph.addEdge("generate_synthesis_artifacts", "capture_repo_snapshot");
            stateGraph.addEdge("capture_repo_snapshot", "finalize_job");
            stateGraph.addEdge("finalize_job", StateGraph.END);

            CompileConfig.Builder builder = CompileConfig.builder()
                    .saverConfig(SaverConfig.builder().register(saver).build());
            if (interruptAfterPersist) {
                builder.interruptAfter("persist_articles");
            }
            return stateGraph.compile(builder.build());
        }

        private LayeredResearchPlan buildResumeDeepResearchPlan() {
            ResearchTask routingTask = new ResearchTask();
            routingTask.setTaskId("task_route");
            routingTask.setQuestion("支付路由入口是什么");
            ResearchLayer firstLayer = new ResearchLayer();
            firstLayer.setLayerIndex(0);
            firstLayer.setTasks(List.of(routingTask));

            ResearchTask retryTask = new ResearchTask();
            retryTask.setTaskId("task_retry");
            retryTask.setQuestion("超时重试配置是什么");
            retryTask.setPreferredUpstreamTaskIds(List.of("task_route"));
            ResearchLayer secondLayer = new ResearchLayer();
            secondLayer.setLayerIndex(1);
            secondLayer.setTasks(List.of(retryTask));

            LayeredResearchPlan plan = new LayeredResearchPlan();
            plan.setRootQuestion("支付超时怎么排查");
            plan.setLayers(List.of(firstLayer, secondLayer));
            return plan;
        }

        private ObjectNode newResumeScenario(String scenarioId) {
            ObjectNode scenario = OBJECT_MAPPER.createObjectNode();
            scenario.put("id", scenarioId);
            scenario.put("interruptedNode", "");
            scenario.put("resumeNextNode", "");
            scenario.put("success", false);
            scenario.set("notes", OBJECT_MAPPER.createArrayNode());
            return scenario;
        }

        private Map<String, Object> resumeInput(StateSnapshot checkpoint) {
            if (checkpoint == null || checkpoint.state() == null || checkpoint.state().data() == null) {
                return Map.of();
            }
            return new LinkedHashMap<String, Object>(checkpoint.state().data());
        }

        private ObjectNode failedResumeScenario(ObjectNode scenario, Exception exception) {
            ArrayNode notes = scenario.withArray("notes");
            notes.add("exception=" + exception.getClass().getSimpleName());
            notes.add("message=" + nullToEmpty(exception.getMessage()));
            return scenario;
        }

        private Path prepareCheckpointDir(String scenarioName) throws Exception {
            Path baseDir = REPORT_DIR.resolve("resume-checkpoints");
            Files.createDirectories(baseDir);
            return Files.createTempDirectory(baseDir, scenarioName + "-");
        }

        private String nullToEmpty(String value) {
            return value == null ? "" : value;
        }
    }

    private static class BenchmarkRedisKeyValueStore implements RedisKeyValueStore {

        private final Map<String, String> values = new ConcurrentHashMap<String, String>();

        private final Map<String, Long> expires = new ConcurrentHashMap<String, Long>();

        @Override
        public String get(String key) {
            return values.get(key);
        }

        @Override
        public void set(String key, String value, java.time.Duration ttl) {
            values.put(key, value);
            expires.put(key, Long.valueOf(ttl.getSeconds()));
        }

        @Override
        public Long getExpire(String key) {
            return expires.get(key);
        }

        @Override
        public void deleteByPrefix(String keyPrefix) {
            for (String key : List.copyOf(values.keySet())) {
                if (key.startsWith(keyPrefix)) {
                    values.remove(key);
                    expires.remove(key);
                }
            }
        }
    }

    private static class QueryResumeFixture {

        private final QueryGraphDefinitionFactory queryGraphDefinitionFactory;

        private final QueryGraphStateMapper queryGraphStateMapper;

        private final RedisQueryWorkingSetStore queryWorkingSetStore;

        private QueryResumeFixture(
                QueryGraphDefinitionFactory queryGraphDefinitionFactory,
                QueryGraphStateMapper queryGraphStateMapper,
                RedisQueryWorkingSetStore queryWorkingSetStore
        ) {
            this.queryGraphDefinitionFactory = queryGraphDefinitionFactory;
            this.queryGraphStateMapper = queryGraphStateMapper;
            this.queryWorkingSetStore = queryWorkingSetStore;
        }
    }

    private static class DeepResearchResumeFixture {

        private final DeepResearchGraphDefinitionFactory graphDefinitionFactory;

        private final DeepResearchStateMapper deepResearchStateMapper;

        private final RedisDeepResearchWorkingSetStore deepResearchWorkingSetStore;

        private final RedisQueryWorkingSetStore queryWorkingSetStore;

        private final DeepResearchExecutionRegistry executionRegistry;

        private DeepResearchResumeFixture(
                DeepResearchGraphDefinitionFactory graphDefinitionFactory,
                DeepResearchStateMapper deepResearchStateMapper,
                RedisDeepResearchWorkingSetStore deepResearchWorkingSetStore,
                RedisQueryWorkingSetStore queryWorkingSetStore,
                DeepResearchExecutionRegistry executionRegistry
        ) {
            this.graphDefinitionFactory = graphDefinitionFactory;
            this.deepResearchStateMapper = deepResearchStateMapper;
            this.deepResearchWorkingSetStore = deepResearchWorkingSetStore;
            this.queryWorkingSetStore = queryWorkingSetStore;
            this.executionRegistry = executionRegistry;
        }
    }

    private static class NoopQueryCacheStore implements QueryCacheStore {

        @Override
        public Optional<QueryResponse> get(String cacheKey) {
            return Optional.empty();
        }

        @Override
        public void put(String cacheKey, QueryResponse queryResponse) {
        }

        @Override
        public void evictAll() {
        }
    }

    private static class FixedReviewerGateway implements ReviewerGateway {

        @Override
        public String review(String reviewPrompt) {
            return """
                    {"approved":true,"rewriteRequired":false,"riskLevel":"LOW","issues":[],"userFacingRewriteHints":[],"cacheWritePolicy":"WRITE"}
                    """;
        }
    }

    private static class FixedFtsSearchService extends FtsSearchService {

        private final List<QueryArticleHit> hits;

        private FixedFtsSearchService(List<QueryArticleHit> hits) {
            super(null);
            this.hits = hits;
        }

        @Override
        public List<QueryArticleHit> search(String question, int limit) {
            return hits;
        }
    }

    private static class CatalogArticleJdbcRepository extends ArticleJdbcRepository {

        private final Map<String, ArticleRecord> articleByKey = new LinkedHashMap<String, ArticleRecord>();

        private CatalogArticleJdbcRepository(List<QueryArticleHit> evidenceCatalog) {
            super(null);
            if (evidenceCatalog == null) {
                return;
            }
            for (QueryArticleHit queryArticleHit : evidenceCatalog) {
                ArticleRecord articleRecord = new ArticleRecord(
                        queryArticleHit.getSourceId(),
                        resolveArticleKey(queryArticleHit),
                        queryArticleHit.getConceptId(),
                        queryArticleHit.getTitle(),
                        queryArticleHit.getContent(),
                        "published",
                        OffsetDateTime.now(),
                        queryArticleHit.getSourcePaths(),
                        queryArticleHit.getMetadataJson(),
                        "",
                        List.of(),
                        List.of(),
                        List.of(),
                        "high",
                        "approved"
                );
                articleByKey.put(articleRecord.getArticleKey(), articleRecord);
                articleByKey.put(articleRecord.getConceptId(), articleRecord);
            }
        }

        @Override
        public Optional<ArticleRecord> findByArticleKey(String articleKey) {
            return Optional.ofNullable(articleByKey.get(articleKey));
        }

        @Override
        public Optional<ArticleRecord> findByConceptId(String conceptId) {
            return Optional.ofNullable(articleByKey.get(conceptId));
        }
    }

    private static class CatalogSourceFileJdbcRepository extends SourceFileJdbcRepository {

        private final Map<String, SourceFileRecord> sourceFileByPath = new LinkedHashMap<String, SourceFileRecord>();

        private CatalogSourceFileJdbcRepository(List<QueryArticleHit> evidenceCatalog) {
            super(null);
            long sourceFileId = 100L;
            if (evidenceCatalog == null) {
                return;
            }
            for (QueryArticleHit queryArticleHit : evidenceCatalog) {
                if (queryArticleHit.getSourcePaths() == null) {
                    continue;
                }
                for (String sourcePath : queryArticleHit.getSourcePaths()) {
                    sourceFileByPath.put(
                            sourcePath,
                            new SourceFileRecord(
                                    Long.valueOf(sourceFileId++),
                                    queryArticleHit.getSourceId(),
                                    sourcePath,
                                    sourcePath,
                                    null,
                                    queryArticleHit.getContent(),
                                    "TEXT",
                                    Long.valueOf(queryArticleHit.getContent().length()),
                                    queryArticleHit.getContent(),
                                    "{}",
                                    false,
                                    sourcePath
                            )
                    );
                }
            }
        }

        @Override
        public Optional<SourceFileRecord> findByPath(String filePath) {
            return Optional.ofNullable(sourceFileByPath.get(filePath));
        }
    }

    private static class FixedDeepResearchResearcherService extends DeepResearchResearcherService {

        private FixedDeepResearchResearcherService() {
            super(null, null);
        }

        @Override
        public EvidenceCard research(
                String queryId,
                ResearchTask task,
                int layerIndex,
                LayerSummary previousLayerSummary,
                List<EvidenceCard> preferredCards,
                DeepResearchExecutionContext executionContext
        ) {
            if (executionContext != null) {
                executionContext.tryAcquireLlmCall();
            }
            String anchorId = evidenceAnchorId(task, layerIndex);
            EvidenceCard evidenceCard = new EvidenceCard();
            evidenceCard.setEvidenceId(anchorId);
            evidenceCard.setLayerIndex(layerIndex);
            evidenceCard.setTaskId(task.getTaskId());
            evidenceCard.setScope(task.getQuestion());
            evidenceCard.setSelectedArticleKeys(List.of("payment-routing"));
            evidenceCard.getEvidenceAnchors().add(articleAnchor(anchorId));
            evidenceCard.getFactFindings().add(factFinding(task, anchorId));
            for (EvidenceCard preferredCard : preferredCards) {
                evidenceCard.getRelatedLeads().add(preferredCard.getEvidenceId());
            }
            return evidenceCard;
        }

        private String evidenceAnchorId(ResearchTask task, int layerIndex) {
            if (task != null && "task_route".equals(task.getTaskId())) {
                return "ev#1";
            }
            if (task != null && "task_retry".equals(task.getTaskId())) {
                return "ev#2";
            }
            return "ev#" + (layerIndex + 1);
        }

        private EvidenceAnchor articleAnchor(String anchorId) {
            EvidenceAnchor evidenceAnchor = new EvidenceAnchor();
            evidenceAnchor.setAnchorId(anchorId);
            evidenceAnchor.setSourceType(EvidenceAnchorSourceType.ARTICLE);
            evidenceAnchor.setSourceId("payment-routing");
            evidenceAnchor.setQuoteText("RoutePlanner -> PaymentService.plan");
            evidenceAnchor.setRetrievalScore(0.9D);
            return evidenceAnchor;
        }

        private FactFinding factFinding(ResearchTask task, String anchorId) {
            FactFinding factFinding = new FactFinding();
            factFinding.setFindingId(anchorId + "-finding");
            factFinding.setSubject(task.getTaskId());
            factFinding.setPredicate("claim");
            factFinding.setQualifier("deep_research");
            factFinding.setValueText(task.getQuestion() + " 的结论");
            factFinding.setFactKey(factFinding.expectedFactKey());
            factFinding.setValueType(FactValueType.STRING);
            factFinding.setClaimText(task.getQuestion() + " 的结论");
            factFinding.setConfidence(0.9D);
            factFinding.setSupportLevel(FindingSupportLevel.DIRECT);
            factFinding.setAnchorIds(List.of(anchorId));
            return factFinding;
        }
    }

    private static class FixedDeepResearchSynthesizer extends DeepResearchSynthesizer {

        private FixedDeepResearchSynthesizer() {
            super(null);
        }

        @Override
        public DeepResearchSynthesisResult synthesize(
                String question,
                List<LayerSummary> layerSummaries,
                EvidenceLedger evidenceLedger
        ) {
            Citation citation = new Citation(
                    0,
                    "[[payment-routing]]",
                    CitationSourceType.ARTICLE,
                    "payment-routing",
                    "深研结论",
                    "RoutePlanner -> PaymentService.plan [[payment-routing]]"
            );
            ClaimSegment claimSegment = new ClaimSegment(
                    0,
                    "RoutePlanner -> PaymentService.plan",
                    "RoutePlanner -> PaymentService.plan [[payment-routing]]",
                    List.of(citation)
            );
            CitationValidationResult validationResult = new CitationValidationResult(
                    "payment-routing",
                    CitationSourceType.ARTICLE,
                    CitationValidationStatus.VERIFIED,
                    0.9D,
                    "rule_overlap_verified",
                    "RoutePlanner -> PaymentService.plan",
                    0
            );
            AnswerProjection answerProjection = new AnswerProjection(
                    1,
                    "ev#1",
                    ProjectionCitationFormat.ARTICLE,
                    "[[payment-routing]]",
                    "payment-routing",
                    ProjectionStatus.ACTIVE,
                    0,
                    null
            );
            DeepResearchSynthesisResult result = new DeepResearchSynthesisResult();
            result.setAnswerMarkdown("# 深度研究结论\n\n- RoutePlanner -> PaymentService.plan [[payment-routing]]");
            result.setAnswerProjectionBundle(new AnswerProjectionBundle(
                    result.getAnswerMarkdown(),
                    List.of(answerProjection)
            ));
            result.setCitationCheckReport(new CitationCheckReport(
                    result.getAnswerMarkdown(),
                    List.of(claimSegment),
                    List.of(validationResult),
                    1,
                    0,
                    0,
                    false,
                    1.0D,
                    0,
                    0,
                    0,
                    0
            ));
            result.setPartialAnswer(false);
            result.setHasConflicts(false);
            result.setEvidenceCardCount(evidenceLedger == null ? 0 : evidenceLedger.cardCount());
            return result;
        }
    }

    private static class RecordingCompileTailOperations {

        private int persistCalls;

        private int rebuildCalls;

        private int refreshCalls;

        private int synthesisCalls;

        private int snapshotCalls;
    }

    private static class RecordingArticlePersistSupport extends ArticlePersistSupport {

        private final RecordingCompileTailOperations operations;

        private RecordingArticlePersistSupport(RecordingCompileTailOperations operations) {
            super(null, null, null, (CompilationWalStore) null, null, null, null);
            this.operations = operations;
        }

        @Override
        public int persistArticles(
                String jobId,
                List<ArticleReviewEnvelope> reviewedArticles,
                Long sourceId,
                String sourceCode,
                Map<String, Long> sourceFileIdsByPath
        ) {
            operations.persistCalls++;
            return reviewedArticles == null ? 0 : reviewedArticles.size();
        }

        @Override
        public void rebuildArticleChunks(List<ArticleReviewEnvelope> reviewedArticles) {
            operations.rebuildCalls++;
        }

        @Override
        public void refreshVectorIndex(List<ArticleReviewEnvelope> reviewedArticles) {
            operations.refreshCalls++;
        }

        @Override
        public void generateGraphSynthesisArtifacts(String jobId) {
            operations.synthesisCalls++;
        }

        @Override
        public void captureRepoSnapshot(String triggerEvent, Path sourceDir, int persistedCount) {
            operations.snapshotCalls++;
        }
    }

    private static String resolveArticleKey(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return "";
        }
        if (queryArticleHit.getArticleKey() != null && !queryArticleHit.getArticleKey().isBlank()) {
            return queryArticleHit.getArticleKey();
        }
        if (queryArticleHit.getConceptId() != null) {
            return queryArticleHit.getConceptId();
        }
        return "";
    }

    private static class BenchmarkArticleJdbcRepository extends ArticleJdbcRepository {

        private final Map<String, ArticleRecord> articleByKey;

        private BenchmarkArticleJdbcRepository(JsonNode articleNode) {
            super(null);
            this.articleByKey = new LinkedHashMap<String, ArticleRecord>();
            if (articleNode != null && articleNode.isObject()) {
                articleNode.fields().forEachRemaining(entry -> articleByKey.put(
                        entry.getKey(),
                        new ArticleRecord(
                                1L,
                                entry.getKey(),
                                entry.getKey(),
                                entry.getKey(),
                                entry.getValue().asText(""),
                                "published",
                                OffsetDateTime.now(),
                                List.of(),
                                "{}",
                                "",
                                List.of(),
                                List.of(),
                                List.of(),
                                "high",
                                "approved"
                        )
                ));
            }
        }

        @Override
        public Optional<ArticleRecord> findByArticleKey(String articleKey) {
            return Optional.ofNullable(articleByKey.get(articleKey));
        }

        @Override
        public Optional<ArticleRecord> findByConceptId(String conceptId) {
            return findByArticleKey(conceptId);
        }
    }

    private enum LegacySeparator {
        ADOPTS("采用", "adopts"),
        USES("使用", "uses"),
        THROUGH("通过", "through"),
        EXPOSES("暴露", "exposes"),
        CALLS("调用", "calls"),
        ENTERS("进入", "enters"),
        DEPENDS_ON("依赖", "depends_on"),
        IS("是", "is"),
        AS("为", "as");

        private final String literal;

        private final String predicate;

        LegacySeparator(String literal, String predicate) {
            this.literal = literal;
            this.predicate = predicate;
        }

        public String literal() {
            return literal;
        }

        public String predicate() {
            return predicate;
        }
    }

    private static final class LegacyClaimParts {

        private final String subject;

        private final String predicate;

        private final String qualifier;

        private final String valueText;

        private LegacyClaimParts(String subject, String predicate, String qualifier, String valueText) {
            this.subject = subject;
            this.predicate = predicate;
            this.qualifier = qualifier;
            this.valueText = valueText;
        }

        private String getSubject() {
            return subject;
        }

        private String getPredicate() {
            return predicate;
        }

        private String getQualifier() {
            return qualifier;
        }

        private String getValueText() {
            return valueText;
        }
    }

    private static class BenchmarkSourceFileJdbcRepository extends SourceFileJdbcRepository {

        private final Map<String, SourceFileRecord> sourceFileByPath;

        private BenchmarkSourceFileJdbcRepository(JsonNode sourceFileNode) {
            super(null);
            this.sourceFileByPath = new LinkedHashMap<String, SourceFileRecord>();
            if (sourceFileNode != null && sourceFileNode.isObject()) {
                sourceFileNode.fields().forEachRemaining(entry -> sourceFileByPath.put(
                        entry.getKey(),
                        new SourceFileRecord(
                                101L,
                                1L,
                                entry.getKey(),
                                entry.getKey(),
                                null,
                                entry.getValue().asText(""),
                                "TEXT",
                                entry.getValue().asText("").length(),
                                entry.getValue().asText(""),
                                "{}",
                                false,
                                entry.getKey()
                        )
                ));
            }
        }

        @Override
        public Optional<SourceFileRecord> findByPath(String filePath) {
            return Optional.ofNullable(sourceFileByPath.get(filePath));
        }
    }

    private static class BenchmarkGraphEntityJdbcRepository extends GraphEntityJdbcRepository {

        private final List<AstEntity> entities;

        private BenchmarkGraphEntityJdbcRepository(ArrayNode entityNodes) {
            super(null);
            this.entities = new ArrayList<AstEntity>();
            for (JsonNode entityNode : entityNodes) {
                AstEntity entity = new AstEntity();
                entity.setId(entityNode.path("id").asText(""));
                entity.setCanonicalName(entityNode.path("canonicalName").asText(""));
                entity.setSimpleName(entityNode.path("simpleName").asText(""));
                entity.setEntityType(AstEntityType.valueOf(entityNode.path("entityType").asText("CLASS")));
                entity.setSystemLabel(entityNode.path("systemLabel").asText(""));
                entity.setSourceFileId(entityNode.path("sourceFileId").asLong());
                entity.setAnchorRef(entityNode.path("anchorRef").asText(""));
                entity.setResolutionStatus(entityNode.path("resolutionStatus").asText("RESOLVED"));
                entity.setMetadataJson(entityNode.path("metadataJson").asText("{}"));
                entities.add(entity);
            }
        }

        @Override
        public List<AstEntity> searchByMentions(List<String> mentions, int limit) {
            return entities;
        }
    }

    private static class BenchmarkGraphFactJdbcRepository extends GraphFactJdbcRepository {

        private final List<AstFact> facts;

        private BenchmarkGraphFactJdbcRepository(ArrayNode factNodes) {
            super(null);
            this.facts = new ArrayList<AstFact>();
            for (JsonNode factNode : factNodes) {
                AstFact fact = new AstFact();
                fact.setEntityId(factNode.path("entityId").asText(""));
                fact.setPredicate(factNode.path("predicate").asText(""));
                fact.setValue(factNode.path("value").asText(""));
                fact.setSourceRef(factNode.path("sourceRef").asText(""));
                fact.setSourceStartLine(factNode.path("sourceStartLine").asInt(0));
                fact.setSourceEndLine(factNode.path("sourceEndLine").asInt(0));
                fact.setEvidenceExcerpt(factNode.path("evidenceExcerpt").asText(""));
                fact.setConfidence(factNode.path("confidence").asDouble(0.0D));
                fact.setExtractor(factNode.path("extractor").asText(""));
                facts.add(fact);
            }
        }

        @Override
        public List<AstFact> findActiveFactsByEntityIds(List<String> entityIds, int limit) {
            List<AstFact> matchedFacts = new ArrayList<AstFact>();
            for (AstFact fact : facts) {
                if (entityIds.contains(fact.getEntityId())) {
                    matchedFacts.add(fact);
                }
            }
            return matchedFacts;
        }
    }

    private static class BenchmarkGraphRelationJdbcRepository extends GraphRelationJdbcRepository {

        private final List<AstRelation> relations;

        private BenchmarkGraphRelationJdbcRepository(ArrayNode relationNodes) {
            super(null);
            this.relations = new ArrayList<AstRelation>();
            for (JsonNode relationNode : relationNodes) {
                AstRelation relation = new AstRelation();
                relation.setSrcId(relationNode.path("srcId").asText(""));
                relation.setEdgeType(relationNode.path("edgeType").asText(""));
                relation.setDstId(relationNode.path("dstId").asText(""));
                relation.setSourceRef(relationNode.path("sourceRef").asText(""));
                relation.setSourceStartLine(relationNode.path("sourceStartLine").asInt(0));
                relation.setSourceEndLine(relationNode.path("sourceEndLine").asInt(0));
                relation.setConfidence(relationNode.path("confidence").asDouble(0.0D));
                relation.setExtractor(relationNode.path("extractor").asText(""));
                relations.add(relation);
            }
        }

        @Override
        public List<AstRelation> findActiveRelationsByEntityIds(List<String> entityIds, int limit) {
            List<AstRelation> matchedRelations = new ArrayList<AstRelation>();
            for (AstRelation relation : relations) {
                if (entityIds.contains(relation.getSrcId())) {
                    matchedRelations.add(relation);
                }
            }
            return matchedRelations;
        }
    }

    private static class BenchmarkArticleSourceRefJdbcRepository extends ArticleSourceRefJdbcRepository {

        private final Map<Long, List<String>> articleKeysBySourceFileId;

        private BenchmarkArticleSourceRefJdbcRepository(JsonNode articleKeysNode) {
            super(null);
            this.articleKeysBySourceFileId = new LinkedHashMap<Long, List<String>>();
            if (articleKeysNode != null && articleKeysNode.isObject()) {
                articleKeysNode.fields().forEachRemaining(entry -> {
                    List<String> articleKeys = new ArrayList<String>();
                    entry.getValue().forEach(value -> articleKeys.add(value.asText("")));
                    articleKeysBySourceFileId.put(Long.parseLong(entry.getKey()), articleKeys);
                });
            }
        }

        @Override
        public Map<Long, List<String>> findArticleKeysBySourceFileIds(List<Long> sourceFileIds) {
            Map<Long, List<String>> matchedMap = new LinkedHashMap<Long, List<String>>();
            for (Long sourceFileId : sourceFileIds) {
                if (articleKeysBySourceFileId.containsKey(sourceFileId)) {
                    matchedMap.put(sourceFileId, articleKeysBySourceFileId.get(sourceFileId));
                }
            }
            return matchedMap;
        }
    }

    private static class BenchmarkGraphSourceFileJdbcRepository extends SourceFileJdbcRepository {

        private final Map<Long, SourceFileRecord> sourceFilesById;

        private BenchmarkGraphSourceFileJdbcRepository(JsonNode sourceFilesNode) {
            super(null);
            this.sourceFilesById = new LinkedHashMap<Long, SourceFileRecord>();
            if (sourceFilesNode != null && sourceFilesNode.isObject()) {
                sourceFilesNode.fields().forEachRemaining(entry -> {
                    JsonNode sourceFileNode = entry.getValue();
                    Long id = sourceFileNode.path("id").asLong();
                    sourceFilesById.put(id, new SourceFileRecord(
                            id,
                            sourceFileNode.path("sourceId").isMissingNode() ? null : sourceFileNode.path("sourceId").asLong(),
                            sourceFileNode.path("filePath").asText(""),
                            sourceFileNode.path("relativePath").asText(""),
                            null,
                            sourceFileNode.path("contentPreview").asText(""),
                            sourceFileNode.path("format").asText("TEXT"),
                            sourceFileNode.path("fileSize").asLong(0L),
                            sourceFileNode.path("contentText").asText(""),
                            sourceFileNode.path("metadataJson").asText("{}"),
                            sourceFileNode.path("verbatim").asBoolean(false),
                            sourceFileNode.path("rawPath").asText("")
                    ));
                });
            }
        }

        @Override
        public Map<Long, SourceFileRecord> findByIds(List<Long> sourceFileIds) {
            Map<Long, SourceFileRecord> matchedMap = new LinkedHashMap<Long, SourceFileRecord>();
            for (Long sourceFileId : sourceFileIds) {
                if (sourceFilesById.containsKey(sourceFileId)) {
                    matchedMap.put(sourceFileId, sourceFilesById.get(sourceFileId));
                }
            }
            return matchedMap;
        }
    }
}
