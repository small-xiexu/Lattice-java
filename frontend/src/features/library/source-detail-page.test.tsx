import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";

import { AppQueryProvider } from "../../api/query-provider";
import {
  sourceDetailFixture,
  sourceRunFixture,
  sourceValidationFixture,
} from "../../test/source-import-fixtures";
import { server } from "../../test/server";
import SourceDetailPage from "./source-detail-page";

describe("source detail page", () => {
  it("deep-links to server runs, files and redacted configuration without invented batches", async () => {
    installBaseHandlers({
      detail: sourceDetailFixture({
        configJson: '{"remoteUrl":"https://git.example.com/docs.git","token":"top-secret"}',
      }),
    });
    renderPage("/library/sources/12?view=runs");

    expect(await screen.findByRole("heading", { name: "Payments Docs" })).toBeVisible();
    expect(screen.getByText("运行 #33")).toBeVisible();
    expect(screen.getByText("运行态：等待中")).toBeVisible();
    expect(screen.getByRole("link", { name: "查看处理任务" })).toHaveAttribute(
      "href",
      "/activity?kind=source-run&id=33",
    );
    expect(screen.queryByText(/批次/)).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("tab", { name: /文件 1/ }));
    expect(await screen.findByText("docs/readme.md")).toBeVisible();
    await userEvent.click(screen.getByText("查看预览"));
    expect(screen.getByText("# Readme")).toBeVisible();

    await userEvent.click(screen.getByRole("tab", { name: "配置" }));
    const configuration = screen.getByText((_, element) =>
      element?.tagName === "PRE" &&
      Boolean(element.textContent?.includes("https://git.example.com/docs.git")),
    );
    expect(configuration).toBeVisible();
    expect(document.body).not.toHaveTextContent("top-secret");
    expect(configuration).toHaveTextContent('"token": "***"');
    expect(screen.getByLabelText("location")).toHaveTextContent("view=config");
  });

  it("validates and submits a materialized source sync before exposing the real run", async () => {
    const user = userEvent.setup();
    let validationCount = 0;
    let syncCount = 0;
    installBaseHandlers();
    server.use(
      http.post("/api/v1/admin/sources/12/validate", () => {
        validationCount += 1;
        return HttpResponse.json(sourceValidationFixture());
      }),
      http.post("/api/v1/admin/sources/12/sync", () => {
        syncCount += 1;
        return HttpResponse.json(sourceRunFixture({ runId: 44, displayStatusLabel: "进行中" }));
      }),
    );
    renderPage("/library/sources/12");
    await screen.findByRole("heading", { name: "Payments Docs" });

    await user.click(screen.getByRole("button", { name: "同步资料" }));

    expect(await screen.findByText("同步已提交")).toBeVisible();
    expect(screen.getByText("#44")).toBeVisible();
    expect(screen.getByRole("link", { name: "查看处理任务" })).toHaveAttribute(
      "href",
      "/activity?kind=source-run&id=44",
    );
    expect(validationCount).toBe(1);
    expect(syncCount).toBe(1);
  });

  it("requires explicit confirmation before archiving and preserves typed Git configuration", async () => {
    const user = userEvent.setup();
    let current = sourceDetailFixture({
      configJson: '{"remoteUrl":"https://git.example.com/docs.git","branch":"main"}',
    });
    let patchBody: Record<string, unknown> = {};
    installBaseHandlers({ detail: () => current });
    server.use(
      http.get("/api/v1/admin/source-credentials", () => HttpResponse.json([])),
      http.patch("/api/v1/admin/sources/12", async ({ request }) => {
        patchBody = (await request.json()) as Record<string, unknown>;
        current = sourceDetailFixture({
          name: "Payments Archive",
          displayName: "Payments Archive",
          status: "ARCHIVED",
          configJson: '{"remoteUrl":"https://git.example.com/docs.git","branch":"release"}',
        });
        return HttpResponse.json(current);
      }),
    );
    renderPage("/library/sources/12");
    await screen.findByRole("heading", { name: "Payments Docs" });
    await user.click(screen.getByRole("button", { name: "编辑" }));

    await user.clear(screen.getByLabelText("名称"));
    await user.type(screen.getByLabelText("名称"), "Payments Archive");
    await user.clear(screen.getByLabelText("分支"));
    await user.type(screen.getByLabelText("分支"), "release");
    await user.selectOptions(screen.getByLabelText("状态"), "ARCHIVED");
    expect(screen.getByRole("button", { name: "保存设置" })).toBeDisabled();
    await user.click(
      screen.getByRole("checkbox", { name: /确认归档后该资料源不能直接恢复/ }),
    );
    await user.click(screen.getByRole("button", { name: "保存设置" }));

    expect(await screen.findByText("资料源设置已保存")).toBeVisible();
    expect(patchBody).toMatchObject({
      name: "Payments Archive",
      status: "ARCHIVED",
      configJson: {
        remoteUrl: "https://git.example.com/docs.git",
        branch: "release",
      },
    });
  });

  it("updates an upload source with selected files and recovers a failed files request", async () => {
    const user = userEvent.setup();
    let filesRequestCount = 0;
    let uploadCount = 0;
    installBaseHandlers({
      detail: sourceDetailFixture({
        sourceType: "UPLOAD",
        configJson: "{}",
      }),
      files: () => {
        filesRequestCount += 1;
        return filesRequestCount === 1
          ? HttpResponse.json(
              { code: "FILES_UNAVAILABLE", message: "文件服务暂不可用" },
              { status: 503 },
            )
          : HttpResponse.json([sourceFileFixture()]);
      },
    });
    server.use(
      http.post("/api/v1/admin/uploads", () => {
        uploadCount += 1;
        return HttpResponse.json(sourceRunFixture({ sourceType: "UPLOAD", runId: 45 }));
      }),
    );
    renderPage("/library/sources/12");

    expect(await screen.findByText("文件服务暂不可用")).toBeVisible();
    await user.click(screen.getByRole("button", { name: "重试" }));
    expect(await screen.findByText("docs/readme.md")).toBeVisible();
    await user.click(screen.getByRole("button", { name: "更新文件" }));
    await user.upload(
      screen.getByLabelText("选择文件", { exact: true }),
      new File(["# Updated"], "updated.md", { type: "text/markdown" }),
    );
    await user.click(screen.getByRole("button", { name: "提交更新" }));

    expect(await screen.findByText("更新文件已提交")).toBeVisible();
    expect(screen.getByText("#45")).toBeVisible();
    expect(uploadCount).toBe(1);
    expect(filesRequestCount).toBeGreaterThanOrEqual(2);
  });

  it("rejects a malformed deep-link id without issuing API requests", async () => {
    renderPage("/library/sources/not-a-number");

    expect(await screen.findByRole("heading", { name: "无法识别资料源" })).toBeVisible();
    expect(screen.getByText("资料源编号必须是正整数")).toBeVisible();
  });
});

