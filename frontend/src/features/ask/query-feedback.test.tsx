import { QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { delay, http, HttpResponse } from "msw";

import type { QueryResponse } from "../../api/contracts/query";
import { createAppQueryClient } from "../../api/query-client";
import { server } from "../../test/server";
import { QueryFeedback } from "./query-feedback";

const QUERY_RESPONSE: QueryResponse = {
  answer: "有证据的回答 [1]。",
  sources: [
    {
      sourceId: 2,
      articleKey: "article-1",
      conceptId: "concept-1",
      title: "资料",
      sourcePaths: ["kb/source.md"],
      derivation: "RETRIEVED",
    },
  ],
  articles: [
    {
      sourceId: 2,
      articleKey: "article-1",
      conceptId: "concept-1",
      title: "资料",
      derivation: "RETRIEVED",
    },
  ],
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
      claimText: "有证据的回答",
      sourceCount: 1,
      sources: [
        {
          sourceType: "ARTICLE",
          targetKey: "article-1",
          sourceId: 2,
          articleKey: "article-1",
          conceptId: "concept-1",
          title: "资料",
          sourcePaths: ["kb/source.md"],
          matchedExcerpt: "证据",
          validationStatus: "VERIFIED",
          reason: null,
          score: 0.9,
        },
      ],
    },
  ],
  structuredEvidence: null,
};

describe("query feedback", () => {
  it("submits reliable feedback once with deduplicated context", async () => {
    const user = userEvent.setup();
    let attempts = 0;
    let requestBody: Record<string, unknown> | undefined;
    server.use(
      http.post("/api/v1/admin/query-feedback", async ({ request }) => {
        attempts += 1;
        requestBody = (await request.json()) as Record<string, unknown>;
        await delay(40);
        return HttpResponse.json(feedbackResponse("reliable", ""));
      }),
    );
    renderFeedback();

    const positiveButton = screen.getByRole("button", { name: "有帮助" });
    await user.click(positiveButton);
    expect(positiveButton).toBeDisabled();
    await user.click(positiveButton);

    expect(await screen.findByText("反馈已提交")).toBeVisible();
    expect(attempts).toBe(1);
    expect(requestBody).toMatchObject({
      queryId: "query-1",
      question: "测试问题",
      answerSummary: "有证据的回答 [1]。",
      feedbackType: "reliable",
      comment: "",
      articleKeys: ["article-1"],
      sourcePaths: ["kb/source.md"],
      reportedBy: "web-app",
    });
    expect(
      screen.getByRole("button", { name: "已提交有帮助反馈" }),
    ).toBeDisabled();
  });

  it("keeps issue input after failure and retries only explicitly", async () => {
    const user = userEvent.setup();
    let attempts = 0;
    let requestBody: Record<string, unknown> | undefined;
    server.use(
      http.post("/api/v1/admin/query-feedback", async ({ request }) => {
        attempts += 1;
        requestBody = (await request.json()) as Record<string, unknown>;
        return attempts === 1
          ? HttpResponse.json(
              { code: "WRITE_FAILED", message: "反馈暂未写入" },
              { status: 503 },
            )
          : HttpResponse.json(
              feedbackResponse("source_conflict", "两条来源结论不同"),
            );
      }),
    );
    renderFeedback();

    await user.click(screen.getByRole("button", { name: "有问题" }));
    await user.selectOptions(screen.getByLabelText("问题类型"), "source_conflict");
    const submitButton = screen.getByRole("button", { name: "提交反馈" });
    expect(submitButton).toBeDisabled();
    await user.type(screen.getByLabelText("补充说明"), "两条来源结论不同");
    await user.click(submitButton);

    expect(await screen.findByRole("alert")).toHaveTextContent("反馈暂未写入");
    expect(screen.getByLabelText("补充说明")).toHaveValue("两条来源结论不同");
    expect(attempts).toBe(1);
    await user.click(screen.getByRole("button", { name: "重试" }));

    expect(await screen.findByText("反馈已提交")).toBeVisible();
    expect(attempts).toBe(2);
    expect(requestBody).toMatchObject({
      feedbackType: "source_conflict",
      comment: "两条来源结论不同",
    });
  });
});

function renderFeedback() {
  render(
    <QueryClientProvider client={createAppQueryClient()}>
      <QueryFeedback question="测试问题" response={QUERY_RESPONSE} />
    </QueryClientProvider>,
  );
}

function feedbackResponse(feedbackType: string, comment: string) {
  return {
    id: 9,
    queryId: "query-1",
    question: "测试问题",
    answerSummary: "有证据的回答 [1]。",
    feedbackType,
    comment,
    articleKeys: ["article-1"],
    sourcePaths: ["kb/source.md"],
    reportedBy: "web-app",
    status: "PENDING",
    resolutionComment: "",
    handledBy: null,
    handledAt: null,
    createdAt: "2026-07-22T13:00:00Z",
    updatedAt: "2026-07-22T13:00:00Z",
  };
}
