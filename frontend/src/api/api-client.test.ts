import { http, HttpResponse } from "msw";
import { z } from "zod";

import { ApiError } from "./api-error";
import { createApiClient } from "./api-client";
import { createSearchApi } from "./contracts/search";
import { createAppQueryClient } from "./query-client";
import { server } from "../test/server";

const client = createApiClient({ baseUrl: "http://localhost" });

describe("apiClient", () => {
  it("validates a typed search response and serializes query parameters", async () => {
    server.use(
      http.get("http://localhost/api/v1/search", ({ request }) => {
        const url = new URL(request.url);
        expect(url.searchParams.get("question")).toBe("支付 重试");
        expect(url.searchParams.get("limit")).toBe("8");
        return HttpResponse.json({
          count: 1,
          items: [
            {
              evidenceType: "ARTICLE",
              sourceId: 12,
              articleKey: "payment-retry",
              conceptId: "payment-retry",
              title: "支付重试",
              content: "证据片段",
              metadataJson: null,
              sourcePaths: ["kb/payment.md"],
              score: 0.82,
            },
          ],
        });
      }),
    );

    const response = await createSearchApi(client).search({
      question: "支付 重试",
      limit: 8,
    });

    expect(response.count).toBe(1);
    expect(response.items[0]?.articleKey).toBe("payment-retry");
  });

  it("normalizes Query and Compile error envelopes", async () => {
    server.use(
      http.post("http://localhost/api/v1/query", () =>
        HttpResponse.json(
          { code: "MODEL_UNAVAILABLE", message: "模型暂不可用" },
          { status: 503 },
        ),
      ),
    );

    const request = client.post("/api/v1/query", {
      body: { question: "test" },
      schema: z.unknown(),
    });

    await expect(request).rejects.toMatchObject({
      status: 503,
      code: "MODEL_UNAVAILABLE",
      message: "模型暂不可用",
      retryable: true,
    });
  });

  it("normalizes Spring Problem Detail field errors", async () => {
    server.use(
      http.put("http://localhost/api/v1/admin/vector/config", () =>
        HttpResponse.json(
          {
            title: "Validation failed",
            status: 400,
            detail: "请求字段无效",
            errors: [
              { field: "embeddingModelProfileId", defaultMessage: "必须大于0" },
            ],
          },
          { status: 400 },
        ),
      ),
    );

    const request = client.put("/api/v1/admin/vector/config", {
      body: {},
      schema: z.unknown(),
    });

    await expect(request).rejects.toMatchObject({
      status: 400,
      code: "Validation failed",
      message: "请求字段无效",
      fieldErrors: { embeddingModelProfileId: ["必须大于0"] },
      retryable: false,
    });
  });

  it("keeps a non-JSON error body as the user-facing message", async () => {
    server.use(
      http.get(
        "http://localhost/actuator/health",
        () => new HttpResponse("Service Unavailable", { status: 503 }),
      ),
    );

    await expect(
      client.get("/actuator/health", { schema: z.unknown() }),
    ).rejects.toMatchObject({
      status: 503,
      code: "HTTP_503",
      message: "Service Unavailable",
    });
  });

  it("rejects successful responses that violate the declared contract", async () => {
    server.use(
      http.get("http://localhost/api/v1/admin/sources", () =>
        HttpResponse.json({ items: [] }),
      ),
    );

    const request = client.get("/api/v1/admin/sources", {
      schema: z.object({
        page: z.number(),
        size: z.number(),
        total: z.number(),
        items: z.array(z.unknown()),
      }),
    });

    await expect(request).rejects.toBeInstanceOf(ApiError);
    await expect(request).rejects.toMatchObject({
      status: 200,
      code: "INVALID_RESPONSE",
      retryable: false,
    });
  });

  it("maps an aborted request without claiming that the backend was cancelled", async () => {
    const abortingClient = createApiClient({
      fetchImplementation: async () => {
        throw new DOMException("aborted", "AbortError");
      },
    });

    await expect(
      abortingClient.get("/api/v1/search", { schema: z.unknown() }),
    ).rejects.toMatchObject({
      status: null,
      code: "REQUEST_ABORTED",
      message: "请求已停止等待",
      retryable: false,
    });
  });

  it("disables automatic retries for queries and mutations", () => {
    const queryClient = createAppQueryClient();

    expect(queryClient.getDefaultOptions().queries?.retry).toBe(false);
    expect(queryClient.getDefaultOptions().mutations?.retry).toBe(false);
  });
});
