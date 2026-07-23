import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router-dom";

import { AppQueryProvider } from "../../api/query-provider";
import {
  chunkRebuildResultFixture,
  retrievalAuditDetailFixture,
  retrievalAuditRunFixture,
  retrievalConfigFixture,
} from "../../test/retrieval-maintenance-fixtures";
import { server } from "../../test/server";
import RetrievalSettingsPage from "./retrieval-settings-page";

describe("retrieval settings page", () => {
  it("edits all runtime fields and keeps changes after a failed save", async () => {
    let saveBody: unknown;
    server.use(
      ...readHandlers(),
      http.put("/api/v1/admin/query/retrieval/config", async ({ request }) => {
        saveBody = await request.json();
        return HttpResponse.json({ code: "CONFIG_CHANGED", message: "检索参数已被其他操作更新" }, { status: 409 });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    const graphWeight = await screen.findByLabelText("知识图谱权重");
    await user.clear(graphWeight);
    await user.type(graphWeight, "0");
    await user.click(screen.getByLabelText("启用查询改写"));
    await user.click(screen.getByRole("button", { name: "保存参数" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("检索参数已被其他操作更新");
    expect(graphWeight).toHaveValue(0);
    expect(screen.getByText("未保存")).toBeVisible();
    expect(saveBody).toEqual(expect.objectContaining({ graphWeight: 0, rewriteEnabled: false, rrfK: 60 }));
  });

  it("validates nonnegative weights and a positive integer RRF K before writing", async () => {
    let writes = 0;
    server.use(
      ...readHandlers(),
      http.put("/api/v1/admin/query/retrieval/config", () => {
        writes += 1;
        return HttpResponse.json(retrievalConfigFixture());
      }),
    );
    const user = userEvent.setup();
    renderPage();

    const rrfK = await screen.findByLabelText("RRF K");
    fireEvent.change(rrfK, { target: { value: "0" } });
    await user.click(screen.getByRole("button", { name: "保存参数" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("RRF K 必须是大于 0 的整数");
    expect(writes).toBe(0);
  });

  it("shows latest/recent audit details, channel diagnostics, and fused hits", async () => {
    server.use(...readHandlers());
    renderPage("/settings/retrieval?view=audits&queryId=query-208");

    expect(await screen.findByRole("article", { name: "检索审计详情" })).toHaveTextContent("检索通道延迟定位");
    expect(screen.getAllByText("fts")).toHaveLength(2);
    expect(screen.getByText("41 ms")).toBeVisible();
    expect(screen.getByRole("table")).toHaveTextContent("检索审计");
    expect(screen.getByText("另有 1 条历史 run")).toBeVisible();
  });

  it("requires explicit confirmation before the synchronous chunk rebuild", async () => {
    let writes = 0;
    server.use(
      ...readHandlers(),
      http.post("/api/v1/admin/compile/rebuild-chunks", () => {
        writes += 1;
        return HttpResponse.json(chunkRebuildResultFixture());
      }),
    );
    const user = userEvent.setup();
    renderPage("/settings/retrieval?view=chunks");

    await user.click(await screen.findByRole("button", { name: "准备重建切片" }));
    const dialog = screen.getByRole("dialog", { name: "重建全部切片" });
    expect(within(dialog).getByText("全部文章与源文件")).toBeVisible();
    expect(writes).toBe(0);

    await user.click(within(dialog).getByRole("button", { name: "确认重建切片" }));
    await waitFor(() => expect(writes).toBe(1));
    expect(await screen.findByRole("status", { name: "最近切片重建结果" })).toHaveTextContent("320");
  });
});

function renderPage(initialEntry = "/settings/retrieval") {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <AppQueryProvider><RetrievalSettingsPage /></AppQueryProvider>
    </MemoryRouter>,
  );
}

function readHandlers() {
  const run = retrievalAuditRunFixture();
  return [
    http.get("/api/v1/admin/query/retrieval/config", () => HttpResponse.json(retrievalConfigFixture())),
    http.get("/api/v1/admin/query/retrieval/audits/recent", () => HttpResponse.json({ count: 1, items: [run] })),
    http.get("/api/v1/admin/query/retrieval/audits/latest", () => HttpResponse.json(retrievalAuditDetailFixture())),
  ];
}
