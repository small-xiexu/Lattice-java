#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { execFileSync } from "node:child_process";

const rootDir = path.resolve(path.dirname(new URL(import.meta.url).pathname), "..");
const suitePath = path.resolve(
  rootDir,
  process.env.QUERY_REGRESSION_SUITE || "docs/test/query-regression-suite.json"
);
const baseUrl = stripTrailingSlash(process.env.QUERY_REGRESSION_BASE_URL || "http://127.0.0.1:18082");
const outputDir = path.resolve(
  rootDir,
  process.env.QUERY_REGRESSION_OUTPUT_DIR || `.codex/run/query-regression-${timestamp()}`
);
const allowFailures = process.env.QUERY_REGRESSION_ALLOW_FAILURES === "1";

const suite = JSON.parse(fs.readFileSync(suitePath, "utf8"));
const defaults = suite.defaults || {};
const gates = suite.gates || {};
const cases = Array.isArray(suite.cases) ? suite.cases : [];

if (cases.length === 0) {
  throw new Error(`query regression suite has no cases: ${suitePath}`);
}

fs.mkdirSync(outputDir, { recursive: true });

const resultJsonlPath = path.join(outputDir, "query_results.jsonl");
const summaryTsvPath = path.join(outputDir, "query_summary.tsv");
const metricsJsonPath = path.join(outputDir, "query_metrics.json");
const replayManifestPath = path.join(outputDir, "query_replay_manifest.json");
fs.writeFileSync(resultJsonlPath, "");

const summaryRows = [];
for (const testCase of cases) {
  const startedAt = Date.now();
  const requestBody = buildRequestBody(testCase);
  const timeoutMs = Number(testCase.timeoutMs || defaults.timeoutMs || 120000);
  const result = await runCase(testCase, requestBody, timeoutMs, startedAt);
  fs.appendFileSync(resultJsonlPath, `${JSON.stringify(result)}\n`);
  summaryRows.push(summaryRow(result));
  console.log(`[query-regression] ${result.id} ${result.pass ? "PASS" : "FAIL"} ${result.elapsedMs}ms`);
}

writeSummary(summaryTsvPath, summaryRows);
const metrics = calculateMetrics(summaryRows, gates);
fs.writeFileSync(metricsJsonPath, `${JSON.stringify(metrics, null, 2)}\n`);
writeReplayManifest(replayManifestPath, suite, cases, summaryRows, metrics);

console.log(`[query-regression] summary: ${summaryTsvPath}`);
console.log(`[query-regression] results: ${resultJsonlPath}`);
console.log(`[query-regression] metrics: ${metricsJsonPath}`);
console.log(`[query-regression] replay manifest: ${replayManifestPath}`);

if (!metrics.pass && !allowFailures) {
  console.error(`[query-regression] gate failed: ${metrics.failedReasons.join("; ")}`);
  process.exit(1);
}

async function runCase(testCase, requestBody, timeoutMs, startedAt) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(`${baseUrl}/api/v1/query`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json"
      },
      body: JSON.stringify(requestBody),
      signal: controller.signal
    });
    const responseText = await response.text();
    const responseBody = parseJsonOrText(responseText);
    const elapsedMs = Date.now() - startedAt;
    const assertion = evaluateCase(testCase, response.status, responseBody, elapsedMs);
    return {
      id: testCase.id,
      category: testCase.category || "",
      question: testCase.question,
      request: requestBody,
      httpStatus: response.status,
      elapsedMs,
      timeout: false,
      pass: assertion.pass,
      failedReasons: assertion.failedReasons,
      metrics: extractResponseMetrics(responseBody),
      response: responseBody
    };
  }
  catch (error) {
    const elapsedMs = Date.now() - startedAt;
    const isTimeout = error && error.name === "AbortError";
    return {
      id: testCase.id,
      category: testCase.category || "",
      question: testCase.question,
      request: requestBody,
      httpStatus: 0,
      elapsedMs,
      timeout: isTimeout,
      pass: false,
      failedReasons: [isTimeout ? "timeout" : `request_error:${error.message || String(error)}`],
      metrics: {},
      response: null
    };
  }
  finally {
    clearTimeout(timeout);
  }
}

function buildRequestBody(testCase) {
  const requestBody = {
    question: testCase.question
  };
  for (const key of ["forceDeep", "forceSimple", "maxLlmCalls", "overallTimeoutMs"]) {
    if (testCase[key] !== undefined) {
      requestBody[key] = testCase[key];
    }
  }
  return requestBody;
}

