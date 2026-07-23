export function reviewAuditFixture() {
  return {
    id: 91,
    sourceId: 12,
    articleKey: "article-alpha",
    conceptId: "concept-alpha",
    action: "approve",
    previousReviewStatus: "needs_human_review",
    nextReviewStatus: "passed",
    comment: "证据一致",
    reviewedBy: "reviewer",
    reviewedAt: "2026-07-22T10:00:00Z",
    metadataJson: "{}",
  };
}

export function articleSnapshotFixture() {
  return {
    snapshotId: 81,
    sourceId: 12,
    articleKey: "article-alpha",
    conceptId: "concept-alpha",
    title: "Concept Alpha",
    content: "# Alpha\n\n旧正文",
    lifecycle: "active",
    compiledAt: "2026-07-21T10:00:00Z",
    sourcePaths: ["docs/alpha.md"],
    metadataJson: "{}",
    summary: "Alpha 摘要",
    referentialKeywords: ["alpha"],
    dependsOn: [],
    related: ["concept-beta"],
    confidence: "high",
    reviewStatus: "needs_human_review",
    snapshotReason: "compile",
    capturedAt: "2026-07-21T10:05:00Z",
  };
}

export function reviewResponseFixture(reviewStatus = "passed") {
  return {
    sourceId: 12,
    articleKey: "article-alpha",
    conceptId: "concept-alpha",
    previousReviewStatus: "needs_human_review",
    reviewStatus,
    reviewedBy: "reviewer",
    reviewedAt: "2026-07-22T10:00:00Z",
    auditId: 91,
  };
}

export function lifecycleResponseFixture() {
  return {
    sourceId: 12,
    articleKey: "article-alpha",
    conceptId: "concept-alpha",
    title: "Concept Alpha",
    lifecycle: "archived",
    reason: "停止维护",
    updatedBy: "operator",
    updatedAt: "2026-07-22T10:10:00Z",
  };
}

export function correctionResponseFixture() {
  return {
    sourceId: 12,
    articleKey: "article-alpha",
    conceptId: "concept-alpha",
    revisedContent: "# Alpha\n\n新正文",
    downstreamIds: ["concept-beta"],
    validationSupported: true,
  };
}

export function rollbackResponseFixture() {
  return {
    sourceId: 12,
    articleKey: "article-alpha",
    conceptId: "concept-alpha",
    restoredSnapshotId: 81,
    restoredAt: "2026-07-22T10:20:00Z",
  };
}

export function hotspotResponseFixture() {
  return {
    rebuiltStatsCount: 4,
    hotspotCandidateCount: 1,
    updatedArticleCount: 1,
    heatScoreThreshold: 3,
    candidates: [{
      articleKey: "article-alpha",
      conceptId: "concept-alpha",
      retrievalHitCount: 1,
      citationCount: 0,
      answerFeedbackCount: 2,
      manualMarkCount: 0,
      heatScore: 7,
      sourcePaths: ["docs/alpha.md"],
      updatedAt: "2026-07-22T10:30:00Z",
    }],
  };
}
