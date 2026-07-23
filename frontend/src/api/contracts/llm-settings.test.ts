import { createApiClient } from "../api-client";
import {
  llmBindingFixture,
  llmConnectionFixture,
  llmModelFixture,
} from "../../test/llm-settings-fixtures";
import { createLlmSettingsApi } from "./llm-settings";

describe("LLM settings API contracts", () => {
  it("parses masked connections without accepting a plaintext key field", async () => {
    const connection = llmConnectionFixture();
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(jsonResponse({ count: 1, items: [connection] }));
    const api = createLlmSettingsApi(createApiClient({ fetchImplementation: fetchMock }));

    const response = await api.listConnections();

    expect(response.items[0]).toMatchObject({ apiKeyMask: "sk-test****7788" });
    expect(response.items[0]).not.toHaveProperty("apiKey");
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/admin/llm/connections", expect.objectContaining({ method: "GET" }));
  });

  it("keeps connection tests separate from persistence and allows a null failure endpoint", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(jsonResponse({
      success: false,
      providerType: "openai_compatible",
      latencyMs: null,
      endpoint: null,
      message: "连接超时",
    }));
    const api = createLlmSettingsApi(createApiClient({ fetchImplementation: fetchMock }));

    const response = await api.testConnection({ connectionId: 1, providerType: "openai_compatible", baseUrl: "http://localhost:8888", apiKey: "" });

    expect(response).toMatchObject({ success: false, endpoint: null });
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/admin/llm/connections/test",
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("parses model and binding lists with nullable cross references", async () => {
    const fetchMock = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse({ count: 1, items: [llmModelFixture()] }))
      .mockResolvedValueOnce(jsonResponse({ count: 1, items: [llmBindingFixture()] }));
    const api = createLlmSettingsApi(createApiClient({ fetchImplementation: fetchMock }));

    const models = await api.listModels();
    const bindings = await api.listBindings();

    expect(models.items[0]).toMatchObject({ modelKind: "CHAT", expectedDimensions: null });
    expect(bindings.items[0]).toMatchObject({ scene: "compile", fallbackModelProfileId: null });
  });

  it("sends an empty key on connection updates so the server preserves the stored secret", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(jsonResponse(llmConnectionFixture()));
    const api = createLlmSettingsApi(createApiClient({ fetchImplementation: fetchMock }));

    await api.updateConnection(1, {
      connectionCode: "local_openai",
      providerType: "openai_compatible",
      baseUrl: "http://localhost:8888",
      apiKey: "",
      enabled: true,
      remarks: null,
      operator: "admin",
    });

    const [, request] = fetchMock.mock.calls[0];
    expect(JSON.parse(String((request as RequestInit).body))).toMatchObject({ apiKey: "", operator: "admin" });
  });
});

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
}
