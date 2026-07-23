import { z } from "zod";

import type { ApiClient } from "../api-client";
import { apiClient } from "../api-client";
import { sourceDetailSchema } from "./sources";

const nullableStringSchema = z.string().nullable();
const nullablePositiveIntegerSchema = z.number().int().positive().nullable();
const nullableNonnegativeIntegerSchema = z.number().int().nonnegative().nullable();

export const sourceCredentialSchema = z.object({
  id: z.number().int().positive(),
  credentialCode: z.string(),
  credentialType: z.string(),
  secretMask: z.string(),
  enabled: z.boolean(),
  updatedAt: nullableStringSchema,
});

const processingTaskActionSchema = z.object({
  actionKey: z.string(),
  label: z.string(),
  buttonClass: nullableStringSchema,
  runId: nullablePositiveIntegerSchema,
  sourceId: nullablePositiveIntegerSchema,
  decision: nullableStringSchema,
  decisionSourceId: nullablePositiveIntegerSchema,
  uploadRetry: z.boolean(),
});

const processingTaskStepSchema = z.object({
  key: z.string(),
  label: z.string(),
  status: z.string(),
  detail: nullableStringSchema,
});

export const sourceRunSchema = z.object({
  runId: z.number().int().positive(),
  sourceId: nullablePositiveIntegerSchema,
  sourceName: nullableStringSchema,
  sourceType: nullableStringSchema,
  status: z.string(),
  resolverMode: nullableStringSchema,
  resolverDecision: nullableStringSchema,
  syncAction: nullableStringSchema,
  matchedSourceId: nullablePositiveIntegerSchema,
  compileJobId: nullableStringSchema,
  compileJobStatus: nullableStringSchema,
  compileDerivedStatus: nullableStringSchema,
  compileCurrentStep: nullableStringSchema,
  compileProgressCurrent: nullableNonnegativeIntegerSchema,
  compileProgressTotal: nullableNonnegativeIntegerSchema,
  compileProgressMessage: nullableStringSchema,
  compileLastHeartbeatAt: nullableStringSchema,
  compileRunningExpiresAt: nullableStringSchema,
  compileErrorCode: nullableStringSchema,
  manifestHash: nullableStringSchema,
  message: nullableStringSchema,
  errorMessage: nullableStringSchema,
  sourceNames: z.array(z.string()),
  actions: z.array(processingTaskActionSchema),
  displayStatus: z.string(),
  displayStatusLabel: z.string(),
  currentStepLabel: z.string(),
  nextStepHint: nullableStringSchema,
  progressText: nullableStringSchema,
  reasonSummary: nullableStringSchema,
  operationalNote: nullableStringSchema,
  progressSteps: z.array(processingTaskStepSchema),
  displayTone: z.string(),
  processingActive: z.boolean(),
  requiresManualAction: z.boolean(),
  noticeTone: z.string(),
  completionNotice: nullableStringSchema,
  pendingHumanReviewCount: z.number().int().nonnegative(),
  publishedCount: z.number().int().nonnegative(),
  rejectedCount: z.number().int().nonnegative(),
  evidenceJson: nullableStringSchema,
  requestedAt: nullableStringSchema,
  updatedAt: nullableStringSchema,
  startedAt: nullableStringSchema,
  finishedAt: nullableStringSchema,
});

export const sourceValidationSchema = z.object({
  valid: z.boolean(),
  sourceType: z.string(),
  message: z.string(),
  resolvedRef: nullableStringSchema,
  branch: nullableStringSchema,
  gitCommit: nullableStringSchema,
});

export const compileReviewSummarySchema = z.object({
  reviewStepPresent: z.boolean(),
  reviewStepName: nullableStringSchema,
  reviewAgentRole: nullableStringSchema,
  requestedReviewMode: nullableStringSchema,
  reviewRoute: nullableStringSchema,
  reviewModeLabel: z.string(),
  acceptedCount: nullableNonnegativeIntegerSchema,
  pendingReviewCount: nullableNonnegativeIntegerSchema,
  needsHumanReviewCount: nullableNonnegativeIntegerSchema,
  fixStepPresent: z.boolean(),
  fixStepName: nullableStringSchema,
  fixAttemptCount: nullableNonnegativeIntegerSchema,
  fixRoute: nullableStringSchema,
  fixDisplayMessage: z.string(),
  reviewDisplayWarning: nullableStringSchema,
});