function evaluateCase(testCase, httpStatus, responseBody) {
  const expectation = testCase.expect || {};
  const failedReasons = [];
  const metrics = extractResponseMetrics(responseBody);
  expectEqual(failedReasons, "httpStatus", httpStatus, expectation.httpStatus || 200);
  expectOneOf(failedReasons, "answerOutcome", metrics.answerOutcome, expectation.answerOutcomeAny);
  expectOneOf(failedReasons, "generationMode", metrics.generationMode, expectation.generationModeAny);
  expectOneOf(
    failedReasons,
    "modelExecutionStatus",
    metrics.modelExecutionStatus,
    expectation.modelExecutionStatusAny
  );
  if (expectation.requireQueryId === true && !metrics.queryId) {
    failedReasons.push("queryId_missing");
  }
  if (expectation.requireQueryId === false && metrics.queryId) {
    failedReasons.push("queryId_unexpected");
  }
  expectMin(failedReasons, "sourceCount", metrics.sourceCount, readNumber(expectation.minSourceCount, defaults.minSourceCount));
  expectMin(failedReasons, "articleCount", metrics.articleCount, expectation.minArticleCount);
  expectMin(failedReasons, "citationMarkerCount", metrics.citationMarkerCount, expectation.minCitationMarkerCount);
  expectMin(
    failedReasons,
    "citationCoverage",
    metrics.citationCoverage,
    readNumber(expectation.minCitationCoverage, defaults.minCitationCoverage)
  );
  const requiredAnswerHaystack = expectation.ignoreQuestionForAnswerTerms === false
    ? metrics.answerText
    : stripQuestionEcho(metrics.answerText, testCase.question);
  expectTerms(failedReasons, "answer", requiredAnswerHaystack, expectation.requiredAnswerTerms, true);
  expectTerms(failedReasons, "answer", metrics.answerText, expectation.forbiddenAnswerTerms, false);
  expectTerms(failedReasons, "source", metrics.sourceText, expectation.requiredSourceTerms, true);
  return {
    pass: failedReasons.length === 0,
    failedReasons
  };
}

function extractResponseMetrics(responseBody) {
  if (!responseBody || typeof responseBody !== "object") {
    return {
      answerText: "",
      sourceText: "",
      sourceCount: 0,
      articleCount: 0,
      citationMarkerCount: 0,
      citationCoverage: 0
    };
  }
  const citationCheck = responseBody.citationCheck || {};
  const citationCoverage = Number(citationCheck.coverageRate || 0);
  const sources = Array.isArray(responseBody.sources) ? responseBody.sources : [];
  const articles = Array.isArray(responseBody.articles) ? responseBody.articles : [];
  const citationMarkers = Array.isArray(responseBody.citationMarkers) ? responseBody.citationMarkers : [];
  const structuredEvidence = responseBody.structuredEvidence || null;
  const structuredEvidencePresent = hasStructuredEvidence(structuredEvidence);
  const deterministicSourceEvidencePresent = hasDeterministicSourceEvidence(responseBody, sources);
  const effectiveCitationCoverage = (structuredEvidencePresent || deterministicSourceEvidencePresent) && sources.length > 0
    ? 1
    : citationCoverage;
  return {
    queryId: stringValue(responseBody.queryId),
    reviewStatus: stringValue(responseBody.reviewStatus),
    answerOutcome: stringValue(responseBody.answerOutcome),
    generationMode: stringValue(responseBody.generationMode),
    modelExecutionStatus: stringValue(responseBody.modelExecutionStatus),
    fallbackReason: stringValue(responseBody.fallbackReason),
    answerText: stringValue(responseBody.answer),
    sourceText: JSON.stringify({ sources, articles, structuredEvidence }),
    sourceCount: sources.length,
    articleCount: articles.length,
    citationMarkerCount: citationMarkers.length,
    citationCoverage: Number.isFinite(effectiveCitationCoverage) ? effectiveCitationCoverage : 0,
    citationVerifiedCount: Number(citationCheck.verifiedCount || 0),
    citationDemotedCount: Number(citationCheck.demotedCount || 0),
    citationSkippedCount: Number(citationCheck.skippedCount || 0),
    citationPrecision: calculateCitationPrecision(citationCheck, citationMarkers),
    unsupportedClaimRate: calculateUnsupportedClaimRate(citationCheck),
    citationEvaluable: isCitationEvaluable(citationCheck, citationMarkers),
    structuredEvidencePresent,
    deterministicSourceEvidencePresent,
    retrievalIdentityTexts: collectRetrievalIdentityTexts(sources, articles, structuredEvidence),
    deepResearchPresent: responseBody.deepResearch != null
  };
}

