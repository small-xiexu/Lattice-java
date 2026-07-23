import { z } from "zod";

import type { ApiClient } from "../api-client";
import { apiClient } from "../api-client";

const nullableStringSchema = z.string().nullable();

export const repoSnapshotSchema = z.object({
  id: z.number().int().positive(),
  createdAt: z.string(),
  triggerEvent: nullableStringSchema,
  gitCommit: nullableStringSchema,
  description: nullableStringSchema,
  articleCount: z.number().int().nonnegative(),
});

export const repoHistorySchema = z.object({
  count: z.number().int().nonnegative(),
  items: z.array(repoSnapshotSchema),
});

export const repoDiffSchema = z.object({
  snapshotId: z.number().int().positive(),
  targetCommitId: nullableStringSchema,
  currentCommitId: nullableStringSchema,
  count: z.number().int().nonnegative(),
  items: z.array(z.object({
    filePath: z.string(),
    changeType: z.string(),
  })),
});

export const repoBaselineResultSchema = z.object({
  snapshotId: z.number().int().positive(),
  createdAt: z.string(),
  triggerEvent: nullableStringSchema,
  description: nullableStringSchema,
  gitCommit: nullableStringSchema,
  createdNewCommit: z.boolean(),
  articleCount: z.number().int().nonnegative(),
  vaultDir: z.string(),
  writtenFiles: z.number().int().nonnegative(),
  skippedFiles: z.number().int().nonnegative(),
  deletedFiles: z.number().int().nonnegative(),
});

export const repoRollbackResultSchema = z.object({
  restoredSnapshotId: z.number().int().positive(),
  restoredAt: z.string(),
});

export const vaultExportResultSchema = z.object({
  vaultDir: z.string(),
  writtenFiles: z.number().int().nonnegative(),
  skippedFiles: z.number().int().nonnegative(),
  deletedFiles: z.number().int().nonnegative(),
});

export const vaultConflictSchema = z.object({
  filePath: z.string(),
  reason: z.string(),
  manifestHash: nullableStringSchema,
  currentDbHash: nullableStringSchema,
  currentFileHash: nullableStringSchema,
});

export const vaultSyncResultSchema = z.object({
  vaultDir: z.string(),
  syncedFiles: z.number().int().nonnegative(),
  skippedFiles: z.number().int().nonnegative(),
  conflicts: z.array(vaultConflictSchema),
  conflictCount: z.number().int().nonnegative(),
});

export type RepoSnapshot = z.infer<typeof repoSnapshotSchema>;
export type RepoDiff = z.infer<typeof repoDiffSchema>;
export type RepoBaselineResult = z.infer<typeof repoBaselineResultSchema>;
export type RepoRollbackResult = z.infer<typeof repoRollbackResultSchema>;
export type VaultExportResult = z.infer<typeof vaultExportResultSchema>;
export type VaultSyncResult = z.infer<typeof vaultSyncResultSchema>;

export function createRepositoryMaintenanceApi(client: ApiClient = apiClient) {
  return {
    listSnapshots(limit = 20, signal?: AbortSignal) {
      return client.get("/api/v1/admin/snapshot/repo", {
        query: { limit },
        schema: repoHistorySchema,
        signal,
      });
    },
    getDiff(snapshotId: number, vaultDir: string, signal?: AbortSignal) {
      return client.get(`/api/v1/admin/snapshot/repo/${snapshotId}/diff`, {
        query: { vaultDir },
        schema: repoDiffSchema,
        signal,
      });
    },
    createBaseline(request: { vaultDir: string; description: string }, signal?: AbortSignal) {
      return client.post("/api/v1/admin/snapshot/repo/baseline", {
        body: request,
        schema: repoBaselineResultSchema,
        signal,
      });
    },
    rollback(request: { snapshotId: number; vaultDir: string }, signal?: AbortSignal) {
      return client.post("/api/v1/admin/rollback/repo", {
        body: request,
        schema: repoRollbackResultSchema,
        signal,
      });
    },
    exportVault(request: { vaultDir: string }, signal?: AbortSignal) {
      return client.post("/api/v1/admin/vault/export", {
        body: request,
        schema: vaultExportResultSchema,
        signal,
      });
    },
    syncVault(request: { vaultDir: string; force: boolean }, signal?: AbortSignal) {
      return client.post("/api/v1/admin/vault/sync", {
        body: request,
        schema: vaultSyncResultSchema,
        signal,
      });
    },
  };
}

export const repositoryMaintenanceApi = createRepositoryMaintenanceApi();
