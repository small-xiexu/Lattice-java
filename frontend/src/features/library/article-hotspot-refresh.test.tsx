import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse, delay } from "msw";

import { AppQueryProvider } from "../../api/query-provider";
import { hotspotResponseFixture } from "../../test/article-governance-fixtures";
import { server } from "../../test/server";
import { ArticleHotspotRefresh } from "./article-hotspot-refresh";

describe("article hotspot refresh", () => {
  it("shows global impact, prevents duplicate submission and returns candidate counts", async () => {
    let requestCount = 0;
    server.use(
      http.post("/api/v1/admin/articles/hotspots/refresh", async ({ request }) => {
        requestCount += 1;
        expect(await request.json()).toEqual({ heatScoreThreshold: 3, limit: 200 });
        await delay(40);
        return HttpResponse.json(hotspotResponseFixture());
      }),
    );
    const user = userEvent.setup();
    render(
      <AppQueryProvider>
        <ArticleHotspotRefresh />
      </AppQueryProvider>,
    );

    await user.click(screen.getByRole("button", { name: "刷新热点" }));
    const dialog = screen.getByRole("dialog", { name: "确认刷新文章热点" });
    expect(within(dialog).getByText("全部文章使用统计")).toBeVisible();
    const confirm = within(dialog).getByRole("button", { name: "确认刷新" });
    await user.dblClick(confirm);

    expect(await screen.findByText(/热点刷新完成/)).toBeVisible();
    expect(screen.getByText(/重建 4 条统计，命中 1 个候选，更新 1 篇文章/)).toBeVisible();
    expect(screen.getByText("article-alpha")).toBeVisible();
    expect(requestCount).toBe(1);
  });
});