export const compileJobSchema = z.object({
  jobId: z.string().min(1),
  sourceDir: nullableStringSchema,
  sourceNames: z.array(z.string()),
  incremental: z.boolean(),
  orchestrationMode: nullableStringSchema,
  reviewMode: nullableStringSchema,
  status: z.string(),
  derivedStatus: z.string(),
  workerId: nullableStringSchema,
  currentStep: nullableStringSchema,
  progressCurrent: z.number().int().nonnegative(),
  progressTotal: z.number().int().nonnegative(),
  progressMessage: nullableStringSchema,
  lastHeartbeatAt: nullableStringSchema,
  runningExpiresAt: nullableStringSchema,
  errorCode: nullableStringSchema,
  persistedCount: z.number().int().nonnegative(),
  errorMessage: nullableStringSchema,
  attemptCount: z.number().int().nonnegative(),
  reviewSummary: compileReviewSummarySchema.nullable(),
  requestedAt: nullableStringSchema,
  startedAt: nullableStringSchema,
  finishedAt: nullableStringSchema,
});

export interface SourceCreateRequest {
  sourceCode?: string;
  name: string;
  contentProfile?: "DOCUMENT" | "CODE";
  visibility?: "NORMAL" | "ADMIN_ONLY";
  defaultSyncMode?: "AUTO" | "FULL" | "INCREMENTAL";
  remoteUrl?: string;
  branch?: string;
  credentialRef?: string;
  mirrorRootRef?: string;
  projectPath?: string;
}

export interface SourceCredentialRequest {
  credentialCode: string;
  credentialType: "GIT_TOKEN" | "GIT_HTTP_BASIC";
  secret: string;
  updatedBy?: string;
}

export interface CompileDirectoryRequest {
  sourceDir: string;
  incremental: boolean;
  async: true;
}

export interface UploadFile {
  file: File;
  path: string;
}

export function createSourceImportsApi(client: ApiClient = apiClient) {
  return {
    listCredentials(signal?: AbortSignal) {
      return client.get("/api/v1/admin/source-credentials", {
        schema: z.array(sourceCredentialSchema),
        signal,
      });
    },
    saveCredential(request: SourceCredentialRequest, signal?: AbortSignal) {
      return client.post("/api/v1/admin/source-credentials", {
        body: request,
        schema: sourceCredentialSchema,
        signal,
      });
    },
    upload(files: readonly UploadFile[], sourceId?: number, signal?: AbortSignal) {
      const formData = buildFilesFormData(files);
      if (sourceId !== undefined) {
        formData.set("sourceId", String(sourceId));
      }
      return client.post("/api/v1/admin/uploads", {
        body: formData,
        schema: sourceRunSchema,
        signal,
      });
    },
    createGit(request: SourceCreateRequest, signal?: AbortSignal) {
      return client.post("/api/v1/admin/sources/git", {
        body: request,
        schema: sourceDetailSchema,
        signal,
      });
    },
    createInternalMirror(request: SourceCreateRequest, signal?: AbortSignal) {
      return client.post("/api/v1/admin/sources/internal-mirror", {
        body: request,
        schema: sourceDetailSchema,
        signal,
      });
    },
    validate(sourceId: number, signal?: AbortSignal) {
      return client.post(`/api/v1/admin/sources/${sourceId}/validate`, {
        schema: sourceValidationSchema,
        signal,
      });
    },
    sync(sourceId: number, signal?: AbortSignal) {
      return client.post(`/api/v1/admin/sources/${sourceId}/sync`, {
        schema: sourceRunSchema,
        signal,
      });
    },
    listRuns(sourceId: number, signal?: AbortSignal) {
      return client.get(`/api/v1/admin/sources/${sourceId}/runs`, {
        schema: z.array(sourceRunSchema),
        signal,
      });
    },
    compileDirectory(request: CompileDirectoryRequest, signal?: AbortSignal) {
      return client.post("/api/v1/admin/compile/jobs", {
        body: request,
        schema: compileJobSchema,
        signal,
      });
    },
    compileUpload(
      files: readonly UploadFile[],
      incremental: boolean,
      signal?: AbortSignal,
    ) {
      const formData = buildFilesFormData(files);
      formData.set("incremental", String(incremental));
      formData.set("async", "true");
      return client.post("/api/v1/admin/compile/upload", {
        body: formData,
        schema: compileJobSchema,
        signal,
      });
    },
  };
}

export const sourceImportsApi = createSourceImportsApi();

function buildFilesFormData(files: readonly UploadFile[]) {
  const formData = new FormData();
  files.forEach(({ file, path }) => {
    formData.append("files", file, path || resolveUploadPath(file));
  });
  return formData;
}

export function resolveUploadPath(file: File) {
  return file.webkitRelativePath?.trim() || file.name;
}

export type SourceCredential = z.infer<typeof sourceCredentialSchema>;
export type SourceRun = z.infer<typeof sourceRunSchema>;
export type SourceValidation = z.infer<typeof sourceValidationSchema>;
export type CompileJob = z.infer<typeof compileJobSchema>;
