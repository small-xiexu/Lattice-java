import { z } from "zod";

import type { ApiClient } from "../api-client";
import { apiClient } from "../api-client";

const nullableStringSchema = z.string().nullable();

export const compileReviewStatusSchema = z.enum([
  "needs_human_review",
  "published",
  "rejected",
  "accepted",
]);

export const compileReviewQueueItemSchema = z.object({
  id: z.number().int().positive(),
  jobId: z.string().min(1),
  sourceId: z.number().int().positive().nullable(),
  sourceCode: nullableStringSchema,
  conceptId: z.string(),
  articleKey: z.string(),
  title: z.string(),
  content: z.string(),
  metadataJson: z.string(),
  reviewStatus: z.string().min(1),
  reviewRoute: nullableStringSchema,
  reviewerModel: nullableStringSchema,
  reviewIssuesJson: z.string(),
  fixAttemptCount: z.number().int().nonnegative(),
  maxFixRounds: z.number().int().nonnegative(),
  sourcePaths: z.array(z.string()),
  createdAt: nullableStringSchema,
  updatedAt: nullableStringSchema,
  reviewedBy: nullableStringSchema,
  reviewedAt: nullableStringSchema,
  reviewComment: nullableStringSchema,
  publishedArticleKey: nullableStringSchema,
});

export const compileReviewQueueListSchema = z.object({
  total: z.number().int().nonnegative(),
  items: z.array(compileReviewQueueItemSchema),
});

export const compileReviewActionResponseSchema = z.object({
  item: compileReviewQueueItemSchema,
  previousReviewStatus: z.string(),
  auditId: z.number().int().nonnegative(),
});

export const compileReviewConfigSchema = z.object({
  autoFixEnabled: z.boolean(),
  maxFixRounds: z.number().int().min(0).max(5),
  allowPersistNeedsHumanReview: z.boolean(),
  humanReviewSeverityThreshold: z.enum(["HIGH", "MEDIUM", "LOW"]),
  configSource: z.string(),
  createdBy: z.string(),
  updatedBy: z.string(),
  createdAt: nullableStringSchema,
  updatedAt: nullableStringSchema,
});

export const pendingQueryItemSchema = z.object({
  queryId: z.string().min(1),
  question: z.string(),
  answer: z.string(),
  reviewStatus: z.string().min(1),
  selectedConceptIds: z.array(z.string()),
  sourceFilePaths: z.array(z.string()),
  createdAt: nullableStringSchema,
  expiresAt: nullableStringSchema,
});

export const pendingQueryListSchema = z.object({
  count: z.number().int().nonnegative(),
  items: z.array(pendingQueryItemSchema),
});

export const pendingQueryCorrectionResponseSchema = z.object({
  queryId: z.string().min(1),
  answer: z.string(),
  status: z.literal("PENDING"),
});

export const pendingQueryStatusResponseSchema = z.object({
  status: z.enum(["CONFIRMED", "DISCARDED"]),
});

export interface CompileReviewQueueRequest {
  status: CompileReviewStatus;
  limit: number;
  signal?: AbortSignal;
}

export interface CompileReviewActionRequest {
  reviewedBy: string;
  comment: string;
  expectedReviewStatus: string;
}

export interface CompileReviewConfigRequest {
  autoFixEnabled: boolean;
  maxFixRounds: number;
  allowPersistNeedsHumanReview: boolean;
  humanReviewSeverityThreshold: "HIGH" | "MEDIUM" | "LOW";
  operator: string;
}

export function createReviewsApi(client: ApiClient = apiClient) {
  return {
    listCompileQueue(request: CompileReviewQueueRequest) {
      return client.get("/api/v1/admin/compile/review-queue", {
        query: { status: request.status, limit: request.limit },
        schema: compileReviewQueueListSchema,
        signal: request.signal,
      });
    },
    getCompileQueueItem(id: number, signal?: AbortSignal) {
      return client.get(`/api/v1/admin/compile/review-queue/${id}`, {
        schema: compileReviewQueueItemSchema,
        signal,
      });
    },
    approveCompileQueueItem(
      id: number,
      request: CompileReviewActionRequest,
      signal?: AbortSignal,
    ) {
      return client.post(`/api/v1/admin/compile/review-queue/${id}/approve`, {
        body: request,
        schema: compileReviewActionResponseSchema,
        signal,
      });
    },
    rejectCompileQueueItem(
      id: number,
      request: CompileReviewActionRequest,
      signal?: AbortSignal,
    ) {
      return client.post(`/api/v1/admin/compile/review-queue/${id}/reject`, {
        body: request,
        schema: compileReviewActionResponseSchema,
        signal,
      });
    },
    getCompileReviewConfig(signal?: AbortSignal) {
      return client.get("/api/v1/admin/compile/review/config", {
        schema: compileReviewConfigSchema,
        signal,
      });
    },
    updateCompileReviewConfig(
      request: CompileReviewConfigRequest,
      signal?: AbortSignal,
    ) {
      return client.put("/api/v1/admin/compile/review/config", {
        body: request,
        schema: compileReviewConfigSchema,
        signal,
      });
    },
    listPendingQueries(signal?: AbortSignal) {
      return client.get("/api/v1/admin/pending", {
        schema: pendingQueryListSchema,
        signal,
      });
    },
    correctPendingQuery(queryId: string, correction: string, signal?: AbortSignal) {
      return client.post(`/api/v1/admin/pending/${encodeURIComponent(queryId)}/correct`, {
        body: { correction },
        schema: pendingQueryCorrectionResponseSchema,
        signal,
      });
    },
    confirmPendingQuery(queryId: string, signal?: AbortSignal) {
      return client.post(`/api/v1/admin/pending/${encodeURIComponent(queryId)}/confirm`, {
        schema: pendingQueryStatusResponseSchema,
        signal,
      });
    },
    discardPendingQuery(queryId: string, signal?: AbortSignal) {
      return client.post(`/api/v1/admin/pending/${encodeURIComponent(queryId)}/discard`, {
        schema: pendingQueryStatusResponseSchema,
        signal,
      });
    },
  };
}

export const reviewsApi = createReviewsApi();

export type CompileReviewStatus = z.infer<typeof compileReviewStatusSchema>;
export type CompileReviewQueueItem = z.infer<typeof compileReviewQueueItemSchema>;
export type CompileReviewQueueList = z.infer<typeof compileReviewQueueListSchema>;
export type CompileReviewActionResponse = z.infer<typeof compileReviewActionResponseSchema>;
export type CompileReviewConfig = z.infer<typeof compileReviewConfigSchema>;
export type PendingQueryItem = z.infer<typeof pendingQueryItemSchema>;
export type PendingQueryList = z.infer<typeof pendingQueryListSchema>;
