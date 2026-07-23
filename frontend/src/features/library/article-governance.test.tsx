import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router-dom";

import { AppQueryProvider } from "../../api/query-provider";
import {
  articleSnapshotFixture,
  correctionResponseFixture,
  lifecycleResponseFixture,
  reviewResponseFixture,
  rollbackResponseFixture,
} from "../../test/article-governance-fixtures";
import { articleDetailFixture } from "../../test/article-fixtures";
import { server } from "../../test/server";
import { ArticleGovernance } from "./article-governance";

describe("article governance", () => {
  it("requires a second confirmation and current review status before approving", async () => {
    installReadHandlers();
    let requestCount = 0;
    server.use(
      http.post("/api/v1/admin/articles/payments--fine-controller/review/approve", async ({ request }) => {
        requestCount += 1;
        expect(await request.json()).toEqual({
          sourceId: 12,
          reviewedBy: "reviewer-a",
          comment: "证据一致",
          expectedReviewStatus: "needs_human_review",
        });
        return HttpResponse.json(reviewResponseFixture());
      }),
    );
    const user = userEvent.setup();
    renderGovernance();

    await user.click(screen.getByRole("button", { name: "通过审核" }));
    const dialog = screen.getByRole("dialog", { name: "确认通过审核" });
    expect(within(dialog).getByText(/待人工复核/)).toBeVisible();
    await user.type(within(dialog).getByLabelText("复核人"), "reviewer-a");
    await user.type(within(dialog).getByLabelText("复核意见"), "证据一致");
    expect(requestCount).toBe(0);
    await user.click(within(dialog).getByRole("button", { name: "确认通过" }));

    expect(await screen.findByText("审核已通过")).toBeVisible();
    expect(screen.getByText(/审计记录 #91/)).toBeVisible();
    expect(requestCount).toBe(1);
  });

  it("keeps request-changes input visible when optimistic review status conflicts", async () => {
    installReadHandlers();
    server.use(
      http.post("/api/v1/admin/articles/payments--fine-controller/review/request-changes", async ({ request }) => {
        expect(await request.json()).toMatchObject({
          expectedReviewStatus: "needs_human_review",
          correctionSummary: "补充真实来源",
        });
        return HttpResponse.json(
          { code: "ARTICLE_REVIEW_CONFLICT", message: "review status changed" },
          { status: 409 },
        );
      }),
    );
    const user = userEvent.setup();
    renderGovernance();

    await user.click(screen.getByRole("button", { name: "要求修改" }));
    const dialog = screen.getByRole("dialog", { name: "确认要求修改" });
    await user.type(within(dialog).getByLabelText("复核人"), "reviewer-b");
    await user.type(within(dialog).getByLabelText("复核意见"), "证据不足");
    await user.type(within(dialog).getByLabelText("修正摘要"), "补充真实来源");
    await user.click(within(dialog).getByRole("button", { name: "提交修改要求" }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent("review status changed");
    expect(within(dialog).getByLabelText("修正摘要")).toHaveValue("补充真实来源");
  });

  it("records lifecycle actor and reason before archiving", async () => {
    installReadHandlers();
    server.use(
      http.post("/api/v1/admin/articles/payments--fine-controller/lifecycle/archive", async ({ request }) => {
        expect(new URL(request.url).searchParams.get("sourceId")).toBe("12");
        expect(await request.json()).toEqual({ updatedBy: "ops", reason: "停止维护" });
        return HttpResponse.json({
          ...lifecycleResponseFixture(),
          articleKey: "payments--fine-controller",
          conceptId: "fine-controller",
          title: "FineController",
        });
      }),
    );
    const user = userEvent.setup();
    renderGovernance();

    await user.click(screen.getByRole("button", { name: "归档" }));
    const dialog = screen.getByRole("dialog", { name: "确认归档" });
    expect(within(dialog).getByText("生命周期将从“生效”切换为“已归档”。")).toBeVisible();
    await user.type(within(dialog).getByLabelText("操作人"), "ops");
    await user.type(within(dialog).getByLabelText("变更原因"), "停止维护");
    await user.click(within(dialog).getByRole("button", { name: "确认归档" }));

    expect(await screen.findByText("生命周期已更新")).toBeVisible();
    expect(screen.getByText(/已切换为“已归档”/)).toBeVisible();
  });

  it("shows the requested correction and the returned content difference", async () => {
    installReadHandlers();
    server.use(
      http.post("/api/v1/admin/articles/payments--fine-controller/correct", async ({ request }) => {
        expect(await request.json()).toEqual({ correctionSummary: "修正超时配置" });
        return HttpResponse.json({
          ...correctionResponseFixture(),
          articleKey: "payments--fine-controller",
          conceptId: "fine-controller",
        });
      }),
    );
    const user = userEvent.setup();
    renderGovernance();

    await user.click(screen.getByRole("button", { name: "人工修正" }));
    const dialog = screen.getByRole("dialog", { name: "确认人工修正" });
    expect(within(dialog).getByText("当前正文")).toBeVisible();
    await user.type(within(dialog).getByLabelText("修正摘要"), "修正超时配置");
    await user.click(within(dialog).getByRole("button", { name: "执行修正" }));

    expect(await screen.findByText("人工修正已完成")).toBeVisible();
    expect(screen.getByRole("heading", { name: "修正前" })).toBeVisible();
    const correctionResult = screen.getByRole("heading", { name: "修正结果" }).closest("section");
    expect(correctionResult).toHaveTextContent("新正文");
  });

  it("compares an exact snapshot and confirms article, source and snapshot before rollback", async () => {
    installReadHandlers({ snapshots: [articleSnapshotFixture()] });
    server.use(
      http.post("/api/v1/admin/rollback/article", async ({ request }) => {
        expect(await request.json()).toEqual({
          articleId: "payments--fine-controller",
          sourceId: 12,
          snapshotId: 81,
        });
        return HttpResponse.json({
          ...rollbackResponseFixture(),
          articleKey: "payments--fine-controller",
          conceptId: "fine-controller",
        });
      }),
    );
    const user = userEvent.setup();
    renderGovernance();

    await user.click(await screen.findByRole("button", { name: "对比快照 81" }));
    expect(screen.getByRole("heading", { name: "快照 #81 对比" })).toBeVisible();
    const snapshotVersion = screen.getByRole("heading", { name: "快照版本" }).closest("section");
    expect(snapshotVersion).toHaveTextContent("旧正文");
    await user.click(screen.getByRole("button", { name: "回滚到快照 81" }));
    const dialog = screen.getByRole("dialog", { name: "确认文章回滚" });
    expect(within(dialog).getByText("payments--fine-controller")).toBeVisible();
    expect(within(dialog).getByText("#12")).toBeVisible();
    expect(within(dialog).getByText("当前版本将恢复为快照 #81，并保留回滚留痕。")).toBeVisible();
    await user.click(within(dialog).getByRole("button", { name: "确认回滚" }));

    expect(await screen.findByText("文章已回滚")).toBeVisible();
    expect(screen.getByText(/恢复快照 #81/)).toBeVisible();
  });
});

function installReadHandlers({ snapshots = [] }: { snapshots?: unknown[] } = {}) {
  server.use(
    http.get("/api/v1/admin/articles/payments--fine-controller/review/audits", () =>
      HttpResponse.json({ count: 0, items: [] }),
    ),
    http.get("/api/v1/admin/snapshot/article", () =>
      HttpResponse.json({ conceptId: "fine-controller", count: snapshots.length, items: snapshots }),
    ),
  );
}

function renderGovernance() {
  return render(
    <MemoryRouter>
      <AppQueryProvider>
        <ArticleGovernance
          article={articleDetailFixture({
            content: "# FineController\n\n当前正文",
            reviewStatus: "needs_human_review",
          })}
        />
      </AppQueryProvider>
    </MemoryRouter>,
  );
}
