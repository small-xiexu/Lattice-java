import { useQueryClient } from "@tanstack/react-query";
import { RefreshCw } from "lucide-react";
import { useSearchParams } from "react-router-dom";

import { PageHeader } from "../../components/page-header";
import { CompileReviewPolicy } from "./compile-review-policy";
import { CompileReviewQueue } from "./compile-review-queue";
import { QueryPendingQueue } from "./query-pending-queue";

type ReviewType = "compile" | "query";
type CompileView = "queue" | "policy";

export default function ReviewsPage() {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const type = parseType(searchParams.get("type"));
  const view = parseView(searchParams.get("view"));

  const updateSearch = (values: Record<string, string | null>) => {
    const next = new URLSearchParams(searchParams);
    Object.entries(values).forEach(([key, value]) => {
      if (value === null) next.delete(key);
      else next.set(key, value);
    });
    setSearchParams(next);
  };

  const selectType = (nextType: ReviewType) => {
    updateSearch({
      type: nextType === "compile" ? null : nextType,
      view: null,
      id: null,
      status: null,
      jobId: null,
      queryId: null,
      q: null,
    });
  };

  return (
    <div className="page-frame reviews-page">
      <PageHeader
        actions={
          <button
            aria-label="刷新当前审核视图"
            className="icon-button reviews-refresh-button"
            onClick={() => void queryClient.invalidateQueries({ queryKey: ["admin"] })}
            title="刷新当前审核视图"
            type="button"
          >
            <RefreshCw aria-hidden="true" size={18} />
          </button>
        }
        context="compile review / query pending"
        title="人工审核"
      />

      <div aria-label="人工审核类型" className="reviews-tabs" role="tablist">
        <ReviewTab active={type === "compile"} id="compile" label="编译审核" onSelect={() => selectType("compile")} />
        <ReviewTab active={type === "query"} id="query" label="Query Pending" onSelect={() => selectType("query")} />
      </div>

      <div aria-labelledby={`reviews-tab-${type}`} id="reviews-panel" role="tabpanel">
        {type === "compile" ? (
          <>
            <div aria-label="编译审核视图" className="reviews-subtabs">
              <button
                aria-pressed={view === "queue"}
                onClick={() => updateSearch({ view: null, id: null })}
                type="button"
              >
                审核队列
              </button>
              <button
                aria-pressed={view === "policy"}
                onClick={() => updateSearch({ view: "policy", id: null })}
                type="button"
              >
                策略配置
              </button>
            </div>
            {view === "queue" ? <CompileReviewQueue /> : <CompileReviewPolicy />}
          </>
        ) : <QueryPendingQueue />}
      </div>
    </div>
  );
}

function ReviewTab({
  active,
  id,
  label,
  onSelect,
}: {
  active: boolean;
  id: ReviewType;
  label: string;
  onSelect: () => void;
}) {
  return (
    <button
      aria-controls="reviews-panel"
      aria-selected={active}
      id={`reviews-tab-${id}`}
      onClick={onSelect}
      role="tab"
      type="button"
    >
      {label}
    </button>
  );
}

function parseType(value: string | null): ReviewType {
  return value === "query" ? "query" : "compile";
}

function parseView(value: string | null): CompileView {
  return value === "policy" ? "policy" : "queue";
}