function hasStructuredEvidence(structuredEvidence) {
  if (!structuredEvidence || typeof structuredEvidence !== "object") {
    return false;
  }
  const rows = Array.isArray(structuredEvidence.rows) ? structuredEvidence.rows : [];
  const groups = Array.isArray(structuredEvidence.groups) ? structuredEvidence.groups : [];
  return rows.length > 0 || groups.length > 0;
}

function hasDeterministicSourceEvidence(responseBody, sources) {
  if (!responseBody || typeof responseBody !== "object" || !Array.isArray(sources) || sources.length === 0) {
    return false;
  }
  return responseBody.generationMode === "RULE_BASED" && responseBody.modelExecutionStatus === "SKIPPED";
}

function calculateRetrievalQuality(result) {
  const expectedTargets = expectedRetrievalTargets(result);
  const identities = result?.metrics?.retrievalIdentityTexts || [];
  if (expectedTargets.length === 0) {
    return {
      expectedCount: 0,
      matchedAt5: 0,
      matchedAt10: 0,
      recallAt5: 0,
      recallAt10: 0,
      mrr: 0
    };
  }
  const matchedAt5 = countMatchedTargets(expectedTargets, identities.slice(0, 5));
  const matchedAt10 = countMatchedTargets(expectedTargets, identities.slice(0, 10));
  const firstRank = firstMatchedRank(expectedTargets, identities);
  return {
    expectedCount: expectedTargets.length,
    matchedAt5,
    matchedAt10,
    recallAt5: ratio(matchedAt5, expectedTargets.length),
    recallAt10: ratio(matchedAt10, expectedTargets.length),
    mrr: firstRank <= 0 ? 0 : 1 / firstRank
  };
}

function expectedRetrievalTargets(result) {
  const expectation = result?.requestExpectation || result?.expectation || {};
  const failedSafeResult = result || {};
  const caseExpectation = failedSafeResult.expect || expectation;
  const explicitTargets = caseExpectation.retrievalTargets || caseExpectation.expectedRetrievalTargets;
  if (Array.isArray(explicitTargets) && explicitTargets.length > 0) {
    return normalizeTargetTerms(explicitTargets);
  }
  const sourceTerms = Array.isArray(caseExpectation.requiredSourceTerms) ? caseExpectation.requiredSourceTerms : [];
  if (sourceTerms.length > 0) {
    return normalizeTargetTerms(sourceTerms);
  }
  const testCase = cases.find((candidate) => candidate.id === failedSafeResult.id);
  const testExpectation = testCase?.expect || {};
  const testTargets = testExpectation.retrievalTargets || testExpectation.expectedRetrievalTargets;
  if (Array.isArray(testTargets) && testTargets.length > 0) {
    return normalizeTargetTerms(testTargets);
  }
  return normalizeTargetTerms(testExpectation.requiredSourceTerms || []);
}

function normalizeTargetTerms(targets) {
  return targets
    .map((target) => {
      if (typeof target === "string") {
        return target;
      }
      if (target && typeof target === "object") {
        return target.articleKey || target.conceptId || target.sourcePath || target.title || target.term || "";
      }
      return "";
    })
    .map((target) => normalizeMetricText(target))
    .filter((target) => target);
}

function countMatchedTargets(targets, identityTexts) {
  let matchedCount = 0;
  for (const target of targets) {
    if (identityTexts.some((identityText) => identityText.includes(target))) {
      matchedCount++;
    }
  }
  return matchedCount;
}

function firstMatchedRank(targets, identityTexts) {
  for (let index = 0; index < identityTexts.length; index++) {
    const identityText = identityTexts[index];
    if (targets.some((target) => identityText.includes(target))) {
      return index + 1;
    }
  }
  return 0;
}

function collectRetrievalIdentityTexts(sources, articles, structuredEvidence) {
  const identities = [];
  for (const item of [...sources, ...articles]) {
    identities.push(normalizeMetricText([
      item.articleKey,
      item.conceptId,
      item.title,
      item.derivation,
      ...(Array.isArray(item.sourcePaths) ? item.sourcePaths : [])
    ].join(" ")));
  }
  if (hasStructuredEvidence(structuredEvidence)) {
    identities.push(normalizeMetricText(JSON.stringify(structuredEvidence)));
  }
  return identities.filter((identity) => identity);
}

