import { useQuery } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight, Search, Upload } from "lucide-react";
import { useRef, useState } from "react";
import { Link } from "react-router-dom";

import { isApiError } from "../../api/api-error";
import {
  sourceStatusSchema,
  sourceTypeSchema,
  sourcesApi,
  type SourceStatus,
  type SourceSummary,
  type SourceType,
} from "../../api/contracts/sources";
import { queryKeys } from "../../api/query-keys";
import { PageHeader } from "../../components/page-header";
import { PageState } from "../../components/page-state";
import { useListUrlState } from "../../state/list-url-state";
import { SourceImportWorkspace } from "./source-import-workspace";

const SOURCE_TYPE_LABELS: Record<SourceType, string> = {
  UPLOAD: "本地上传",
  GIT: "Git",
  INTERNAL_MIRROR: "内部镜像",
};

const SOURCE_STATUS_LABELS: Record<SourceStatus, string> = {
  ACTIVE: "启用",
  DISABLED: "停用",
  ARCHIVED: "已归档",
};

const SYNC_STATUS_LABELS: Record<string, string> = {
  QUEUED: "等待中",
  PENDING: "等待中",
  RUNNING: "同步中",
  SUCCEEDED: "成功",
  SUCCESS: "成功",
  FAILED: "失败",
  CANCELLED: "已取消",
  CANCELED: "已取消",
};

export default function SourceListPage() {
  const importTriggerRef = useRef<HTMLButtonElement>(null);
  const [importOpen, setImportOpen] = useState(false);
  const [listState, setListState] = useListUrlState();
  const status = parseSourceStatus(listState.status);
  const sourceType = parseSourceType(listState.sourceType);
  const parameters = {
    keyword: listState.query || undefined,
    status,
    sourceType,
    page: listState.page,
    size: listState.size,
  };
  const sourceQuery = useQuery({
    queryKey: queryKeys.sources.list(parameters),
    queryFn: ({ signal }) => sourcesApi.list({ ...parameters, signal }),
  });

  const resetFilters = () =>
    setListState({ query: "", status: null, sourceType: null, page: 1 });

  return (
    <div className="page-frame source-list-page">
      <PageHeader
        actions={
          <button
            className="primary-button"
            onClick={() => setImportOpen(true)}
            ref={importTriggerRef}
            type="button"
          >
            <Upload aria-hidden="true" size={17} />
            导入资料
          </button>
        }
        title="资料源"
      />
      {importOpen ? (
        <SourceImportWorkspace
          onClose={() => {
            setImportOpen(false);
            requestAnimationFrame(() => importTriggerRef.current?.focus());
          }}
        />
      ) : null}
      <div className="page-toolbar source-toolbar">
        <label className="search-field">
          <Search aria-hidden="true" size={17} />
          <span className="sr-only">搜索资料源</span>
          <input
            onChange={(event) =>
              setListState(
                { query: event.target.value, page: 1 },
                { replace: true },
              )
            }
            placeholder="搜索名称或编码"
            type="search"
            value={listState.query}
          />
        </label>
        <label className="filter-field">
          <span>状态</span>
          <select
            onChange={(event) =>
              setListState({ status: event.target.value || null, page: 1 })
            }
            value={status ?? ""}
          >
            <option value="">全部</option>
            <option value="ACTIVE">启用</option>
            <option value="DISABLED">停用</option>
            <option value="ARCHIVED">已归档</option>
          </select>
        </label>
        <label className="filter-field">
          <span>类型</span>
          <select
            onChange={(event) =>
              setListState({ sourceType: event.target.value || null, page: 1 })
            }
            value={sourceType ?? ""}
          >
            <option value="">全部</option>
            <option value="UPLOAD">本地上传</option>
            <option value="GIT">Git</option>
            <option value="INTERNAL_MIRROR">内部镜像</option>
          </select>
        </label>
        <span aria-live="polite" className="result-count">
          {sourceQuery.data ? `${sourceQuery.data.total} 项` : "-- 项"}
        </span>
      </div>

      <div aria-busy={sourceQuery.isPending} className="source-list-region">
        {sourceQuery.isPending ? (
          <PageState status="loading" title="正在加载资料源" />
        ) : sourceQuery.isError ? (
          <PageState
            actionLabel="重试"
            description={resolveErrorMessage(sourceQuery.error)}
            onAction={() => void sourceQuery.refetch()}
            status="error"
            title="资料源加载失败"
          />
        ) : sourceQuery.data.items.length === 0 ? (
          <PageState
            actionLabel={hasFilters(listState) ? "清除筛选" : undefined}
            onAction={hasFilters(listState) ? resetFilters : undefined}
            status="empty"
            title={hasFilters(listState) ? "没有符合条件的资料源" : "暂无资料源"}
          />
        ) : (
          <>
            <SourceTable sources={sourceQuery.data.items} />
            <Pagination
              page={sourceQuery.data.page}
              size={sourceQuery.data.size}
              total={sourceQuery.data.total}
              onPageChange={(page) => setListState({ page })}
              onSizeChange={(size) => setListState({ size, page: 1 })}
            />
          </>
        )}
      </div>
    </div>
  );
}

