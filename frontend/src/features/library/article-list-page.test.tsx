import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";

import { AppQueryProvider } from "../../api/query-provider";
import { articleSummaryFixture, searchHitFixture } from "../../test/article-fixtures";
import { server } from "../../test/server";
import ArticleListPage from "./article-list-page";

describe("article list page", () => {
  it("restores server filters from the URL and keeps them when opening a detail", async () => {
    const requests: Record<string, string>[] = [];
    server.use(
      http.get("/api/v1/admin/articles", ({ request }) => {
        requests.push(Object.fromEntries(new URL(request.url).searchParams));
        return HttpResponse.json({ count: 1, items: [articleSummaryFixture()] });
      }),
    );
    renderPage("/library/articles?q=FineController&sourceId=12&lifecycle=ACTIVE");

    expect(await screen.findByRole("link", { name: "FineController" })).toHaveAttribute(
      "href",
      "/library/articles/payments--fine-controller?sourceId=12",
    );
    expect(screen.getByText("1 项")).toBeVisible();
    expect(screen.getByText("src/main/java/FineController.java")).toBeVisible();
    expect(requests[0]).toEqual({
      query: "FineController",
      lifecycle: "ACTIVE",
      sourceId: "12",
    });

    await userEvent.selectOptions(screen.getByLabelText("风险"), "high");
    await waitFor(() => expect(screen.getByLabelText("location")).toHaveTextContent("riskLevel=high"));
    expect(requests.at(-1)).toMatchObject({ riskLevel: "high", sourceId: "12" });
  });

  it("uses the independent Search API in semantic mode and preserves evidence identity", async () => {
    server.use(
      http.get("/api/v1/search", ({ request }) => {
        expect(Object.fromEntries(new URL(request.url).searchParams)).toEqual({
          question: "罚金控制器",
          limit: "10",
        });
        return HttpResponse.json({ count: 1, items: [searchHitFixture()] });
      }),
    );
    renderPage("/library/articles?mode=semantic&q=罚金控制器&limit=10");

    expect(await screen.findByRole("region", { name: "语义检索结果" })).toBeVisible();
    expect(screen.getByText("相关度 0.9123")).toBeVisible();
    expect(screen.getByRole("link", { name: "FineController" })).toHaveAttribute(
      "href",
      "/library/articles/payments--fine-controller?sourceId=12",
    );
  });
});

function renderPage(initialEntry: string) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <AppQueryProvider>
        <LocationOutput />
        <Routes>
          <Route path="/library/articles" element={<ArticleListPage />} />
        </Routes>
      </AppQueryProvider>
    </MemoryRouter>,
  );
}

function LocationOutput() {
  const location = useLocation();
  return <output aria-label="location">{location.pathname}{location.search}</output>;
}
