import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router-dom";

import { AppQueryProvider } from "../../api/query-provider";
import {
  compileReviewConfigFixture,
  compileReviewItemFixture,
  pendingQueryItemFixture,
} from "../../test/review-fixtures";
import { server } from "../../test/server";
import ReviewsPage from "./reviews-page";

describe("reviews page", () => {
  it("filters an activity deep link by job and shows the selected draft evidence", async () => {
    const matching = compileReviewItemFixture();
    const other = compileReviewItemFixture({ id: 7, jobId: "job-other", title: "Other draft" });
    server.use(
      http.get("/api/v1/admin/compile/review-queue", () =>
        HttpResponse.json({ total: 2, items: [matching, other] }),
      ),
      http.get("/api/v1/admin/compile/review-queue/6", () => HttpResponse.json(matching)),
    );

    renderReviews("/reviews?jobId=job-review-6");

    expect(await screen.findByText("Payment retry policy")).toBeVisible();
    expect(screen.queryByText("Other draft")).not.toBeInTheDocument();
    expect(await screen.findByRole("heading", { name: "审查问题" })).toBeVisible();
    expect(screen.getByText("Missing source")).toBeVisible();
    expect(screen.getByLabelText("待审核草稿正文")).toHaveTextContent("Review draft content");
  });

  it("does not approve until confirmation and sends the current status", async () => {
    let item = compileReviewItemFixture();
    let actionCount = 0;
    let requestBody: unknown;
    server.use(
      http.get("/api/v1/admin/compile/review-queue", ({ request }) => {
        const status = new URL(request.url).searchParams.get("status");
        return HttpResponse.json({
          total: status === item.reviewStatus ? 1 : 0,
          items: status === item.reviewStatus ? [item] : [],
        });
      }),
      http.get("/api/v1/admin/compile/review-queue/6", () => HttpResponse.json(item)),
      http.post("/api/v1/admin/compile/review-queue/6/approve", async ({ request }) => {
        actionCount += 1;
        requestBody = await request.json();
        item = compileReviewItemFixture({
          reviewStatus: "published",
          reviewedBy: "admin",
          reviewedAt: "2026-07-22T10:05:00Z",
          publishedArticleKey: "payments-docs--payment-retry",
        });
        return HttpResponse.json({
          item,
          previousReviewStatus: "needs_human_review",
          auditId: 92,
        });
      }),
    );
    const user = userEvent.setup();
    renderReviews("/reviews?id=6");

    await user.click(await screen.findByRole("button", { name: "通过并发布" }));
    const dialog = screen.getByRole("dialog", { name: "确认发布审核草稿" });
    expect(actionCount).toBe(0);
    expect(within(dialog).getByText(/提交时校验状态仍为 needs_human_review/)).toBeVisible();
    await user.click(within(dialog).getByRole("button", { name: "确认通过并发布" }));

    expect(await screen.findByText("草稿已发布")).toBeVisible();
    expect(actionCount).toBe(1);
    expect(requestBody).toEqual({
      reviewedBy: "admin",
      comment: "",
      expectedReviewStatus: "needs_human_review",
    });
  });

  it("reloads the latest item after a concurrent status change", async () => {
    let item = compileReviewItemFixture();
    server.use(
      http.get("/api/v1/admin/compile/review-queue", () =>
        HttpResponse.json({ total: 1, items: [item] }),
      ),
      http.get("/api/v1/admin/compile/review-queue/6", () => HttpResponse.json(item)),
      http.post("/api/v1/admin/compile/review-queue/6/reject", () => {
        item = compileReviewItemFixture({
          reviewStatus: "published",
          publishedArticleKey: "payments-docs--payment-retry",
        });
        return HttpResponse.json(
          { code: "COMPILE_EXECUTION_FAILED", message: "compile review queue status changed" },
          { status: 500 },
        );
      }),
    );
    const user = userEvent.setup();
    renderReviews("/reviews?id=6");

    await user.click(await screen.findByRole("button", { name: "驳回" }));
    const dialog = screen.getByRole("dialog", { name: "确认驳回审核草稿" });
    await user.type(within(dialog).getByLabelText("驳回原因"), "source mismatch");
    await user.click(within(dialog).getByRole("button", { name: "确认驳回" }));

    expect(await screen.findByText("审核操作失败，已重新加载最新状态")).toBeVisible();
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(screen.getAllByText("已发布").some((element) => element.matches("span"))).toBe(true);
  });

  it("validates and confirms policy changes while preserving a failed draft", async () => {
    const config = compileReviewConfigFixture();
    let putCount = 0;
    let requestBody: unknown;
    server.use(
      http.get("/api/v1/admin/compile/review/config", () => HttpResponse.json(config)),
      http.put("/api/v1/admin/compile/review/config", async ({ request }) => {
        putCount += 1;
        requestBody = await request.json();
        return HttpResponse.json(
          { code: "CONFIG_SAVE_FAILED", message: "database unavailable" },
          { status: 500 },
        );
      }),
    );
    const user = userEvent.setup();
    renderReviews("/reviews?view=policy");

    const rounds = await screen.findByRole("spinbutton", { name: /最大自动修复轮次/ });
    await user.clear(rounds);
    await user.type(rounds, "3");
    await user.click(screen.getByRole("button", { name: "保存策略" }));
    expect(putCount).toBe(0);
    const dialog = screen.getByRole("dialog", { name: "确认更新编译审核策略" });
    await user.click(within(dialog).getByRole("button", { name: "确认保存并立即生效" }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent("database unavailable");
    expect(putCount).toBe(1);
    expect(requestBody).toMatchObject({ maxFixRounds: 3, operator: "admin" });
    await user.click(within(dialog).getByRole("button", { name: "取消" }));
    expect(screen.getByRole("spinbutton", { name: /最大自动修复轮次/ })).toHaveValue(3);
    expect(screen.getByText("策略保存失败，表单内容已保留")).toBeVisible();
  });

  it("shows pending query evidence and filters the full client-side list", async () => {
    const matching = pendingQueryItemFixture();
    const other = pendingQueryItemFixture({
      queryId: "query-pending-2",
      question: "库存同步策略是什么？",
      answer: "库存答案",
      sourceFilePaths: [],
    });
    server.use(
      http.get("/api/v1/admin/pending", () => HttpResponse.json({ count: 2, items: [matching, other] })),
    );
    const user = userEvent.setup();
    renderReviews("/reviews?type=query&queryId=query-pending-1");

    expect(await screen.findByRole("heading", { name: "支付超时后应如何恢复？" })).toBeVisible();
    expect(screen.getByLabelText("待确认答案正文")).toHaveTextContent("原始答案");
    expect(screen.getByText("docs/payment-retry.md")).toBeVisible();
    await user.type(screen.getByRole("searchbox", { name: "搜索待确认查询" }), "库存");

    expect(await screen.findByRole("heading", { name: "库存同步策略是什么？" })).toBeVisible();
    expect(screen.queryByRole("heading", { name: "支付超时后应如何恢复？" })).not.toBeInTheDocument();
  });

  it("requires confirmation before correcting and reloads the revised answer", async () => {
    let item = pendingQueryItemFixture();
    let postCount = 0;
    let requestBody: unknown;
    server.use(
      http.get("/api/v1/admin/pending", () => HttpResponse.json({ count: 1, items: [item] })),
      http.post("/api/v1/admin/pending/query-pending-1/correct", async ({ request }) => {
        postCount += 1;
        requestBody = await request.json();
        item = pendingQueryItemFixture({ answer: "# 查询回答\n\n重写后的答案。" });
        return HttpResponse.json({ queryId: item.queryId, answer: item.answer, status: "PENDING" });
      }),
    );
    const user = userEvent.setup();
    renderReviews("/reviews?type=query&queryId=query-pending-1");

    await user.click(await screen.findByRole("button", { name: "更正答案" }));
    const dialog = screen.getByRole("dialog", { name: "确认更正待确认答案" });
    expect(postCount).toBe(0);
    await user.type(within(dialog).getByLabelText("更正说明"), "补充失败重试证据");
    await user.click(within(dialog).getByRole("button", { name: "提交更正并重写" }));

    expect(await screen.findByText("答案已更正")).toBeVisible();
    expect(screen.getByLabelText("待确认答案正文")).toHaveTextContent("重写后的答案");
    expect(requestBody).toEqual({ correction: "补充失败重试证据" });
    expect(postCount).toBe(1);
  });

  it("removes a confirmed item and exposes the persisted contribution impact", async () => {
    let items = [pendingQueryItemFixture()];
    server.use(
      http.get("/api/v1/admin/pending", () => HttpResponse.json({ count: items.length, items })),
      http.post("/api/v1/admin/pending/query-pending-1/confirm", () => {
        items = [];
        return HttpResponse.json({ status: "CONFIRMED" });
      }),
    );
    const user = userEvent.setup();
    renderReviews("/reviews?type=query&queryId=query-pending-1");

    await user.click(await screen.findByRole("button", { name: "确认并沉淀" }));
    const dialog = screen.getByRole("dialog", { name: "确认沉淀最终答案" });
    expect(within(dialog).getByText("写入贡献记录并移出队列")).toBeVisible();
    await user.click(within(dialog).getByRole("button", { name: "确认并写入贡献" }));

    expect(await screen.findByText("待确认查询已确认")).toBeVisible();
    expect(screen.getByText("当前没有待确认查询")).toBeVisible();
  });

  it("keeps a failed discard visible and refreshes the pending list", async () => {
    const item = pendingQueryItemFixture();
    let listCount = 0;
    server.use(
      http.get("/api/v1/admin/pending", () => {
        listCount += 1;
        return HttpResponse.json({ count: 1, items: [item] });
      }),
      http.post("/api/v1/admin/pending/query-pending-1/discard", () => HttpResponse.json(
        { code: "PENDING_QUERY_CHANGED", message: "记录已被其他操作更新" },
        { status: 409 },
      )),
    );
    const user = userEvent.setup();
    renderReviews("/reviews?type=query&queryId=query-pending-1");

    await user.click(await screen.findByRole("button", { name: "丢弃" }));
    const dialog = screen.getByRole("dialog", { name: "确认丢弃待确认查询" });
    expect(within(dialog).getByText("删除待确认记录，不沉淀贡献")).toBeVisible();
    await user.click(within(dialog).getByRole("button", { name: "确认丢弃" }));

    expect(await screen.findByText("治理操作失败，已刷新队列")).toBeVisible();
    expect(within(dialog).getByRole("alert")).toHaveTextContent("记录已被其他操作更新");
    await waitFor(() => expect(listCount).toBeGreaterThan(1));
    expect(screen.getByRole("heading", { name: "支付超时后应如何恢复？" })).toBeVisible();
  });
});

function renderReviews(initialEntry: string) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <AppQueryProvider>
        <ReviewsPage />
      </AppQueryProvider>
    </MemoryRouter>,
  );
}
