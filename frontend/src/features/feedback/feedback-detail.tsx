import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CheckCircle2, CircleSlash2 } from "lucide-react";
import { useState } from "react";

import {
  queryFeedbackApi,
  type QueryFeedbackDetail,
  type QueryFeedbackHandleRequest,
  type QueryFeedbackList,
} from "../../api/contracts/query-feedback";
import { queryKeys } from "../../api/query-keys";
import { MarkdownReport } from "../../components/markdown-report";
import { PageState } from "../../components/page-state";
import { ArticleGovernanceDialog } from "../library/article-governance-dialog";
import {
  feedbackActionLabel,
  feedbackStatusLabel,
  feedbackStatusTone,
  feedbackTypeLabel,
  formatFeedbackTime,
  resolveFeedbackError,
} from "./feedback-utils";

export type FeedbackAction = "resolve" | "dismiss";

export function FeedbackDetail({
  feedbackId,
  onSuccess,
  onFailure,
}: {
  feedbackId: number;
  onSuccess: (action: FeedbackAction) => void;
  onFailure: (description: string) => void;
}) {
  const queryClient = useQueryClient();
  const [action, setAction] = useState<FeedbackAction | null>(null);
  const detailKey = queryKeys.feedback.detail(feedbackId);
  const query = useQuery({
    queryKey: detailKey,
    queryFn: ({ signal }) => queryFeedbackApi.detail(feedbackId, signal),
  });
  const mutation = useMutation({
    mutationFn: async ({ requestAction, request }: {
      requestAction: FeedbackAction;
      request: QueryFeedbackHandleRequest;
    }) => {
      const latest = await queryFeedbackApi.detail(feedbackId);
      if (latest.feedback.status.toUpperCase() !== "PENDING") {
        throw new Error(`反馈已被其他操作处理，当前状态：${feedbackStatusLabel(latest.feedback.status)}`);
      }
      const feedback = requestAction === "resolve"
        ? await queryFeedbackApi.resolve(feedbackId, request)
        : await queryFeedbackApi.dismiss(feedbackId, request);
      return { requestAction, feedback };
    },
    onSuccess: async ({ requestAction, feedback }) => {
      queryClient.setQueriesData<QueryFeedbackList>(
        {
          predicate: (query) => query.queryKey[0] === "admin"
            && query.queryKey[1] === "query-feedback"
            && query.queryKey[2] === "list",
        },
        (current) => current ? {
          ...current,
          items: current.items.map((item) => item.id === feedback.id ? feedback : item),
        } : current,
      );
      queryClient.setQueryData<QueryFeedbackDetail>(detailKey, (current) => current
        ? { ...current, feedback }
        : current);
      setAction(null);
      onSuccess(requestAction);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.feedback.root }),
        queryClient.invalidateQueries({ queryKey: queryKeys.overview }),
      ]);
    },
    onError: async (error) => {
      onFailure(resolveFeedbackError(error));
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.feedback.root }),
        queryClient.invalidateQueries({ queryKey: detailKey }),
      ]);
    },
  });

  if (query.isPending) return <PageState status="loading" title="正在加载反馈详情" />;
  if (query.isError) {
    return (
      <PageState
        actionLabel="重试"
        description={resolveFeedbackError(query.error)}
        onAction={() => void query.refetch()}
        status="error"
        title="反馈详情加载失败"
      />
    );
  }

  const { feedback, audits } = query.data;
  const pending = feedback.status.toUpperCase() === "PENDING";
  return (
    <article className="feedback-detail">
      <header>
        <div>
          <div className="feedback-detail-labels">
            <span className={`review-status is-${feedbackStatusTone(feedback.status)}`}>
              {feedbackStatusLabel(feedback.status)}
            </span>
            <span className="feedback-type-label">{feedbackTypeLabel(feedback.feedbackType)}</span>
          </div>
          <h2>{feedback.question || "未提供问题"}</h2>
          <code>feedback #{feedback.id}{feedback.queryId ? ` · query ${feedback.queryId}` : ""}</code>
        </div>
        {pending ? (
          <div className="feedback-detail-actions">
            <button
              className="primary-button"
              disabled={mutation.isPending}
              onClick={() => { mutation.reset(); setAction("resolve"); }}
              type="button"
            >
              <CheckCircle2 aria-hidden="true" size={16} />
              标记已解决
            </button>
            <button
              className="secondary-button"
              disabled={mutation.isPending}
              onClick={() => { mutation.reset(); setAction("dismiss"); }}
              type="button"
            >
              <CircleSlash2 aria-hidden="true" size={16} />
              忽略反馈
            </button>
          </div>
        ) : null}
      </header>

      <dl className="feedback-detail-facts">
        <Fact label="反馈人">{feedback.reportedBy}</Fact>
        <Fact label="创建时间">{formatFeedbackTime(feedback.createdAt)}</Fact>
        <Fact label="处理人">{feedback.handledBy ?? "--"}</Fact>
        <Fact label="处理时间">{formatFeedbackTime(feedback.handledAt)}</Fact>
      </dl>

      <DetailSection count={undefined} id={`feedback-comment-${feedback.id}`} title="反馈说明">
        <p className="feedback-comment-body">{feedback.comment || "未填写反馈说明"}</p>
      </DetailSection>

      <DetailSection count={undefined} id={`feedback-answer-${feedback.id}`} title="原回答摘要">
        <MarkdownReport content={feedback.answerSummary || "未记录回答摘要"} label="反馈关联的原回答摘要" />
      </DetailSection>

      <DetailSection count={feedback.articleKeys.length} id={`feedback-articles-${feedback.id}`} title="关联文章">
        <StringList empty="未关联文章" items={feedback.articleKeys} />
      </DetailSection>

      <DetailSection count={feedback.sourcePaths.length} id={`feedback-sources-${feedback.id}`} title="来源路径">
        <StringList empty="未记录来源路径" items={feedback.sourcePaths} />
      </DetailSection>

      {!pending ? (
        <DetailSection count={undefined} id={`feedback-resolution-${feedback.id}`} title="处理结论">
          <p className="feedback-comment-body">{feedback.resolutionComment || "未填写处理结论"}</p>
        </DetailSection>
      ) : null}

      <DetailSection count={audits.length} id={`feedback-audits-${feedback.id}`} title="审计记录">
        {audits.length ? (
          <ol className="feedback-audit-list">
            {audits.map((audit) => (
              <li key={audit.id}>
                <span className="feedback-audit-marker" />
                <div>
                  <strong>{feedbackActionLabel(audit.action)}</strong>
                  <p>{audit.comment || "未填写备注"}</p>
                  <span>
                    {audit.previousStatus ? `${feedbackStatusLabel(audit.previousStatus)} → ` : ""}
                    {feedbackStatusLabel(audit.nextStatus)} · {audit.operatedBy} · {formatFeedbackTime(audit.operatedAt)}
                  </span>
                </div>
              </li>
            ))}
          </ol>
        ) : <p className="feedback-empty-line">暂无审计记录</p>}
      </DetailSection>

      {action ? (
        <FeedbackActionDialog
          action={action}
          error={mutation.isError ? resolveFeedbackError(mutation.error) : undefined}
          feedback={feedback}
          onClose={() => setAction(null)}
          onConfirm={(request) => mutation.mutate({ requestAction: action, request })}
          pending={mutation.isPending}
        />
      ) : null}
    </article>
  );
}

