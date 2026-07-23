import { z } from "zod";

import type { ApiClient } from "../api-client";
import { apiClient } from "../api-client";

const nonnegativeIntegerSchema = z.number().int().nonnegative();
const nullableStringSchema = z.string().nullable();
const stringCountMapSchema = z.record(z.string(), nonnegativeIntegerSchema);

export const qualityMetricsReportSchema = z.object({
  totalArticles: nonnegativeIntegerSchema,
  passedArticles: nonnegativeIntegerSchema,
  pendingReviewArticles: nonnegativeIntegerSchema,
  needsHumanReviewArticles: nonnegativeIntegerSchema,
  contributionCount: nonnegativeIntegerSchema,
  sourceFileCount: nonnegativeIntegerSchema,
});

export const qualityResponseSchema = z.object({
  report: qualityMetricsReportSchema,
  trend: z.object({
    days: z.number().int().positive(),
    latestMeasuredAt: nullableStringSchema,
    reviewPassRateDelta: z.number(),
    groundingRateDelta: z.number(),
    referentialRateDelta: z.number(),
    totalArticlesDelta: z.number().int(),
  }),
});

export const adminOverviewSchema = z.object({
  status: z.object({
    articleCount: nonnegativeIntegerSchema,
    sourceFileCount: nonnegativeIntegerSchema,
    contributionCount: nonnegativeIntegerSchema,
    pendingQueryCount: nonnegativeIntegerSchema,
    reviewPendingArticleCount: nonnegativeIntegerSchema,
    humanReviewDraftPendingCount: nonnegativeIntegerSchema,
    highRiskArticleCount: nonnegativeIntegerSchema,
    hotspotPendingVerificationCount: nonnegativeIntegerSchema,
    userReportedAnswerCount: nonnegativeIntegerSchema,
    answerFeedbackPendingCount: nonnegativeIntegerSchema,
  }),
  quality: qualityMetricsReportSchema,
  pending: z.object({
    count: nonnegativeIntegerSchema,
    items: z.array(z.object({
      queryId: z.string().min(1),
      question: z.string(),
      reviewStatus: z.string().min(1),
    })),
  }),
});

export const coverageReportSchema = z.object({
  totalSourceFileCount: nonnegativeIntegerSchema,
  coveredSourceFileCount: nonnegativeIntegerSchema,
  uncoveredSourceFileCount: nonnegativeIntegerSchema,
  coverageRatio: z.number().min(0).max(1),
  coveredSourcePaths: z.array(z.string()),
});

export const omissionReportSchema = z.object({
  totalSourceFileCount: nonnegativeIntegerSchema,
  omittedSourceFileCount: nonnegativeIntegerSchema,
  items: z.array(z.string()),
});

export const lintReportSchema = z.object({
  checkedDimensions: z.array(z.string()),
  totalIssues: nonnegativeIntegerSchema,
  issues: z.array(z.object({
    dimension: z.string().min(1),
    targetId: z.string().min(1),
    message: z.string().min(1),
    fixable: z.boolean(),
    fixSuggestion: nullableStringSchema,
  })),
});

export const lintFixResultSchema = z.object({
  fixed: nonnegativeIntegerSchema,
  skipped: nonnegativeIntegerSchema,
  errors: z.array(z.string()),
});

export const inspectionReportSchema = z.object({
  totalQuestions: nonnegativeIntegerSchema,
  questions: z.array(z.object({
    id: z.string().min(1),
    type: z.string().min(1),
    question: z.string(),
    prompt: z.string(),
    suggestedAnswer: z.string(),
    sourcePaths: z.array(z.string()),
    reviewStatus: z.string().min(1),
    createdAt: z.string(),
    expiresAt: z.string(),
  })),
});

export const inspectionImportResultSchema = z.object({
  importedCount: nonnegativeIntegerSchema,
  resolvedIds: z.array(z.string()),
});

export const linkEnhancementReportSchema = z.object({
  totalArticles: nonnegativeIntegerSchema,
  processedArticleCount: nonnegativeIntegerSchema,
  updatedArticleCount: nonnegativeIntegerSchema,
  fixedLinkCount: nonnegativeIntegerSchema,
  syncedSectionCount: nonnegativeIntegerSchema,
  unresolvedLinkCount: nonnegativeIntegerSchema,
  items: z.array(z.object({
    conceptId: z.string().min(1),
    title: z.string(),
    updated: z.boolean(),
    fixedLinkCount: nonnegativeIntegerSchema,
    syncedSectionCount: nonnegativeIntegerSchema,
    unresolvedLinks: z.array(z.string()),
  })),
});

