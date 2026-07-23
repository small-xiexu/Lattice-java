import { http, HttpResponse } from "msw";

import {
  factCardFixture,
  linkEnhancementFixture,
  lintFixture,
  overviewFixture,
  qualityFixture,
} from "../../test/quality-fixtures";
import { server } from "../../test/server";
import { createApiClient } from "../api-client";
import { createQualityApi } from "./quality";

const client = createApiClient({ baseUrl: "http://localhost" });
const api = createQualityApi(client);

describe("quality contracts", () => {
  it("maps overview, quality, coverage and omission reports", async () => {
    server.use(
      http.get("http://localhost/api/v1/admin/overview", () => HttpResponse.json(overviewFixture())),
      http.get("http://localhost/api/v1/admin/quality", ({ request }) => {
        expect(new URL(request.url).searchParams.get("days")).toBe("7");
        return HttpResponse.json(qualityFixture());
      }),
      http.get("http://localhost/api/v1/admin/coverage", () => HttpResponse.json({
        totalSourceFileCount: 7,
        coveredSourceFileCount: 5,
        uncoveredSourceFileCount: 2,
        coverageRatio: 5 / 7,
        coveredSourcePaths: ["docs/alpha.md"],
      })),
      http.get("http://localhost/api/v1/admin/omissions", () => HttpResponse.json({
        totalSourceFileCount: 7,
        omittedSourceFileCount: 2,
        items: ["docs/missing.md"],
      })),
    );

    const [overview, quality, coverage, omissions] = await Promise.all([
      api.overview(), api.quality(), api.coverage(), api.omissions(),
    ]);

    expect(overview.status.highRiskArticleCount).toBe(1);
    expect(quality.trend.latestMeasuredAt).toBe("2026-07-22T08:30:00Z");
    expect(coverage.coveredSourcePaths).toEqual(["docs/alpha.md"]);
    expect(omissions.items).toEqual(["docs/missing.md"]);
  });

  it("scopes lint fixes to explicitly selected target ids", async () => {
    server.use(
      http.get("http://localhost/api/v1/admin/lint", () => HttpResponse.json(lintFixture())),
      http.post("http://localhost/api/v1/admin/lint/fix", async ({ request }) => {
        expect(await request.json()).toEqual({ targetIds: ["article-alpha"] });
        return HttpResponse.json({ fixed: 1, skipped: 0, errors: [] });
      }),
    );

    expect((await api.lint()).issues[0]?.fixable).toBe(true);
    expect((await api.fixLint(["article-alpha"])).fixed).toBe(1);
  });

  it("imports a confirmed inspection answer without dropping identity fields", async () => {
    server.use(
      http.get("http://localhost/api/v1/admin/inspect", () => HttpResponse.json({
        totalQuestions: 1,
        questions: [{
          id: "inspection-1",
          type: "missing_answer",
          question: "默认超时是多少？",
          prompt: "确认最终答案",
          suggestedAnswer: "30 秒",
          sourcePaths: ["docs/alpha.md"],
          reviewStatus: "pending_review",
          createdAt: "2026-07-22T08:00:00Z",
          expiresAt: "2026-07-29T08:00:00Z",
        }],
      })),
      http.post("http://localhost/api/v1/admin/inspect/import-answers", async ({ request }) => {
        expect(await request.json()).toEqual({
          inspectionId: "inspection-1",
          finalAnswer: "默认超时为 30 秒。",
          confirmedBy: "reviewer-a",
        });
        return HttpResponse.json({ importedCount: 1, resolvedIds: ["inspection-1"] });
      }),
    );

    expect((await api.inspect()).questions[0]?.sourcePaths).toEqual(["docs/alpha.md"]);
    expect((await api.importInspectionAnswer({
      inspectionId: "inspection-1",
      finalAnswer: "默认超时为 30 秒。",
      confirmedBy: "reviewer-a",
    })).resolvedIds).toEqual(["inspection-1"]);
  });

  it("distinguishes link preview from persistence with the persist flag", async () => {
    const persistValues: boolean[] = [];
    server.use(
      http.post("http://localhost/api/v1/admin/link-enhance", async ({ request }) => {
        persistValues.push(Boolean((await request.json() as { persist?: boolean }).persist));
        return HttpResponse.json(linkEnhancementFixture());
      }),
    );

    await api.enhanceLinks(false);
    await api.enhanceLinks(true);
    expect(persistValues).toEqual([false, true]);
  });

  it("uses a bounded Fact Card list and exact numeric detail id", async () => {
    server.use(
      http.get("http://localhost/api/v1/admin/fact-cards/summary", () => HttpResponse.json({
        totalCount: 1,
        countByCardType: { SUMMARY: 1 },
        countByReviewStatus: { accepted: 1 },
        sourceReferenceMissingCount: 0,
        lowConfidenceCount: 0,
      })),
      http.get("http://localhost/api/v1/admin/fact-cards", ({ request }) => {
        expect(new URL(request.url).searchParams.get("limit")).toBe("25");
        return HttpResponse.json({ count: 1, items: [factCardFixture()] });
      }),
      http.get("http://localhost/api/v1/admin/fact-cards/41", () => HttpResponse.json(factCardFixture())),
    );

    const [summary, list, detail] = await Promise.all([
      api.factCardSummary(), api.factCards(25), api.factCard(41),
    ]);
    expect(summary.countByCardType).toEqual({ SUMMARY: 1 });
    expect(list.count).toBe(1);
    expect(detail.sourceChunkIds).toEqual([31]);
  });
});