function FeedbackActionDialog({
  action,
  feedback,
  pending,
  error,
  onClose,
  onConfirm,
}: {
  action: FeedbackAction;
  feedback: QueryFeedbackDetail["feedback"];
  pending: boolean;
  error?: string;
  onClose: () => void;
  onConfirm: (request: QueryFeedbackHandleRequest) => void;
}) {
  const [handledBy, setHandledBy] = useState("admin");
  const [comment, setComment] = useState("");
  const configuration = action === "resolve" ? {
    title: "确认反馈已解决",
    confirmLabel: "确认解决并记录审计",
    description: "反馈会从待处理队列移出，处理结论、操作者和时间会写入审计记录。",
    impact: "状态迁移为已解决，保留完整反馈与审计历史",
  } : {
    title: "确认忽略这条反馈",
    confirmLabel: "确认忽略并记录审计",
    description: "反馈会从待处理队列移出，忽略原因、操作者和时间会写入审计记录。",
    impact: "状态迁移为已忽略，后续不再作为待办处理",
  };
  const validationError = !handledBy.trim()
    ? "请填写处理人"
    : !comment.trim()
      ? `请填写${action === "resolve" ? "处理结论" : "忽略原因"}`
      : undefined;
  return (
    <ArticleGovernanceDialog
      confirmLabel={configuration.confirmLabel}
      description={configuration.description}
      destructive={action === "dismiss"}
      error={error ?? validationError}
      onClose={onClose}
      onConfirm={() => {
        if (!validationError) onConfirm({ handledBy: handledBy.trim(), comment: comment.trim() });
      }}
      pending={pending}
      title={configuration.title}
    >
      <dl className="governance-impact-summary">
        <div><dt>反馈</dt><dd>#{feedback.id} · {feedback.question}</dd></div>
        <div><dt>当前状态</dt><dd>{feedbackStatusLabel(feedback.status)}</dd></div>
        <div><dt>操作影响</dt><dd>{configuration.impact}</dd></div>
      </dl>
      <label className="governance-form-field">
        处理人
        <input
          autoFocus
          onChange={(event) => setHandledBy(event.target.value)}
          value={handledBy}
        />
      </label>
      <label className="governance-form-field">
        {action === "resolve" ? "处理结论" : "忽略原因"}
        <textarea
          onChange={(event) => setComment(event.target.value)}
          placeholder={action === "resolve" ? "说明问题如何处理或为何已解决" : "说明不再处理这条反馈的原因"}
          rows={5}
          value={comment}
        />
      </label>
    </ArticleGovernanceDialog>
  );
}

function DetailSection({
  id,
  title,
  count,
  children,
}: {
  id: string;
  title: string;
  count?: number;
  children: React.ReactNode;
}) {
  return (
    <section aria-labelledby={id} className="feedback-detail-section">
      <div className="feedback-section-heading">
        <h3 id={id}>{title}</h3>
        {count !== undefined ? <span>{count}</span> : null}
      </div>
      {children}
    </section>
  );
}

function StringList({ items, empty }: { items: string[]; empty: string }) {
  return items.length ? (
    <ul className="feedback-string-list">
      {items.map((item, index) => <li key={`${item}-${index}`}><code>{item}</code></li>)}
    </ul>
  ) : <p className="feedback-empty-line">{empty}</p>;
}

function Fact({ label, children }: { label: string; children: React.ReactNode }) {
  return <div><dt>{label}</dt><dd>{children}</dd></div>;
}
