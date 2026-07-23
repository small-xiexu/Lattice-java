import { http, HttpResponse } from "msw";

import { searchHitFixture } from "../../test/article-fixtures";
import { server } from "../../test/server";
import { createApiClient } from "../api-client";
import { createSearchApi } from "./search";

describe("search contract", () => {
  it("uses question and bounded limit and preserves evidence identities", async () => {
    const client = createApiClient({ baseUrl: "http://localhost" });
    server.use(
      http.get("http://localhost/api/v1/search", ({ request }) => {
        expect(Object.fromEntries(new URL(request.url).searchParams)).toEqual({
          question: "罚金控制器",
          limit: "20",
        });
        return HttpResponse.json({ count: 1, items: [searchHitFixture()] });
      }),
    );

    const result = await createSearchApi(client).search({
      question: "罚金控制器",
      limit: 20,
    });

    expect(result.items[0]).toMatchObject({
      evidenceType: "ARTICLE",
      articleKey: "payments--fine-controller",
      conceptId: "fine-controller",
      sourceId: 12,
    });
  });
});
