import { z } from "zod";

import type { ApiClient } from "../api-client";
import { apiClient } from "../api-client";
import { countedResponseSchema } from "./common";

const nullableStringSchema = z.string().nullable();
const stringListSchema = z.array(z.string()).nullable().transform((value) => value ?? []);

export const articleTitleProfileSchema = z.object({
  sourceTitle: nullableStringSchema,
  anchorTitle: nullableStringSchema,
  representativeTitle: nullableStringSchema,
  titleGenerationMode: nullableStringSchema,
});

export const articleSummarySchema = z.object({
  sourceId: z.number().int().positive().nullable(),
  articleKey: z.string().min(1),
  conceptId: z.string().min(1),
  title: z.string().min(1),
  lifecycle: z.string().min(1),
  reviewStatus: z.string().min(1),
  riskLevel: z.string().min(1),
  riskReasons: stringListSchema,
  requiresResultVerification: z.boolean(),
  compiledAt: nullableStringSchema,
  createdAt: nullableStringSchema,
  updatedAt: nullableStringSchema,
  summary: nullableStringSchema,
  sourceCount: z.number().int().nonnegative(),
  primarySourcePath: nullableStringSchema,
  sourcePaths: stringListSchema,
  primarySourceName: nullableStringSchema,
  titleProfile: articleTitleProfileSchema.nullable(),
  isHotspot: z.boolean(),
});

export const articleListResponseSchema = countedResponseSchema(articleSummarySchema);

export const articleDetailSchema = z.object({
  sourceId: z.number().int().positive().nullable(),
  articleKey: z.string().min(1),
  conceptId: z.string().min(1),
  title: z.string().min(1),
  content: z.string(),
  lifecycle: z.string().min(1),
  compiledAt: nullableStringSchema,
  createdAt: nullableStringSchema,
  updatedAt: nullableStringSchema,
  summary: nullableStringSchema,
  reviewStatus: z.string().min(1),
  riskLevel: z.string().min(1),
  riskReasons: stringListSchema,
  isHotspot: z.boolean(),
  requiresResultVerification: z.boolean(),
  confidence: nullableStringSchema,
  sourceCount: z.number().int().nonnegative(),
  primarySourcePath: nullableStringSchema,
  sourcePaths: stringListSchema,
  referentialKeywords: stringListSchema,
  dependsOn: stringListSchema,
  related: stringListSchema,
  metadataJson: nullableStringSchema,
  titleProfile: articleTitleProfileSchema.nullable(),
});

export type ArticleSummary = z.infer<typeof articleSummarySchema>;
export type ArticleDetail = z.infer<typeof articleDetailSchema>;

export interface ArticleListParameters {
  query?: string;
  lifecycle?: string;
  sourceId?: number;
  reviewStatus?: string;
  riskLevel?: string;
  riskReason?: string;
  isHotspot?: boolean;
  requiresResultVerification?: boolean;
  signal?: AbortSignal;
}

export function createArticlesApi(client: ApiClient = apiClient) {
  return {
    list({ signal, ...query }: ArticleListParameters = {}) {
      return client.get("/api/v1/admin/articles", {
        query,
        schema: articleListResponseSchema,
        signal,
      });
    },
    detail(articleId: string, sourceId?: number, signal?: AbortSignal) {
      return client.get(`/api/v1/admin/articles/${encodeURIComponent(articleId)}`, {
        query: { sourceId },
        schema: articleDetailSchema,
        signal,
      });
    },
  };
}

export const articlesApi = createArticlesApi();