function calculateCitationPrecision(citationCheck, citationMarkers) {
  const verifiedCount = Number(citationCheck?.verifiedCount || 0);
  const demotedCount = Number(citationCheck?.demotedCount || 0);
  const checkedCount = verifiedCount + demotedCount;
  if (checkedCount > 0) {
    return verifiedCount / checkedCount;
  }
  const markerSources = citationMarkerSources(citationMarkers);
  if (markerSources.length === 0) {
    return 0;
  }
  const verifiedSources = markerSources.filter((source) =>
    normalizeMetricText(source.validationStatus) === "verified"
  ).length;
  return ratio(verifiedSources, markerSources.length);
}

function calculateUnsupportedClaimRate(citationCheck) {
  if (!citationCheck || typeof citationCheck !== "object") {
    return 0;
  }
  const unsupportedClaimCount = Number(citationCheck.unsupportedClaimCount || 0);
  const claimCount = Number(citationCheck.claimCount || citationCheck.totalClaims || 0);
  if (claimCount > 0) {
    return unsupportedClaimCount / claimCount;
  }
  if (unsupportedClaimCount > 0) {
    return 1;
  }
  return 0;
}

function isCitationEvaluable(citationCheck, citationMarkers) {
  if (citationCheck && typeof citationCheck === "object") {
    return Number(citationCheck.verifiedCount || 0) > 0
      || Number(citationCheck.demotedCount || 0) > 0
      || Number(citationCheck.skippedCount || 0) > 0
      || citationCheck.coverageRate !== undefined;
  }
  return citationMarkerSources(citationMarkers).length > 0;
}

function citationMarkerSources(citationMarkers) {
  if (!Array.isArray(citationMarkers)) {
    return [];
  }
  return citationMarkers.flatMap((marker) => Array.isArray(marker.sources) ? marker.sources : []);
}

function summaryRow(result) {
  const metrics = result.metrics || {};
  const retrievalQuality = calculateRetrievalQuality(result);
  return {
    id: result.id,
    category: result.category,
    pass: result.pass ? "true" : "false",
    failedReasons: (result.failedReasons || []).join("|"),
    failed_reasons: (result.failedReasons || []).join("|"),
    httpStatus: String(result.httpStatus || 0),
    http_status: String(result.httpStatus || 0),
    timeout: result.timeout ? "true" : "false",
    elapsedMs: String(result.elapsedMs || 0),
    elapsed_ms: String(result.elapsedMs || 0),
    queryId: metrics.queryId || "",
    query_id: metrics.queryId || "",
    reviewStatus: metrics.reviewStatus || "",
    review_status: metrics.reviewStatus || "",
    answerOutcome: metrics.answerOutcome || "",
    answer_outcome: metrics.answerOutcome || "",
    generationMode: metrics.generationMode || "",
    generation_mode: metrics.generationMode || "",
    modelExecutionStatus: metrics.modelExecutionStatus || "",
    model_execution_status: metrics.modelExecutionStatus || "",
    fallbackReason: metrics.fallbackReason || "",
    fallback_reason: metrics.fallbackReason || "",
    sourceCount: String(metrics.sourceCount || 0),
    source_count: String(metrics.sourceCount || 0),
    articleCount: String(metrics.articleCount || 0),
    article_count: String(metrics.articleCount || 0),
    citationMarkerCount: String(metrics.citationMarkerCount || 0),
    citation_marker_count: String(metrics.citationMarkerCount || 0),
    citationCoverage: String(metrics.citationCoverage || 0),
    citation_coverage: String(metrics.citationCoverage || 0),
    retrievalExpectedCount: String(retrievalQuality.expectedCount),
    retrieval_expected_count: String(retrievalQuality.expectedCount),
    retrievalMatchedAt5: String(retrievalQuality.matchedAt5),
    retrieval_matched_at_5: String(retrievalQuality.matchedAt5),
    retrievalMatchedAt10: String(retrievalQuality.matchedAt10),
    retrieval_matched_at_10: String(retrievalQuality.matchedAt10),
    retrievalRecallAt5: String(retrievalQuality.recallAt5),
    retrieval_recall_at_5: String(retrievalQuality.recallAt5),
    retrievalRecallAt10: String(retrievalQuality.recallAt10),
    retrieval_recall_at_10: String(retrievalQuality.recallAt10),
    retrievalMrr: String(retrievalQuality.mrr),
    retrieval_mrr: String(retrievalQuality.mrr),
    unsupportedClaimRate: String(metrics.unsupportedClaimRate || 0),
    unsupported_claim_rate: String(metrics.unsupportedClaimRate || 0),
    citationPrecision: String(metrics.citationPrecision || 0),
    citation_precision: String(metrics.citationPrecision || 0),
    citationEvaluable: metrics.citationEvaluable ? "true" : "false",
    citation_evaluable: metrics.citationEvaluable ? "true" : "false",
    citationVerifiedCount: String(metrics.citationVerifiedCount || 0),
    citation_verified_count: String(metrics.citationVerifiedCount || 0),
    citationDemotedCount: String(metrics.citationDemotedCount || 0),
    citation_demoted_count: String(metrics.citationDemotedCount || 0),
    deepResearchPresent: metrics.deepResearchPresent ? "true" : "false",
    deep_research_present: metrics.deepResearchPresent ? "true" : "false",
    question: result.question
  };
}

