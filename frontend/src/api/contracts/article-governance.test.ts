import { http, HttpResponse } from "msw";

import { server } from "../../test/server";
import {
  articleSnapshotFixture,
  correctionResponseFixture,
  hotspotResponseFixture,
  lifecycleResponseFixture,
  reviewAuditFixture,
  reviewResponseFixture,
  rollbackResponseFixture,
} from "../../test/article-governance-fixtures";
import { createApiClient } from "../api-client";
import { createArticleGovernanceApi } from "./article-governance";

const client = createApiClient({ baseUrl: "http://localhost" });
const api = createArticleGovernanceApi(client);

describe("article governance contract", () => {
  it("scopes audits and bounded snapshot history by article identity", async () => {
    server.use(
      http.get("http://localhost/api/v1/admin/articles/concept%2Falpha/review/audits", ({ request }) => {
        expect(new URL(request.url).searchParams.get("sourceId")).toBe("12");
        return HttpResponse.json({ count: 1, items: [reviewAuditFixture()] });
      }),
      http.get("http://localhost/api/v1/admin/snapshot/article", ({ request }) => {
        expect(Object.fromEntries(new URL(request.url).searchParams)).toEqual({
          articleId: "concept/alpha",
          sourceId: "12",
          limit: "10",
        });
        return HttpResponse.json({ conceptId: "concept-alpha", count: 1, items: [articleSnapshotFixture()] });
      }),
    );

    const [audits, snapshots] = await Promise.all([
      api.audits("concept/alpha", 12),
      api.snapshots("concept/alpha", 12, 10),
    ]);

    expect(audits.items[0]?.action).toBe("approve");
    expect(snapshots.items[0]?.sourcePaths).toEqual(["docs/alpha.md"]);
  });

  it("preserves optimistic review status and lifecycle audit fields", async () => {
    server.use(
      http.post("http://localhost/api/v1/admin/articles/article-alpha/review/request-changes", async ({ request }) => {
        expect(await request.json()).toEqual({
          sourceId: 12,
          reviewedBy: "reviewer",
          comment: "需要补证据",
          expectedReviewStatus: "needs_human_review",
          correctionSummary: "补充来源说明",
        });
        return HttpResponse.json(reviewResponseFixture("needs_review"));
      }),
      http.post("http://localhost/api/v1/admin/articles/article-alpha/lifecycle/archive", async ({ request }) => {
        expect(new URL(request.url).searchParams.get("sourceId")).toBe("12");
        expect(await request.json()).toEqual({ reason: "停止维护", updatedBy: "operator" });
        return HttpResponse.json(lifecycleResponseFixture());
      }),
    );

    const review = await api.requestChanges("article-alpha", {
      sourceId: 12,
      reviewedBy: "reviewer",
      comment: "需要补证据",
      expectedReviewStatus: "needs_human_review",
      correctionSummary: "补充来源说明",
    });
    const lifecycle = await api.transitionLifecycle(
      "article-alpha",
      12,
      "archive",
      { reason: "停止维护", updatedBy: "operator" },
    );

    expect(review.auditId).toBe(91);
    expect(lifecycle.lifecycle).toBe("archived");
  });

  it("maps correction, rollback and hotspot refresh without retry metadata", async () => {
    server.use(
      http.post("http://localhost/api/v1/admin/articles/article-alpha/correct", async ({ request }) => {
        expect(new URL(request.url).searchParams.get("sourceId")).toBe("12");
        expect(await request.json()).toEqual({ correctionSummary: "修正超时配置" });
        return HttpResponse.json(correctionResponseFixture());
      }),
      http.post("http://localhost/api/v1/admin/rollback/article", async ({ request }) => {
        expect(await request.json()).toEqual({ articleId: "article-alpha", sourceId: 12, snapshotId: 81 });
        return HttpResponse.json(rollbackResponseFixture());
      }),
      http.post("http://localhost/api/v1/admin/articles/hotspots/refresh", async ({ request }) => {
        expect(await request.json()).toEqual({ heatScoreThreshold: 3, limit: 50 });
        return HttpResponse.json(hotspotResponseFixture());
      }),
    );

    const correction = await api.correct("article-alpha", 12, "修正超时配置");
    const rollback = await api.rollback("article-alpha", 12, 81);
    const hotspots = await api.refreshHotspots({ heatScoreThreshold: 3, limit: 50 });

    expect(correction.downstreamIds).toEqual(["concept-beta"]);
    expect(rollback.restoredSnapshotId).toBe(81);
    expect(hotspots.candidates[0]?.heatScore).toBe(7);
  });
});