function SourceTable({ sources }: { sources: SourceSummary[] }) {
  return (
    <div className="data-table-scroll">
      <table aria-label="资料源列表" className="data-table source-table">
        <thead>
          <tr>
            <th scope="col">资料源</th>
            <th scope="col">类型</th>
            <th scope="col">状态</th>
            <th scope="col">最近同步</th>
            <th scope="col">更新时间</th>
          </tr>
        </thead>
        <tbody>
          {sources.map((source) => (
            <tr key={source.id}>
              <td data-label="资料源">
                <Link
                  className="source-name-link"
                  to={`/library/sources/${source.id}`}
                >
                  {source.displayName}
                </Link>
                {source.primaryDocumentTitle ? (
                  <span className="source-document-title">
                    {source.primaryDocumentTitle}
                  </span>
                ) : null}
                <code className="source-code">{source.sourceCode}</code>
              </td>
              <td data-label="类型">{SOURCE_TYPE_LABELS[source.sourceType]}</td>
              <td data-label="状态">
                <span className={`status-label is-${source.status.toLowerCase()}`}>
                  {SOURCE_STATUS_LABELS[source.status]}
                </span>
              </td>
              <td data-label="最近同步">
                <span>{resolveSyncStatus(source.lastSyncStatus)}</span>
                <time dateTime={source.lastSyncAt ?? undefined}>
                  {formatDateTime(source.lastSyncAt)}
                </time>
              </td>
              <td data-label="更新时间">
                <time dateTime={source.updatedAt ?? undefined}>
                  {formatDateTime(source.updatedAt)}
                </time>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

interface PaginationProps {
  page: number;
  size: number;
  total: number;
  onPageChange: (page: number) => void;
  onSizeChange: (size: number) => void;
}

function Pagination({
  page,
  size,
  total,
  onPageChange,
  onSizeChange,
}: PaginationProps) {
  const pageCount = Math.max(1, Math.ceil(total / size));
  return (
    <nav aria-label="资料源分页" className="table-pagination">
      <label>
        <span>每页</span>
        <select
          aria-label="每页数量"
          onChange={(event) => onSizeChange(Number(event.target.value))}
          value={size}
        >
          {[10, 20, 50, 100].map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      </label>
      <span className="pagination-summary">
        第 {page} / {pageCount} 页
      </span>
      <button
        aria-label="上一页"
        className="icon-button pagination-button"
        disabled={page <= 1}
        onClick={() => onPageChange(page - 1)}
        title="上一页"
        type="button"
      >
        <ChevronLeft aria-hidden="true" size={18} />
      </button>
      <button
        aria-label="下一页"
        className="icon-button pagination-button"
        disabled={page >= pageCount}
        onClick={() => onPageChange(page + 1)}
        title="下一页"
        type="button"
      >
        <ChevronRight aria-hidden="true" size={18} />
      </button>
    </nav>
  );
}

function parseSourceStatus(value: string | null) {
  const result = sourceStatusSchema.safeParse(value);
  return result.success ? result.data : undefined;
}

function parseSourceType(value: string | null) {
  const result = sourceTypeSchema.safeParse(value);
  return result.success ? result.data : undefined;
}

function hasFilters(state: {
  query: string;
  status: string | null;
  sourceType: string | null;
}) {
  return Boolean(state.query || state.status || state.sourceType);
}

function resolveSyncStatus(status: string | null) {
  if (!status) {
    return "未同步";
  }
  return SYNC_STATUS_LABELS[status] ?? "状态未知";
}

function formatDateTime(value: string | null) {
  if (!value) {
    return "--";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "--";
  }
  return new Intl.DateTimeFormat("zh-CN", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date);
}

function resolveErrorMessage(error: unknown) {
  return isApiError(error) ? error.message : "请求未能完成，请重试。";
}