function writeSummary(filePath, rows) {
  const headers = [
    "id",
    "category",
    "pass",
    "failed_reasons",
    "http_status",
    "timeout",
    "elapsed_ms",
    "query_id",
    "review_status",
    "answer_outcome",
    "generation_mode",
    "model_execution_status",
    "fallback_reason",
    "source_count",
    "article_count",
    "citation_marker_count",
    "citation_coverage",
    "retrieval_expected_count",
    "retrieval_matched_at_5",
    "retrieval_matched_at_10",
    "retrieval_recall_at_5",
    "retrieval_recall_at_10",
    "retrieval_mrr",
    "unsupported_claim_rate",
    "citation_precision",
    "citation_evaluable",
    "citation_verified_count",
    "citation_demoted_count",
    "deep_research_present",
    "question"
  ];
  const lines = [headers.join("\t")];
  for (const row of rows) {
    lines.push(headers.map((header) => tsvValue(row[header])).join("\t"));
  }
  fs.writeFileSync(filePath, `${lines.join("\n")}\n`);
}

function calculateMetrics(rows, gateConfig) {
  const total = rows.length;
  const passCount = rows.filter((row) => row.pass === "true").length;
  const timeoutCount = rows.filter((row) => row.timeout === "true").length;
  const httpFailureCount = rows.filter((row) => row.http_status !== "200").length;
  const fallbackCount = rows.filter((row) => row.generation_mode === "FALLBACK").length;
  const llmSuccessCount = rows.filter((row) =>
    row.generation_mode === "LLM" && row.model_execution_status === "SUCCESS"
  ).length;
  const coverageValues = rows.map((row) => Number(row.citation_coverage || 0));
  const averageCitationCoverage = coverageValues.reduce((sum, value) => sum + value, 0) / total;
  const recallRows = rows.filter((row) => row.retrieval_expected_count > 0);
  const citationRows = rows.filter((row) => row.citation_evaluable === "true");
  const metrics = {
    total,
    passCount,
    casePassRate: ratio(passCount, total),
    httpFailureRate: ratio(httpFailureCount, total),
    timeoutRate: ratio(timeoutCount, total),
    fallbackRate: ratio(fallbackCount, total),
    llmSuccessRate: ratio(llmSuccessCount, total),
    averageCitationCoverage,
    recallEvaluatedCount: recallRows.length,
    recallAt5: average(recallRows, "retrieval_recall_at_5"),
    recallAt10: average(recallRows, "retrieval_recall_at_10"),
    mrr: average(recallRows, "retrieval_mrr"),
    citationEvaluatedCount: citationRows.length,
    unsupportedClaimRate: average(citationRows, "unsupported_claim_rate"),
    citationPrecision: average(citationRows, "citation_precision"),
    failedReasons: []
  };
  checkGate(metrics, "casePassRate", gateConfig.minCasePassRate, ">=");
  checkGate(metrics, "httpFailureRate", gateConfig.maxHttpFailureRate, "<=");
  checkGate(metrics, "timeoutRate", gateConfig.maxTimeoutRate, "<=");
  checkGate(metrics, "fallbackRate", gateConfig.maxFallbackRate, "<=");
  checkGate(metrics, "llmSuccessRate", gateConfig.minLlmSuccessRate, ">=");
  checkGate(metrics, "averageCitationCoverage", gateConfig.minAverageCitationCoverage, ">=");
  metrics.pass = metrics.failedReasons.length === 0;
  return metrics;
}

