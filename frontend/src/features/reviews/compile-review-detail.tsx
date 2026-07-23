import { useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { Check, ExternalLink, X } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";

import {
  reviewsApi,
  type CompileReviewActionRequest,
  type CompileReviewQueueItem,
} from "../../api/contracts/reviews";
import { queryKeys } from "../../api/query-keys";
import { InlineAlert } from "../../components/inline-alert";
import { MarkdownReport } from "../../components/markdown-report";
import { PageState } from "../../components/page-state";
import { ArticleGovernanceDialog } from "../library/article-governance-dialog";
import {
  formatReviewTime,
  issueTone,
  parseReviewIssues,
  reviewStatusLabel,
  reviewStatusTone,
  resolveReviewError,
  stripFrontmatter,
} from "./review-utils";

type ReviewAction = "approve" | "reject";

export function CompileReviewDetail({ reviewId }: { reviewId: number }) {
  const query = useQuery({
    queryKey: queryKeys.reviews.compileDetail(reviewId),
    queryFn: ({ signal }) => reviewsApi.getCompileQueueItem(reviewId, signal),
  });

  if (query.isPending) return <PageState status="loading" title="正在加载审核详情" />;
  if (query.isError) {
    return (
      <PageState
        actionLabel="重试"
        description={resolveReviewError(query.error)}
        onAction={() => void query.refetch()}
        status="error"
        title="审核详情加载失败"
      />
    );
  }
  return <CompileReviewDetailContent item={query.data} />;
}

function CompileReviewDetailContent({ item }: { item: CompileReviewQueueItem }) {
  const queryClient = useQueryClient();
  const [action, setAction] = useState<ReviewAction | null>(null);
  const mutation = useMutation({
    mutationFn: ({
      action: requestAction,
      request,
    }: {
      action: ReviewAction;
      request: CompileReviewActionRequest;
    }) => requestAction === "approve"
      ? reviewsApi.approveCompileQueueItem(item.id, request)
      : reviewsApi.rejectCompileQueueItem(item.id, request),
    onSuccess: async (response) => {
      setAction(null);
      queryClient.setQueryData(
        queryKeys.reviews.compileDetail(item.id),
        response.item,
      );
      await invalidateReviewState(queryClient);
    },
    onError: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.reviews.compileDetail(item.id) }),
        queryClient.invalidateQueries({ queryKey: ["admin", "compile-review-queue"] }),
      ]);
      setAction(null);
    },
  });
  const issues = parseReviewIssues(item.reviewIssuesJson);
  const pending = item.reviewStatus === "needs_human_review";

  return (
    <article className="compile-review-detail">
      <header>
        <div>
          <span className={`review-status is-${reviewStatusTone(item.reviewStatus)}`}>
            {reviewStatusLabel(item.reviewStatus)}
          </span>
          <h2>{item.title || "未命名草稿"}</h2>
          <code>{item.articleKey}</code>
        </div>
        {pending ? (
          <div className="review-detail-actions">
            <button
              className="secondary-button"
              disabled={mutation.isPending}
              onClick={() => setAction("reject")}
              type="button"
            >
              <X aria-hidden="true" size={16} />
              驳回
            </button>
            <button
              className="primary-button"
              disabled={mutation.isPending}
              onClick={() => setAction("approve")}
              type="button"
            >
              <Check aria-hidden="true" size={16} />
              通过并发布
            </button>
          </div>
        ) : null}
      </header>

      {mutation.isSuccess ? (
        <InlineAlert
          description={`审计记录 #${mutation.data.auditId}，原状态 ${reviewStatusLabel(mutation.data.previousReviewStatus)}`}
          title={mutation.data.item.reviewStatus === "rejected" ? "草稿已驳回" : "草稿已发布"}
          tone="success"
        />
      ) : null}
      {mutation.isError && !action ? (
        <InlineAlert
          description={resolveReviewError(mutation.error)}
          title="审核操作失败，已重新加载最新状态"
          tone="error"
        />
      ) : null}

      <dl className="review-detail-facts">
        <Fact label="队列编号">#{item.id}</Fact>
        <Fact label="资料源">{item.sourceCode || "--"}</Fact>
        <Fact label="自动修复">{item.fixAttemptCount} / {item.maxFixRounds} 轮</Fact>
        <Fact label="审核路由">{item.reviewRoute || "--"}</Fact>
        <Fact label="审核模型">{item.reviewerModel || "--"}</Fact>
        <Fact label="更新时间">{formatReviewTime(item.updatedAt)}</Fact>
      </dl>

      <nav aria-label="审核对象跳转" className="review-object-links">
        <Link to={`/activity?kind=compile-job&id=${encodeURIComponent(item.jobId)}`}>
          编译作业 <ExternalLink aria-hidden="true" size={14} />
        </Link>
        {item.sourceId ? (
          <Link to={`/library/sources/${item.sourceId}?view=files`}>
            资料源 #{item.sourceId} <ExternalLink aria-hidden="true" size={14} />
          </Link>
        ) : null}
        {item.publishedArticleKey ? (
          <Link to={`/library/articles/${encodeURIComponent(item.publishedArticleKey)}${item.sourceId ? `?sourceId=${item.sourceId}` : ""}`}>
            已发布文章 <ExternalLink aria-hidden="true" size={14} />
          </Link>
        ) : null}
      </nav>

      <section className="review-detail-section" aria-labelledby={`review-issues-${item.id}`}>
        <div className="review-section-heading">
          <h3 id={`review-issues-${item.id}`}>审查问题</h3>
          <span>{issues.length}</span>
        </div>
        {issues.length ? (
          <ul className="review-issue-list">
            {issues.map((issue, index) => (
              <li key={`${issue.category}-${index}`}>
                <span className={`review-issue-severity is-${issueTone(issue.severity)}`}>{issue.severity}</span>
                <div><strong>{issue.category}</strong><p>{issue.description}</p></div>
              </li>
            ))}
          </ul>
        ) : <p className="review-empty-line">服务端未列出结构化审查问题</p>}
      </section>

      <section className="review-detail-section review-draft-section" aria-labelledby={`review-content-${item.id}`}>
        <div className="review-section-heading">
          <h3 id={`review-content-${item.id}`}>草稿正文</h3>
        </div>
        <MarkdownReport content={stripFrontmatter(item.content)} label="待审核草稿正文" />
      </section>

      <section className="review-detail-section" aria-labelledby={`review-sources-${item.id}`}>
        <div className="review-section-heading">
          <h3 id={`review-sources-${item.id}`}>来源路径</h3>
          <span>{item.sourcePaths.length}</span>
        </div>
        {item.sourcePaths.length ? (
          <ul className="review-source-list">
            {item.sourcePaths.map((path) => <li key={path}><code>{path}</code></li>)}
          </ul>
        ) : <p className="review-empty-line">未提供来源路径</p>}
      </section>

      <details className="review-raw-details">
        <summary>原始元数据</summary>
        <pre>{formatJson(item.metadataJson)}</pre>
      </details>

      {item.reviewedAt || item.reviewedBy || item.reviewComment ? (
        <section className="review-decision-record" aria-label="人工审核记录">
          <h3>人工审核记录</h3>
          <dl>
            <Fact label="操作人">{item.reviewedBy || "--"}</Fact>
            <Fact label="操作时间">{formatReviewTime(item.reviewedAt)}</Fact>
            <Fact label="审核意见">{item.reviewComment || "--"}</Fact>
          </dl>
        </section>
      ) : null}

      {action ? (
        <CompileReviewActionDialog
          action={action}
          error={mutation.error ? resolveReviewError(mutation.error) : undefined}
          item={item}
          onClose={() => setAction(null)}
          onConfirm={(request) => mutation.mutate({ action, request })}
          pending={mutation.isPending}
        />
      ) : null}
    </article>
  );
}

