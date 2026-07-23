import type {
  AdminOverview,
  FactCard,
  LinkEnhancementReport,
  LintReport,
  QualityResponse,
} from "../api/contracts/quality";

export function qualityFixture(): QualityResponse {
  return {
    report: {
      totalArticles: 12,
      passedArticles: 8,
      pendingReviewArticles: 3,
      needsHumanReviewArticles: 1,
      contributionCount: 18,
      sourceFileCount: 7,
    },
    trend: {
      days: 7,
      latestMeasuredAt: "2026-07-22T08:30:00Z",
      reviewPassRateDelta: 0.08,
      groundingRateDelta: -0.02,
      referentialRateDelta: 0.04,
      totalArticlesDelta: 2,
    },
  };
}

export function overviewFixture(): AdminOverview {
  const quality = qualityFixture().report;
  return {
    status: {
      articleCount: 12,
      sourceFileCount: 7,
      contributionCount: 18,
      pendingQueryCount: 2,
      reviewPendingArticleCount: 4,
      humanReviewDraftPendingCount: 1,
      highRiskArticleCount: 1,
      hotspotPendingVerificationCount: 2,
      userReportedAnswerCount: 3,
      answerFeedbackPendingCount: 1,
    },
    quality,
    pending: {
      count: 1,
      items: [{ queryId: "query-1", question: "退款流程是什么？", reviewStatus: "pending_review" }],
    },
  };
}

export function lintFixture(): LintReport {
  return {
    checkedDimensions: ["source", "reference"],
    totalIssues: 2,
    issues: [
      {
        dimension: "reference",
        targetId: "article-alpha",
        message: "来源引用缺失",
        fixable: true,
        fixSuggestion: "重建来源引用",
      },
      {
        dimension: "source",
        targetId: "article-beta",
        message: "来源文件不存在",
        fixable: false,
        fixSuggestion: null,
      },
    ],
  };
}

export function linkEnhancementFixture(): LinkEnhancementReport {
  return {
    totalArticles: 12,
    processedArticleCount: 12,
    updatedArticleCount: 1,
    fixedLinkCount: 2,
    syncedSectionCount: 1,
    unresolvedLinkCount: 1,
    items: [{
      conceptId: "article-alpha",
      title: "Article Alpha",
      updated: true,
      fixedLinkCount: 2,
      syncedSectionCount: 1,
      unresolvedLinks: ["missing-concept"],
    }],
  };
}

export function factCardFixture(overrides: Partial<FactCard> = {}): FactCard {
  return {
    id: 41,
    cardId: "fact-card-alpha",
    sourceId: 12,
    sourceFileId: 23,
    sourceFilePath: "docs/alpha.md",
    cardType: "SUMMARY",
    answerShape: "SINGLE_VALUE",
    title: "Alpha 事实",
    claim: "Alpha 的默认超时为 30 秒。",
    itemsJson: "[]",
    evidenceText: "配置文件声明默认超时为 30 秒。",
    sourceChunkIds: [31],
    articleIds: [51],
    confidence: 0.92,
    reviewStatus: "accepted",
    contentHash: "hash-alpha",
    createdAt: "2026-07-22T08:00:00Z",
    updatedAt: "2026-07-22T08:30:00Z",
    ...overrides,
  };
}
