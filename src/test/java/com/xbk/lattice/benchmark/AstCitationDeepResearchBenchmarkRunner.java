package com.xbk.lattice.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xbk.lattice.compiler.ast.domain.AstEntity;
import com.xbk.lattice.compiler.ast.domain.AstEntityType;
import com.xbk.lattice.compiler.ast.domain.AstFact;
import com.xbk.lattice.compiler.ast.domain.AstRelation;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ArticleSourceRefJdbcRepository;
import com.xbk.lattice.infra.persistence.GraphEntityJdbcRepository;
import com.xbk.lattice.infra.persistence.GraphFactJdbcRepository;
import com.xbk.lattice.infra.persistence.GraphRelationJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import com.xbk.lattice.query.citation.CitationCheckReport;
import com.xbk.lattice.query.citation.CitationCheckService;
import com.xbk.lattice.query.citation.CitationExtractor;
import com.xbk.lattice.query.citation.CitationValidationResult;
import com.xbk.lattice.query.citation.CitationValidator;
import com.xbk.lattice.query.deepresearch.domain.EvidenceCard;
import com.xbk.lattice.query.deepresearch.domain.EvidenceLedger;
import com.xbk.lattice.query.deepresearch.domain.LayerSummary;
import com.xbk.lattice.query.evidence.domain.EvidenceAnchor;
import com.xbk.lattice.query.evidence.domain.EvidenceAnchorSourceType;
import com.xbk.lattice.query.evidence.domain.FactFinding;
import com.xbk.lattice.query.evidence.domain.FactValueType;
import com.xbk.lattice.query.evidence.domain.FindingSupportLevel;
import com.xbk.lattice.query.deepresearch.service.DeepResearchSynthesizer;
import com.xbk.lattice.query.service.GraphSearchService;
import com.xbk.lattice.query.service.QueryArticleHit;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AST / Citation / Deep Research 对标 benchmark runner
 *
 * 职责：读取共享题集，执行 Java shadow、TS shadow，并产出 gap report
 */
class AstCitationDeepResearchBenchmarkRunner {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

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

    @Test
    void shouldGenerateGapReport() throws Exception {
        Files.createDirectories(REPORT_DIR);
        JsonNode dataset = OBJECT_MAPPER.readTree(Files.readString(DATASET_PATH, StandardCharsets.UTF_8));

        ObjectNode javaResult = runJavaShadow(dataset);
        Path javaOutputPath = REPORT_DIR.resolve("java-shadow-result.json");
        Files.writeString(javaOutputPath, OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(javaResult));

        Path tsOutputPath = REPORT_DIR.resolve("ts-shadow-result.json");
        ObjectNode tsResult = runTsShadow(tsOutputPath);

        ObjectNode comparison = compare(javaResult, tsResult);
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

    private ObjectNode runJavaShadow(JsonNode dataset) {
        ArrayNode scenarioResults = OBJECT_MAPPER.createArrayNode();
        for (JsonNode scenario : dataset.withArray("citationScenarios")) {
            scenarioResults.add(runJavaCitationScenario(scenario));
        }
        for (JsonNode scenario : dataset.withArray("deepResearchScenarios")) {
            scenarioResults.add(runJavaDeepResearchScenario(scenario));
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

    private ObjectNode compare(ObjectNode javaResult, ObjectNode tsResult) {
        Map<String, JsonNode> javaScenarioMap = indexScenarios(javaResult.withArray("scenarios"));
        Map<String, JsonNode> tsScenarioMap = indexScenarios(tsResult.withArray("scenarios"));

        ArrayNode scenarioComparisons = OBJECT_MAPPER.createArrayNode();
        Map<String, Integer> taxonomyCounts = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, JsonNode> entry : javaScenarioMap.entrySet()) {
            String scenarioId = entry.getKey();
            JsonNode javaScenario = entry.getValue();
            JsonNode tsScenario = tsScenarioMap.get(scenarioId);
            if (tsScenario == null || "graph".equals(javaScenario.path("kind").asText(""))) {
                continue;
            }
            ObjectNode scenarioComparison = OBJECT_MAPPER.createObjectNode();
            scenarioComparison.put("id", scenarioId);
            scenarioComparison.put("kind", javaScenario.path("kind").asText(""));
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
        ObjectNode taxonomyNode = comparison.putObject("topGaps");
        taxonomyCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .forEach(entry -> taxonomyNode.put(entry.getKey(), entry.getValue()));
        boolean outperform = javaResult.path("metrics").path("unsupportedClaimRate").asDouble(Double.MAX_VALUE)
                < tsResult.path("metrics").path("unsupportedClaimRate").asDouble(Double.MAX_VALUE)
                && !isLower(javaResult.path("metrics"), tsResult.path("metrics"), "citationPrecision")
                && !isLower(javaResult.path("metrics"), tsResult.path("metrics"), "multiHopCompleteness");
        comparison.put("outperformOriginal", outperform);
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
                .append("（原版无同构 shadow 指标）").append("\n");
        reportBuilder.append("- 是否满足“超过原版”门槛：")
                .append(comparison.path("outperformOriginal").asBoolean(false) ? "是" : "否")
                .append("\n\n");

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
        return reportBuilder.toString();
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

        for (JsonNode scenario : scenarios) {
            if (!"graph".equals(scenario.path("kind").asText(""))) {
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
        return metrics;
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
