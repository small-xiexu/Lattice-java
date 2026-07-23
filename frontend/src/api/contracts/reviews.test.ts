import { createApiClient } from "../api-client";
import { createReviewsApi } from "./reviews";
import {
  compileReviewConfigFixture,
  compileReviewItemFixture,
  pendingQueryItemFixture,
} from "../../test/review-fixtures";

describe("reviews API contracts", () => {
  it("maps the compile queue list filters and parses long-form detail fields", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({
      total: 1,
      items: [compileReviewItemFixture()],
    }), { status: 200, headers: { "Content-Type": "application/json" } }));
    const api = createReviewsApi(createApiClient({ fetchImplementation: fetchMock }));

    const result = await api.listCompileQueue({
      status: "needs_human_review",
      limit: 50,
    });

    expect(result.items[0].reviewIssuesJson).toContain("GROUNDING");
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/admin/compile/review-queue?status=needs_human_review&limit=50",
      expect.objectContaining({ method: "GET" }),
    );
  });

  it("always sends the current review status with an approval action", async () => {
    const item = compileReviewItemFixture({
      reviewStatus: "published",
      publishedArticleKey: "payments-docs--payment-retry",
    });
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({
      item,
      previousReviewStatus: "needs_human_review",
      auditId: 92,
    }), { status: 200, headers: { "Content-Type": "application/json" } }));
    const api = createReviewsApi(createApiClient({ fetchImplementation: fetchMock }));

    await api.approveCompileQueueItem(6, {
      reviewedBy: "reviewer",
      comment: "verified",
      expectedReviewStatus: "needs_human_review",
    });

    const request = fetchMock.mock.calls[0][1] as RequestInit;
    expect(JSON.parse(String(request.body))).toEqual({
      reviewedBy: "reviewer",
      comment: "verified",
      expectedReviewStatus: "needs_human_review",
    });
  });

  it("parses the effective compile review policy", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response(
      JSON.stringify(compileReviewConfigFixture()),
      { status: 200, headers: { "Content-Type": "application/json" } },
    ));
    const api = createReviewsApi(createApiClient({ fetchImplementation: fetchMock }));

    const result = await api.getCompileReviewConfig();

    expect(result).toMatchObject({
      autoFixEnabled: true,
      maxFixRounds: 1,
      humanReviewSeverityThreshold: "HIGH",
    });
  });

  it("parses the full pending query list", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({
      count: 1,
      items: [pendingQueryItemFixture()],
    }), { status: 200, headers: { "Content-Type": "application/json" } }));
    const api = createReviewsApi(createApiClient({ fetchImplementation: fetchMock }));

    const result = await api.listPendingQueries();

    expect(result.items[0]).toMatchObject({
      queryId: "query-pending-1",
      selectedConceptIds: ["payment-retry"],
      sourceFilePaths: ["docs/payment-retry.md"],
    });
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/admin/pending",
      expect.objectContaining({ method: "GET" }),
    );
  });

  it("encodes the pending query id and sends only the correction text", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({
      queryId: "query/with/slash",
      answer: "revised",
      status: "PENDING",
    }), { status: 200, headers: { "Content-Type": "application/json" } }));
    const api = createReviewsApi(createApiClient({ fetchImplementation: fetchMock }));

    await api.correctPendingQuery("query/with/slash", "补充证据");

    const [path, request] = fetchMock.mock.calls[0];
    expect(path).toBe("/api/v1/admin/pending/query%2Fwith%2Fslash/correct");
    expect(JSON.parse(String((request as RequestInit).body))).toEqual({ correction: "补充证据" });
  });
});
