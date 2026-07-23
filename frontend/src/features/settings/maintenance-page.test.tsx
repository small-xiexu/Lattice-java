import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router-dom";

import { AppQueryProvider } from "../../api/query-provider";
import {
  repoBaselineResultFixture,
  repoDiffFixture,
  repoRollbackResultFixture,
  repoSnapshotFixture,
  vaultExportResultFixture,
  vaultSyncResultFixture,
} from "../../test/retrieval-maintenance-fixtures";
import { server } from "../../test/server";
import MaintenancePage from "./maintenance-page";

describe("repository maintenance page", () => {
  it("keeps legacy snapshots visible but blocks diff and rollback without a Git commit", async () => {
    server.use(historyHandler([repoSnapshotFixture({ id: 2, gitCommit: null })]));
    renderPage();

    expect(await screen.findByText("该历史记录没有绑定 Git commit，只能用于审计，不能执行差异或整库回滚。")).toBeVisible();
    expect(screen.getByRole("button", { name: "预览差异" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "准备整库回滚" })).toBeDisabled();
  });

  it("previews diff before rollback and requires the target snapshot id", async () => {
    let rollbackBody: unknown;
    let rollbackWrites = 0;
    server.use(
      historyHandler([repoSnapshotFixture()]),
      http.get("/api/v1/admin/snapshot/repo/12/diff", ({ request }) => {
        expect(new URL(request.url).searchParams.get("vaultDir")).toBe("/tmp/lattice-vault");
        return HttpResponse.json(repoDiffFixture());
      }),
      http.post("/api/v1/admin/rollback/repo", async ({ request }) => {
        rollbackWrites += 1;
        rollbackBody = await request.json();
        return HttpResponse.json(repoRollbackResultFixture());
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.type(await screen.findByLabelText("Vault 仓库绝对路径"), "/tmp/lattice-vault");
    await user.click(screen.getByRole("button", { name: "预览差异" }));
    expect(await screen.findByRole("region", { name: "仓库差异预览" })).toHaveTextContent("2 个文件");
    await user.click(screen.getByRole("button", { name: "准备整库回滚" }));

    const dialog = screen.getByRole("dialog", { name: "整库回滚" });
    await user.type(within(dialog).getByLabelText("输入快照 ID 12 确认"), "11");
    await user.click(within(dialog).getByRole("button", { name: "确认整库回滚" }));
    expect(await within(dialog).findByRole("alert")).toHaveTextContent("请输入目标快照 ID：12");
    expect(rollbackWrites).toBe(0);

    const confirmation = within(dialog).getByLabelText("输入快照 ID 12 确认");
    await user.clear(confirmation);
    await user.type(confirmation, "12");
    await user.click(within(dialog).getByRole("button", { name: "确认整库回滚" }));

    await waitFor(() => expect(rollbackWrites).toBe(1));
    expect(rollbackBody).toEqual({ snapshotId: 12, vaultDir: "/tmp/lattice-vault" });
    expect(await screen.findByText("整库回滚完成")).toBeVisible();
  });

  it("confirms baseline creation and reports exported file counts", async () => {
    let baselineBody: unknown;
    server.use(
      historyHandler([repoSnapshotFixture()]),
      http.post("/api/v1/admin/snapshot/repo/baseline", async ({ request }) => {
        baselineBody = await request.json();
        return HttpResponse.json(repoBaselineResultFixture());
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.type(await screen.findByLabelText("Vault 仓库绝对路径"), "/tmp/lattice-vault");
    await user.type(screen.getByLabelText("Baseline 描述"), "发布前基线");
    await user.click(screen.getByRole("button", { name: "准备创建基线" }));
    const dialog = screen.getByRole("dialog", { name: "创建仓库基线" });
    expect(within(dialog).getByText("/tmp/lattice-vault")).toBeVisible();
    await user.click(within(dialog).getByRole("button", { name: "确认创建基线" }));

    await waitFor(() => expect(baselineBody).toEqual({ vaultDir: "/tmp/lattice-vault", description: "发布前基线" }));
    expect(await screen.findByText("基线创建完成")).toBeVisible();
    expect(screen.getByText(/写入 4 \/ 跳过 36 \/ 删除 1/)).toBeVisible();
  });

  it("shows the Vault export target before sending the request", async () => {
    let exportBody: unknown;
    let exportWrites = 0;
    server.use(
      historyHandler([repoSnapshotFixture()]),
      http.post("/api/v1/admin/vault/export", async ({ request }) => {
        exportWrites += 1;
        exportBody = await request.json();
        return HttpResponse.json(vaultExportResultFixture());
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.type(await screen.findByLabelText("Vault 仓库绝对路径"), "/tmp/lattice-vault");
    await user.click(screen.getByRole("button", { name: "准备导出" }));
    const dialog = screen.getByRole("dialog", { name: "导出 Vault" });
    expect(within(dialog).getByText("/tmp/lattice-vault")).toBeVisible();
    expect(exportWrites).toBe(0);
    await user.click(within(dialog).getByRole("button", { name: "确认导出" }));

    await waitFor(() => expect(exportWrites).toBe(1));
    expect(exportBody).toEqual({ vaultDir: "/tmp/lattice-vault" });
    expect(await screen.findByText("Vault 导出完成")).toBeVisible();
  });

  it("requires a safe-sync conflict report and the complete target before forcing sync", async () => {
    const syncBodies: unknown[] = [];
    server.use(
      historyHandler([repoSnapshotFixture()]),
      http.post("/api/v1/admin/vault/sync", async ({ request }) => {
        const body = await request.json();
        syncBodies.push(body);
        return HttpResponse.json(vaultSyncResultFixture({
          syncedFiles: body && typeof body === "object" && "force" in body && body.force ? 1 : 0,
          conflicts: body && typeof body === "object" && "force" in body && body.force ? [] : [{
            filePath: "concepts/retrieval.md",
            reason: "database changed after export",
            manifestHash: "manifest123456",
            currentDbHash: "database123456",
            currentFileHash: "file123456",
          }],
          conflictCount: body && typeof body === "object" && "force" in body && body.force ? 0 : 1,
        }));
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.type(await screen.findByLabelText("Vault 仓库绝对路径"), "/tmp/lattice-vault");
    const forceButton = screen.getByRole("button", { name: "准备强制同步" });
    expect(forceButton).toBeDisabled();
    await user.click(screen.getByRole("button", { name: "安全同步" }));
    await user.click(within(screen.getByRole("dialog", { name: "安全同步 Vault" })).getByRole("button", { name: "确认安全同步" }));

    expect(await screen.findByRole("region", { name: "Vault 同步结果" })).toHaveTextContent("1 个冲突");
    expect(forceButton).toBeEnabled();
    await user.click(forceButton);
    const dialog = screen.getByRole("dialog", { name: "强制同步 Vault" });
    const confirmation = within(dialog).getByLabelText("输入完整目标路径确认");
    await user.type(confirmation, "/tmp/other-vault");
    await user.click(within(dialog).getByRole("button", { name: "确认强制同步" }));
    expect(await within(dialog).findByRole("alert")).toHaveTextContent("请输入完整目标路径：/tmp/lattice-vault");
    expect(syncBodies).toHaveLength(1);

    await user.clear(confirmation);
    await user.type(confirmation, "/tmp/lattice-vault");
    await user.click(within(dialog).getByRole("button", { name: "确认强制同步" }));
    await waitFor(() => expect(syncBodies).toHaveLength(2));
    expect(syncBodies).toEqual([
      { vaultDir: "/tmp/lattice-vault", force: false },
      { vaultDir: "/tmp/lattice-vault", force: true },
    ]);
  });
});

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/settings/maintenance"]}>
      <AppQueryProvider><MaintenancePage /></AppQueryProvider>
    </MemoryRouter>,
  );
}

function historyHandler(items: ReturnType<typeof repoSnapshotFixture>[]) {
  return http.get("/api/v1/admin/snapshot/repo", () => HttpResponse.json({ count: items.length, items }));
}
