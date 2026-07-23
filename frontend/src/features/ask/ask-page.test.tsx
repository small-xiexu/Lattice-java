import { QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { delay, http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router-dom";

import { LiveAnnouncerProvider } from "../../accessibility/live-announcer-provider";
import type { QueryResponse } from "../../api/contracts/query";
import { createAppQueryClient } from "../../api/query-client";
import { overviewFixture } from "../../test/quality-fixtures";
import { QuerySessionProvider } from "../../state/query-session-provider";
import { server } from "../../test/server";
import AskPage from "./ask-page";

const BASE_RESPONSE: QueryResponse = {
  answer: "Lattice 使用证据支撑回答 [1]。",
  sources: [],
  articles: [],
  queryId: "query-1",
  reviewStatus: "PASSED",
  answerOutcome: "SUCCESS",
  generationMode: "LLM",
  modelExecutionStatus: "SUCCESS",
  citationCheck: null,
  deepResearch: null,
  fallbackReason: null,
  citationMarkers: [
    {
      markerOrdinal: 1,
      markerId: "marker-1",
      citationLiteral: "[1]",
      citationLiterals: ["[1]"],
      claimText: "Lattice 使用证据支撑回答",
      sourceCount: 1,
      sources: [
        {
          sourceType: "ARTICLE",
          targetKey: "article-1",
          sourceId: 7,
          articleKey: "article-1",
          conceptId: "concept-1",
          title: "证据文章",
          sourcePaths: ["kb/lattice.md"],
          matchedExcerpt: "证据片段",
          validationStatus: "VERIFIED",
          reason: null,
          score: 0.92,
        },
      ],
    },
  ],
  structuredEvidence: null,
};

afterEach(() => window.sessionStorage.clear());

describe("ask interaction prototype", () => {
  beforeEach(() => {
    server.use(
      http.get("/api/v1/admin/overview", () => HttpResponse.json(overviewFixture())),
    );
  });

  it("shows real knowledge readiness without blocking questions when overview fails", async () => {
    const user = userEvent.setup();
    const firstRender = renderAskPage();

    expect(await screen.findByText("文章 12 · 源文件 7 · 待处理 1")).toBeVisible();
    expect(screen.getByRole("link", { name: "查看知识质量" })).toHaveAttribute(
      "href",
      "/library/quality",
    );
    firstRender.unmount();

    server.use(
      http.get("/api/v1/admin/overview", () =>
        HttpResponse.json({ code: "OVERVIEW_FAILED", message: "overview failed" }, { status: 503 }),
      ),
      http.post("/api/v1/query", () => HttpResponse.json(BASE_RESPONSE)),
    );
    renderAskPage();
    expect(await screen.findByText("暂时不可用，不影响提问")).toBeVisible();
    await submitQuestion(user, "概览失败时仍可提问");
    expect(await screen.findByText("回答完成")).toBeVisible();
  });

  it("shows a stable waiting state and maps quick mode to forceSimple", async () => {
    const user = userEvent.setup();
    let requestBody: unknown;
    let attempts = 0;
    server.use(
      http.post("/api/v1/query", async ({ request }) => {
        attempts += 1;
        requestBody = await request.json();
        await delay(40);
        return HttpResponse.json(BASE_RESPONSE);
      }),
    );
    renderAskPage();

    await user.type(screen.getByLabelText("问题"), "  如何回答？  ");
    await user.click(screen.getByRole("radio", { name: "快速问答" }));
    await user.click(screen.getByRole("button", { name: "提问" }));

    expect(screen.getByRole("status", { name: "" })).toHaveTextContent(
      "正在等待回答",
    );
    const pendingButton = screen.getByRole("button", { name: "等待中" });
    expect(pendingButton).toBeDisabled();
    fireEvent.click(pendingButton);
    expect(await screen.findByRole("heading", { name: "回答" })).toBeVisible();
    expect(requestBody).toEqual({ question: "如何回答？", forceSimple: true });
    expect(attempts).toBe(1);
    expect(screen.getByText("回答完成")).toBeVisible();
  });

  it("surfaces a successful fallback as a degraded answer", async () => {
    const user = userEvent.setup();
    server.use(
      http.post("/api/v1/query", () =>
        HttpResponse.json({
          ...BASE_RESPONSE,
          generationMode: "FALLBACK",
          modelExecutionStatus: "FAILED",
          fallbackReason: "LLM_CALL_FAILED",
        }),
      ),
    );
    renderAskPage();

    await submitQuestion(user, "降级结果测试");

    expect(await screen.findAllByText("降级回答")).toHaveLength(2);
    expect(
      screen.getByText("模型调用失败，当前展示由检索证据生成的降级回答。"),
    ).toBeVisible();
    expect(screen.queryByText("回答完成")).not.toBeInTheDocument();
  });

  it("renders partial and empty outcomes without inventing content", async () => {
    const user = userEvent.setup();
    let response: QueryResponse = {
      ...BASE_RESPONSE,
      answer: "仅覆盖部分证据。",
      answerOutcome: "PARTIAL_ANSWER",
      fallbackReason: "CITATION_QUALITY_INSUFFICIENT",
      citationMarkers: [],
    };
    server.use(
      http.post("/api/v1/query", () => HttpResponse.json(response)),
    );
    renderAskPage();

    await submitQuestion(user, "部分回答测试");
    expect(await screen.findByRole("status")).toHaveTextContent("部分回答");
    expect(
      screen.getByText("引用质量未达到完整回答要求，请结合证据核对。"),
    ).toBeVisible();

    response = {
      ...BASE_RESPONSE,
      answer: null,
      answerOutcome: "INSUFFICIENT_EVIDENCE",
      citationMarkers: [],
    };
    await submitQuestion(user, "空回答测试");
    expect(await screen.findByText("没有可展示的回答")).toBeVisible();
    expect(screen.getAllByText("证据不足")).toHaveLength(2);
  });

  it("keeps failures local and retries only after an explicit action", async () => {
    const user = userEvent.setup();
    let attempts = 0;
    server.use(
      http.post("/api/v1/query", () => {
        attempts += 1;
        return attempts === 1
          ? HttpResponse.json(
              { code: "MODEL_UNAVAILABLE", message: "模型暂不可用" },
              { status: 503 },
            )
          : HttpResponse.json(BASE_RESPONSE);
      }),
    );
    renderAskPage();

    await submitQuestion(user, "失败恢复测试");
    expect(await screen.findByRole("alert")).toHaveTextContent("模型暂不可用");
    expect(attempts).toBe(1);
    await user.click(screen.getByRole("button", { name: "重试" }));
    expect(await screen.findByText("回答完成")).toBeVisible();
    expect(attempts).toBe(2);
  });

  it("distinguishes no knowledge from a model failure", async () => {
    const user = userEvent.setup();
    let response: QueryResponse = {
      ...BASE_RESPONSE,
      answer: null,
      answerOutcome: "NO_RELEVANT_KNOWLEDGE",
      citationMarkers: [],
    };
    server.use(
      http.post("/api/v1/query", () => HttpResponse.json(response)),
    );
    renderAskPage();

    await submitQuestion(user, "无相关知识测试");
    expect(await screen.findAllByText("无相关知识")).toHaveLength(2);

    response = {
      ...BASE_RESPONSE,
      answer: null,
      answerOutcome: "MODEL_FAILURE",
      generationMode: "FALLBACK",
      modelExecutionStatus: "FAILED",
      fallbackReason: "LLM_CALL_FAILED",
      citationMarkers: [],
    };
    await submitQuestion(user, "模型失败测试");
    expect(await screen.findAllByText("生成失败")).toHaveLength(2);
    expect(screen.getByRole("alert")).toHaveTextContent(
      "模型调用未完成，当前没有可用的生成结果。",
    );
  });

  it("opens citations as a focus-trapped sheet on narrow screens", async () => {
    const originalMatchMedia = window.matchMedia;
    window.matchMedia = createMatchMedia(true);
    const user = userEvent.setup();
    server.use(
      http.post("/api/v1/query", () => HttpResponse.json(BASE_RESPONSE)),
    );

    try {
      renderAskPage();
      await submitQuestion(user, "移动引用测试");
      const marker = await screen.findByRole("button", { name: "引用 1" });
      await user.click(marker);

      expect(screen.getByRole("dialog", { name: "引用证据" })).toHaveAttribute(
        "aria-modal",
        "true",
      );
      await waitFor(() =>
        expect(screen.getByRole("heading", { name: "引用 1" })).toHaveFocus(),
      );
      await user.keyboard("{Escape}");
      await waitFor(() =>
        expect(screen.getByRole("button", { name: "引用 1" })).toHaveFocus(),
      );
    } finally {
      window.matchMedia = originalMatchMedia;
    }
  });
});

function renderAskPage() {
  return render(
    <QueryClientProvider client={createAppQueryClient()}>
      <LiveAnnouncerProvider>
        <QuerySessionProvider>
          <MemoryRouter>
            <AskPage />
          </MemoryRouter>
        </QuerySessionProvider>
      </LiveAnnouncerProvider>
    </QueryClientProvider>,
  );
}

async function submitQuestion(
  user: ReturnType<typeof userEvent.setup>,
  question: string,
) {
  const field = screen.getByLabelText("问题");
  await user.clear(field);
  await user.type(field, question);
  await user.click(screen.getByRole("button", { name: "提问" }));
}

function createMatchMedia(matches: boolean): typeof window.matchMedia {
  return ((query: string) => ({
    matches,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })) as typeof window.matchMedia;
}