function CompileReviewActionDialog({
  action,
  item,
  pending,
  error,
  onClose,
  onConfirm,
}: {
  action: ReviewAction;
  item: CompileReviewQueueItem;
  pending: boolean;
  error?: string;
  onClose: () => void;
  onConfirm: (request: CompileReviewActionRequest) => void;
}) {
  const [reviewedBy, setReviewedBy] = useState("admin");
  const [comment, setComment] = useState("");
  const approve = action === "approve";
  const invalid = !reviewedBy.trim() || (!approve && !comment.trim());
  return (
    <ArticleGovernanceDialog
      confirmLabel={approve ? "确认通过并发布" : "确认驳回"}
      description={approve
        ? "草稿会写入正式文章、重建切片并触发向量刷新。"
        : "草稿会停止发布并写入审核审计，不会生成正式文章。"}
      destructive={!approve}
      error={error ?? (invalid ? "操作人必填；驳回时必须填写原因" : undefined)}
      onClose={onClose}
      onConfirm={() => {
        if (!invalid) {
          onConfirm({
            reviewedBy: reviewedBy.trim(),
            comment: comment.trim(),
            expectedReviewStatus: item.reviewStatus,
          });
        }
      }}
      pending={pending}
      title={approve ? "确认发布审核草稿" : "确认驳回审核草稿"}
    >
      <dl className="governance-impact-summary">
        <div><dt>审核项</dt><dd>#{item.id} {item.title}</dd></div>
        <div><dt>当前状态</dt><dd>{reviewStatusLabel(item.reviewStatus)}</dd></div>
        <div><dt>来源文件</dt><dd>{item.sourcePaths.length} 个</dd></div>
        <div><dt>并发保护</dt><dd>提交时校验状态仍为 {item.reviewStatus}</dd></div>
      </dl>
      <label className="governance-form-field">
        操作人
        <input autoComplete="off" onChange={(event) => setReviewedBy(event.target.value)} value={reviewedBy} />
      </label>
      <label className="governance-form-field">
        {approve ? "审核备注（选填）" : "驳回原因"}
        <textarea
          onChange={(event) => setComment(event.target.value)}
          placeholder={approve ? "记录核验结论" : "说明阻止发布的具体原因"}
          rows={4}
          value={comment}
        />
      </label>
    </ArticleGovernanceDialog>
  );
}

function Fact({ label, children }: { label: string; children: React.ReactNode }) {
  return <div><dt>{label}</dt><dd>{children}</dd></div>;
}

function formatJson(value: string) {
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

async function invalidateReviewState(queryClient: QueryClient) {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ["admin", "compile-review-queue"] }),
    queryClient.invalidateQueries({ queryKey: ["admin", "processing-tasks"] }),
    queryClient.invalidateQueries({ queryKey: ["admin", "source-runs"] }),
    queryClient.invalidateQueries({ queryKey: ["admin", "compile-jobs"] }),
  ]);
}
