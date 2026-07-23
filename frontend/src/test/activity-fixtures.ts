import type {
  CompileJob,
  SourceRun,
} from "../api/contracts/source-imports";
import type {
  ProcessingTask,
  ProcessingTaskList,
} from "../api/contracts/activity";

const reviewSummary = {
  reviewStepPresent: true,
  reviewStepName: "review_articles",
  reviewAgentRole: "ReviewerAgent",
  requestedReviewMode: "LLM",
  reviewRoute: "compile.reviewer.test",
  reviewModeLabel: "LLM 审查",
  acceptedCount: 0,
  pendingReviewCount: 0,
  needsHumanReviewCount: 0,
  fixStepPresent: false,
  fixStepName: null,
  fixAttemptCount: 0,
  fixRoute: null,
  fixDisplayMessage: "未触发自动修复",
  reviewDisplayWarning: null,
};

export function sourceRunFixture(
  overrides: Partial<SourceRun> = {},
): SourceRun {
  return {
    runId: 41,
    sourceId: 12,
    sourceName: "Payments Docs",
    sourceType: "UPLOAD",
    status: "RUNNING",
    resolverMode: "RULE_ONLY",
    resolverDecision: "NEW_SOURCE",
    syncAction: "CREATE",
    matchedSourceId: 12,
    compileJobId: "job-41",
    compileJobStatus: "RUNNING",
    compileDerivedStatus: "RUNNING",
    compileCurrentStep: "review_articles",
    compileProgressCurrent: 2,
    compileProgressTotal: 4,
    compileProgressMessage: "正在检查文章",
    compileLastHeartbeatAt: "2026-07-22T10:01:00Z",
    compileRunningExpiresAt: "2026-07-22T10:02:00Z",
    compileErrorCode: null,
    manifestHash: "manifest-41",
    message: "任务执行中",
    errorMessage: null,
    sourceNames: ["payments/readme.md"],
    actions: [],
    displayStatus: "RUNNING",
    displayStatusLabel: "运行中",
    currentStepLabel: "质量检查",
    nextStepHint: "等待检查完成",
    progressText: "2/4",
    reasonSummary: "后台正在处理资料",
    operationalNote: "运行态：运行中",
    progressSteps: [
      { key: "TASK_RECEIVED", label: "资料接收", status: "COMPLETED", detail: "" },
      { key: "REVIEW_ARTICLES", label: "质量检查", status: "ACTIVE", detail: "正在检查" },
      { key: "FINALIZE_JOB", label: "写入知识库", status: "PENDING", detail: "" },
    ],
    displayTone: "info",
    processingActive: true,
    requiresManualAction: false,
    noticeTone: "info",
    completionNotice: null,
    pendingHumanReviewCount: 0,
    publishedCount: 0,
    rejectedCount: 0,
    evidenceJson: "{\"trace\":\"activity-fixture\"}",
    requestedAt: "2026-07-22T10:00:00Z",
    updatedAt: "2026-07-22T10:01:00Z",
    startedAt: "2026-07-22T10:00:10Z",
    finishedAt: null,
    ...overrides,
  };
}
export function processingTaskFixture(
  overrides: Partial<ProcessingTask> = {},
): ProcessingTask {
  return {
    ...sourceRunFixture(),
    taskId: "source-run:41",
    taskType: "SOURCE_SYNC",
    title: "Payments Docs",
    compileReviewSummary: reviewSummary,
    ...overrides,
  };
}

export function processingTaskListFixture(
  items: ProcessingTask[] = [processingTaskFixture()],
): ProcessingTaskList {
  return {
    summary: {
      runningCount: items.filter((item) => item.processingActive).length,
      waitingCount: items.filter((item) => item.requiresManualAction).length,
      stalledCount: 0,
      succeededCount: items.filter((item) => item.status === "SUCCEEDED").length,
      failedCount: items.filter((item) => item.status === "FAILED").length,
      cards: [
        { label: "运行中", value: 1, note: "后台处理中", tone: "info" },
        { label: "失败", value: 0, note: null, tone: "" },
      ],
      helpState: null,
    },
    items,
  };
}

export function compileJobFixture(
  overrides: Partial<CompileJob> = {},
): CompileJob {
  return {
    jobId: "job-41",
    sourceDir: "/tmp/payments",
    sourceNames: ["payments/readme.md"],
    incremental: false,
    orchestrationMode: "state_graph",
    reviewMode: "LLM",
    status: "RUNNING",
    derivedStatus: "RUNNING",
    workerId: "worker-1",
    currentStep: "review_articles",
    progressCurrent: 2,
    progressTotal: 4,
    progressMessage: "正在检查文章",
    lastHeartbeatAt: "2026-07-22T10:01:00Z",
    runningExpiresAt: "2026-07-22T10:02:00Z",
    errorCode: null,
    persistedCount: 0,
    errorMessage: null,
    attemptCount: 1,
    reviewSummary,
    requestedAt: "2026-07-22T10:00:00Z",
    startedAt: "2026-07-22T10:00:10Z",
    finishedAt: null,
    ...overrides,
  };
}