interface HandlerOverrides {
  detail?: Record<string, unknown> | (() => Record<string, unknown>);
  files?: () => Response;
  runs?: Record<string, unknown>[];
}

function installBaseHandlers(overrides: HandlerOverrides = {}) {
  server.use(
    http.get("/api/v1/admin/sources/12", () =>
      HttpResponse.json(
        typeof overrides.detail === "function"
          ? overrides.detail()
          : (overrides.detail ?? sourceDetailFixture()),
      ),
    ),
    http.get("/api/v1/admin/sources/12/files", () =>
      overrides.files ? overrides.files() : HttpResponse.json([sourceFileFixture()]),
    ),
    http.get("/api/v1/admin/sources/12/runs", () =>
      HttpResponse.json(overrides.runs ?? [sourceRunFixture()]),
    ),
  );
}

function sourceFileFixture() {
  return {
    id: 41,
    sourceId: 12,
    relativePath: "docs/readme.md",
    format: "md",
    fileSize: 128,
    parseMode: "text_read",
    parseProvider: "filesystem",
    contentPreview: "# Readme",
  };
}

function renderPage(initialEntry: string) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <AppQueryProvider>
        <LocationOutput />
        <Routes>
          <Route path="/library/sources/:sourceId" element={<SourceDetailPage />} />
          <Route path="/library/sources" element={<div>资料源列表</div>} />
        </Routes>
      </AppQueryProvider>
    </MemoryRouter>,
  );
}

function LocationOutput() {
  const location = useLocation();
  return <output aria-label="location">{location.pathname}{location.search}</output>;
}