function writeReplayManifest(filePath, suitePayload, suiteCases, rows, metrics) {
  const replayCases = suiteCases.map((testCase) => {
    const row = rows.find((summary) => summary.id === testCase.id) || {};
    return {
      id: testCase.id,
      category: testCase.category || "",
      question: testCase.question,
      request: buildRequestBody(testCase),
      expectation: testCase.expect || {},
      timeoutMs: Number(testCase.timeoutMs || defaults.timeoutMs || 120000),
      pass: row.pass === "true",
      failedReasons: splitList(row.failed_reasons),
      queryId: row.query_id || "",
      elapsedMs: Number(row.elapsed_ms || 0),
      generationMode: row.generation_mode || "",
      modelExecutionStatus: row.model_execution_status || "",
      answerOutcome: row.answer_outcome || "",
      retrievalRecallAt5: Number(row.retrieval_recall_at_5 || 0),
      retrievalRecallAt10: Number(row.retrieval_recall_at_10 || 0),
      retrievalMrr: Number(row.retrieval_mrr || 0),
      unsupportedClaimRate: Number(row.unsupported_claim_rate || 0),
      citationPrecision: Number(row.citation_precision || 0),
      citationCoverage: Number(row.citation_coverage || 0)
    };
  });
  const manifest = {
    manifestVersion: 1,
    generatedAt: new Date().toISOString(),
    suite: {
      id: suitePayload.suiteId || "",
      version: suitePayload.version || "",
      description: suitePayload.description || "",
      path: relativeToRoot(suitePath),
      sha256: sha256File(suitePath),
      defaults,
      gates,
      coverageDimensions: suitePayload.coverageDimensions || []
    },
    replay: {
      baseUrl,
      outputDir: relativeToRoot(outputDir),
      outputFiles: {
        resultsJsonl: relativeToRoot(resultJsonlPath),
        summaryTsv: relativeToRoot(summaryTsvPath),
        metricsJson: relativeToRoot(metricsJsonPath),
        manifestJson: relativeToRoot(filePath)
      }
    },
    environment: {
      cwd: rootDir,
      nodeVersion: process.version,
      gitCommit: readGitValue(["rev-parse", "HEAD"]),
      gitBranch: readGitValue(["rev-parse", "--abbrev-ref", "HEAD"]),
      gitDirty: hasGitChanges()
    },
    caseSummary: {
      total: replayCases.length,
      categories: countBy(replayCases, "category"),
      ids: replayCases.map((testCase) => testCase.id)
    },
    metrics,
    cases: replayCases
  };
  fs.writeFileSync(filePath, `${JSON.stringify(manifest, null, 2)}\n`);
}

function checkGate(metrics, metricName, threshold, operator) {
  if (threshold === undefined || threshold === null) {
    return;
  }
  const actual = Number(metrics[metricName]);
  const expected = Number(threshold);
  const ok = operator === ">=" ? actual >= expected : actual <= expected;
  if (!ok) {
    metrics.failedReasons.push(`${metricName}${operator}${expected},actual=${actual}`);
  }
}

function expectEqual(failedReasons, name, actual, expected) {
  if (expected === undefined || expected === null) {
    return;
  }
  if (String(actual) !== String(expected)) {
    failedReasons.push(`${name}_expected_${expected}_actual_${actual}`);
  }
}

function expectOneOf(failedReasons, name, actual, expectedValues) {
  if (!Array.isArray(expectedValues) || expectedValues.length === 0) {
    return;
  }
  if (!expectedValues.includes(actual)) {
    failedReasons.push(`${name}_unexpected_${actual || ""}`);
  }
}

function expectMin(failedReasons, name, actual, expectedMin) {
  if (expectedMin === undefined || expectedMin === null) {
    return;
  }
  const actualNumber = Number(actual || 0);
  const expectedNumber = Number(expectedMin);
  if (actualNumber < expectedNumber) {
    failedReasons.push(`${name}_below_${expectedNumber}_actual_${actualNumber}`);
  }
}

