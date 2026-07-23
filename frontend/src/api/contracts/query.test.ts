import { createApiClient } from "../api-client";
import { createQueryApi, queryResponseSchema } from "./query";
import { buildQueryRequest } from "../../features/ask/query-request";
import { http, HttpResponse } from "msw";

import { server } from "../../test/server";

const FULL_QUERY_RESPONSE = {
  answer: "统计结果为 2 [1]。",
  sources: [
    {
      sourceId: 7,
      articleKey: "article-1",
      conceptId: "concept-1",
      title: "配置表",
      sourcePaths: ["kb/config.xlsx"],
      derivation: "RETRIEVED",
    },
  ],
  articles: [
    {
      sourceId: 7,
      articleKey: "article-1",
      conceptId: "concept-1",
      title: "配置表",
      derivation: "RETRIEVED",
    },
  ],
  queryId: "query-1",
  reviewStatus: "PASSED",
  answerOutcome: "SUCCESS",
  generationMode: "LLM",
  modelExecutionStatus: "SUCCESS",
  citationCheck: {
    verifiedCount: 1,
    demotedCount: 0,
    skippedCount: 0,
    coverageRate: 1,
    noCitation: false,
    claimCount: 1,
    unsupportedClaimCount: 0,
  },
  deepResearch: {
    routed: true,
    layerCount: 2,
    taskCount: 3,
    evidenceCardCount: 2,
    llmCallCount: 4,
    citationCoverage: 1,
    partialAnswer: false,
    hasConflicts: false,
  },
  fallbackReason: null,
  citationMarkers: [
    {
      markerOrdinal: 1,
      markerId: "marker-1",
      citationLiteral: "[1]",
      citationLiterals: ["[1]"],
      claimText: "统计结果为 2",
      sourceCount: 1,
      sources: [
        {
          sourceType: "SOURCE_FILE",
          targetKey: "kb/config.xlsx",
          sourceId: 7,
          articleKey: "article-1",
          conceptId: "concept-1",
          title: "配置表",
          sourcePaths: ["kb/config.xlsx"],
          matchedExcerpt: "count=2",
          validationStatus: "VERIFIED",
          reason: null,
          score: 0.91,
        },
      ],
    },
  ],
  structuredEvidence: {
    queryType: "TABLE_LOOKUP",
    rows: [
      {
        sourcePath: "kb/config.xlsx",
        tableName: "config",
        sheetName: "Sheet1",
        rowNumber: 2,
        cells: [
          {
            columnName: "count",
            columnIndex: 1,
            cellValue: "2",
            normalizedValue: "2",
            role: "primary",
          },
        ],
      },
    ],
    groups: [
      {
        groupByField: "status",
        groupValue: "active",
        normalizedGroupValue: "active",
        count: 2,
        filters: { region: "cn" },
      },
    ],
  },
};

describe("query contract", () => {
  it("maps smart, quick, and deep modes without conflicting flags", () => {
    expect(buildQueryRequest("问题", "auto")).toEqual({ question: "问题" });
    expect(buildQueryRequest("问题", "simple")).toEqual({
      question: "问题",
      forceSimple: true,
    });
    expect(buildQueryRequest("问题", "deep")).toEqual({
      question: "问题",
      forceDeep: true,
    });
  });

  it("parses the complete QueryResponse projection", () => {
    const response = queryResponseSchema.parse(FULL_QUERY_RESPONSE);

    expect(response.citationCheck?.coverageRate).toBe(1);
    expect(response.deepResearch?.taskCount).toBe(3);
    expect(response.structuredEvidence?.rows[0]?.cells[0]?.cellValue).toBe(
      "2",
    );
  });

  it("rejects malformed nested evidence instead of silently accepting it", () => {
    const invalidResponse = structuredClone(FULL_QUERY_RESPONSE);
    invalidResponse.citationCheck.coverageRate = 1.2;

    expect(queryResponseSchema.safeParse(invalidResponse).success).toBe(false);
  });

  it("sends the exact deep-mode request and validates the response", async () => {
    const client = createApiClient({ baseUrl: "http://localhost" });
    let requestBody: unknown;
    server.use(
      http.post("http://localhost/api/v1/query", async ({ request }) => {
        requestBody = await request.json();
        return HttpResponse.json(FULL_QUERY_RESPONSE);
      }),
    );

    const response = await createQueryApi(client).query(
      buildQueryRequest("完整契约", "deep"),
    );

    expect(requestBody).toEqual({ question: "完整契约", forceDeep: true });
    expect(response.modelExecutionStatus).toBe("SUCCESS");
  });
});
