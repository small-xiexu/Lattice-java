import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router-dom";

import { App } from "./app";
import { processingTaskListFixture } from "./test/activity-fixtures";
import {
  documentParsePolicyFixture,
  documentParseProviderFixture,
} from "./test/document-parse-settings-fixtures";
import { llmModelFixture } from "./test/llm-settings-fixtures";
import {
  repoSnapshotFixture,
  retrievalAuditRunFixture,
  retrievalConfigFixture,
} from "./test/retrieval-maintenance-fixtures";
import { server } from "./test/server";
import { overviewFixture } from "./test/quality-fixtures";
import { vectorConfigFixture, vectorStatusFixture } from "./test/vector-settings-fixtures";

const ROUTE_CASES = [
  ["/ask", "问答与研究"],
  ["/library/sources", "资料源"],
  ["/library/sources/42", "资料源详情"],
  ["/library/articles", "知识文章"],
  ["/library/articles/payment-retry", "文章详情"],
  ["/library/quality", "知识质量"],
  ["/activity", "处理中心"],
  ["/reviews", "人工审核"],
  ["/feedback", "结果反馈"],
  ["/settings/models", "模型与绑定"],
  ["/settings/vector", "向量索引"],
  ["/settings/parsing", "文档解析"],
  ["/settings/retrieval", "检索参数"],
  ["/settings/maintenance", "系统维护"],
  ["/developer", "开发者接入"],
] as const;

describe("App", () => {
  beforeEach(() => {
    server.use(
      http.get("/api/v1/admin/overview", () => HttpResponse.json(overviewFixture())),
    );
  });

  afterEach(() => cleanup());

  it.each(ROUTE_CASES)("renders %s as %s", async (path, title) => {
    server.use(
      http.get("/api/v1/admin/processing-tasks", () =>
        HttpResponse.json(processingTaskListFixture([])),
      ),
      http.get("/api/v1/admin/compile/review-queue", () =>
        HttpResponse.json({ total: 0, items: [] }),
      ),
      ...vectorRouteHandlers(),
      ...documentParseRouteHandlers(),
      ...retrievalRouteHandlers(),
      ...maintenanceRouteHandlers(),
      ...developerRouteHandlers(),
    );
    render(
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>,
    );

    expect(
      await screen.findByRole("heading", { level: 1, name: title }, { timeout: 3000 }),
    ).toBeInTheDocument();
  });

  it("marks the current navigation item as active", async () => {
    render(
      <MemoryRouter initialEntries={["/library/quality"]}>
        <App />
      </MemoryRouter>,
    );

    await screen.findByRole("heading", { name: "知识质量" });
    expect(screen.getByRole("link", { name: "知识质量" })).toHaveClass(
      "is-active",
    );
  });

  it("shows the real overview in the shell and keeps navigation available on failure", async () => {
    const firstRender = render(
      <MemoryRouter initialEntries={["/ask"]}>
        <App />
      </MemoryRouter>,
    );
    expect(await screen.findByRole("status", { name: "文章 12 · 源文件 7" })).toBeVisible();
    firstRender.unmount();

    server.use(
      http.get("/api/v1/admin/overview", () =>
        HttpResponse.json({ code: "OVERVIEW_FAILED", message: "overview failed" }, { status: 503 }),
      ),
    );
    render(
      <MemoryRouter initialEntries={["/ask"]}>
        <App />
      </MemoryRouter>,
    );
    expect(await screen.findByRole("status", { name: "知识状态不可用" })).toBeVisible();
    expect(screen.getByRole("heading", { name: "问答与研究" })).toBeVisible();
  });

  it("opens the page palette and navigates by keyboard command", async () => {
    server.use(...vectorRouteHandlers());
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={["/ask"]}>
        <App />
      </MemoryRouter>,
    );

    await screen.findByRole("heading", { name: "问答与研究" });
    await user.keyboard("{Control>}k{/Control}");
    await user.type(screen.getByRole("textbox", { name: "搜索页面" }), "向量");
    await user.click(screen.getByRole("button", { name: "向量索引" }));

    expect(
      await screen.findByRole("heading", { name: "向量索引" }),
    ).toBeInTheDocument();
  });
});

function vectorRouteHandlers() {
  return [
    http.get("/api/v1/admin/vector/config", () => HttpResponse.json(vectorConfigFixture())),
    http.get("/api/v1/admin/vector/status", () => HttpResponse.json(vectorStatusFixture())),
    http.get("/api/v1/admin/llm/models", () => HttpResponse.json({
      count: 1,
      items: [llmModelFixture({ id: 2, modelCode: "embedding-main", modelKind: "EMBEDDING", expectedDimensions: 2000 })],
    })),
  ];
}

function documentParseRouteHandlers() {
  return [
    http.get("/api/v1/admin/document-parse/providers", () => HttpResponse.json({
      count: 1,
      items: [documentParseProviderFixture()],
    })),
    http.get("/api/v1/admin/document-parse/connections", () => HttpResponse.json({ count: 0, items: [] })),
    http.get("/api/v1/admin/document-parse/policies/default", () => HttpResponse.json(documentParsePolicyFixture({
      id: null,
      imageConnectionId: null,
      scannedPdfConnectionId: null,
      createdAt: null,
      createdBy: null,
      updatedAt: null,
      updatedBy: null,
    }))),
  ];
}

function retrievalRouteHandlers() {
  const run = retrievalAuditRunFixture();
  return [
    http.get("/api/v1/admin/query/retrieval/config", () => HttpResponse.json(retrievalConfigFixture())),
    http.get("/api/v1/admin/query/retrieval/audits/recent", () => HttpResponse.json({ count: 1, items: [run] })),
  ];
}

function maintenanceRouteHandlers() {
  return [
    http.get("/api/v1/admin/snapshot/repo", () => HttpResponse.json({ count: 1, items: [repoSnapshotFixture()] })),
  ];
}

function developerRouteHandlers() {
  return [
    http.get("/actuator/health", () => HttpResponse.json({ status: "UP" })),
  ];
}
