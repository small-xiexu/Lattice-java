import { useQuery } from "@tanstack/react-query";
import { Search } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";

import {
  queryFeedbackApi,
  type QueryFeedbackResponse,
  type QueryFeedbackStatus,
} from "../../api/contracts/query-feedback";
import { queryKeys } from "../../api/query-keys";
import { InlineAlert } from "../../components/inline-alert";
import { PageState } from "../../components/page-state";
import { FeedbackDetail, type FeedbackAction } from "./feedback-detail";
import {
  feedbackStatusLabel,
  feedbackStatusTone,
  feedbackTypeLabel,
  formatFeedbackTime,
  resolveFeedbackError,
} from "./feedback-utils";

interface FeedbackNotice {
  title: string;
  description: string;
  tone: "success" | "error";
}

export function FeedbackQueue() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [notice, setNotice] = useState<FeedbackNotice | null>(null);
  const status = parseStatus(searchParams.get("status"));
  const limit = parseLimit(searchParams.get("limit"));
  const keyword = searchParams.get("q")?.trim() ?? "";
  const selectedId = parseId(searchParams.get("id"));
  const filters = { status, limit };
  const query = useQuery({
    queryKey: queryKeys.feedback.list(filters),
    queryFn: ({ signal }) => queryFeedbackApi.list({ ...filters, signal }),
  });
  const items = useMemo(
    () => filterFeedback(query.data?.items ?? [], keyword),
    [keyword, query.data?.items],
  );
  const selectedItem = items.find((item) => item.id === selectedId) ?? null;

  const updateSearch = (values: Record<string, string | null>, replace = false) => {
    const next = new URLSearchParams(searchParams);
    Object.entries(values).forEach(([key, value]) => {
      if (value === null) next.delete(key);
      else next.set(key, value);
    });
    setSearchParams(next, { replace });
  };

  useEffect(() => {
    if (!query.data) return;
    if (items.length === 0 && selectedId !== null) {
      const next = new URLSearchParams(searchParams);
      next.delete("id");
      setSearchParams(next, { replace: true });
      return;
    }
    if (items.length > 0 && !selectedItem) {
      const next = new URLSearchParams(searchParams);
      next.set("id", String(items[0].id));
      setSearchParams(next, { replace: true });
    }
  }, [items, query.data, searchParams, selectedId, selectedItem, setSearchParams]);

  const handleSuccess = (action: FeedbackAction) => {
    const resolved = action === "resolve";
    setNotice({
      title: resolved ? "反馈已解决" : "反馈已忽略",
      description: resolved
        ? "处理结论和操作者已写入审计记录。"
        : "忽略原因和操作者已写入审计记录。",
      tone: "success",
    });
  };

  return (
    <div className="feedback-queue">
      <div className="feedback-toolbar">
        <label className="reviews-search-field">
          <Search aria-hidden="true" size={15} />
          <span className="sr-only">搜索结果反馈</span>
          <input
            aria-label="搜索结果反馈"
            onChange={(event) => updateSearch({ q: event.target.value || null, id: null }, true)}
            placeholder="搜索问题、反馈或来源"
            type="search"
            value={keyword}
          />
        </label>
        <label className="feedback-filter-field">
          <span>状态</span>
          <select
            aria-label="反馈状态"
            onChange={(event) => updateSearch({
              status: event.target.value === "PENDING" ? null : event.target.value,
              id: null,
            })}
            value={status}
          >
            <option value="ALL">全部</option>
            <option value="PENDING">待处理</option>
            <option value="RESOLVED">已解决</option>
            <option value="DISMISSED">已忽略</option>
          </select>
        </label>
        <label className="feedback-filter-field is-limit">
          <span>数量</span>
          <select
            aria-label="反馈返回数量"
            onChange={(event) => updateSearch({
              limit: event.target.value === "50" ? null : event.target.value,
              id: null,
            })}
            value={limit}
          >
            {[20, 50, 100, 200].map((value) => <option key={value} value={value}>{value}</option>)}
          </select>
        </label>
        <span aria-live="polite" className="result-count">
          {query.data ? `${items.length} / ${query.data.count} 项` : "-- 项"}
        </span>
      </div>

      {notice ? <InlineAlert {...notice} /> : null}
      {query.isPending ? <PageState status="loading" title="正在加载结果反馈" /> : null}
      {query.isError ? (
        <PageState
          actionLabel="重试"
          description={resolveFeedbackError(query.error)}
          onAction={() => void query.refetch()}
          status="error"
          title="结果反馈加载失败"
        />
      ) : null}
      {query.data ? (
        <div className="feedback-layout">
          <section aria-label="结果反馈列表" className="feedback-list-column">
            {items.length ? (
              <FeedbackList
                items={items}
                onSelect={(id) => updateSearch({ id: String(id) })}
                selectedId={selectedId}
              />
            ) : (
              <PageState
                description={keyword ? "请调整搜索条件" : undefined}
                status="empty"
                title={keyword ? "没有匹配的结果反馈" : "当前筛选下没有反馈"}
              />
            )}
          </section>
          <section aria-label="结果反馈详情" className="feedback-detail-column">
            {selectedItem ? (
              <FeedbackDetail
                feedbackId={selectedItem.id}
                onFailure={(description) => setNotice({
                  title: "反馈处理失败，已刷新最新状态",
                  description,
                  tone: "error",
                })}
                onSuccess={handleSuccess}
              />
            ) : <div className="feedback-detail-placeholder">选择一条反馈查看回答、来源和审计记录</div>}
          </section>
        </div>
      ) : null}
    </div>
  );
}

function FeedbackList({
  items,
  selectedId,
  onSelect,
}: {
  items: QueryFeedbackResponse[];
  selectedId: number | null;
  onSelect: (id: number) => void;
}) {
  return (
    <ul className="feedback-list">
      {items.map((item) => (
        <li key={item.id}>
          <button
            aria-current={selectedId === item.id ? "true" : undefined}
            className={selectedId === item.id ? "is-selected" : undefined}
            onClick={() => onSelect(item.id)}
            type="button"
          >
            <span className="feedback-list-main">
              <strong>{item.question || "未提供问题"}</strong>
              <span>{feedbackTypeLabel(item.feedbackType)}</span>
            </span>
            <span className={`review-status is-${feedbackStatusTone(item.status)}`}>
              {feedbackStatusLabel(item.status)}
            </span>
            <span className="feedback-list-comment">{item.comment || "未填写反馈说明"}</span>
            <span className="feedback-list-foot">
              <span>#{item.id} · {item.reportedBy}</span>
              <time dateTime={item.createdAt ?? undefined}>{formatFeedbackTime(item.createdAt)}</time>
            </span>
          </button>
        </li>
      ))}
    </ul>
  );
}

function filterFeedback(items: QueryFeedbackResponse[], keyword: string) {
  const normalized = keyword.toLocaleLowerCase();
  if (!normalized) return items;
  return items.filter((item) => [
    String(item.id),
    item.queryId ?? "",
    item.question,
    item.answerSummary,
    item.feedbackType,
    item.comment,
    item.reportedBy,
    item.handledBy ?? "",
    ...item.articleKeys,
    ...item.sourcePaths,
  ].some((value) => value.toLocaleLowerCase().includes(normalized)));
}

function parseStatus(value: string | null): QueryFeedbackStatus {
  return value === "ALL" || value === "RESOLVED" || value === "DISMISSED" ? value : "PENDING";
}

function parseLimit(value: string | null) {
  const parsed = Number(value);
  return [20, 50, 100, 200].includes(parsed) ? parsed : 50;
}

function parseId(value: string | null) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}
