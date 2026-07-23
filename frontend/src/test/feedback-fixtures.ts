import type {
  QueryFeedbackAudit,
  QueryFeedbackDetail,
  QueryFeedbackResponse,
} from "../api/contracts/query-feedback";

export function queryFeedbackFixture(
  overrides: Partial<QueryFeedbackResponse> = {},
): QueryFeedbackResponse {
  return {
    id: 8,
    queryId: "query-feedback-8",
    question: "支付超时后如何恢复？",
    answerSummary: "# 查询回答\n\n原回答缺少重试证据。",
    feedbackType: "answer_problem",
    comment: "回答没有说明失败后的恢复步骤",
    articleKeys: ["payments--retry"],
    sourcePaths: ["docs/payment-retry.md"],
    reportedBy: "web-app",
    status: "PENDING",
    resolutionComment: "",
    handledBy: null,
    handledAt: null,
    createdAt: "2026-07-22T10:00:00Z",
    updatedAt: "2026-07-22T10:00:00Z",
    ...overrides,
  };
}

export function queryFeedbackAuditFixture(
  overrides: Partial<QueryFeedbackAudit> = {},
): QueryFeedbackAudit {
  return {
    id: 18,
    feedbackId: 8,
    action: "CREATE",
    previousStatus: null,
    nextStatus: "PENDING",
    comment: "回答没有说明失败后的恢复步骤",
    operatedBy: "web-app",
    operatedAt: "2026-07-22T10:00:00Z",
    metadataJson: "{}",
    ...overrides,
  };
}

export function queryFeedbackDetailFixture(
  overrides: Partial<QueryFeedbackDetail> = {},
): QueryFeedbackDetail {
  return {
    feedback: queryFeedbackFixture(),
    audits: [queryFeedbackAuditFixture()],
    ...overrides,
  };
}
