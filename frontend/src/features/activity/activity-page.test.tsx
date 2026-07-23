import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router-dom";

import { AppQueryProvider } from "../../api/query-provider";
import {
  compileJobFixture,
  processingTaskFixture,
  processingTaskListFixture,
  sourceRunFixture,
} from "../../test/activity-fixtures";
import { server } from "../../test/server";
import ActivityPage from "./activity-page";

describe("activity page", () => {
  it("shows processing summaries and a selected task's execution steps", async () => {
    const task = processingTaskFixture({
      processingActive: false,
      status: "SUCCEEDED",
      displayStatus: "SUCCEEDED",
      displayStatusLabel: "已完成",
    });
    server.use(
      http.get("/api/v1/admin/processing-tasks", () =>
        HttpResponse.json(processingTaskListFixture([task])),
      ),
    );
    const user = userEvent.setup();
    renderActivity("/activity");

    expect(await screen.findByText("Payments Docs")).toBeVisible();
    expect(screen.getByLabelText("任务汇总")).toHaveTextContent("已完成1");
    await user.click(screen.getByRole("button", { name: /Payments Docs/ }));

    expect(await screen.findByRole("heading", { name: "执行步骤" })).toBeVisible();
    expect(screen.getByLabelText("任务执行步骤")).toHaveTextContent("质量检查");
  });

  it("does not confirm a source run until the explicit dialog action", async () => {
    const confirmAction = {
      actionKey: "CONFIRM_EXISTING_SOURCE_APPEND",
      label: "追加到 Payments Docs",
      buttonClass: "primary-button",
      runId: 41,
      sourceId: 12,
      decision: "EXISTING_SOURCE_APPEND",
      decisionSourceId: 12,
      uploadRetry: false,
    };
    const waitingRun = sourceRunFixture({
      status: "WAIT_CONFIRM",
      displayStatus: "WAIT_CONFIRM",
      displayStatusLabel: "待人工确认",
      processingActive: false,
      requiresManualAction: true,
      actions: [confirmAction],
    });
    let requestCount = 0;
    let requestBody: unknown;
    server.use(
      http.get("/api/v1/admin/source-runs", () => HttpResponse.json([waitingRun])),
      http.get("/api/v1/admin/source-runs/41", () => HttpResponse.json(waitingRun)),
      http.post("/api/v1/admin/source-runs/41/confirm", async ({ request }) => {
        requestCount += 1;
        requestBody = await request.json();
        return HttpResponse.json(sourceRunFixture({
          processingActive: false,
          status: "COMPILE_QUEUED",
          displayStatus: "COMPILE_QUEUED",
          displayStatusLabel: "等待编译",
        }));
      }),
    );
    const user = userEvent.setup();
    renderActivity("/activity?kind=source-run&id=41");

    await user.click(await screen.findByRole("button", { name: "追加到 Payments Docs" }));
    const dialog = screen.getByRole("dialog", { name: "确认同步归属" });
    expect(within(dialog).getByText("追加到 Payments Docs")).toBeVisible();
    expect(requestCount).toBe(0);
    await user.click(within(dialog).getByRole("button", { name: "确认并继续处理" }));

    expect(await screen.findByText("任务操作已提交")).toBeVisible();
    expect(requestCount).toBe(1);
    expect(requestBody).toEqual({ decision: "EXISTING_SOURCE_APPEND", sourceId: 12 });
  });

  it("keeps compile retry confirmation open when the server rejects the request", async () => {
    const failedJob = compileJobFixture({
      status: "FAILED",
      derivedStatus: "FAILED",
      errorCode: "COMPILE_FAILED",
      errorMessage: "编译失败",
    });
    let requestCount = 0;
    server.use(
      http.get("/api/v1/admin/jobs", () =>
        HttpResponse.json({ count: 1, items: [failedJob] }),
      ),
      http.get("/api/v1/admin/jobs/job-41", () => HttpResponse.json(failedJob)),
      http.post("/api/v1/admin/jobs/job-41/retry", () => {
        requestCount += 1;
        return HttpResponse.json(
          { code: "JOB_RETRY_CONFLICT", message: "作业状态已变化" },
          { status: 409 },
        );
      }),
    );
    const user = userEvent.setup();
    renderActivity("/activity?kind=compile-job&id=job-41");

    await user.click(await screen.findByRole("button", { name: "重试编译作业" }));
    const dialog = screen.getByRole("dialog", { name: "确认重试任务" });
    expect(requestCount).toBe(0);
    await user.click(within(dialog).getByRole("button", { name: "确认重试" }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent("作业状态已变化");
    expect(requestCount).toBe(1);
    expect(screen.getByRole("dialog", { name: "确认重试任务" })).toBeVisible();
  });
});

function renderActivity(initialEntry: string) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <AppQueryProvider>
        <ActivityPage />
      </AppQueryProvider>
    </MemoryRouter>,
  );
}
