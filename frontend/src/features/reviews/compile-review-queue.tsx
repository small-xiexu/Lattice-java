import { useQuery } from "@tanstack/react-query";
import { X } from "lucide-react";
import { useEffect, useMemo } from "react";
import { useSearchParams } from "react-router-dom";

import {
  compileReviewStatusSchema,
  reviewsApi,
  type CompileReviewQueueItem,
  type CompileReviewStatus,
} from "../../api/contracts/reviews";
import { queryKeys } from "../../api/query-keys";
import { PageState } from "../../components/page-state";
import { CompileReviewDetail } from "./compile-review-detail";
import {
  formatReviewTime,
  parseReviewIssues,
  reviewStatusLabel,
  reviewStatusTone,
  resolveReviewError,
} from "./review-utils";

export function CompileReviewQueue() {
  const [searchParams, setSearchParams] = useSearchParams();
  const status = parseStatus(searchParams.get("status"));
  const limit = parseLimit(searchParams.get("limit"));
  const jobId = searchParams.get("jobId")?.trim() ?? "";
  const selectedId = parseId(searchParams.get("id"));
  const filters = { status, limit };
  const query = useQuery({
    queryKey: queryKeys.reviews.compileQueue(filters),
    queryFn: ({ signal }) => reviewsApi.listCompileQueue({ status, limit, signal }),
  });
  const items = useMemo(() => {
    const queueItems = query.data?.items ?? [];
    return jobId ? queueItems.filter((item) => item.jobId === jobId) : queueItems;
  }, [jobId, query.data?.items]);

  const updateSearch = (values: Record<string, string | number | null>, replace = false) => {
    const next = new URLSearchParams(searchParams);
    Object.entries(values).forEach(([key, value]) => {
      if (value === null) next.delete(key);
      else next.set(key, String(value));
    });
    setSearchParams(next, { replace });
  };

  useEffect(() => {
    if (selectedId === null && items.length > 0) {
      const next = new URLSearchParams(searchParams);
      next.set("id", String(items[0].id));
      setSearchParams(next, { replace: true });
    }
  }, [items, searchParams, selectedId, setSearchParams]);

  return (
    <div className="compile-review-queue">
      <div className="reviews-toolbar">
        <label className="filter-field">
          <span>状态</span>
          <select
            onChange={(event) => updateSearch({
              status: event.target.value === "needs_human_review" ? null : event.target.value,
              id: null,
            })}
            value={status}
          >
            <option value="needs_human_review">待人工确认</option>
            <option value="published">已发布</option>
            <option value="rejected">已驳回</option>
            <option value="accepted">已接受</option>
          </select>
        </label>
        <label className="filter-field">
          <span>最近</span>
          <select
            onChange={(event) => updateSearch({ limit: event.target.value }, true)}
            value={limit}
          >
            {[20, 50, 100, 200].map((value) => <option key={value} value={value}>{value} 条</option>)}
          </select>
        </label>
        {jobId ? (
          <button
            className="reviews-filter-chip"
            onClick={() => updateSearch({ jobId: null, id: null })}
            title="清除作业筛选"
            type="button"
          >
            作业 {shortId(jobId)}
            <X aria-hidden="true" size={14} />
          </button>
        ) : null}
        <span aria-live="polite" className="result-count">
          {query.data ? `${items.length} 项` : "-- 项"}
        </span>
      </div>

      {query.isPending ? <PageState status="loading" title="正在加载编译审核队列" /> : null}
      {query.isError ? (
        <PageState
          actionLabel="重试"
          description={resolveReviewError(query.error)}
          onAction={() => void query.refetch()}
          status="error"
          title="编译审核队列加载失败"
        />
      ) : null}
      {query.data ? (
        <div className="reviews-layout">
          <section aria-label="编译审核列表" className="reviews-list-column">
            {items.length ? (
              <ReviewQueueList
                items={items}
                onSelect={(id) => updateSearch({ id })}
                selectedId={selectedId}
              />
            ) : (
              <PageState
                description={jobId ? "当前状态与返回上限内没有该作业的审核项" : undefined}
                status="empty"
                title="当前筛选下没有审核项"
              />
            )}
          </section>
          <section aria-label="编译审核详情" className="reviews-detail-column">
            {selectedId ? (
              <CompileReviewDetail reviewId={selectedId} />
            ) : (
              <div className="reviews-detail-placeholder">选择一条草稿查看问题与正文</div>
            )}
          </section>
        </div>
      ) : null}
    </div>
  );
}

function ReviewQueueList({
  items,
  selectedId,
  onSelect,
}: {
  items: CompileReviewQueueItem[];
  selectedId: number | null;
  onSelect: (id: number) => void;
}) {
  return (
    <ul className="reviews-list">
      {items.map((item) => {
        const issueCount = parseReviewIssues(item.reviewIssuesJson).length;
        return (
          <li key={item.id}>
            <button
              aria-current={selectedId === item.id ? "true" : undefined}
              className={selectedId === item.id ? "is-selected" : undefined}
              onClick={() => onSelect(item.id)}
              type="button"
            >
              <span className="reviews-list-main">
                <strong>{item.title || "未命名草稿"}</strong>
                <span>{item.sourceCode || "未关联资料源"}</span>
              </span>
              <span className={`review-status is-${reviewStatusTone(item.reviewStatus)}`}>
                {reviewStatusLabel(item.reviewStatus)}
              </span>
              <span className="reviews-list-foot">
                <span>{issueCount} 个结构化问题</span>
                <time dateTime={item.updatedAt ?? undefined}>{formatReviewTime(item.updatedAt)}</time>
              </span>
            </button>
          </li>
        );
      })}
    </ul>
  );
}

function parseStatus(value: string | null): CompileReviewStatus {
  const parsed = compileReviewStatusSchema.safeParse(value ?? "needs_human_review");
  return parsed.success ? parsed.data : "needs_human_review";
}

function parseLimit(value: string | null) {
  const parsed = Number(value ?? 50);
  return Number.isInteger(parsed) && parsed >= 1 && parsed <= 200 ? parsed : 50;
}

function parseId(value: string | null) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

function shortId(value: string) {
  return value.length > 14 ? `${value.slice(0, 8)}...${value.slice(-4)}` : value;
}
