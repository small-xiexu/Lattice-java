import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router-dom";

import { AppQueryProvider } from "../../api/query-provider";
import {
  queryFeedbackDetailFixture,
  queryFeedbackFixture,
} from "../../test/feedback-fixtures";
import { server } from "../../test/server";
import FeedbackPage from "./feedback-page";

describe("feedback page", () => {
  it("shows feedback evidence and audit history and filters the client list", async () => {
    const matching = queryFeedbackFixture();
    const other = queryFeedbackFixture({ id: 9, question: "库存同步失败", comment: "未展示冲突来源" });
    server.use(
      http.get("/api/v1/admin/query-feedback", () => HttpResponse.json({ count: 2, items: [matching, other] })),
      http.get("/api/v1/admin/query-feedback/:id", ({ params }) => {
        const feedback = params.id === "9" ? other : matching;
        return HttpResponse.json(queryFeedbackDetailFixture({ feedback }));
      }),
    );
    const user = userEvent.setup();
    renderFeedback("/feedback?id=8");

    expect(await screen.findByRole("heading", { name: "支付超时后如何恢复？" })).toBeVisible();
    expect(screen.getByLabelText("反馈关联的原回答摘要")).toHaveTextContent("原回答缺少重试证据");
    expect(screen.getByText("创建反馈", { selector: "strong" })).toBeVisible();
    await user.type(screen.getByRole("searchbox", { name: "搜索结果反馈" }), "库存");

    expect(await screen.findByRole("heading", { name: "库存同步失败" })).toBeVisible();
    expect(screen.queryByRole("heading", { name: "支付超时后如何恢复？" })).not.toBeInTheDocument();
  });

  it("requires explicit confirmation, refreshes status, and records a resolution", async () => {
    let feedback = queryFeedbackFixture();
    let detailCount = 0;
    let postCount = 0;
    let requestBody: unknown;
    server.use(
      http.get("/api/v1/admin/query-feedback", () => HttpResponse.json({ count: 1, items: [feedback] })),
      http.get("/api/v1/admin/query-feedback/8", () => {
        detailCount += 1;
        return HttpResponse.json(queryFeedbackDetailFixture({ feedback }));
      }),
      http.post("/api/v1/admin/query-feedback/8/resolve", async ({ request }) => {
        postCount += 1;
        requestBody = await request.json();
        feedback = queryFeedbackFixture({
          status: "RESOLVED",
          resolutionComment: "证据已补充",
          handledBy: "admin",
          handledAt: "2026-07-22T10:10:00Z",
        });
        return HttpResponse.json(feedback);
      }),
    );
    const user = userEvent.setup();
    renderFeedback("/feedback?id=8");

    await user.click(await screen.findByRole("button", { name: "标记已解决" }));
    const dialog = screen.getByRole("dialog", { name: "确认反馈已解决" });
    expect(postCount).toBe(0);
    expect(within(dialog).getByText("状态迁移为已解决，保留完整反馈与审计历史")).toBeVisible();
    await user.type(within(dialog).getByLabelText("处理结论"), "证据已补充");
    await user.click(within(dialog).getByRole("button", { name: "确认解决并记录审计" }));

    expect(await screen.findByText("反馈已解决")).toBeVisible();
    expect(requestBody).toEqual({ handledBy: "admin", comment: "证据已补充" });
    expect(postCount).toBe(1);
    expect(detailCount).toBeGreaterThan(1);
  });

  it("keeps a failed dismiss visible and reloads the latest state", async () => {
    const feedback = queryFeedbackFixture();
    let detailCount = 0;
    server.use(
      http.get("/api/v1/admin/query-feedback", () => HttpResponse.json({ count: 1, items: [feedback] })),
      http.get("/api/v1/admin/query-feedback/8", () => {
        detailCount += 1;
        return HttpResponse.json(queryFeedbackDetailFixture({ feedback }));
      }),
      http.post("/api/v1/admin/query-feedback/8/dismiss", () => HttpResponse.json(
        { code: "FEEDBACK_CHANGED", message: "反馈已被其他操作更新" },
        { status: 409 },
      )),
    );
    const user = userEvent.setup();
    renderFeedback("/feedback?id=8");

    await user.click(await screen.findByRole("button", { name: "忽略反馈" }));
    const dialog = screen.getByRole("dialog", { name: "确认忽略这条反馈" });
    await user.type(within(dialog).getByLabelText("忽略原因"), "不属于回答质量问题");
    await user.click(within(dialog).getByRole("button", { name: "确认忽略并记录审计" }));

    expect(await screen.findByText("反馈处理失败，已刷新最新状态")).toBeVisible();
    expect(within(dialog).getByRole("alert")).toHaveTextContent("反馈已被其他操作更新");
    await waitFor(() => expect(detailCount).toBeGreaterThan(2));
  });

  it("uses the selected server-side status filter", async () => {
    let requestedStatus = "";
    server.use(
      http.get("/api/v1/admin/query-feedback", ({ request }) => {
        requestedStatus = new URL(request.url).searchParams.get("status") ?? "";
        return HttpResponse.json({ count: 0, items: [] });
      }),
    );

    renderFeedback("/feedback?status=DISMISSED");

    expect(await screen.findByText("当前筛选下没有反馈")).toBeVisible();
    expect(requestedStatus).toBe("DISMISSED");
  });
});

function renderFeedback(initialEntry: string) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <AppQueryProvider>
        <FeedbackPage />
      </AppQueryProvider>
    </MemoryRouter>,
  );
}
