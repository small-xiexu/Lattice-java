import { z } from "zod";

import type { ApiClient } from "../api-client";
import { apiClient } from "../api-client";

const nullableStringSchema = z.string().nullable();

export const citationCheckSummarySchema = z.object({
  verifiedCount: z.number().int().nonnegative(),
  demotedCount: z.number().int().nonnegative(),
  skippedCount: z.number().int().nonnegative(),
  coverageRate: z.number().min(0).max(1),
  noCitation: z.boolean(),
  claimCount: z.number().int().nonnegative(),
  unsupportedClaimCount: z.number().int().nonnegative(),
});

export const deepResearchSummarySchema = z.object({
  routed: z.boolean(),
  layerCount: z.number().int().nonnegative(),
  taskCount: z.number().int().nonnegative(),
  evidenceCardCount: z.number().int().nonnegative(),
  llmCallCount: z.number().int().nonnegative(),
  citationCoverage: z.number().min(0).max(1),
  partialAnswer: z.boolean(),
  hasConflicts: z.boolean(),
});

export const queryStructuredCellEvidenceSchema = z.object({
  columnName: nullableStringSchema,
  columnIndex: z.number().int().nonnegative(),
  cellValue: nullableStringSchema,
  normalizedValue: nullableStringSchema,
  role: nullableStringSchema,
});

export const queryStructuredRowEvidenceSchema = z.object({
  sourcePath: nullableStringSchema,
  tableName: nullableStringSchema,
  sheetName: nullableStringSchema,
  rowNumber: z.number().int().nonnegative(),
  cells: z.array(queryStructuredCellEvidenceSchema),
});

export const queryStructuredGroupEvidenceSchema = z.object({
  groupByField: nullableStringSchema,
  groupValue: nullableStringSchema,
  normalizedGroupValue: nullableStringSchema,
  count: z.number().int().nonnegative(),
  filters: z.record(z.string(), z.string()),
});

export const queryStructuredEvidenceSchema = z.object({
  queryType: nullableStringSchema,
  rows: z.array(queryStructuredRowEvidenceSchema),
  groups: z.array(queryStructuredGroupEvidenceSchema),
});

export const queryCitationSourceSchema = z.object({
  sourceType: z.string(),
  targetKey: nullableStringSchema,
  sourceId: z.number().int().positive().nullable(),
  articleKey: nullableStringSchema,
  conceptId: nullableStringSchema,
  title: nullableStringSchema,
  sourcePaths: z.array(z.string()),
  matchedExcerpt: nullableStringSchema,
  validationStatus: nullableStringSchema,
  reason: nullableStringSchema,
  score: z.number(),
});

export const queryCitationMarkerSchema = z.object({
  markerOrdinal: z.number().int().positive(),
  markerId: z.string().min(1),
  citationLiteral: nullableStringSchema,
  citationLiterals: z.array(z.string()),
  claimText: nullableStringSchema,
  sourceCount: z.number().int().nonnegative(),
  sources: z.array(queryCitationSourceSchema),
});

export const querySourceSchema = z.object({
  sourceId: z.number().int().positive().nullable(),
  articleKey: nullableStringSchema,
  conceptId: nullableStringSchema,
  title: nullableStringSchema,
  sourcePaths: z.array(z.string()).nullable(),
  derivation: nullableStringSchema,
});

export const queryArticleSchema = z.object({
  sourceId: z.number().int().positive().nullable(),
  articleKey: nullableStringSchema,
  conceptId: nullableStringSchema,
  title: nullableStringSchema,
  derivation: nullableStringSchema,
});

export const queryResponseSchema = z.object({
  answer: nullableStringSchema,
  sources: z.array(querySourceSchema).nullable(),
  articles: z.array(queryArticleSchema).nullable(),
  queryId: nullableStringSchema,
  reviewStatus: nullableStringSchema,
  answerOutcome: nullableStringSchema,
  generationMode: nullableStringSchema,
  modelExecutionStatus: nullableStringSchema,
  citationCheck: citationCheckSummarySchema.nullable(),
  deepResearch: deepResearchSummarySchema.nullable(),
  fallbackReason: nullableStringSchema,
  citationMarkers: z.array(queryCitationMarkerSchema),
  structuredEvidence: queryStructuredEvidenceSchema.nullable(),
});

export type QueryResponse = z.infer<typeof queryResponseSchema>;
type QueryRequestOptions = {
  question: string;
  maxLlmCalls?: number;
  overallTimeoutMs?: number;
};

export type QueryRequest = QueryRequestOptions &
  (
    | { forceSimple: true; forceDeep?: never }
    | { forceDeep: true; forceSimple?: never }
    | { forceSimple?: never; forceDeep?: never }
  );

export function createQueryApi(client: ApiClient = apiClient) {
  return {
    query(request: QueryRequest, signal?: AbortSignal) {
      return client.post("/api/v1/query", {
        body: request,
        schema: queryResponseSchema,
        signal,
      });
    },
  };
}

export const queryApi = createQueryApi();
