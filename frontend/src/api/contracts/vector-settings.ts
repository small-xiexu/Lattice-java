import { z } from "zod";

import type { ApiClient } from "../api-client";
import { apiClient } from "../api-client";

const nullableStringSchema = z.string().nullable();
const nullablePositiveIntegerSchema = z.number().int().positive().nullable();

export const vectorConfigSchema = z.object({
  vectorEnabled: z.boolean(),
  embeddingModelProfileId: nullablePositiveIntegerSchema,
  providerType: nullableStringSchema,
  modelName: nullableStringSchema,
  profileDimensions: nullablePositiveIntegerSchema,
  configSource: z.string(),
  rebuildRecommended: z.boolean(),
  rebuildReason: nullableStringSchema,
  createdBy: nullableStringSchema,
  updatedBy: nullableStringSchema,
  createdAt: nullableStringSchema,
  updatedAt: nullableStringSchema,
});

export const vectorIndexStatusSchema = z.object({
  vectorEnabled: z.boolean(),
  vectorTypeAvailable: z.boolean(),
  vectorIndexTableAvailable: z.boolean(),
  indexingAvailable: z.boolean(),
  embeddingModelProfileId: nullablePositiveIntegerSchema,
  configuredProviderType: nullableStringSchema,
  configuredModelName: nullableStringSchema,
  configuredExpectedDimensions: z.number().int().nonnegative(),
  profileDimensions: nullablePositiveIntegerSchema,
  embeddingColumnType: nullableStringSchema,
  schemaDimensions: nullablePositiveIntegerSchema,
  dimensionsMatch: z.boolean().nullable(),
  dimensionsConsistent: z.boolean(),
  annIndexReady: z.boolean(),
  annIndexType: nullableStringSchema,
  articleCount: z.number().int().nonnegative(),
  indexedArticleCount: z.number().int().nonnegative(),
  indexedModelNames: z.array(z.string()),
  latestUpdatedAt: nullableStringSchema,
});

export const vectorIndexRebuildResultSchema = z.object({
  targetArticleCount: z.number().int().nonnegative(),
  previousIndexedArticleCount: z.number().int().nonnegative(),
  indexedArticleCount: z.number().int().nonnegative(),
  previousIndexedChunkCount: z.number().int().nonnegative(),
  indexedChunkCount: z.number().int().nonnegative(),
  truncateFirst: z.boolean(),
  configuredModelName: nullableStringSchema,
  operator: z.string(),
  rebuiltAt: z.string(),
});

export type VectorConfig = z.infer<typeof vectorConfigSchema>;
export type VectorIndexStatus = z.infer<typeof vectorIndexStatusSchema>;
export type VectorIndexRebuildResult = z.infer<typeof vectorIndexRebuildResultSchema>;

export interface VectorConfigRequest {
  vectorEnabled: boolean;
  embeddingModelProfileId: number;
  operator: string;
}

export interface VectorIndexRebuildRequest {
  truncateFirst: boolean;
  operator: string;
}

export function createVectorSettingsApi(client: ApiClient = apiClient) {
  return {
    getConfig(signal?: AbortSignal) {
      return client.get("/api/v1/admin/vector/config", {
        schema: vectorConfigSchema,
        signal,
      });
    },
    updateConfig(request: VectorConfigRequest, signal?: AbortSignal) {
      return client.put("/api/v1/admin/vector/config", {
        body: request,
        schema: vectorConfigSchema,
        signal,
      });
    },
    getStatus(signal?: AbortSignal) {
      return client.get("/api/v1/admin/vector/status", {
        schema: vectorIndexStatusSchema,
        signal,
      });
    },
    rebuild(request: VectorIndexRebuildRequest, signal?: AbortSignal) {
      return client.post("/api/v1/admin/vector/rebuild", {
        body: request,
        schema: vectorIndexRebuildResultSchema,
        signal,
      });
    },
  };
}

export const vectorSettingsApi = createVectorSettingsApi();
