import type { ArticleDetail, ArticleSummary } from "../api/contracts/articles";
import type { SearchHit } from "../api/contracts/search";

export function articleSummaryFixture(
  overrides: Partial<ArticleSummary> = {},
): ArticleSummary {
  return {
    sourceId: 12,
    articleKey: "payments--fine-controller",
    conceptId: "fine-controller",
    title: "FineController",
    lifecycle: "ACTIVE",
    reviewStatus: "passed",
    riskLevel: "low",
    riskReasons: [],
    requiresResultVerification: false,
    compiledAt: "2026-07-22T12:00:00Z",
    createdAt: "2026-07-22T12:01:00Z",
    updatedAt: "2026-07-22T12:02:00Z",
    summary: "提供罚金计算相关的 HTTP API。",
    sourceCount: 1,
    primarySourcePath: "src/main/java/FineController.java",
    sourcePaths: ["src/main/java/FineController.java"],
    primarySourceName: "src/main/java/FineController.java",
    titleProfile: {
      sourceTitle: "FineController",
      anchorTitle: "FineController",
      representativeTitle: "FineController",
      titleGenerationMode: "ANCHOR_DIRECT",
    },
    isHotspot: false,
    ...overrides,
  };
}

export function articleDetailFixture(
  overrides: Partial<ArticleDetail> = {},
): ArticleDetail {
  return {
    ...articleSummaryFixture(),
    content: "---\ntitle: FineController\n---\n# FineController\n\n正文内容。",
    confidence: "high",
    referentialKeywords: ["FineController", "FineService"],
    dependsOn: ["fine-service"],
    related: ["fine-calculation-request", "fine-calculation-response"],
    metadataJson: '{"structured":true}',
    ...overrides,
  };
}

export function searchHitFixture(overrides: Partial<SearchHit> = {}): SearchHit {
  return {
    evidenceType: "ARTICLE",
    sourceId: 12,
    articleKey: "payments--fine-controller",
    conceptId: "fine-controller",
    title: "FineController",
    content: "提供罚金计算相关的 HTTP API。",
    metadataJson: null,
    sourcePaths: ["src/main/java/FineController.java"],
    score: 0.9123,
    ...overrides,
  };
}
