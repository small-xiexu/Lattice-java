import { z } from "zod";

import type { ApiClient } from "../api-client";
import { apiClient } from "../api-client";
import {
  compileJobSchema,
  compileReviewSummarySchema,
  sourceRunSchema,
} from "./source-imports";

const nullableStringSchema = z.string().nullable();
const nullablePositiveIntegerSchema = z.number().int().positive().nullable();

export const processingTaskStatusSchema = z.enum([
  "active",
  "terminal",
  "all",
]);

const helpActionSchema = z.object({
  label: z.string(),
  action: z.string(),
  className: z.string(),
});

const helpStateSchema = z.object({
  tone: z.string(),
  title: z.string(),
  description: z.string(),
  faqKey: nullableStringSchema,
  actions: z.array(helpActionSchema),
});

const processingSummaryCardSchema = z.object({
  label: z.string(),
  value: z.number().int().nonnegative(),
  note: nullableStringSchema,
  tone: z.string(),
});

export const processingTaskSchema = sourceRunSchema
  .omit({ runId: true })
  .extend({
    taskId: z.string().min(1),
    taskType: z.string().min(1),
    title: z.string().min(1),
    runId: nullablePositiveIntegerSchema,
    compileReviewSummary: compileReviewSummarySchema.nullable(),
  });

export const processingTaskListSchema = z.object({
  summary: z.object({
    runningCount: z.number().int().nonnegative(),
    waitingCount: z.number().int().nonnegative(),
    stalledCount: z.number().int().nonnegative(),
    succeededCount: z.number().int().nonnegative(),
    failedCount: z.number().int().nonnegative(),
    cards: z.array(processingSummaryCardSchema),
    helpState: helpStateSchema.nullable(),
  }),
  items: z.array(processingTaskSchema),
});

export const compileJobListSchema = z.object({
  count: z.number().int().nonnegative(),
  items: z.array(compileJobSchema),
});

export interface ProcessingTaskListRequest {
  limit: number;
  status: ProcessingTaskStatus;
  signal?: AbortSignal;
}

export interface SourceRunConfirmRequest {
  decision: string;
  sourceId?: number;
}

export function createActivityApi(client: ApiClient = apiClient) {
  return {
    listProcessingTasks(request: ProcessingTaskListRequest) {
      return client.get("/api/v1/admin/processing-tasks", {
        query: { limit: request.limit, status: request.status },
        schema: processingTaskListSchema,
        signal: request.signal,
      });
    },
    listSourceRuns(limit: number, signal?: AbortSignal) {
      return client.get("/api/v1/admin/source-runs", {
        query: { limit },
        schema: z.array(sourceRunSchema),
        signal,
      });
    },
    getSourceRun(runId: number, signal?: AbortSignal) {
      return client.get(`/api/v1/admin/source-runs/${runId}`, {
        schema: sourceRunSchema,
        signal,
      });
    },
    confirmSourceRun(
      runId: number,
      request: SourceRunConfirmRequest,
      signal?: AbortSignal,
    ) {
      return client.post(`/api/v1/admin/source-runs/${runId}/confirm`, {
        body: request,
        schema: sourceRunSchema,
        signal,
      });
    },
    retrySourceRun(runId: number, signal?: AbortSignal) {
      return client.post(`/api/v1/admin/source-runs/${runId}/retry`, {
        schema: sourceRunSchema,
        signal,
      });
    },
    listCompileJobs(signal?: AbortSignal) {
      return client.get("/api/v1/admin/jobs", {
        schema: compileJobListSchema,
        signal,
      });
    },
    getCompileJob(jobId: string, signal?: AbortSignal) {
      return client.get(`/api/v1/admin/jobs/${encodeURIComponent(jobId)}`, {
        schema: compileJobSchema,
        signal,
      });
    },
    retryCompileJob(jobId: string, signal?: AbortSignal) {
      return client.post(`/api/v1/admin/jobs/${encodeURIComponent(jobId)}/retry`, {
        schema: compileJobSchema,
        signal,
      });
    },
  };
}

export const activityApi = createActivityApi();

export type ProcessingTaskStatus = z.infer<typeof processingTaskStatusSchema>;
export type ProcessingTask = z.infer<typeof processingTaskSchema>;
export type ProcessingTaskList = z.infer<typeof processingTaskListSchema>;
export type CompileJobList = z.infer<typeof compileJobListSchema>;
