import { createApiClient } from "../api-client";
import { createDeveloperAccessApi } from "./developer-access";

describe("developer access API contracts", () => {
  it("reads the real Actuator health endpoint", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({
      status: "DOWN",
      components: {
        db: { status: "DOWN", details: { error: "unavailable" } },
      },
    }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    }));
    const api = createDeveloperAccessApi(createApiClient({ fetchImplementation: fetchMock }));

    const health = await api.getHealth();

    expect(fetchMock.mock.calls[0][0]).toBe("/actuator/health");
    expect(health.status).toBe("DOWN");
    expect(health.components?.db?.status).toBe("DOWN");
  });
});