function expectTerms(failedReasons, name, haystack, terms, shouldContain) {
  if (!Array.isArray(terms) || terms.length === 0) {
    return;
  }
  const normalizedHaystack = String(haystack || "").toLowerCase();
  for (const term of terms) {
    const normalizedTerm = String(term || "").toLowerCase();
    const contains = normalizedHaystack.includes(normalizedTerm);
    if (shouldContain && !contains) {
      failedReasons.push(`${name}_missing_term:${term}`);
    }
    if (!shouldContain && contains) {
      failedReasons.push(`${name}_forbidden_term:${term}`);
    }
  }
}

function stripQuestionEcho(answerText, question) {
  const answer = String(answerText || "");
  const rawQuestion = String(question || "").trim();
  if (!answer || !rawQuestion) {
    return answer;
  }
  const lines = answer.split(/\r?\n/);
  const normalizedQuestion = normalizeEchoText(rawQuestion);
  const retainedLines = [];
  let skipNextLine = false;
  for (const line of lines) {
    const trimmedLine = line.trim();
    const normalizedLine = normalizeEchoText(trimmedLine);
    if (skipNextLine) {
      skipNextLine = false;
      if (normalizedLine && (normalizedQuestion.includes(normalizedLine) || normalizedLine.includes(normalizedQuestion))) {
        continue;
      }
    }
    if (/^#{1,6}\s*问题\s*$/.test(trimmedLine) || /^问题[:：]?$/.test(trimmedLine)) {
      skipNextLine = true;
      continue;
    }
    if (normalizedLine && (normalizedQuestion.includes(normalizedLine) || normalizedLine.includes(normalizedQuestion))) {
      continue;
    }
    retainedLines.push(line);
  }
  return retainedLines.join("\n");
}

function normalizeEchoText(value) {
  return String(value || "")
    .toLowerCase()
    .replace(/[#*`|\[\]（）()“”"'：:；;，,。！？?\s\t\r\n-]+/g, "")
    .replace(/\d+$/g, "")
    .trim();
}

function parseJsonOrText(value) {
  try {
    return JSON.parse(value);
  }
  catch {
    return value;
  }
}

function splitList(value) {
  if (!value) {
    return [];
  }
  return String(value).split("|").filter((item) => item);
}

function countBy(items, key) {
  const counts = {};
  for (const item of items) {
    const countKey = item[key] || "";
    counts[countKey] = (counts[countKey] || 0) + 1;
  }
  return counts;
}

function sha256File(filePath) {
  return crypto.createHash("sha256").update(fs.readFileSync(filePath)).digest("hex");
}

function readGitValue(args) {
  try {
    return execFileSync("git", args, {
      cwd: rootDir,
      encoding: "utf8",
      stdio: ["ignore", "pipe", "ignore"]
    }).trim();
  }
  catch {
    return "";
  }
}

function hasGitChanges() {
  try {
    const output = execFileSync("git", ["status", "--porcelain"], {
      cwd: rootDir,
      encoding: "utf8",
      stdio: ["ignore", "pipe", "ignore"]
    }).trim();
    return output.length > 0;
  }
  catch {
    return false;
  }
}

function relativeToRoot(filePath) {
  return path.relative(rootDir, filePath).replaceAll(path.sep, "/");
}

function readNumber(value, fallback) {
  if (value !== undefined && value !== null) {
    return value;
  }
  return fallback;
}

function average(rows, fieldName) {
  if (!Array.isArray(rows) || rows.length === 0) {
    return 0;
  }
  const sum = rows.reduce((total, row) => total + Number(row[fieldName] || 0), 0);
  return sum / rows.length;
}

function ratio(numerator, denominator) {
  if (!denominator) {
    return 0;
  }
  return numerator / denominator;
}

function normalizeMetricText(value) {
  return String(value || "")
    .toLowerCase()
    .replace(/\\+/g, "/")
    .replace(/[#*`|\[\]（）()“”"'：:；;，,。！？?\s\t\r\n]+/g, " ")
    .trim();
}

function stringValue(value) {
  if (value === undefined || value === null) {
    return "";
  }
  return String(value);
}

function tsvValue(value) {
  return String(value === undefined || value === null ? "" : value)
    .replace(/\t/g, " ")
    .replace(/\r?\n/g, " ");
}

function stripTrailingSlash(value) {
  return value.replace(/\/+$/, "");
}

function timestamp() {
  return new Date().toISOString().replace(/[-:]/g, "").replace(/\..+$/, "").replace("T", "-");
}
