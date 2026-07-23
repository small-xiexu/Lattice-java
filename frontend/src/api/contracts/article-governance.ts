import { z } from "zod";

import type { ApiClient } from "../api-client";
import { apiClient } from "../api-client";
import { countedResponseSchema } from "./common";

const nullableStringSchema = z.string().nullable();
const stringListSchema = z.array(z.string()).nullable().transform((value) => value ?? []);

export const articleReviewAuditSchema = z.object({
  id: z.number().int().positive(),
  sourceId: z.number().int().positive().nullable(),
  articleKey: z.string().min(1),
  conceptId: z.string().min(1),
  action: z.enum(["approve", "request_changes"]),
  previousReviewStatus: z.string().min(1),
  nextReviewStatus: z.string().min(1),
  comment: nullableStringSchema,
  reviewedBy: nullableStringSchema,
  reviewedAt: nullableStringSchema,
  metadataJson: nullableStringSchema,
});

export const articleReviewAuditListSchema = countedResponseSchema(articleReviewAuditSchema);

export const articleSnapshotSchema = z.object({
  snapshotId: z.number().int().positive(),
  sourceId: z.number().int().positive().nullable(),
  articleKey: z.string().min(1),
  conceptId: z.string().min(1),
  title: z.string(),
  content: z.string(),
  lifecycle: z.string().min(1),
  compiledAt: nullableStringSchema,
  sourcePaths: stringListSchema,
  metadataJson: nullableStringSchema,
  summary: nullableStringSchema,
  referentialKeywords: stringListSchema,
  dependsOn: stringListSchema,
  related: stringListSchema,
  confidence: nullableStringSchema,
  reviewStatus: z.string().min(1),
  snapshotReason: nullableStringSchema,
  capturedAt: nullableStringSchema,
});

export const articleSnapshotListSchema = z.object({
  conceptId: nullableStringSchema,
  count: z.number().int().nonnegative(),
  items: z.array(articleSnapshotSchema),
});

export const articleReviewResponseSchema = z.object({
  sourceId: z.number().int().positive().nullable(),
  articleKey: z.string().min(1),
  conceptId: z.string().min(1),
  previousReviewStatus: z.string().min(1),
  reviewStatus: z.string().min(1),
  reviewedBy: nullableStringSchema,
  reviewedAt: nullableStringSchema,
  auditId: z.number().int().positive(),
});

export const lifecycleTransitionSchema = z.object({
  sourceId: z.number().int().positive().nullable(),
  articleKey: z.string().min(1),
  conceptId: z.string().min(1),
  title: z.string(),
  lifecycle: z.string().min(1),
  reason: nullableStringSchema,
  updatedBy: nullableStringSchema,
  updatedAt: nullableStringSchema,
});

export const articleCorrectionResultSchema = z.object({
  sourceId: z.number().int().positive().nullable(),
  articleKey: z.string().min(1),
  conceptId: z.string().min(1),
  revisedContent: z.string(),
  downstreamIds: stringListSchema,
  validationSupported: z.boolean(),
});

export const articleRollbackResultSchema = z.object({
  sourceId: z.number().int().positive().nullable(),
  articleKey: z.string().min(1),
  conceptId: z.string().min(1),
  restoredSnapshotId: z.number().int().positive(),
  restoredAt: z.string().min(1),
});

export const articleUsageStatsSchema = z.object({
  articleKey: z.string().min(1),
  conceptId: z.string().min(1),
  retrievalHitCount: z.number().int().nonnegative(),
  citationCount: z.number().int().nonnegative(),
  answerFeedbackCount: z.number().int().nonnegative(),
  manualMarkCount: z.number().int().nonnegative(),
  heatScore: z.number().int().nonnegative(),
  sourcePaths: stringListSchema,
  updatedAt: nullableStringSchema,
});

export const articleHotspotRefreshResponseSchema = z.object({
  rebuiltStatsCount: z.number().int().nonnegative(),
  hotspotCandidateCount: z.number().int().nonnegative(),
  updatedArticleCount: z.number().int().nonnegative(),
  heatScoreThreshold: z.number().int().positive(),
  candidates: z.array(articleUsageStatsSchema),
});

export type ArticleSnapshot = z.infer<typeof articleSnapshotSchema>;
export type ArticleReviewAudit = z.infer<typeof articleReviewAuditSchema>;
export type ArticleCorrectionResult = z.infer<typeof articleCorrectionResultSchema>;
export type ArticleHotspotRefreshResponse = z.infer<typeof articleHotspotRefreshResponseSchema>;
export type LifecycleAction = "activate" | "deprecate" | "archive";

export interface ArticleReviewRequest {
  sourceId?: number;
  reviewedBy: string;
  comment?: string;
  expectedReviewStatus: string;
  correctionSummary?: string;
}

export interface LifecycleRequest {
  reason: string;
  updatedBy: string;
}

export interface HotspotRefreshRequest {
  heatScoreThreshold?: number;
  limit?: number;
}

export function createArticleGovernanceApi(client: ApiClient = apiClient) {
  return {
    audits(articleId: string, sourceId?: number, signal?: AbortSignal) {
      return client.get(`/api/v1/admin/articles/${encodeURIComponent(articleId)}/review/audits`, {
        query: { sourceId },
        schema: articleReviewAuditListSchema,
        signal,
      });
    },
    snapshots(articleId: string, sourceId?: number, limit = 10, signal?: AbortSignal) {
      return client.get("/api/v1/admin/snapshot/article", {
        query: { articleId, sourceId, limit },
        schema: articleSnapshotListSchema,
        signal,
      });
    },
    approve(articleId: string, request: ArticleReviewRequest) {
      return client.post(`/api/v1/admin/articles/${encodeURIComponent(articleId)}/review/approve`, {
        body: request,
        schema: articleReviewResponseSchema,
      });
    },
    requestChanges(articleId: string, request: ArticleReviewRequest) {
      return client.post(`/api/v1/admin/articles/${encodeURIComponent(articleId)}/review/request-changes`, {
        body: request,
        schema: articleReviewResponseSchema,
      });
    },
    transitionLifecycle(
      articleId: string,
      sourceId: number | undefined,
      action: LifecycleAction,
      request: LifecycleRequest,
    ) {
      return client.post(`/api/v1/admin/articles/${encodeURIComponent(articleId)}/lifecycle/${action}`, {
        body: request,
        query: { sourceId },
        schema: lifecycleTransitionSchema,
      });
    },
    correct(articleId: string, sourceId: number | undefined, correctionSummary: string) {
      return client.post(`/api/v1/admin/articles/${encodeURIComponent(articleId)}/correct`, {
        body: { correctionSummary },
        query: { sourceId },
        schema: articleCorrectionResultSchema,
      });
    },
    rollback(articleId: string, sourceId: number | undefined, snapshotId: number) {
      return client.post("/api/v1/admin/rollback/article", {
        body: { articleId, sourceId, snapshotId },
        schema: articleRollbackResultSchema,
      });
    },
    refreshHotspots(request: HotspotRefreshRequest) {
      return client.post("/api/v1/admin/articles/hotspots/refresh", {
        body: request,
        schema: articleHotspotRefreshResponseSchema,
      });
    },
  };
}

export const articleGovernanceApi = createArticleGovernanceApi();
