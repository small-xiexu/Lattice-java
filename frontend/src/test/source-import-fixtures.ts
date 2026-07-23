export function sourceDetailFixture(overrides: Record<string, unknown> = {}) {
  return {
    id: 12,
    sourceCode: "payments-docs",
    name: "Payments Docs",
    displayName: "Payments Docs",
    primaryDocumentTitle: null,
    sourceType: "GIT",
    contentProfile: "DOCUMENT",
    status: "ACTIVE",
    visibility: "NORMAL",
    defaultSyncMode: "AUTO",
    configJson: "{}",
    metadataJson: "{}",
    latestManifestHash: null,
    lastSyncRunId: null,
    lastSyncStatus: null,
    lastSyncAt: null,
    createdAt: "2026-07-22T21:00:00+08:00",
    updatedAt: "2026-07-22T21:00:00+08:00",
    ...overrides,
  };
}

export function sourceRunFixture(overrides: Record<string, unknown> = {}) {
  return {
    runId: 33,
    sourceId: 12,
    sourceName: "Payments Docs",
    sourceType: "GIT",
    status: "QUEUED",
    resolverMode: "RULE_ONLY",
    resolverDecision: "EXISTING_SOURCE",
    syncAction: "UPDATE",
    matchedSourceId: 12,
    compileJobId: "job-33",
    compileJobStatus: "QUEUED",
    compileDerivedStatus: "QUEUED",
    compileCurrentStep: null,
    compileProgressCurrent: 0,
    compileProgressTotal: 0,
    compileProgressMessage: "等待处理",
    compileLastHeartbeatAt: null,
    compileRunningExpiresAt: null,
    compileErrorCode: null,
    manifestHash: "manifest-33",
    message: "已提交",
    errorMessage: null,
    sourceNames: ["docs/readme.md"],
    actions: [],
    displayStatus: "QUEUED",
    displayStatusLabel: "等待中",
    currentStepLabel: "资料接收",
    nextStepHint: "等待处理任务开始",
    progressText: "等待处理",
    reasonSummary: null,
    operationalNote: "运行态：等待中",
    progressSteps: [
      { key: "TASK_RECEIVED", label: "资料接收", status: "COMPLETED", detail: "" },
    ],
    displayTone: "info",
    processingActive: true,
    requiresManualAction: false,
    noticeTone: "info",
    completionNotice: null,
    pendingHumanReviewCount: 0,
    publishedCount: 0,
    rejectedCount: 0,
    evidenceJson: null,
    requestedAt: "2026-07-22T21:01:00+08:00",
    updatedAt: "2026-07-22T21:01:00+08:00",
    startedAt: null,
    finishedAt: null,
    ...overrides,
  };
}

export function sourceValidationFixture(overrides: Record<string, unknown> = {}) {
  return {
    valid: true,
    sourceType: "GIT",
    message: "Git 资料源可访问",
    resolvedRef: "refs/heads/main",
    branch: "main",
    gitCommit: "abc123",
    ...overrides,
  };
}

export function sourceCredentialFixture(overrides: Record<string, unknown> = {}) {
  return {
    id: 8,
    credentialCode: "git-private-main",
    credentialType: "GIT_TOKEN",
    secretMask: "ghp_***",
    enabled: true,
    updatedAt: "2026-07-22T21:02:00+08:00",
    ...overrides,
  };
}

export function compileJobFixture(overrides: Record<string, unknown> = {}) {
  return {
    jobId: "compile-44",
    sourceDir: "/srv/lattice/docs",
    sourceNames: [],
    incremental: false,
    orchestrationMode: "state_graph",
    reviewMode: "LLM",
    status: "QUEUED",
    derivedStatus: "QUEUED",
    workerId: null,
    currentStep: null,
    progressCurrent: 0,
    progressTotal: 0,
    progressMessage: "等待处理",
    lastHeartbeatAt: null,
    runningExpiresAt: null,
    errorCode: null,
    persistedCount: 0,
    errorMessage: null,
    attemptCount: 1,
    reviewSummary: null,
    requestedAt: "2026-07-22T21:03:00+08:00",
    startedAt: null,
    finishedAt: null,
    ...overrides,
  };
}
