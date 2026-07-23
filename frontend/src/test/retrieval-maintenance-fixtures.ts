import type {
  ChunkRebuildResult,
  RetrievalAuditDetail,
  RetrievalAuditRun,
  RetrievalConfig,
} from "../api/contracts/retrieval-settings";
import type {
  RepoBaselineResult,
  RepoDiff,
  RepoRollbackResult,
  RepoSnapshot,
  VaultExportResult,
  VaultSyncResult,
} from "../api/contracts/repository-maintenance";

export function retrievalConfigFixture(overrides: Partial<RetrievalConfig> = {}): RetrievalConfig {
  return {
    parallelEnabled: true,
    rewriteEnabled: true,
    intentAwareVectorEnabled: true,
    ftsWeight: 1,
    refkeyWeight: 1.45,
    articleChunkWeight: 1.25,
    sourceWeight: 1,
    sourceChunkWeight: 1.3,
    factCardWeight: 1.4,
    contributionWeight: 1,
    graphWeight: 1.2,
    articleVectorWeight: 1,
    chunkVectorWeight: 1.35,
    rrfK: 60,
    ...overrides,
  };
}

export function retrievalAuditRunFixture(overrides: Partial<RetrievalAuditRun> = {}): RetrievalAuditRun {
  return {
    runId: 208,
    queryId: "query-208",
    question: "如何定位检索通道延迟？",
    normalizedQuestion: "如何定位检索通道延迟",
    retrievalQuestion: "检索通道延迟定位",
    versionTag: "retrieval-core-v2",
    strategyTag: "intent=GENERAL|mode=parallel|vector=on",
    questionTypeTag: "GENERAL",
    answerShape: "GENERAL",
    retrievalMode: "parallel",
    rewriteApplied: true,
    rewriteAuditRef: "rewrite-208",
    retrievalStrategyRef: "strategy-208",
    fusedHitCount: 10,
    channelCount: 2,
    factCardHitCount: 4,
    sourceChunkHitCount: 6,
    coverageStatus: "sufficient",
    channelRunSummaryJson: "{}",
    channelRuns: [
      {
        channelName: "fts",
        status: "SUCCESS",
        durationMillis: 41,
        hitCount: 8,
        skippedReason: "",
        errorSummary: "",
        timeout: false,
        zeroHit: false,
      },
      {
        channelName: "graph",
        status: "SUCCESS",
        durationMillis: 2,
        hitCount: 0,
        skippedReason: "",
        errorSummary: "",
        timeout: false,
        zeroHit: true,
      },
    ],
    createdAt: "2026-07-22T14:48:00Z",
    ...overrides,
  };
}

export function retrievalAuditDetailFixture(overrides: Partial<RetrievalAuditDetail> = {}): RetrievalAuditDetail {
  const latestRun = retrievalAuditRunFixture();
  return {
    queryId: latestRun.queryId ?? "query-208",
    found: true,
    latestRun,
    historyCount: 1,
    runHistory: [retrievalAuditRunFixture({ runId: 207, createdAt: "2026-07-22T14:47:00Z" })],
    channelHitCount: 1,
    channelHits: [{
      hitId: 1001,
      runId: 208,
      channelName: "fts",
      hitRank: 1,
      fusedRank: 2,
      includedInFused: true,
      channelWeight: 1,
      evidenceType: "article",
      articleKey: "retrieval-audit",
      conceptId: "retrieval-audit",
      title: "检索审计",
      score: 0.91,
      factCardId: null,
      cardType: null,
      reviewStatus: "PUBLISHED",
      confidence: null,
      sourceChunkIdsJson: "[]",
      sourcePathsJson: "[]",
      metadataJson: "{}",
      createdAt: "2026-07-22T14:48:00Z",
    }],
    ...overrides,
  };
}

export function chunkRebuildResultFixture(overrides: Partial<ChunkRebuildResult> = {}): ChunkRebuildResult {
  return {
    rebuiltArticleCount: 40,
    rebuiltSourceFileCount: 39,
    articleChunkCount: 320,
    sourceFileChunkCount: 410,
    rebuiltAt: "2026-07-22T15:00:00Z",
    ...overrides,
  };
}

export function repoSnapshotFixture(overrides: Partial<RepoSnapshot> = {}): RepoSnapshot {
  return {
    id: 12,
    createdAt: "2026-07-22T12:00:00Z",
    triggerEvent: "vault.baseline",
    gitCommit: "abc123def456",
    description: "发布前基线",
    articleCount: 40,
    ...overrides,
  };
}

export function repoDiffFixture(overrides: Partial<RepoDiff> = {}): RepoDiff {
  return {
    snapshotId: 12,
    targetCommitId: "abc123def456",
    currentCommitId: "fed654cba321",
    count: 2,
    items: [
      { filePath: "articles/retrieval.md", changeType: "M" },
      { filePath: "articles/new.md", changeType: "A" },
    ],
    ...overrides,
  };
}

export function repoBaselineResultFixture(overrides: Partial<RepoBaselineResult> = {}): RepoBaselineResult {
  return {
    snapshotId: 13,
    createdAt: "2026-07-22T15:10:00Z",
    triggerEvent: "vault.baseline",
    description: "发布前基线",
    gitCommit: "new123commit",
    createdNewCommit: true,
    articleCount: 40,
    vaultDir: "/tmp/lattice-vault",
    writtenFiles: 4,
    skippedFiles: 36,
    deletedFiles: 1,
    ...overrides,
  };
}

export function repoRollbackResultFixture(overrides: Partial<RepoRollbackResult> = {}): RepoRollbackResult {
  return {
    restoredSnapshotId: 12,
    restoredAt: "2026-07-22T15:20:00Z",
    ...overrides,
  };
}

export function vaultExportResultFixture(overrides: Partial<VaultExportResult> = {}): VaultExportResult {
  return {
    vaultDir: "/tmp/lattice-vault",
    writtenFiles: 38,
    skippedFiles: 2,
    deletedFiles: 1,
    ...overrides,
  };
}

export function vaultSyncResultFixture(overrides: Partial<VaultSyncResult> = {}): VaultSyncResult {
  const conflicts = overrides.conflicts ?? [];
  return {
    vaultDir: "/tmp/lattice-vault",
    syncedFiles: 3,
    skippedFiles: conflicts.length,
    conflicts,
    conflictCount: conflicts.length,
    ...overrides,
  };
}
