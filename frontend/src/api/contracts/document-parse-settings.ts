import { z } from "zod";

import type { ApiClient } from "../api-client";
import { apiClient } from "../api-client";

const nullableStringSchema = z.string().nullable();
const nullablePositiveIntegerSchema = z.number().int().positive().nullable();
const auditFields = {
  createdBy: nullableStringSchema,
  updatedBy: nullableStringSchema,
  createdAt: nullableStringSchema,
  updatedAt: nullableStringSchema,
};

export const documentParseProviderFieldSchema = z.object({
  fieldKey: z.string().min(1),
  label: z.string().min(1),
  inputType: z.enum(["text", "password", "textarea"]),
  required: z.boolean(),
  defaultValue: z.string(),
  placeholder: z.string(),
  description: z.string(),
});

export const documentParseProviderSchema = z.object({
  providerType: z.string().min(1),
  displayName: z.string().min(1),
  defaultBaseUrl: z.string(),
  probeMode: z.string().min(1),
  supportedCapabilities: z.array(z.string()),
  credentialFields: z.array(documentParseProviderFieldSchema),
  configFields: z.array(documentParseProviderFieldSchema),
});

export const documentParseProviderListSchema = z.object({
  count: z.number().int().nonnegative(),
  items: z.array(documentParseProviderSchema),
});

export const documentParseConnectionSchema = z.object({
  id: z.number().int().positive(),
  connectionCode: z.string(),
  providerType: z.string(),
  baseUrl: z.string(),
  credentialMask: z.string(),
  credentialConfigured: z.boolean(),
  configJson: z.string(),
  enabled: z.boolean(),
  ...auditFields,
});

export const documentParseConnectionListSchema = z.object({
  count: z.number().int().nonnegative(),
  items: z.array(documentParseConnectionSchema),
});

export const documentParsePolicySchema = z.object({
  id: nullablePositiveIntegerSchema,
  policyScope: z.literal("default"),
  imageConnectionId: nullablePositiveIntegerSchema,
  scannedPdfConnectionId: nullablePositiveIntegerSchema,
  cleanupEnabled: z.boolean(),
  cleanupModelProfileId: nullablePositiveIntegerSchema,
  fallbackPolicyJson: z.string(),
  ...auditFields,
});

export const documentParseConnectionTestResultSchema = z.object({
  success: z.boolean(),
  providerType: z.string(),
  latencyMs: z.number().int().nonnegative().nullable(),
  endpoint: nullableStringSchema,
  message: z.string(),
});

const mutationResponseSchema = z.object({
  id: z.number().int().positive(),
  status: z.string(),
});

export type DocumentParseProviderField = z.infer<typeof documentParseProviderFieldSchema>;
export type DocumentParseProvider = z.infer<typeof documentParseProviderSchema>;
export type DocumentParseConnection = z.infer<typeof documentParseConnectionSchema>;
export type DocumentParsePolicy = z.infer<typeof documentParsePolicySchema>;
export type DocumentParseConnectionTestResult = z.infer<typeof documentParseConnectionTestResultSchema>;

export interface DocumentParseConnectionRequest {
  connectionCode: string;
  providerType: string;
  baseUrl: string;
  credentialJson: string;
  configJson: string;
  enabled: boolean;
  operator: string;
}

export interface DocumentParseConnectionTestRequest {
  connectionId: number | null;
  providerType: string;
  baseUrl: string;
  credentialJson: string;
  configJson: string;
}

export interface DocumentParsePolicyRequest {
  imageConnectionId: number | null;
  scannedPdfConnectionId: number | null;
  cleanupEnabled: boolean;
  cleanupModelProfileId: number | null;
  fallbackPolicyJson: string;
  operator: string;
}

export function createDocumentParseSettingsApi(client: ApiClient = apiClient) {
  return {
    listProviders(signal?: AbortSignal) {
      return client.get("/api/v1/admin/document-parse/providers", {
        schema: documentParseProviderListSchema,
        signal,
      });
    },
    listConnections(signal?: AbortSignal) {
      return client.get("/api/v1/admin/document-parse/connections", {
        schema: documentParseConnectionListSchema,
        signal,
      });
    },
    createConnection(request: DocumentParseConnectionRequest, signal?: AbortSignal) {
      return client.post("/api/v1/admin/document-parse/connections", {
        body: request,
        schema: documentParseConnectionSchema,
        signal,
      });
    },
    updateConnection(id: number, request: DocumentParseConnectionRequest, signal?: AbortSignal) {
      return client.put(`/api/v1/admin/document-parse/connections/${id}`, {
        body: request,
        schema: documentParseConnectionSchema,
        signal,
      });
    },
    deleteConnection(id: number, signal?: AbortSignal) {
      return client.delete(`/api/v1/admin/document-parse/connections/${id}`, {
        schema: mutationResponseSchema,
        signal,
      });
    },
    testConnection(request: DocumentParseConnectionTestRequest, signal?: AbortSignal) {
      return client.post("/api/v1/admin/document-parse/connections/test", {
        body: request,
        schema: documentParseConnectionTestResultSchema,
        signal,
      });
    },
    getPolicy(signal?: AbortSignal) {
      return client.get("/api/v1/admin/document-parse/policies/default", {
        schema: documentParsePolicySchema,
        signal,
      });
    },
    updatePolicy(request: DocumentParsePolicyRequest, signal?: AbortSignal) {
      return client.put("/api/v1/admin/document-parse/policies/default", {
        body: request,
        schema: documentParsePolicySchema,
        signal,
      });
    },
  };
}

export const documentParseSettingsApi = createDocumentParseSettingsApi();
