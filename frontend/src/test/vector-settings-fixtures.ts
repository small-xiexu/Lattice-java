import type {
  VectorConfig,
  VectorIndexRebuildResult,
  VectorIndexStatus,
} from "../api/contracts/vector-settings";

export function vectorConfigFixture(overrides: Partial<VectorConfig> = {}): VectorConfig {
  return {
    vectorEnabled: true,
    embeddingModelProfileId: 2,
    providerType: "openai_compatible",
    modelName: "embedding-3",
    profileDimensions: 2000,
    configSource: "database",
    rebuildRecommended: false,
    rebuildReason: "",
    createdBy: "admin",
    updatedBy: "admin",
    createdAt: "2026-06-08T11:48:14Z",
    updatedAt: "2026-06-08T11:48:14Z",
    ...overrides,
  };
}

export function vectorStatusFixture(overrides: Partial<VectorIndexStatus> = {}): VectorIndexStatus {
  return {
    vectorEnabled: true,
    vectorTypeAvailable: true,
    vectorIndexTableAvailable: true,
    indexingAvailable: true,
    embeddingModelProfileId: 2,
    configuredProviderType: "openai_compatible",
    configuredModelName: "embedding-3",
    configuredExpectedDimensions: 2000,
    profileDimensions: 2000,
    embeddingColumnType: "vector(2000)",
    schemaDimensions: 2000,
    dimensionsMatch: true,
    dimensionsConsistent: true,
    annIndexReady: false,
    annIndexType: "",
    articleCount: 40,
    indexedArticleCount: 40,
    indexedModelNames: ["embedding-3"],
    latestUpdatedAt: "2026-06-11T02:52:57Z",
    ...overrides,
  };
}

export function vectorRebuildFixture(overrides: Partial<VectorIndexRebuildResult> = {}): VectorIndexRebuildResult {
  return {
    targetArticleCount: 40,
    previousIndexedArticleCount: 40,
    indexedArticleCount: 40,
    previousIndexedChunkCount: 120,
    indexedChunkCount: 124,
    truncateFirst: false,
    configuredModelName: "embedding-3",
    operator: "admin",
    rebuiltAt: "2026-07-22T18:00:00Z",
    ...overrides,
  };
}
