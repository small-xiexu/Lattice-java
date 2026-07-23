import type {
  LlmBinding,
  LlmConnection,
  LlmModel,
} from "../api/contracts/llm-settings";

export function llmConnectionFixture(overrides: Partial<LlmConnection> = {}): LlmConnection {
  return {
    id: 1,
    connectionCode: "local_openai",
    providerType: "openai_compatible",
    baseUrl: "http://127.0.0.1:8888",
    apiKeyMask: "sk-test****7788",
    enabled: true,
    remarks: "本地代理",
    createdBy: "admin",
    updatedBy: "admin",
    createdAt: "2026-07-22T10:00:00Z",
    updatedAt: "2026-07-22T10:00:00Z",
    ...overrides,
  };
}

export function llmModelFixture(overrides: Partial<LlmModel> = {}): LlmModel {
  return {
    id: 1,
    modelCode: "gpt-default",
    connectionId: 1,
    modelName: "gpt-5.5",
    modelKind: "CHAT",
    expectedDimensions: null,
    supportsDimensionOverride: false,
    temperature: 0.1,
    maxTokens: 4096,
    timeoutSeconds: 30,
    inputPricePer1kTokens: null,
    outputPricePer1kTokens: null,
    extraOptionsJson: "{}",
    enabled: true,
    remarks: null,
    createdBy: "admin",
    updatedBy: "admin",
    createdAt: "2026-07-22T10:00:00Z",
    updatedAt: "2026-07-22T10:00:00Z",
    ...overrides,
  };
}

export function llmBindingFixture(overrides: Partial<LlmBinding> = {}): LlmBinding {
  return {
    id: 1,
    scene: "compile",
    agentRole: "writer",
    primaryModelProfileId: 1,
    fallbackModelProfileId: null,
    routeLabel: "compile.writer.gpt-default",
    enabled: true,
    remarks: null,
    createdBy: "admin",
    updatedBy: "admin",
    createdAt: "2026-07-22T10:00:00Z",
    updatedAt: "2026-07-22T10:00:00Z",
    ...overrides,
  };
}
