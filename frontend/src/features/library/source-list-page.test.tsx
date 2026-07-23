import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import {
  MemoryRouter,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router-dom";

import { AppQueryProvider } from "../../api/query-provider";
import { server } from "../../test/server";
import SourceListPage from "./source-list-page";

describe("source list page", () => {
  it("restores URL filters and pagination after opening a source and returning", async () => {
    const user = userEvent.setup();
    const requests: Record<string, string>[] = [];
    server.use(
      http.get("/api/v1/admin/sources", ({ request }) => {
        const parameters = Object.fromEntries(new URL(request.url).searchParams);
        requests.push(parameters);
        return HttpResponse.json(
          sourcePage({
            page: Number(parameters.page),
            size: Number(parameters.size),
            total: 12,
          }),
        );
      }),
    );

    renderPage(
      "/library/sources?q=pay&status=ACTIVE&sourceType=GIT&page=2&size=10",
    );

    expect(await screen.findByRole("link", { name: "支付知识库" })).toBeVisible();
    expect(screen.getByRole("searchbox", { name: "搜索资料源" })).toHaveValue("pay");
    expect(screen.getByLabelText("状态")).toHaveValue("ACTIVE");
    expect(screen.getByLabelText("类型")).toHaveValue("GIT");
    expect(screen.getByText("第 2 / 2 页")).toBeVisible();
    expect(requests.at(-1)).toEqual({
      keyword: "pay",
      status: "ACTIVE",
      sourceType: "GIT",
      page: "2",
      size: "10",
    });

    await user.click(screen.getByRole("link", { name: "支付知识库" }));
    expect(screen.getByLabelText("location")).toHaveTextContent(
      "/library/sources/7",
    );
    await user.click(screen.getByRole("button", { name: "返回资料源" }));

    expect(await screen.findByRole("searchbox", { name: "搜索资料源" })).toHaveValue(
      "pay",
    );
    expect(screen.getByLabelText("location")).toHaveTextContent(
      "q=pay&status=ACTIVE&sourceType=GIT&page=2&size=10",
    );
  });

  it("writes filters and page changes to the URL and request", async () => {
    const user = userEvent.setup();
    let latestParameters: Record<string, string> = {};
    server.use(
      http.get("/api/v1/admin/sources", ({ request }) => {
        latestParameters = Object.fromEntries(new URL(request.url).searchParams);
        const page = Number(latestParameters.page);
        return HttpResponse.json(
          sourcePage({
            page,
            size: Number(latestParameters.size),
            total: 21,
            displayName: page === 2 ? "第二页资料" : "支付知识库",
          }),
        );
      }),
    );
    renderPage("/library/sources");

    expect(await screen.findByText("支付知识库")).toBeVisible();
    await user.type(screen.getByRole("searchbox", { name: "搜索资料源" }), "git");
    await user.selectOptions(screen.getByLabelText("状态"), "DISABLED");
    await user.selectOptions(screen.getByLabelText("类型"), "INTERNAL_MIRROR");
    await waitFor(() =>
      expect(latestParameters).toMatchObject({
        keyword: "git",
        status: "DISABLED",
        sourceType: "INTERNAL_MIRROR",
        page: "1",
        size: "20",
      }),
    );

    await user.click(screen.getByRole("button", { name: "下一页" }));
    expect(await screen.findByText("第二页资料")).toBeVisible();
    expect(screen.getByLabelText("location")).toHaveTextContent("page=2");
  });

  it("distinguishes filtered empty state and clears filters", async () => {
    const user = userEvent.setup();
    server.use(
      http.get("/api/v1/admin/sources", ({ request }) => {
        const filtered = new URL(request.url).searchParams.has("status");
        return HttpResponse.json(
          filtered
            ? { page: 1, size: 20, total: 0, items: [] }
            : sourcePage(),
        );
      }),
    );
    renderPage("/library/sources?status=ARCHIVED");

    expect(
      await screen.findByRole("heading", { name: "没有符合条件的资料源" }),
    ).toBeVisible();
    await user.click(screen.getByRole("button", { name: "清除筛选" }));
    expect(await screen.findByText("支付知识库")).toBeVisible();
    expect(screen.getByLabelText("location")).toHaveTextContent(
      "/library/sources",
    );
  });

  it("shows a stable error and retries only after an explicit action", async () => {
    const user = userEvent.setup();
    let requestCount = 0;
    server.use(
      http.get("/api/v1/admin/sources", () => {
        requestCount += 1;
        return requestCount === 1
          ? HttpResponse.json(
              { code: "SOURCE_UNAVAILABLE", message: "资料源服务暂不可用" },
              { status: 503 },
            )
          : HttpResponse.json(sourcePage());
      }),
    );
    renderPage("/library/sources");

    expect(
      await screen.findByRole("heading", { name: "资料源加载失败" }),
    ).toBeVisible();
    expect(screen.getByText("资料源服务暂不可用")).toBeVisible();
    expect(requestCount).toBe(1);
    await user.click(screen.getByRole("button", { name: "重试" }));
    expect(await screen.findByText("支付知识库")).toBeVisible();
    expect(requestCount).toBe(2);
  });
});

function renderPage(initialEntry: string) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <AppQueryProvider>
        <LocationOutput />
        <Routes>
          <Route path="/library/sources" element={<SourceListPage />} />
          <Route path="/library/sources/:sourceId" element={<DetailHarness />} />
        </Routes>
      </AppQueryProvider>
    </MemoryRouter>,
  );
}

function LocationOutput() {
  const location = useLocation();
  return (
    <output aria-label="location">
      {location.pathname}
      {location.search}
    </output>
  );
}

function DetailHarness() {
  const navigate = useNavigate();
  return (
    <button onClick={() => navigate(-1)} type="button">
      返回资料源
    </button>
  );
}

function sourcePage(
  overrides: Partial<{
    page: number;
    size: number;
    total: number;
    displayName: string;
  }> = {},
) {
  return {
    page: overrides.page ?? 1,
    size: overrides.size ?? 20,
    total: overrides.total ?? 1,
    items: [
      {
        id: 7,
        sourceCode: "payments-git",
        name: "Payments Git",
        displayName: overrides.displayName ?? "支付知识库",
        primaryDocumentTitle: "支付接入说明",
        sourceType: "GIT",
        contentProfile: "DOCUMENT",
        status: "ACTIVE",
        visibility: "NORMAL",
        defaultSyncMode: "INCREMENTAL",
        lastSyncRunId: 19,
        lastSyncStatus: "SUCCEEDED",
        lastSyncAt: "2026-07-22T20:10:00+08:00",
        updatedAt: "2026-07-22T20:10:02+08:00",
      },
    ],
  };
}
