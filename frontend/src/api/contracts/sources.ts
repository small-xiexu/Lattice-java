import { z } from "zod";

import type { ApiClient } from "../api-client";
import { apiClient } from "../api-client";
import { pagedResponseSchema } from "./common";

export const sourceTypeSchema = z.enum([
  "UPLOAD",
  "GIT",
  "INTERNAL_MIRROR",
]);
export const sourceStatusSchema = z.enum(["ACTIVE", "DISABLED", "ARCHIVED"]);
export const sourceVisibilitySchema = z.enum(["NORMAL", "ADMIN_ONLY"]);
export const sourceSyncModeSchema = z.enum(["AUTO", "FULL", "INCREMENTAL"]);

const nullableStringSchema = z.string().nullable();

export const sourceSummarySchema = z.object({
  id: z.number().int().positive(),
  sourceCode: z.string(),
  name: z.string(),
  displayName: z.string(),
  primaryDocumentTitle: nullableStringSchema,
  sourceType: sourceTypeSchema,
  contentProfile: z.string(),
  status: sourceStatusSchema,
  visibility: sourceVisibilitySchema,
  defaultSyncMode: sourceSyncModeSchema,
  lastSyncRunId: z.number().int().positive().nullable(),
  lastSyncStatus: nullableStringSchema,
  lastSyncAt: nullableStringSchema,
  updatedAt: nullableStringSchema,
});

export const sourcePageSchema = pagedResponseSchema(sourceSummarySchema);

export const sourceDetailSchema = sourceSummarySchema.extend({
  configJson: z.string(),
  metadataJson: z.string(),
  latestManifestHash: nullableStringSchema,
  createdAt: nullableStringSchema,
});

export const sourceFileSchema = z.object({
  id: z.number().int().positive(),
  sourceId: z.number().int().positive(),
  relativePath: z.string(),
  format: z.string(),
  fileSize: z.number().int().nonnegative(),
  parseMode: nullableStringSchema,
  parseProvider: nullableStringSchema,
  contentPreview: nullableStringSchema,
});

export type SourceType = z.infer<typeof sourceTypeSchema>;
export type SourceStatus = z.infer<typeof sourceStatusSchema>;
export type SourceSummary = z.infer<typeof sourceSummarySchema>;
export type SourcePage = z.infer<typeof sourcePageSchema>;
export type SourceDetail = z.infer<typeof sourceDetailSchema>;
export type SourceFile = z.infer<typeof sourceFileSchema>;

export interface SourcePatchRequest {
  name: string;
  status: SourceStatus;
  visibility: z.infer<typeof sourceVisibilitySchema>;
  defaultSyncMode: z.infer<typeof sourceSyncModeSchema>;
  configJson: Record<string, unknown>;
}

export interface SourceListParameters {
  keyword?: string;
  status?: SourceStatus;
  sourceType?: SourceType;
  page: number;
  size: number;
  signal?: AbortSignal;
}

export function createSourcesApi(client: ApiClient = apiClient) {
  return {
    list({ signal, ...parameters }: SourceListParameters) {
      return client.get("/api/v1/admin/sources", {
        query: parameters,
        schema: sourcePageSchema,
        signal,
      });
    },
    detail(sourceId: number, signal?: AbortSignal) {
      return client.get(`/api/v1/admin/sources/${sourceId}`, {
        schema: sourceDetailSchema,
        signal,
      });
    },
    files(sourceId: number, signal?: AbortSignal) {
      return client.get(`/api/v1/admin/sources/${sourceId}/files`, {
        schema: z.array(sourceFileSchema),
        signal,
      });
    },
    update(sourceId: number, request: SourcePatchRequest, signal?: AbortSignal) {
      return client.patch(`/api/v1/admin/sources/${sourceId}`, {
        body: request,
        schema: sourceDetailSchema,
        signal,
      });
    },
  };
}

export const sourcesApi = createSourcesApi();
