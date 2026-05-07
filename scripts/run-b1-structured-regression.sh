#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
MAVEN_SETTINGS="${MAVEN_SETTINGS:-$ROOT_DIR/.codex/maven-settings.xml}"
MAVEN_REPO="${MAVEN_REPO:-/Users/sxie/maven/repository}"
QUERY_SUMMARY_TSV="${QUERY_SUMMARY_TSV:-}"

TEST_CLASSES=(
  StructuredQueryPlannerTests
  StructuredTableJdbcRepositoryTests
  DocumentParseRouterIntegrationTests
  QueryControllerTests
  StructuredTableQueryRegressionTests
  AnswerShapeClassifierTests
  AnswerCoverageCheckServiceTests
  QueryPreparationServiceTests
  QueryHitIntentRerankerTests
  WeightedRrfFusionTest
  NonCouponComplexDocumentRegressionTests
  FactCardEvidenceQualityServiceTests
  StructuredRetrievalTopKQualityServiceTests
)

SCAN_TARGETS=(
  src/main/java/com/xbk/lattice/query/service/AnswerShapeClassifier.java
  src/main/java/com/xbk/lattice/query/service/QueryIntentClassifier.java
  src/main/java/com/xbk/lattice/query/service/StructuredRetrievalTopKQualityService.java
  src/main/java/com/xbk/lattice/query/service/StructuredRetrievalTopKReport.java
  src/main/java/com/xbk/lattice/query/service/StructuredRetrievalTopKSample.java
  src/main/java/com/xbk/lattice/query/service/StructuredRetrievalTopKTarget.java
  src/main/java/com/xbk/lattice/query/structured
  src/test/java/com/xbk/lattice/query/service/AnswerShapeClassifierTests.java
  src/test/java/com/xbk/lattice/query/service/AnswerCoverageCheckServiceTests.java
  src/test/java/com/xbk/lattice/query/service/QueryHitIntentRerankerTests.java
  src/test/java/com/xbk/lattice/query/service/NonCouponComplexDocumentRegressionTests.java
  src/test/java/com/xbk/lattice/query/service/StructuredRetrievalTopKQualityServiceTests.java
  src/test/java/com/xbk/lattice/query/structured
  src/test/java/com/xbk/lattice/api/query/StructuredTableQueryRegressionTests.java
)

BLOCKED_PATTERN='scenario_id|case_num|step_index|scenarios\.xlsx|100912|100978|100979|100914|流量结论|featureToggleList'

join_by_comma() {
  local IFS=","
  echo "$*"
}

run_unit_regression() {
  local test_names
  test_names="$(join_by_comma "${TEST_CLASSES[@]}")"
  echo "[b1] run automated regression tests"
  mvn -q \
    -s "$MAVEN_SETTINGS" \
    -Dmaven.repo.local="$MAVEN_REPO" \
    -Dtest="$test_names" \
    test
}

check_query_summary() {
  if [[ -z "$QUERY_SUMMARY_TSV" ]]; then
    echo "[b1] skip query summary gate: QUERY_SUMMARY_TSV is empty"
    return
  fi
  if [[ ! -f "$QUERY_SUMMARY_TSV" ]]; then
    echo "[b1] query summary file not found: $QUERY_SUMMARY_TSV" >&2
    exit 1
  fi

  echo "[b1] check query summary gate: $QUERY_SUMMARY_TSV"
  awk -F '\t' '
    NR == 1 {
      for (columnIndex = 1; columnIndex <= NF; columnIndex++) {
        columns[$columnIndex] = columnIndex
      }
      required[1] = "http_status"
      required[2] = "review_status"
      required[3] = "answer_outcome"
      for (requiredIndex = 1; requiredIndex <= 3; requiredIndex++) {
        if (!(required[requiredIndex] in columns)) {
          printf("[b1] missing query summary column: %s\n", required[requiredIndex]) > "/dev/stderr"
          exit 2
        }
      }
      next
    }
    {
      total += 1
      if ($(columns["http_status"]) != "200" || $(columns["review_status"]) != "PASSED" || $(columns["answer_outcome"]) != "SUCCESS") {
        failures += 1
        printf("[b1] query summary failed row %d: http_status=%s review_status=%s answer_outcome=%s\n",
          NR, $(columns["http_status"]), $(columns["review_status"]), $(columns["answer_outcome"])) > "/dev/stderr"
      }
    }
    END {
      if (NR <= 1) {
        print "[b1] query summary is empty" > "/dev/stderr"
        exit 3
      }
      if (failures > 0) {
        printf("[b1] query summary gate failed: total=%d failures=%d\n", total, failures) > "/dev/stderr"
        exit 4
      }
      printf("[b1] query summary gate passed: total=%d\n", total)
    }
  ' "$QUERY_SUMMARY_TSV"
}

check_no_sample_specific_branches() {
  echo "[b1] scan sample-specific branches"
  if rg -n "$BLOCKED_PATTERN" "${SCAN_TARGETS[@]}"; then
    echo "[b1] blocked sample-specific token found in implementation or generic regression targets" >&2
    exit 5
  fi
  echo "[b1] hard-code scan passed"
}

cd "$ROOT_DIR"
run_unit_regression
check_query_summary
check_no_sample_specific_branches
echo "[b1] regression passed"
