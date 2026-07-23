import { z } from "zod";

import type { ApiClient } from "../api-client";
import { apiClient } from "../api-client";

const nullableStringSchema = z.string().nullable();

export const queryFeedbackResponseSchema = z.object({
  id: z.number().int().positive(),
  queryId: nullableStringSchema,
  question: z.string(),
  answerSummary: z.string(),
  feedbackType: z.string(),
  comment: z.string(),
  articleKeys: z.array(z.string()),
  sourcePaths: z.array(z.string()),
  reportedBy: z.string(),
  status: z.string(),
  resolutionComment: nullableStringSchema,
  handledBy: nullableStringSchema,
  handledAt: nullableStringSchema,
  createdAt: nullableStringSchema,
  updatedAt: nullableStringSchema,
});

export const queryFeedbackListSchema = z.object({
  count: z.number().int().nonnegative(),
  items: z.array(queryFeedbackResponseSchema),
});

export const queryFeedbackAuditSchema = z.object({
  id: z.number().int().positive(),
  feedbackId: z.number().int().positive(),
  action: z.string(),
  previousStatus: nullableStringSchema,
  nextStatus: z.string(),
  comment: z.string(),
  operatedBy: z.string(),
  operatedAt: nullableStringSchema,
  metadataJson: z.string(),
});

export const queryFeedbackDetailSchema = z.object({
  feedback: queryFeedbackResponseSchema,
  audits: z.array(queryFeedbackAuditSchema),
});

export type QueryFeedbackResponse = z.infer<
  typeof queryFeedbackResponseSchema
>;
export type QueryFeedbackList = z.infer<typeof queryFeedbackListSchema>;
export type QueryFeedbackAudit = z.infer<typeof queryFeedbackAuditSchema>;
export type QueryFeedbackDetail = z.infer<typeof queryFeedbackDetailSchema>;
export type QueryFeedbackStatus = "ALL" | "PENDING" | "RESOLVED" | "DISMISSED";
export type QueryFeedbackType =
  | "reliable"
  | "answer_problem"
  | "source_conflict"
  | "needs_manual_confirmation";
export interface QueryFeedbackRequest {
  queryId: string;
  question: string;
  answerSummary: string;
  feedbackType: QueryFeedbackType;
  comment: string;
  articleKeys: string[];
  sourcePaths: string[];
  reportedBy: "web-app";
}

export interface QueryFeedbackListRequest {
  status: QueryFeedbackStatus;
  limit: number;
  signal?: AbortSignal;
}

export interface QueryFeedbackHandleRequest {
  handledBy: string;
  comment: string;
}

export function createQueryFeedbackApi(client: ApiClient = apiClient) {
  return {
    create(request: QueryFeedbackRequest, signal?: AbortSignal) {
      return client.post("/api/v1/admin/query-feedback", {
        body: request,
        schema: queryFeedbackResponseSchema,
        signal,
      });
    },
    list(request: QueryFeedbackListRequest) {
      return client.get("/api/v1/admin/query-feedback", {
        query: { status: request.status, limit: request.limit },
        schema: queryFeedbackListSchema,
        signal: request.signal,
      });
    },
    detail(feedbackId: number, signal?: AbortSignal) {
      return client.get(`/api/v1/admin/query-feedback/${feedbackId}`, {
        schema: queryFeedbackDetailSchema,
        signal,
      });
    },
    resolve(
      feedbackId: number,
      request: QueryFeedbackHandleRequest,
      signal?: AbortSignal,
    ) {
      return client.post(`/api/v1/admin/query-feedback/${feedbackId}/resolve`, {
        body: request,
        schema: queryFeedbackResponseSchema,
        signal,
      });
    },
    dismiss(
      feedbackId: number,
      request: QueryFeedbackHandleRequest,
      signal?: AbortSignal,
    ) {
      return client.post(`/api/v1/admin/query-feedback/${feedbackId}/dismiss`, {
        body: request,
        schema: queryFeedbackResponseSchema,
        signal,
      });
    },
  };
}

export const queryFeedbackApi = createQueryFeedbackApi();
