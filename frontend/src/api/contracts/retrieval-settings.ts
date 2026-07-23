import { z } from "zod";

import type { ApiClient } from "../api-client";
import { apiClient } from "../api-client";

const nullableStringSchema = z.string().nullable();

export const retrievalConfigSchema = z.object({
  parallelEnabled: z.boolean(),
  rewriteEnabled: z.boolean(),
  intentAwareVectorEnabled: z.boolean(),
  ftsWeight: z.number().nonnegative(),
  refkeyWeight: z.number().nonnegative(),
  articleChunkWeight: z.number().nonnegative(),
  sourceWeight: z.number().nonnegative(),
  sourceChunkWeight: z.number().nonnegative(),
  factCardWeight: z.number().nonnegative(),
  contributionWeight: z.number().nonnegative(),
  graphWeight: z.number().nonnegative(),
  articleVectorWeight: z.number().nonnegative(),
  chunkVectorWeight: z.number().nonnegative(),
  rrfK: z.number().int().positive(),
});

export const retrievalChannelRunSchema = z.object({
  channelName: z.string(),
  status: z.string(),
  durationMillis: z.number().int().nonnegative(),
  hitCount: z.number().int().nonnegative(),
  skippedReason: nullableStringSchema,
  errorSummary: nullableStringSchema,
  timeout: z.boolean(),
  zeroHit: z.boolean(),
});

export const retrievalAuditRunSchema = z.object({
  runId: z.number().int().positive().nullable(),
  queryId: nullableStringSchema,
  question: nullableStringSchema,
  normalizedQuestion: nullableStringSchema,
  retrievalQuestion: nullableStringSchema,
  versionTag: nullableStringSchema,
  strategyTag: nullableStringSchema,
  questionTypeTag: nullableStringSchema,
  answerShape: nullableStringSchema,
  retrievalMode: nullableStringSchema,
  rewriteApplied: z.boolean(),
  rewriteAuditRef: nullableStringSchema,
  retrievalStrategyRef: nullableStringSchema,
  fusedHitCount: z.number().int().nonnegative(),
  channelCount: z.number().int().nonnegative(),
  factCardHitCount: z.number().int().nonnegative(),
  sourceChunkHitCount: z.number().int().nonnegative(),
  coverageStatus: nullableStringSchema,
  channelRunSummaryJson: nullableStringSchema,
  channelRuns: z.array(retrievalChannelRunSchema),
  createdAt: nullableStringSchema,
});

export const retrievalChannelHitSchema = z.object({
  hitId: z.number().int().positive().nullable(),
  runId: z.number().int().positive().nullable(),
  channelName: nullableStringSchema,
  hitRank: z.number().int().nonnegative(),
  fusedRank: z.number().int().nonnegative().nullable(),
  includedInFused: z.boolean(),
  channelWeight: z.number().nonnegative(),
  evidenceType: nullableStringSchema,
  articleKey: nullableStringSchema,
  conceptId: nullableStringSchema,
  title: nullableStringSchema,
  score: z.number(),
  factCardId: z.number().int().positive().nullable(),
  cardType: nullableStringSchema,
  reviewStatus: nullableStringSchema,
  confidence: z.number().nullable(),
  sourceChunkIdsJson: nullableStringSchema,
  sourcePathsJson: nullableStringSchema,
  metadataJson: nullableStringSchema,
  createdAt: nullableStringSchema,
});

export const retrievalAuditListSchema = z.object({
  count: z.number().int().nonnegative(),
  items: z.array(retrievalAuditRunSchema),
});

export const retrievalAuditDetailSchema = z.object({
  queryId: z.string(),
  found: z.boolean(),
  latestRun: retrievalAuditRunSchema.nullable(),
  historyCount: z.number().int().nonnegative(),
  runHistory: z.array(retrievalAuditRunSchema),
  channelHitCount: z.number().int().nonnegative(),
  channelHits: z.array(retrievalChannelHitSchema),
});

export const chunkRebuildResultSchema = z.object({
  rebuiltArticleCount: z.number().int().nonnegative(),
  rebuiltSourceFileCount: z.number().int().nonnegative(),
  articleChunkCount: z.number().int().nonnegative(),
  sourceFileChunkCount: z.number().int().nonnegative(),
  rebuiltAt: z.string(),
});

export type RetrievalConfig = z.infer<typeof retrievalConfigSchema>;
export type RetrievalAuditRun = z.infer<typeof retrievalAuditRunSchema>;
export type RetrievalAuditDetail = z.infer<typeof retrievalAuditDetailSchema>;
export type ChunkRebuildResult = z.infer<typeof chunkRebuildResultSchema>;

export function createRetrievalSettingsApi(client: ApiClient = apiClient) {
  return {
    getConfig(signal?: AbortSignal) {
      return client.get("/api/v1/admin/query/retrieval/config", {
        schema: retrievalConfigSchema,
        signal,
      });
    },
    updateConfig(request: RetrievalConfig, signal?: AbortSignal) {
      return client.put("/api/v1/admin/query/retrieval/config", {
        body: request,
        schema: retrievalConfigSchema,
        signal,
      });
    },
    listRecent(limit = 20, signal?: AbortSignal) {
      return client.get("/api/v1/admin/query/retrieval/audits/recent", {
        query: { limit },
        schema: retrievalAuditListSchema,
        signal,
      });
    },
    getLatest(queryId: string, historyLimit = 5, signal?: AbortSignal) {
      return client.get("/api/v1/admin/query/retrieval/audits/latest", {
        query: { queryId, historyLimit },
        schema: retrievalAuditDetailSchema,
        signal,
      });
    },
    rebuildChunks(signal?: AbortSignal) {
      return client.post("/api/v1/admin/compile/rebuild-chunks", {
        schema: chunkRebuildResultSchema,
        signal,
      });
    },
  };
}

export const retrievalSettingsApi = createRetrievalSettingsApi();
