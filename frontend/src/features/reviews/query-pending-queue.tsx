import { useQuery } from "@tanstack/react-query";
import { Search } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";

import { reviewsApi, type PendingQueryItem } from "../../api/contracts/reviews";
import { queryKeys } from "../../api/query-keys";
import { InlineAlert } from "../../components/inline-alert";
import { PageState } from "../../components/page-state";
import { formatReviewTime, pendingStatusLabel, resolveReviewError } from "./review-utils";
import { QueryPendingDetail, type PendingAction } from "./query-pending-detail";

interface PendingNotice {
  title: string;
  description: string;
  tone: "success" | "error";
}

export function QueryPendingQueue() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [notice, setNotice] = useState<PendingNotice | null>(null);
  const keyword = searchParams.get("q")?.trim() ?? "";
  const selectedQueryId = searchParams.get("queryId")?.trim() ?? "";
  const query = useQuery({
    queryKey: queryKeys.reviews.pendingQueries,
    queryFn: ({ signal }) => reviewsApi.listPendingQueries(signal),
  });
  const items = useMemo(() => filterPendingItems(query.data?.items ?? [], keyword), [keyword, query.data?.items]);
  const selectedItem = items.find((item) => item.queryId === selectedQueryId) ?? null;

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
    if (items.length === 0 && selectedQueryId) {
      const next = new URLSearchParams(searchParams);
      next.delete("queryId");
      setSearchParams(next, { replace: true });
      return;
    }
    if (items.length > 0 && !selectedItem) {
      const next = new URLSearchParams(searchParams);
      next.set("queryId", items[0].queryId);
      setSearchParams(next, { replace: true });
    }
  }, [items, query.data, searchParams, selectedItem, selectedQueryId, setSearchParams]);

  const handleSuccess = (action: PendingAction, item: PendingQueryItem) => {
    const notices: Record<PendingAction, PendingNotice> = {
      correct: {
        title: "答案已更正",
        description: "服务端已基于更正说明重写答案，该记录仍等待最终确认。",
        tone: "success",
      },
      confirm: {
        title: "待确认查询已确认",
        description: "最终答案已沉淀为贡献记录，原待确认记录已移出队列。",
        tone: "success",
      },
      discard: {
        title: "待确认查询已丢弃",
        description: "记录已移出队列，未写入贡献记录。",
        tone: "success",
      },
    };
    setNotice(notices[action]);
    if (action !== "correct" && selectedQueryId === item.queryId) {
      updateSearch({ queryId: null }, true);
    }
  };

  return (
    <div className="query-pending-queue">
      <div className="reviews-toolbar query-pending-toolbar">
        <label className="reviews-search-field">
          <Search aria-hidden="true" size={15} />
          <span className="sr-only">搜索待确认查询</span>
          <input
            aria-label="搜索待确认查询"
            onChange={(event) => updateSearch({ q: event.target.value || null, queryId: null }, true)}
            placeholder="搜索问题、答案或来源"
            type="search"
            value={keyword}
          />
        </label>
        <span aria-live="polite" className="result-count">
          {query.data ? `${items.length} / ${query.data.count} 项` : "-- 项"}
        </span>
      </div>

      {notice ? (
        <InlineAlert description={notice.description} title={notice.title} tone={notice.tone} />
      ) : null}
      {query.isPending ? <PageState status="loading" title="正在加载待确认查询" /> : null}
      {query.isError ? (
        <PageState
          actionLabel="重试"
          description={resolveReviewError(query.error)}
          onAction={() => void query.refetch()}
          status="error"
          title="待确认查询加载失败"
        />
      ) : null}
      {query.data ? (
        <div className="reviews-layout">
          <section aria-label="待确认查询列表" className="reviews-list-column">
            {items.length ? (
              <QueryPendingList
                items={items}
                onSelect={(queryId) => updateSearch({ queryId })}
                selectedQueryId={selectedQueryId}
              />
            ) : (
              <PageState
                description={keyword ? "请调整搜索条件" : undefined}
                status="empty"
                title={keyword ? "没有匹配的待确认查询" : "当前没有待确认查询"}
              />
            )}
          </section>
          <section aria-label="待确认查询详情" className="reviews-detail-column">
            {selectedItem ? (
              <QueryPendingDetail
                item={selectedItem}
                onFailure={(description) => setNotice({
                  title: "治理操作失败，已刷新队列",
                  description,
                  tone: "error",
                })}
                onSuccess={handleSuccess}
              />
            ) : (
              <div className="reviews-detail-placeholder">选择一条记录核对答案与来源</div>
            )}
          </section>
        </div>
      ) : null}
    </div>
  );
}

function QueryPendingList({
  items,
  selectedQueryId,
  onSelect,
}: {
  items: PendingQueryItem[];
  selectedQueryId: string;
  onSelect: (queryId: string) => void;
}) {
  return (
    <ul className="reviews-list query-pending-list">
      {items.map((item) => (
        <li key={item.queryId}>
          <button
            aria-current={selectedQueryId === item.queryId ? "true" : undefined}
            className={selectedQueryId === item.queryId ? "is-selected" : undefined}
            onClick={() => onSelect(item.queryId)}
            type="button"
          >
            <span className="reviews-list-main">
              <strong>{item.question || "未提供问题"}</strong>
              <span>{shortQueryId(item.queryId)}</span>
            </span>
            <span className={`review-status is-${pendingStatusTone(item.reviewStatus)}`}>
              {pendingStatusLabel(item.reviewStatus)}
            </span>
            <span className="reviews-list-foot">
              <span>{item.sourceFilePaths.length} 个来源</span>
              <time dateTime={item.createdAt ?? undefined}>{formatReviewTime(item.createdAt)}</time>
            </span>
          </button>
        </li>
      ))}
    </ul>
  );
}

function filterPendingItems(items: PendingQueryItem[], keyword: string) {
  const normalized = keyword.toLocaleLowerCase();
  if (!normalized) return items;
  return items.filter((item) => [
    item.queryId,
    item.question,
    item.answer,
    item.reviewStatus,
    ...item.selectedConceptIds,
    ...item.sourceFilePaths,
  ].some((value) => value.toLocaleLowerCase().includes(normalized)));
}

function pendingStatusTone(value: string) {
  return value.toUpperCase() === "PASSED" ? "success" : "warning";
}

function shortQueryId(value: string) {
  return value.length > 22 ? `${value.slice(0, 12)}...${value.slice(-6)}` : value;
}
