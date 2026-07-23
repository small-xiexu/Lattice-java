import { z } from "zod";

import type { ApiClient } from "../api-client";
import { apiClient } from "../api-client";

const nullableStringSchema = z.string().nullable();
const nullableNumberSchema = z.number().nullable();

const auditFields = {
  createdBy: nullableStringSchema,
  updatedBy: nullableStringSchema,
  createdAt: nullableStringSchema,
  updatedAt: nullableStringSchema,
};

export const llmConnectionSchema = z.object({
  id: z.number().int().positive(),
  connectionCode: z.string(),
  providerType: z.string(),
  baseUrl: z.string(),
  apiKeyMask: z.string(),
  enabled: z.boolean(),
  remarks: nullableStringSchema,
  ...auditFields,
});

export const llmConnectionListSchema = z.object({
  count: z.number().int().nonnegative(),
  items: z.array(llmConnectionSchema),
});

export const llmModelSchema = z.object({
  id: z.number().int().positive(),
  modelCode: z.string(),
  connectionId: z.number().int().positive(),
  modelName: z.string(),
  modelKind: z.enum(["CHAT", "EMBEDDING"]),
  expectedDimensions: z.number().int().positive().nullable(),
  supportsDimensionOverride: z.boolean(),
  temperature: nullableNumberSchema,
  maxTokens: z.number().int().positive().nullable(),
  timeoutSeconds: z.number().int().positive().nullable(),
  inputPricePer1kTokens: nullableNumberSchema,
  outputPricePer1kTokens: nullableNumberSchema,
  extraOptionsJson: z.string(),
  enabled: z.boolean(),
  remarks: nullableStringSchema,
  ...auditFields,
});

export const llmModelListSchema = z.object({
  count: z.number().int().nonnegative(),
  items: z.array(llmModelSchema),
});

export const llmBindingSchema = z.object({
  id: z.number().int().positive(),
  scene: z.enum(["compile", "query", "deep_research"]),
  agentRole: z.string(),
  primaryModelProfileId: z.number().int().positive(),
  fallbackModelProfileId: z.number().int().positive().nullable(),
  routeLabel: z.string(),
  enabled: z.boolean(),
  remarks: nullableStringSchema,
  ...auditFields,
});

export const llmBindingListSchema = z.object({
  count: z.number().int().nonnegative(),
  items: z.array(llmBindingSchema),
});

export const llmConnectionTestResultSchema = z.object({
  success: z.boolean(),
  providerType: z.string(),
  latencyMs: z.number().int().nonnegative().nullable(),
  endpoint: nullableStringSchema,
  message: z.string(),
});

export const llmModelTestResultSchema = z.object({
  success: z.boolean(),
  providerType: z.string(),
  modelKind: z.enum(["CHAT", "EMBEDDING"]),
  latencyMs: z.number().int().nonnegative().nullable(),
  message: z.string(),
});

const mutationResponseSchema = z.object({
  id: z.number().int().positive(),
  status: z.string(),
});

export type LlmConnection = z.infer<typeof llmConnectionSchema>;
export type LlmModel = z.infer<typeof llmModelSchema>;
export type LlmBinding = z.infer<typeof llmBindingSchema>;
export type LlmScene = LlmBinding["scene"];
export type LlmModelKind = LlmModel["modelKind"];
export type LlmConnectionTestResult = z.infer<typeof llmConnectionTestResultSchema>;
export type LlmModelTestResult = z.infer<typeof llmModelTestResultSchema>;

export interface LlmConnectionRequest {
  connectionCode: string;
  providerType: string;
  baseUrl: string;
  apiKey: string;
  enabled: boolean;
  remarks: string | null;
  operator: string;
}

export interface LlmModelRequest {
  modelCode: string;
  connectionId: number;
  modelName: string;
  modelKind: LlmModelKind;
  expectedDimensions: number | null;
  supportsDimensionOverride: boolean;
  temperature: number | null;
  maxTokens: number | null;
  timeoutSeconds: number | null;
  inputPricePer1kTokens: number | null;
  outputPricePer1kTokens: number | null;
  extraOptionsJson: string;
  enabled: boolean;
  remarks: string | null;
  operator: string;
}

