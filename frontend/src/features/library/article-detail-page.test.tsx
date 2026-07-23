import { render, screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { MemoryRouter, Route, Routes } from "react-router-dom";

import { AppQueryProvider } from "../../api/query-provider";
import {
  articleDetailFixture,
  articleSummaryFixture,
} from "../../test/article-fixtures";
import { server } from "../../test/server";
import ArticleDetailPage from "./article-detail-page";

describe("article detail page", () => {
  it("renders source paths and concept relationships from the real detail projection", async () => {
    server.use(
      http.get("/api/v1/admin/articles/payments--fine-controller", ({ request }) => {
        expect(new URL(request.url).searchParams.get("sourceId")).toBe("12");
        return HttpResponse.json(articleDetailFixture());
      }),
      http.get("/api/v1/admin/articles", () =>
        HttpResponse.json({
          count: 1,
          items: [
            articleSummaryFixture({
              articleKey: "payments--fine-service",
              conceptId: "fine-service",
              title: "FineService",
            }),
          ],
        }),
      ),
    );
    renderPage();

    expect(await screen.findByRole("heading", { level: 1, name: "FineController" })).toBeVisible();
    expect(screen.getByRole("article", { name: "文章正文" })).toHaveTextContent("正文内容");
    expect(screen.getByRole("article", { name: "文章正文" })).not.toHaveTextContent("title: FineController");
    expect(screen.getByRole("link", { name: /src\/main\/java\/FineController.java/ })).toHaveAttribute(
      "href",
      "/library/sources/12?view=files",
    );
    expect(await screen.findByRole("link", { name: "fine-service" })).toHaveAttribute(
      "href",
      "/library/articles/payments--fine-service?sourceId=12",
    );
    expect(screen.getByText("fine-calculation-request")).toBeVisible();
    expect(await screen.findAllByText("未收录")).toHaveLength(2);
    expect(screen.getByText("conceptId").nextSibling).toHaveTextContent("fine-controller");
    expect(screen.getByRole("link", { name: "返回文章列表" })).toHaveAttribute(
      "href",
      "/library/articles?q=FineController&sourceId=12",
    );
  });

  it("recovers a failed detail request without losing the deep link", async () => {
    let requestCount = 0;
    server.use(
      http.get("/api/v1/admin/articles/payments--fine-controller", () => {
        requestCount += 1;
        return requestCount === 1
          ? HttpResponse.json({ code: "ARTICLE_UNAVAILABLE", message: "文章服务暂不可用" }, { status: 503 })
          : HttpResponse.json(articleDetailFixture());
      }),
      http.get("/api/v1/admin/articles", () =>
        HttpResponse.json({ count: 0, items: [] }),
      ),
    );
    renderPage();

    expect(await screen.findByText("文章服务暂不可用")).toBeVisible();
    await screen.getByRole("button", { name: "重试" }).click();
    expect(await screen.findByRole("heading", { level: 1, name: "FineController" })).toBeVisible();
    expect(requestCount).toBe(2);
  });
});

function renderPage() {
  server.use(
    http.get("/api/v1/admin/articles/payments--fine-controller/review/audits", () =>
      HttpResponse.json({ count: 0, items: [] }),
    ),
    http.get("/api/v1/admin/snapshot/article", () =>
      HttpResponse.json({ conceptId: "fine-controller", count: 0, items: [] }),
    ),
  );
  return render(
    <MemoryRouter
      initialEntries={[
        {
          pathname: "/library/articles/payments--fine-controller",
          search: "?sourceId=12",
          state: { from: "/library/articles?q=FineController&sourceId=12" },
        },
      ]}
    >
      <AppQueryProvider>
        <Routes>
          <Route path="/library/articles/:articleKey" element={<ArticleDetailPage />} />
        </Routes>
      </AppQueryProvider>
    </MemoryRouter>,
  );
}
