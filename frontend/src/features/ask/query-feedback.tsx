import { useMutation } from "@tanstack/react-query";
import { Send, ThumbsDown, ThumbsUp } from "lucide-react";
import { useState } from "react";

import { isApiError } from "../../api/api-error";
import {
  queryFeedbackApi,
  type QueryFeedbackRequest,
  type QueryFeedbackType,
} from "../../api/contracts/query-feedback";
import type { QueryResponse } from "../../api/contracts/query";
import { InlineAlert } from "../../components/inline-alert";

interface QueryFeedbackProps {
  question: string;
  response: QueryResponse;
}

const ISSUE_OPTIONS: Array<{ label: string; value: QueryFeedbackType }> = [
  { label: "回答有问题", value: "answer_problem" },
  { label: "来源存在冲突", value: "source_conflict" },
  { label: "需要人工确认", value: "needs_manual_confirmation" },
];

export function QueryFeedback({ question, response }: QueryFeedbackProps) {
  const queryId = response.queryId;
  const [issueFormOpen, setIssueFormOpen] = useState(false);
  const [feedbackType, setFeedbackType] =
    useState<QueryFeedbackType>("answer_problem");
  const [comment, setComment] = useState("");
  const [submittedTypes, setSubmittedTypes] = useState<Set<QueryFeedbackType>>(
    () => new Set(),
  );
  const mutation = useMutation({
    mutationFn: (request: QueryFeedbackRequest) =>
      queryFeedbackApi.create(request),
    onSuccess: (_response, request) => {
      setSubmittedTypes((current) => new Set(current).add(request.feedbackType));
      if (request.feedbackType !== "reliable") {
        setIssueFormOpen(false);
        setComment("");
      }
    },
  });

  if (!queryId) {
    return null;
  }

  const submit = (type: QueryFeedbackType, feedbackComment: string) => {
    if (mutation.isPending || submittedTypes.has(type)) {
      return;
    }
    mutation.mutate(
      buildFeedbackRequest(queryId, question, response, type, feedbackComment),
    );
  };
  const positiveSubmitted = submittedTypes.has("reliable");

  return (
    <section aria-label="结果反馈" className="query-feedback">
      <div className="query-feedback-prompt">
        <span>这个回答有帮助吗？</span>
        <div className="query-feedback-actions">
          <button
            aria-label={positiveSubmitted ? "已提交有帮助反馈" : "有帮助"}
            aria-pressed={positiveSubmitted}
            className="icon-button"
            disabled={mutation.isPending || positiveSubmitted}
            onClick={() => submit("reliable", "")}
            title="有帮助"
            type="button"
          >
            <ThumbsUp aria-hidden="true" size={17} />
          </button>
          <button
            aria-expanded={issueFormOpen}
            aria-label="有问题"
            className="icon-button"
            disabled={mutation.isPending}
            onClick={() => setIssueFormOpen((open) => !open)}
            title="有问题"
            type="button"
          >
            <ThumbsDown aria-hidden="true" size={17} />
          </button>
        </div>
      </div>
      {issueFormOpen ? (
        <form
          className="query-feedback-form"
          onSubmit={(event) => {
            event.preventDefault();
            submit(feedbackType, comment.trim());
          }}
        >
          <label>
            问题类型
            <select
              onChange={(event) =>
                setFeedbackType(event.target.value as QueryFeedbackType)
              }
              value={feedbackType}
            >
              {ISSUE_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
          <label>
            补充说明
            <textarea
              maxLength={4000}
              onChange={(event) => setComment(event.target.value)}
              required
              rows={3}
              value={comment}
            />
          </label>
          <div className="query-feedback-form-actions">
            <span>{comment.length}/4000</span>
            <button
              className="primary-button"
              disabled={
                mutation.isPending ||
                !comment.trim() ||
                submittedTypes.has(feedbackType)
              }
              type="submit"
            >
              <Send aria-hidden="true" size={16} />
              {submittedTypes.has(feedbackType) ? "已提交" : "提交反馈"}
            </button>
          </div>
        </form>
      ) : null}
      {mutation.isSuccess ? (
        <InlineAlert title="反馈已提交" tone="success" />
      ) : mutation.isError ? (
        <InlineAlert
          actionLabel="重试"
          description={resolveErrorMessage(mutation.error)}
          onAction={() => {
            if (mutation.variables) {
              mutation.mutate(mutation.variables);
            }
          }}
          title="反馈提交失败"
          tone="error"
        />
      ) : null}
    </section>
  );
}

function buildFeedbackRequest(
  queryId: string,
  question: string,
  response: QueryResponse,
  feedbackType: QueryFeedbackType,
  comment: string,
): QueryFeedbackRequest {
  const markerSources = response.citationMarkers.flatMap(
    (marker) => marker.sources,
  );
  return {
    queryId,
    question,
    answerSummary: (response.answer ?? "").slice(0, 4000),
    feedbackType,
    comment,
    articleKeys: uniqueStrings([
      ...(response.articles ?? []).map((article) => article.articleKey),
      ...markerSources.map((source) => source.articleKey),
    ]),
    sourcePaths: uniqueStrings([
      ...(response.sources ?? []).flatMap((source) => source.sourcePaths ?? []),
      ...markerSources.flatMap((source) => source.sourcePaths),
    ]),
    reportedBy: "web-app",
  };
}

function uniqueStrings(values: Array<string | null>) {
  return [...new Set(values.filter((value): value is string => Boolean(value)))];
}

function resolveErrorMessage(error: unknown) {
  return isApiError(error) ? error.message : "请求未能完成，请重试。";
}
