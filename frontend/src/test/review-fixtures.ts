import type {
  CompileReviewConfig,
  CompileReviewQueueItem,
  PendingQueryItem,
} from "../api/contracts/reviews";

export function compileReviewItemFixture(
  overrides: Partial<CompileReviewQueueItem> = {},
): CompileReviewQueueItem {
  return {
    id: 6,
    jobId: "job-review-6",
    sourceId: 12,
    sourceCode: "payments-docs",
    conceptId: "payment-retry",
    articleKey: "payments-docs--payment-retry",
    title: "Payment retry policy",
    content: "# Payment retry policy\n\nReview draft content.",
    metadataJson: "{\"analysisMode\":\"LLM\"}",
    reviewStatus: "needs_human_review",
    reviewRoute: "compile.reviewer.test",
    reviewerModel: "gpt-test",
    reviewIssuesJson: "[{\"severity\":\"HIGH\",\"category\":\"GROUNDING\",\"description\":\"Missing source\"}]",
    fixAttemptCount: 1,
    maxFixRounds: 1,
    sourcePaths: ["docs/payment-retry.md"],
    createdAt: "2026-07-22T10:00:00Z",
    updatedAt: "2026-07-22T10:02:00Z",
    reviewedBy: null,
    reviewedAt: null,
    reviewComment: null,
    publishedArticleKey: null,
    ...overrides,
  };
}

export function compileReviewConfigFixture(
  overrides: Partial<CompileReviewConfig> = {},
): CompileReviewConfig {
  return {
    autoFixEnabled: true,
    maxFixRounds: 1,
    allowPersistNeedsHumanReview: false,
    humanReviewSeverityThreshold: "HIGH",
    configSource: "properties",
    createdBy: "",
    updatedBy: "",
    createdAt: null,
    updatedAt: null,
    ...overrides,
  };
}

export function pendingQueryItemFixture(
  overrides: Partial<PendingQueryItem> = {},
): PendingQueryItem {
  return {
    queryId: "query-pending-1",
    question: "支付超时后应如何恢复？",
    answer: "# 查询回答\n\n原始答案。",
    reviewStatus: "PASSED",
    selectedConceptIds: ["payment-retry"],
    sourceFilePaths: ["docs/payment-retry.md"],
    createdAt: "2026-07-22T10:00:00Z",
    expiresAt: "2026-07-29T10:00:00Z",
    ...overrides,
  };
}
