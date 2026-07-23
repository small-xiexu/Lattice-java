import { createApiClient } from "../api-client";
import {
  vectorConfigFixture,
  vectorRebuildFixture,
  vectorStatusFixture,
} from "../../test/vector-settings-fixtures";
import { createVectorSettingsApi } from "./vector-settings";

describe("vector settings API contracts", () => {
  it("parses nullable configuration and dimension diagnostics", async () => {
    const fetchMock = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse(vectorConfigFixture({ rebuildReason: null })))
      .mockResolvedValueOnce(jsonResponse(vectorStatusFixture({ dimensionsMatch: null })));
    const api = createVectorSettingsApi(createApiClient({ fetchImplementation: fetchMock }));

    const config = await api.getConfig();
    const status = await api.getStatus();

    expect(config).toMatchObject({ embeddingModelProfileId: 2, rebuildReason: null });
    expect(status).toMatchObject({ schemaDimensions: 2000, dimensionsMatch: null });
  });

  it("sends configuration saves to the dedicated endpoint", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(jsonResponse(vectorConfigFixture()));
    const api = createVectorSettingsApi(createApiClient({ fetchImplementation: fetchMock }));

    await api.updateConfig({ vectorEnabled: true, embeddingModelProfileId: 2, operator: "ops" });

    const [path, request] = fetchMock.mock.calls[0];
    expect(path).toBe("/api/v1/admin/vector/config");
    expect(request).toEqual(expect.objectContaining({ method: "PUT" }));
    expect(JSON.parse(String((request as RequestInit).body))).toEqual({
      vectorEnabled: true,
      embeddingModelProfileId: 2,
      operator: "ops",
    });
  });

  it("keeps rebuild mode explicit and parses the synchronous summary", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(jsonResponse(vectorRebuildFixture({ truncateFirst: true })));
    const api = createVectorSettingsApi(createApiClient({ fetchImplementation: fetchMock }));

    const result = await api.rebuild({ truncateFirst: true, operator: "ops" });

    expect(result).toMatchObject({ targetArticleCount: 40, truncateFirst: true });
    const [, request] = fetchMock.mock.calls[0];
    expect(JSON.parse(String((request as RequestInit).body))).toEqual({ truncateFirst: true, operator: "ops" });
  });
});

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
}