export interface LlmBindingRequest {
  scene: LlmScene;
  agentRole: string;
  primaryModelProfileId: number;
  fallbackModelProfileId: number | null;
  routeLabel: string;
  enabled: boolean;
  remarks: string | null;
  operator: string;
}

export interface LlmConnectionTestRequest {
  connectionId: number | null;
  providerType: string;
  baseUrl: string;
  apiKey: string;
}

export interface LlmModelTestRequest {
  modelId: number | null;
  connectionId: number;
  modelName: string;
  modelKind: LlmModelKind;
  expectedDimensions: number | null;
  timeoutSeconds: number | null;
}

export function createLlmSettingsApi(client: ApiClient = apiClient) {
  return {
    listConnections(signal?: AbortSignal) {
      return client.get("/api/v1/admin/llm/connections", {
        schema: llmConnectionListSchema,
        signal,
      });
    },
    createConnection(request: LlmConnectionRequest, signal?: AbortSignal) {
      return client.post("/api/v1/admin/llm/connections", {
        body: request,
        schema: llmConnectionSchema,
        signal,
      });
    },
    updateConnection(id: number, request: LlmConnectionRequest, signal?: AbortSignal) {
      return client.put(`/api/v1/admin/llm/connections/${id}`, {
        body: request,
        schema: llmConnectionSchema,
        signal,
      });
    },
    deleteConnection(id: number, signal?: AbortSignal) {
      return client.delete(`/api/v1/admin/llm/connections/${id}`, {
        schema: mutationResponseSchema,
        signal,
      });
    },
    testConnection(request: LlmConnectionTestRequest, signal?: AbortSignal) {
      return client.post("/api/v1/admin/llm/connections/test", {
        body: request,
        schema: llmConnectionTestResultSchema,
        signal,
      });
    },
    listModels(signal?: AbortSignal) {
      return client.get("/api/v1/admin/llm/models", {
        schema: llmModelListSchema,
        signal,
      });
    },
    createModel(request: LlmModelRequest, signal?: AbortSignal) {
      return client.post("/api/v1/admin/llm/models", {
        body: request,
        schema: llmModelSchema,
        signal,
      });
    },
    updateModel(id: number, request: LlmModelRequest, signal?: AbortSignal) {
      return client.put(`/api/v1/admin/llm/models/${id}`, {
        body: request,
        schema: llmModelSchema,
        signal,
      });
    },
    deleteModel(id: number, signal?: AbortSignal) {
      return client.delete(`/api/v1/admin/llm/models/${id}`, {
        schema: mutationResponseSchema,
        signal,
      });
    },
    testModel(request: LlmModelTestRequest, signal?: AbortSignal) {
      return client.post("/api/v1/admin/llm/models/test", {
        body: request,
        schema: llmModelTestResultSchema,
        signal,
      });
    },
    listBindings(signal?: AbortSignal) {
      return client.get("/api/v1/admin/llm/bindings", {
        schema: llmBindingListSchema,
        signal,
      });
    },
    createBinding(request: LlmBindingRequest, signal?: AbortSignal) {
      return client.post("/api/v1/admin/llm/bindings", {
        body: request,
        schema: llmBindingSchema,
        signal,
      });
    },
    updateBinding(id: number, request: LlmBindingRequest, signal?: AbortSignal) {
      return client.put(`/api/v1/admin/llm/bindings/${id}`, {
        body: request,
        schema: llmBindingSchema,
        signal,
      });
    },
    deleteBinding(id: number, signal?: AbortSignal) {
      return client.delete(`/api/v1/admin/llm/bindings/${id}`, {
        schema: mutationResponseSchema,
        signal,
      });
    },
  };
}

export const llmSettingsApi = createLlmSettingsApi();
