import { z } from "zod";

import type { ApiClient } from "../api-client";
import { apiClient } from "../api-client";
import { countedResponseSchema } from "./common";

export const searchHitSchema = z.object({
  evidenceType: z.string(),
  sourceId: z.number().int().positive().nullable(),
  articleKey: z.string().nullable(),
  conceptId: z.string().nullable(),
  title: z.string(),
  content: z.string(),
  metadataJson: z.string().nullable(),
  sourcePaths: z.array(z.string()),
  score: z.number(),
});

export const searchResponseSchema = countedResponseSchema(searchHitSchema);

export type SearchHit = z.infer<typeof searchHitSchema>;
export type SearchResponse = z.infer<typeof searchResponseSchema>;

export interface SearchParameters {
  question: string;
  limit?: number;
  signal?: AbortSignal;
}

export function createSearchApi(client: ApiClient = apiClient) {
  return {
    search({ question, limit, signal }: SearchParameters) {
      return client.get("/api/v1/search", {
        query: { question, limit },
        schema: searchResponseSchema,
        signal,
      });
    },
  };
}

export const searchApi = createSearchApi();
