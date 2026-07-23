import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter, useLocation } from "react-router-dom";

import { AppQueryProvider } from "../../api/query-provider";
import {
  factCardFixture,
  linkEnhancementFixture,
  lintFixture,
  overviewFixture,
  qualityFixture,
} from "../../test/quality-fixtures";
import { server } from "../../test/server";
import QualityPage from "./quality-page";

describe("quality page", () => {
  it("shows independent overview, quality, coverage and omission results", async () => {
    installOverviewHandlers();
    renderPage();

    expect(await screen.findByText("66.7%")).toBeVisible();
    expect(screen.getByText("docs/missing.md")).toBeVisible();
    expect(screen.getByText("退款流程是什么？")).toBeVisible();
    expect(screen.getByText("+8.0%")).toBeVisible();
  });

  it("requires explicit selection and confirmation before a scoped lint fix", async () => {
    let fixRequests = 0;
    server.use(
      http.get("/api/v1/admin/lint", () => HttpResponse.json(lintFixture())),
      http.post("/api/v1/admin/lint/fix", async ({ request }) => {
        fixRequests += 1;
        expect(await request.json()).toEqual({ targetIds: ["article-alpha"] });
        return HttpResponse.json({ fixed: 1, skipped: 0, errors: [] });
      }),
    );
    const user = userEvent.setup();
    renderPage("/library/quality?view=lint");

    expect(await screen.findByText("article-alpha")).toBeVisible();
    expect(screen.getByText("不可修复")).toBeVisible();
    await user.click(screen.getByRole("checkbox", { name: "选择 article-alpha" }));
    await user.click(screen.getByRole("button", { name: "修复所选 (1)" }));
    const dialog = screen.getByRole("dialog", { name: "确认 Lint 自动修复" });
    expect(within(dialog).getByText("article-alpha")).toBeVisible();
    expect(fixRequests).toBe(0);
    await user.click(within(dialog).getByRole("button", { name: "确认修复" }));

    expect(await screen.findByText("Lint 修复完成")).toBeVisible();
    expect(fixRequests).toBe(1);
  });

  it("keeps inspection answer fields after a conflict and permits an explicit retry", async () => {
    let importRequests = 0;
    server.use(
      http.get("/api/v1/admin/inspect", () => HttpResponse.json(inspectionResponse())),
      http.post("/api/v1/admin/inspect/import-answers", async ({ request }) => {
        importRequests += 1;
        expect(await request.json()).toEqual({
          inspectionId: "inspection-1",
          finalAnswer: "最终答案",
          confirmedBy: "reviewer-a",
        });
        if (importRequests === 1) {
          return HttpResponse.json({ code: "INSPECTION_CONFLICT", message: "检查项状态已变化" }, { status: 409 });
        }
        return HttpResponse.json({ importedCount: 1, resolvedIds: ["inspection-1"] });
      }),
    );
    const user = userEvent.setup();
    renderPage("/library/quality?view=inspection");

    await user.click(await screen.findByRole("button", { name: "导入人工答案" }));
    const dialog = screen.getByRole("dialog", { name: "确认导入人工答案" });
    await user.clear(within(dialog).getByLabelText("最终答案"));
    await user.type(within(dialog).getByLabelText("最终答案"), "最终答案");
    await user.type(within(dialog).getByLabelText("确认人"), "reviewer-a");
    await user.click(within(dialog).getByRole("button", { name: "确认导入" }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent("检查项状态已变化");
    expect(within(dialog).getByLabelText("最终答案")).toHaveValue("最终答案");
    expect(within(dialog).getByLabelText("确认人")).toHaveValue("reviewer-a");
    await user.click(within(dialog).getByRole("button", { name: "确认导入" }));
    expect(await screen.findByText("已导入 1 条人工答案")).toBeVisible();
    expect(importRequests).toBe(2);
  });

  it("does not enable link persistence until the current session has a preview", async () => {
    const persistValues: boolean[] = [];
    server.use(
      http.post("/api/v1/admin/link-enhance", async ({ request }) => {
        persistValues.push(Boolean((await request.json() as { persist?: boolean }).persist));
        return HttpResponse.json(linkEnhancementFixture());
      }),
    );
    const user = userEvent.setup();
    renderPage("/library/quality?view=links");

    expect(screen.getByRole("button", { name: "持久化增强" })).toBeDisabled();
    await user.click(screen.getByRole("button", { name: "生成预览" }));
    expect(await screen.findByText("Article Alpha")).toBeVisible();
    expect(persistValues).toEqual([false]);
    await user.click(screen.getByRole("button", { name: "持久化增强" }));
    const dialog = screen.getByRole("dialog", { name: "确认持久化链接增强" });
    expect(within(dialog).getByText("1 篇")).toBeVisible();
    expect(persistValues).toEqual([false]);
    await user.click(within(dialog).getByRole("button", { name: "确认持久化" }));

    expect(await screen.findByText("链接增强已持久化")).toBeVisible();
    expect(persistValues).toEqual([false, true]);
  });

  it("keeps Fact Card list limit and selected detail in the URL", async () => {
    server.use(
      http.get("/api/v1/admin/fact-cards/summary", () => HttpResponse.json({
        totalCount: 1,
        countByCardType: { SUMMARY: 1 },
        countByReviewStatus: { accepted: 1 },
        sourceReferenceMissingCount: 0,
        lowConfidenceCount: 0,
      })),
      http.get("/api/v1/admin/fact-cards", ({ request }) => {
        expect(new URL(request.url).searchParams.get("limit")).toBe("25");
        return HttpResponse.json({ count: 1, items: [factCardFixture()] });
      }),
      http.get("/api/v1/admin/fact-cards/41", () => HttpResponse.json(factCardFixture())),
    );
    const user = userEvent.setup();
    renderPage("/library/quality?view=fact-cards&limit=25");

    await user.click(await screen.findByRole("button", { name: "Alpha 事实" }));
    expect(await screen.findByRole("heading", { name: "事实结论" })).toBeVisible();
    expect(screen.getByTestId("location-search")).toHaveTextContent("view=fact-cards");
    expect(screen.getByTestId("location-search")).toHaveTextContent("limit=25");
    expect(screen.getByTestId("location-search")).toHaveTextContent("factCardId=41");
    expect(screen.getByRole("link", { name: "查看资料源 #12" })).toHaveAttribute("href", "/library/sources/12");
  });

  it("recovers a failed quality report without blocking the other overview sections", async () => {
    let qualityRequests = 0;
    installOverviewHandlers({
      quality: () => {
        qualityRequests += 1;
        return qualityRequests === 1
          ? HttpResponse.json({ message: "质量采样暂不可用" }, { status: 503 })
          : HttpResponse.json(qualityFixture());
      },
    });
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText("docs/missing.md")).toBeVisible();
    const qualitySection = screen.getByRole("heading", { name: "质量指标" }).closest("section");
    expect(qualitySection).not.toBeNull();
    expect(await within(qualitySection as HTMLElement).findByText("质量采样暂不可用")).toBeVisible();
    await user.click(within(qualitySection as HTMLElement).getByRole("button", { name: "重试" }));

    await waitFor(() => expect(within(qualitySection as HTMLElement).getByText("+8.0%")).toBeVisible());
    expect(qualityRequests).toBe(2);
  });
});

function installOverviewHandlers({ quality }: { quality?: () => Response } = {}) {
  server.use(
    http.get("/api/v1/admin/overview", () => HttpResponse.json(overviewFixture())),
    http.get("/api/v1/admin/quality", () => quality?.() ?? HttpResponse.json(qualityFixture())),
    http.get("/api/v1/admin/coverage", () => HttpResponse.json({
      totalSourceFileCount: 3,
      coveredSourceFileCount: 2,
      uncoveredSourceFileCount: 1,
      coverageRatio: 2 / 3,
      coveredSourcePaths: ["docs/alpha.md", "docs/beta.md"],
    })),
    http.get("/api/v1/admin/omissions", () => HttpResponse.json({
      totalSourceFileCount: 3,
      omittedSourceFileCount: 1,
      items: ["docs/missing.md"],
    })),
  );
}

function inspectionResponse() {
  return {
    totalQuestions: 1,
    questions: [{
      id: "inspection-1",
      type: "missing_answer",
      question: "默认超时是多少？",
      prompt: "确认最终答案",
      suggestedAnswer: "建议答案",
      sourcePaths: ["docs/alpha.md"],
      reviewStatus: "pending_review",
      createdAt: "2026-07-22T08:00:00Z",
      expiresAt: "2026-07-29T08:00:00Z",
    }],
  };
}

function LocationProbe() {
  return <output data-testid="location-search">{useLocation().search}</output>;
}

function renderPage(path = "/library/quality") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AppQueryProvider>
        <QualityPage />
        <LocationProbe />
      </AppQueryProvider>
    </MemoryRouter>,
  );
}
