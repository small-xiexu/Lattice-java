import { createApiClient } from "../api-client";
import { queryFeedbackDetailFixture, queryFeedbackFixture } from "../../test/feedback-fixtures";
import { createQueryFeedbackApi } from "./query-feedback";

describe("query feedback API contracts", () => {
  it("maps status and limit while parsing the full feedback list", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({
      count: 1,
      items: [queryFeedbackFixture()],
    }), { status: 200, headers: { "Content-Type": "application/json" } }));
    const api = createQueryFeedbackApi(createApiClient({ fetchImplementation: fetchMock }));

    const response = await api.list({ status: "PENDING", limit: 50 });

    expect(response.items[0]).toMatchObject({ id: 8, status: "PENDING" });
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/admin/query-feedback?status=PENDING&limit=50",
      expect.objectContaining({ method: "GET" }),
    );
  });

  it("parses feedback audits from the detail endpoint", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response(
      JSON.stringify(queryFeedbackDetailFixture()),
      { status: 200, headers: { "Content-Type": "application/json" } },
    ));
    const api = createQueryFeedbackApi(createApiClient({ fetchImplementation: fetchMock }));

    const response = await api.detail(8);

    expect(response.audits[0]).toMatchObject({ action: "CREATE", nextStatus: "PENDING" });
  });

  it("sends the operator and conclusion when resolving feedback", async () => {
    const resolved = queryFeedbackFixture({
      status: "RESOLVED",
      handledBy: "admin",
      resolutionComment: "证据已补充",
    });
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response(
      JSON.stringify(resolved),
      { status: 200, headers: { "Content-Type": "application/json" } },
    ));
    const api = createQueryFeedbackApi(createApiClient({ fetchImplementation: fetchMock }));

    await api.resolve(8, { handledBy: "admin", comment: "证据已补充" });

    const [path, request] = fetchMock.mock.calls[0];
    expect(path).toBe("/api/v1/admin/query-feedback/8/resolve");
    expect(JSON.parse(String((request as RequestInit).body))).toEqual({
      handledBy: "admin",
      comment: "证据已补充",
    });
  });
});
