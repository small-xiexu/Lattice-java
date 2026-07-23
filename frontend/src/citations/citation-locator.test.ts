import { locateCitationBindings } from "./citation-locator";
import type { CitationMarkerData, CitationSource } from "./citation-types";

const SOURCE: CitationSource = {
  sourceType: "ARTICLE",
  targetKey: "article-1",
  sourceId: 1,
  articleKey: "article-1",
  conceptId: "concept-1",
  title: "证据文章",
  sourcePaths: ["kb/article.md"],
  matchedExcerpt: "证据原文",
  validationStatus: "VERIFIED",
  reason: null,
  score: 0.9,
};

function marker(
  literal: string,
  overrides: Partial<CitationMarkerData> = {},
): CitationMarkerData {
  return {
    markerOrdinal: 1,
    markerId: "marker-1",
    citationLiteral: literal,
    citationLiterals: [literal],
    claimText: "结论",
    sourceCount: 1,
    sources: [SOURCE],
    ...overrides,
  };
}

describe("citation locator", () => {
  it("binds a literal only when it has one eligible text match and source", () => {
    expect(locateCitationBindings("结论 [1] 完成", [marker("[1]")])).toEqual([
      expect.objectContaining({
        mode: "exact",
        startOffset: 3,
        endOffset: 6,
      }),
    ]);
  });

  it.each([
    ["同节点多匹配", "结论 [1]，再次 [1]", "[1]", "multiple-text-matches"],
    ["跨段多匹配", "第一段 [1]\n\n第二段 [1]", "[1]", "multiple-text-matches"],
    ["代码块", "```text\n[3]\n```", "[3]", "no-text-match"],
    ["跨节点字面量", "[**4**]", "[4]", "no-text-match"],
  ])("downgrades %s", (_label, markdown, literal, reason) => {
    expect(locateCitationBindings(markdown, [marker(literal)])).toEqual([
      expect.objectContaining({ mode: "evidence-list", reason }),
    ]);
  });

  it("supports a unique text node inside a GFM table", () => {
    const result = locateCitationBindings(
      "| 字段 |\n| --- |\n| [2] |",
      [marker("[2]")],
    );
    expect(result[0]).toMatchObject({ mode: "exact", literal: "[2]" });
  });

  it("downgrades ambiguous sources and conflicting markers", () => {
    const ambiguous = marker("[5]", {
      sourceCount: 2,
      sources: [SOURCE, { ...SOURCE, targetKey: "article-2" }],
    });
    expect(locateCitationBindings("结论 [5]", [ambiguous])[0]).toMatchObject({
      mode: "evidence-list",
      reason: "ambiguous-sources",
    });

    const conflict = locateCitationBindings("结论 [1]", [
      marker("[1]"),
      marker("[1]", { markerId: "marker-2", markerOrdinal: 2 }),
    ]);
    expect(conflict).toEqual([
      expect.objectContaining({ reason: "marker-conflict" }),
      expect.objectContaining({ reason: "marker-conflict" }),
    ]);
  });
});
