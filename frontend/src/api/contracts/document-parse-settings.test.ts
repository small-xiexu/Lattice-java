import { createApiClient } from "../api-client";
import {
  documentParseConnectionFixture,
  documentParsePolicyFixture,
  documentParseProviderFixture,
} from "../../test/document-parse-settings-fixtures";
import { createDocumentParseSettingsApi } from "./document-parse-settings";

describe("document parse settings API contracts", () => {
  it("parses provider descriptors and an unconfigured default policy", async () => {
    const fetchMock = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse({ count: 1, items: [documentParseProviderFixture()] }))
      .mockResolvedValueOnce(jsonResponse(documentParsePolicyFixture({
        id: null,
        imageConnectionId: null,
        scannedPdfConnectionId: null,
        createdAt: null,
        createdBy: null,
        updatedAt: null,
        updatedBy: null,
      })));
    const api = createDocumentParseSettingsApi(createApiClient({ fetchImplementation: fetchMock }));

    const providers = await api.listProviders();
    const policy = await api.getPolicy();

    expect(providers.items[0]).toMatchObject({ providerType: "tencent_ocr", probeMode: "json_body_sync" });
    expect(providers.items[0].credentialFields).toHaveLength(2);
    expect(policy).toMatchObject({ id: null, imageConnectionId: null, cleanupEnabled: false });
  });

  it("preserves stored credentials by sending an empty credential payload on update", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(jsonResponse(documentParseConnectionFixture()));
    const api = createDocumentParseSettingsApi(createApiClient({ fetchImplementation: fetchMock }));

    await api.updateConnection(7, {
      connectionCode: "tencent-ocr-main",
      providerType: "tencent_ocr",
      baseUrl: "https://ocr.example.test",
      credentialJson: "",
      configJson: "{\"endpointPath\":\"/ocr/v1/general-basic\"}",
      enabled: true,
      operator: "ops",
    });

    const [path, request] = fetchMock.mock.calls[0];
    expect(path).toBe("/api/v1/admin/document-parse/connections/7");
    expect(request).toEqual(expect.objectContaining({ method: "PUT" }));
    expect(JSON.parse(String((request as RequestInit).body))).toMatchObject({ credentialJson: "", operator: "ops" });
  });

  it("keeps connection testing and destructive deletion on dedicated endpoints", async () => {
    const fetchMock = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse({
        success: true,
        providerType: "tencent_ocr",
        latencyMs: 28,
        endpoint: "https://ocr.example.test/ocr/v1/general-basic",
        message: "连接测试成功",
      }))
      .mockResolvedValueOnce(jsonResponse({ id: 7, status: "deleted" }));
    const api = createDocumentParseSettingsApi(createApiClient({ fetchImplementation: fetchMock }));

    await api.testConnection({
      connectionId: 7,
      providerType: "tencent_ocr",
      baseUrl: "https://ocr.example.test",
      credentialJson: "",
      configJson: "{}",
    });
    await api.deleteConnection(7);

    expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/admin/document-parse/connections/test");
    expect(fetchMock.mock.calls[1][0]).toBe("/api/v1/admin/document-parse/connections/7");
    expect(fetchMock.mock.calls[1][1]).toEqual(expect.objectContaining({ method: "DELETE" }));
  });
});

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
}
