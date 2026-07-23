import { http, HttpResponse } from "msw";

import { articleDetailFixture, articleSummaryFixture } from "../../test/article-fixtures";
import { server } from "../../test/server";
import { createApiClient } from "../api-client";
import { articleDetailSchema, createArticlesApi } from "./articles";

const client = createApiClient({ baseUrl: "http://localhost" });

describe("articles contract", () => {
  it("maps every server-side filter without inventing pagination", async () => {
    server.use(
      http.get("http://localhost/api/v1/admin/articles", ({ request }) => {
        expect(Object.fromEntries(new URL(request.url).searchParams)).toEqual({
          query: "FineController",
          lifecycle: "ACTIVE",
          sourceId: "12",
          reviewStatus: "passed",
          riskLevel: "low",
          riskReason: "citation_missing",
          isHotspot: "true",
          requiresResultVerification: "false",
        });
        return HttpResponse.json({ count: 1, items: [articleSummaryFixture()] });
      }),
    );

    const result = await createArticlesApi(client).list({
      query: "FineController",
      lifecycle: "ACTIVE",
      sourceId: 12,
      reviewStatus: "passed",
      riskLevel: "low",
      riskReason: "citation_missing",
      isHotspot: true,
      requiresResultVerification: false,
    });

    expect(result.count).toBe(1);
    expect(result.items[0]?.sourcePaths).toEqual(["src/main/java/FineController.java"]);
  });

  it("encodes the article identity and scopes ambiguous details by source", async () => {
    server.use(
      http.get(
        "http://localhost/api/v1/admin/articles/concept%2Fwith%20spaces",
        ({ request }) => {
          expect(new URL(request.url).searchParams.get("sourceId")).toBe("12");
          return HttpResponse.json(articleDetailFixture({ articleKey: "concept/with spaces" }));
        },
      ),
    );

    const result = await createArticlesApi(client).detail("concept/with spaces", 12);

    expect(result.dependsOn).toEqual(["fine-service"]);
    expect(result.related).toHaveLength(2);
  });

  it("rejects malformed traceability fields", () => {
    expect(
      articleDetailSchema.safeParse(
        articleDetailFixture({ sourcePaths: ["src/FineController.java"] }),
      ).success,
    ).toBe(true);
    expect(
      articleDetailSchema.safeParse({ ...articleDetailFixture(), dependsOn: [12] }).success,
    ).toBe(false);
  });
});
