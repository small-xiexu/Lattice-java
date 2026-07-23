export const queryKeys = {
  query: {
    root: ["query"] as const,
  },
  search: (question: string, limit: number) =>
    ["search", { question, limit }] as const,
  overview: ["admin", "overview"] as const,
  sources: {
    root: ["admin", "sources"] as const,
    credentials: ["admin", "source-credentials"] as const,
    list: (filters: Record<string, unknown>) =>
      ["admin", "sources", "list", filters] as const,
    detail: (sourceId: number) =>
      ["admin", "sources", "detail", sourceId] as const,
    files: (sourceId: number) =>
      ["admin", "sources", "files", sourceId] as const,
    runs: (sourceId: number) =>
      ["admin", "sources", sourceId, "runs"] as const,
  },
  articles: {
    root: ["admin", "articles"] as const,
    list: (filters: object) =>
      ["admin", "articles", "list", filters] as const,
    detail: (articleId: string, sourceId?: number) =>
      ["admin", "articles", "detail", articleId, sourceId ?? null] as const,
    audits: (articleId: string, sourceId?: number) =>
      ["admin", "articles", articleId, "audits", sourceId ?? null] as const,
    snapshots: (articleId: string, sourceId?: number, limit = 10) =>
      ["admin", "articles", articleId, "snapshots", sourceId ?? null, limit] as const,
  },
  activity: {
    processingTasks: (filters: Record<string, unknown>) =>
      ["admin", "processing-tasks", filters] as const,
    sourceRuns: (filters: Record<string, unknown>) =>
      ["admin", "source-runs", filters] as const,
    compileJobs: ["admin", "compile-jobs"] as const,
    detail: (kind: string, id: string | number) =>
      ["admin", "activity", kind, id] as const,
  },
  reviews: {
    compileQueue: (filters: Record<string, unknown>) =>
      ["admin", "compile-review-queue", filters] as const,
    compileDetail: (reviewId: number) =>
      ["admin", "compile-review-queue", "detail", reviewId] as const,
    pendingQueries: ["admin", "pending-queries"] as const,
  },
  feedback: {
    root: ["admin", "query-feedback"] as const,
    list: (filters: Record<string, unknown>) =>
      ["admin", "query-feedback", "list", filters] as const,
    detail: (feedbackId: number) =>
      ["admin", "query-feedback", "detail", feedbackId] as const,
  },
  quality: {
    root: ["admin", "quality"] as const,
    coverage: ["admin", "coverage"] as const,
    omissions: ["admin", "omissions"] as const,
    lint: ["admin", "lint"] as const,
    inspection: ["admin", "inspect"] as const,
    factCards: (limit: number) => ["admin", "fact-cards", limit] as const,
    factCardSummary: ["admin", "fact-cards", "summary"] as const,
    factCardDetail: (factCardId: number) =>
      ["admin", "fact-cards", "detail", factCardId] as const,
  },
  settings: {
    llm: {
      root: ["admin", "settings", "llm"] as const,
      connections: ["admin", "settings", "llm", "connections"] as const,
      models: ["admin", "settings", "llm", "models"] as const,
      bindings: ["admin", "settings", "llm", "bindings"] as const,
    },
    vector: {
      root: ["admin", "settings", "vector"] as const,
      config: ["admin", "settings", "vector", "config"] as const,
      status: ["admin", "settings", "vector", "status"] as const,
    },
    documentParse: {
      root: ["admin", "settings", "document-parse"] as const,
      providers: ["admin", "settings", "document-parse", "providers"] as const,
      connections: ["admin", "settings", "document-parse", "connections"] as const,
      policy: ["admin", "settings", "document-parse", "policy"] as const,
    },
    retrieval: {
      root: ["admin", "settings", "retrieval"] as const,
      config: ["admin", "settings", "retrieval", "config"] as const,
      recent: (limit: number) =>
        ["admin", "settings", "retrieval", "audits", "recent", limit] as const,
      latest: (queryId: string, historyLimit: number) =>
        ["admin", "settings", "retrieval", "audits", "latest", queryId, historyLimit] as const,
    },
    compileReview: ["admin", "settings", "compile-review"] as const,
  },
  maintenance: {
    repoSnapshots: (limit: number) =>
      ["admin", "maintenance", "repo-snapshots", limit] as const,
    repoDiff: (snapshotId: number, vaultDir: string) =>
      ["admin", "maintenance", "repo-diff", snapshotId, vaultDir] as const,
  },
  health: ["actuator", "health"] as const,
} as const;