export const factCardSummarySchema = z.object({
  totalCount: nonnegativeIntegerSchema,
  countByCardType: stringCountMapSchema,
  countByReviewStatus: stringCountMapSchema,
  sourceReferenceMissingCount: nonnegativeIntegerSchema,
  lowConfidenceCount: nonnegativeIntegerSchema,
});

export const factCardSchema = z.object({
  id: z.number().int().positive(),
  cardId: z.string().min(1),
  sourceId: z.number().int().positive().nullable(),
  sourceFileId: z.number().int().positive().nullable(),
  sourceFilePath: z.string(),
  cardType: z.string().min(1),
  answerShape: z.string().min(1),
  title: z.string(),
  claim: z.string(),
  itemsJson: z.string(),
  evidenceText: z.string(),
  sourceChunkIds: z.array(z.number().int().positive()),
  articleIds: z.array(z.number().int().positive()),
  confidence: z.number().min(0).max(1),
  reviewStatus: z.string().min(1),
  contentHash: z.string(),
  createdAt: nullableStringSchema,
  updatedAt: nullableStringSchema,
});

export const factCardListSchema = z.object({
  count: nonnegativeIntegerSchema,
  items: z.array(factCardSchema),
});

export type AdminOverview = z.infer<typeof adminOverviewSchema>;
export type QualityResponse = z.infer<typeof qualityResponseSchema>;
export type CoverageReport = z.infer<typeof coverageReportSchema>;
export type OmissionReport = z.infer<typeof omissionReportSchema>;
export type LintReport = z.infer<typeof lintReportSchema>;
export type InspectionQuestion = z.infer<typeof inspectionReportSchema>["questions"][number];
export type LinkEnhancementReport = z.infer<typeof linkEnhancementReportSchema>;
export type FactCard = z.infer<typeof factCardSchema>;

export interface InspectionImportRequest {
  inspectionId: string;
  finalAnswer: string;
  confirmedBy: string;
}

export function createQualityApi(client: ApiClient = apiClient) {
  return {
    overview(signal?: AbortSignal) {
      return client.get("/api/v1/admin/overview", { schema: adminOverviewSchema, signal });
    },
    quality(days = 7, signal?: AbortSignal) {
      return client.get("/api/v1/admin/quality", {
        query: { days },
        schema: qualityResponseSchema,
        signal,
      });
    },
    coverage(signal?: AbortSignal) {
      return client.get("/api/v1/admin/coverage", { schema: coverageReportSchema, signal });
    },
    omissions(signal?: AbortSignal) {
      return client.get("/api/v1/admin/omissions", { schema: omissionReportSchema, signal });
    },
    lint(signal?: AbortSignal) {
      return client.get("/api/v1/admin/lint", { schema: lintReportSchema, signal });
    },
    fixLint(targetIds: string[]) {
      return client.post("/api/v1/admin/lint/fix", {
        body: { targetIds },
        schema: lintFixResultSchema,
      });
    },
    inspect(signal?: AbortSignal) {
      return client.get("/api/v1/admin/inspect", { schema: inspectionReportSchema, signal });
    },
    importInspectionAnswer(request: InspectionImportRequest) {
      return client.post("/api/v1/admin/inspect/import-answers", {
        body: request,
        schema: inspectionImportResultSchema,
      });
    },
    enhanceLinks(persist: boolean) {
      return client.post("/api/v1/admin/link-enhance", {
        body: { persist },
        schema: linkEnhancementReportSchema,
      });
    },
    factCardSummary(signal?: AbortSignal) {
      return client.get("/api/v1/admin/fact-cards/summary", { schema: factCardSummarySchema, signal });
    },
    factCards(limit = 50, signal?: AbortSignal) {
      return client.get("/api/v1/admin/fact-cards", {
        query: { limit },
        schema: factCardListSchema,
        signal,
      });
    },
    factCard(id: number, signal?: AbortSignal) {
      return client.get(`/api/v1/admin/fact-cards/${id}`, { schema: factCardSchema, signal });
    },
  };
}

export const qualityApi = createQualityApi();
