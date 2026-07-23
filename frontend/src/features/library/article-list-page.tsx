import { useQuery } from "@tanstack/react-query";
import { Search, SlidersHorizontal } from "lucide-react";
import { FormEvent, useRef } from "react";
import { Link, useLocation, useSearchParams } from "react-router-dom";

import { isApiError } from "../../api/api-error";
import {
  articlesApi,
  type ArticleListParameters,
  type ArticleSummary,
} from "../../api/contracts/articles";
import { searchApi, type SearchHit } from "../../api/contracts/search";
import { queryKeys } from "../../api/query-keys";
import { PageHeader } from "../../components/page-header";
import { PageState } from "../../components/page-state";
import { ArticleHotspotRefresh } from "./article-hotspot-refresh";

type ArticleSearchMode = "filter" | "semantic";

export default function ArticleListPage() {
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const mode = parseMode(searchParams.get("mode"));
  const query = searchParams.get("q")?.trim() ?? "";
  const searchInputRef = useRef<HTMLInputElement>(null);
  const limit = parseLimit(searchParams.get("limit"));
  const filters = readFilters(searchParams);
  const exactParameters: ArticleListParameters = {
    query: query || undefined,
    ...filters,
  };

  const articleQuery = useQuery({
    enabled: mode === "filter",
    queryKey: queryKeys.articles.list(exactParameters),
    queryFn: ({ signal }) => articlesApi.list({ ...exactParameters, signal }),
  });
  const semanticQuery = useQuery({
    enabled: mode === "semantic" && query.length > 0,
    queryKey: queryKeys.search(query, limit),
    queryFn: ({ signal }) => searchApi.search({ question: query, limit, signal }),
  });

  const updateParameters = (changes: Record<string, string | null>) => {
    const next = new URLSearchParams(searchParams);
    Object.entries(changes).forEach(([key, value]) => {
      if (value) next.set(key, value);
      else next.delete(key);
    });
    setSearchParams(next);
  };
  const submitSearch = (event: FormEvent) => {
    event.preventDefault();
    updateParameters({ q: searchInputRef.current?.value.trim() || null });
  };
  const resetFilters = () => {
    const next = new URLSearchParams();
    if (mode === "semantic") next.set("mode", "semantic");
    setSearchParams(next);
    if (searchInputRef.current) searchInputRef.current.value = "";
  };
  const listLocation = `${location.pathname}${location.search}`;

  return (
    <div className="page-frame article-list-page">
      <PageHeader actions={<ArticleHotspotRefresh />} title="知识文章" />
      <fieldset aria-label="检索方式" className="mode-selector article-mode-selector">
        <ModeOption
          checked={mode === "filter"}
          label="属性筛选"
          onChange={() => updateParameters({ mode: null })}
          value="filter"
        />
        <ModeOption
          checked={mode === "semantic"}
          label="语义检索"
          onChange={() => updateParameters({ mode: "semantic" })}
          value="semantic"
        />
      </fieldset>

      <form className="article-search-form" onSubmit={submitSearch} role="search">
        <label className="search-field">
          <Search aria-hidden="true" size={17} />
          <span className="sr-only">搜索文章</span>
          <input
            defaultValue={query}
            key={`${mode}-${query}`}
            placeholder={mode === "semantic" ? "输入自然语言问题" : "搜索标题、正文或来源路径"}
            ref={searchInputRef}
            type="search"
          />
        </label>
        <button className="primary-button" type="submit">
          <Search aria-hidden="true" size={17} />
          搜索
        </button>
        <span aria-live="polite" className="result-count">
          {resolveCount(mode, articleQuery.data?.count, semanticQuery.data?.count)}
        </span>
      </form>

      {mode === "filter" ? (
        <>
          <div className="article-filter-bar">
            <SlidersHorizontal aria-hidden="true" size={17} />
            <SelectFilter
              label="生命周期"
              onChange={(value) => updateParameters({ lifecycle: value })}
              options={[
                ["ACTIVE", "生效"],
                ["DEPRECATED", "已废弃"],
                ["ARCHIVED", "已归档"],
              ]}
              value={searchParams.get("lifecycle") ?? ""}
            />
            <SelectFilter
              label="审核"
              onChange={(value) => updateParameters({ reviewStatus: value })}
              options={[
                ["passed", "通过"],
                ["accepted", "已接受"],
                ["needs_human_review", "待人工复核"],
                ["published", "已发布"],
              ]}
              value={searchParams.get("reviewStatus") ?? ""}
            />
            <SelectFilter
              label="风险"
              onChange={(value) => updateParameters({ riskLevel: value })}
              options={[
                ["low", "低"],
                ["medium", "中"],
                ["high", "高"],
              ]}
              value={searchParams.get("riskLevel") ?? ""}
            />
            <SelectFilter
              label="热点"
              onChange={(value) => updateParameters({ hotspot: value })}
              options={[
                ["true", "是"],
                ["false", "否"],
              ]}
              value={searchParams.get("hotspot") ?? ""}
            />
            <SelectFilter
              label="需抽检"
              onChange={(value) => updateParameters({ verify: value })}
              options={[
                ["true", "是"],
                ["false", "否"],
              ]}
              value={searchParams.get("verify") ?? ""}
            />
            <label className="article-text-filter">
              <span>资料源</span>
              <input
                min="1"
                onChange={(event) => updateParameters({ sourceId: event.target.value })}
                placeholder="ID"
                type="number"
                value={searchParams.get("sourceId") ?? ""}
              />
            </label>
            <label className="article-text-filter">
              <span>风险原因</span>
              <input
                onChange={(event) => updateParameters({ riskReason: event.target.value })}
                placeholder="精确值"
                type="text"
                value={searchParams.get("riskReason") ?? ""}
              />
            </label>
          </div>
          <ArticleResults
            error={articleQuery.error}
            filtered={hasExactFilters(searchParams)}
            loading={articleQuery.isPending}
            onReset={resetFilters}
            onRetry={() => void articleQuery.refetch()}
            origin={listLocation}
            articles={articleQuery.data?.items}
          />
        </>
      ) : (
        <SemanticWorkspace
          error={semanticQuery.error}
          hits={semanticQuery.data?.items}
          limit={limit}
          loading={semanticQuery.isPending && query.length > 0}
          onLimitChange={(value) => updateParameters({ limit: String(value) })}
          onRetry={() => void semanticQuery.refetch()}
          origin={listLocation}
          query={query}
        />
      )}
    </div>
  );
}

function ModeOption({
  checked,
  label,
  onChange,
  value,
}: {
  checked: boolean;
  label: string;
  onChange: () => void;
  value: ArticleSearchMode;
}) {
  return (
    <label className="mode-option">
      <input checked={checked} name="article-search-mode" onChange={onChange} type="radio" value={value} />
      <span>{label}</span>
    </label>
  );
}

function SelectFilter({
  label,
  onChange,
  options,
  value,
}: {
  label: string;
  onChange: (value: string | null) => void;
  options: [string, string][];
  value: string;
}) {
  return (
    <label className="filter-field">
      <span>{label}</span>
      <select onChange={(event) => onChange(event.target.value || null)} value={value}>
        <option value="">全部</option>
        {options.map(([optionValue, optionLabel]) => (
          <option key={optionValue} value={optionValue}>{optionLabel}</option>
        ))}
      </select>
    </label>
  );
}

function ArticleResults({
  articles,
  error,
  filtered,
  loading,
  onReset,
  onRetry,
  origin,
}: {
  articles?: ArticleSummary[];
  error: unknown;
  filtered: boolean;
  loading: boolean;
  onReset: () => void;
  onRetry: () => void;
  origin: string;
}) {
  if (loading) return <PageState status="loading" title="正在加载文章" />;
  if (error) {
    return <PageState actionLabel="重试" description={resolveErrorMessage(error)} onAction={onRetry} status="error" title="文章加载失败" />;
  }
  if (!articles?.length) {
    return (
      <PageState
        actionLabel={filtered ? "清除筛选" : undefined}
        onAction={filtered ? onReset : undefined}
        status="empty"
        title={filtered ? "没有符合条件的文章" : "暂无知识文章"}
      />
    );
  }
  return (
    <div className="data-table-scroll article-list-region">
      <table aria-label="知识文章列表" className="data-table article-table">
        <thead><tr><th scope="col">文章</th><th scope="col">状态</th><th scope="col">风险</th><th scope="col">来源</th><th scope="col">更新时间</th></tr></thead>
        <tbody>
          {articles.map((article) => (
            <tr key={`${article.sourceId ?? "global"}-${article.articleKey}`}>
              <td data-label="文章">
                <ArticleLink articleKey={article.articleKey} origin={origin} sourceId={article.sourceId}>{article.title}</ArticleLink>
                <code className="article-identity">{article.conceptId}</code>
                {article.summary ? <span className="article-summary">{article.summary}</span> : null}
              </td>
              <td data-label="状态"><StatusLabel value={article.lifecycle} /><span className="article-secondary-state">审核：{labelValue(article.reviewStatus)}</span></td>
              <td data-label="风险"><StatusLabel value={article.riskLevel} /><span className="article-secondary-state">{article.isHotspot ? "热点" : article.requiresResultVerification ? "需抽检" : "常规"}</span></td>
              <td data-label="来源"><span>{article.sourceId ? `资料源 #${article.sourceId}` : "多源文章"}</span><code className="article-source-path">{article.primarySourcePath ?? "--"}</code></td>
              <td data-label="更新时间"><time dateTime={article.updatedAt ?? undefined}>{formatDateTime(article.updatedAt)}</time></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function SemanticWorkspace({
  error,
  hits,
  limit,
  loading,
  onLimitChange,
  onRetry,
  origin,
  query,
}: {
  error: unknown;
  hits?: SearchHit[];
  limit: number;
  loading: boolean;
  onLimitChange: (limit: number) => void;
  onRetry: () => void;
  origin: string;
  query: string;
}) {
  if (!query) return <PageState description="语义检索按问题返回文章、资料源和其他证据。" status="empty" title="输入问题开始检索" />;
  if (loading) return <PageState status="loading" title="正在检索证据" />;
  if (error) return <PageState actionLabel="重试" description={resolveErrorMessage(error)} onAction={onRetry} status="error" title="语义检索失败" />;
  if (!hits?.length) return <PageState status="empty" title="没有检索到相关证据" />;
  return (
    <section className="semantic-results" aria-label="语义检索结果">
      <div className="semantic-result-controls">
        <label className="filter-field"><span>返回上限</span><select onChange={(event) => onLimitChange(Number(event.target.value))} value={limit}>{[10, 20, 50].map((value) => <option key={value} value={value}>{value}</option>)}</select></label>
      </div>
      <ol>
        {hits.map((hit, index) => (
          <li key={`${hit.evidenceType}-${hit.articleKey ?? hit.conceptId ?? index}-${index}`}>
            <header>
              <span className="evidence-type-label">{labelEvidenceType(hit.evidenceType)}</span>
              <span className="semantic-score">相关度 {formatScore(hit.score)}</span>
            </header>
            {hit.evidenceType === "ARTICLE" && hit.articleKey ? (
              <ArticleLink articleKey={hit.articleKey} origin={origin} sourceId={hit.sourceId}>{hit.title}</ArticleLink>
            ) : hit.sourceId ? (
              <Link className="article-title-link" to={`/library/sources/${hit.sourceId}?view=files`}>{hit.title}</Link>
            ) : (
              <strong className="semantic-title">{hit.title}</strong>
            )}
            <p>{truncate(stripFrontmatter(hit.content), 280)}</p>
            {hit.sourcePaths[0] ? <code>{hit.sourcePaths[0]}</code> : null}
          </li>
        ))}
      </ol>
    </section>
  );
}

function ArticleLink({ articleKey, children, origin, sourceId }: { articleKey: string; children: string; origin: string; sourceId: number | null }) {
  return (
    <Link
      className="article-title-link"
      state={{ from: origin }}
      to={{ pathname: `/library/articles/${encodeURIComponent(articleKey)}`, search: sourceId ? `?sourceId=${sourceId}` : "" }}
    >
      {children}
    </Link>
  );
}

function StatusLabel({ value }: { value: string }) {
  return <span className={`status-label is-${value.toLowerCase().replaceAll("_", "-")}`}>{labelValue(value)}</span>;
}

function readFilters(searchParams: URLSearchParams): Omit<ArticleListParameters, "query" | "signal"> {
  const sourceId = Number(searchParams.get("sourceId"));
  return {
    lifecycle: optional(searchParams.get("lifecycle")),
    sourceId: Number.isInteger(sourceId) && sourceId > 0 ? sourceId : undefined,
    reviewStatus: optional(searchParams.get("reviewStatus")),
    riskLevel: optional(searchParams.get("riskLevel")),
    riskReason: optional(searchParams.get("riskReason")),
    isHotspot: parseBoolean(searchParams.get("hotspot")),
    requiresResultVerification: parseBoolean(searchParams.get("verify")),
  };
}

function parseMode(value: string | null): ArticleSearchMode {
  return value === "semantic" ? "semantic" : "filter";
}

function parseLimit(value: string | null) {
  const parsed = Number(value);
  return [10, 20, 50].includes(parsed) ? parsed : 20;
}

function parseBoolean(value: string | null) {
  if (value === "true") return true;
  if (value === "false") return false;
  return undefined;
}

function optional(value: string | null) {
  return value?.trim() || undefined;
}

function hasExactFilters(searchParams: URLSearchParams) {
  return ["q", "lifecycle", "sourceId", "reviewStatus", "riskLevel", "riskReason", "hotspot", "verify"].some((key) => searchParams.has(key));
}

function resolveCount(mode: ArticleSearchMode, exact?: number, semantic?: number) {
  const value = mode === "filter" ? exact : semantic;
  return value === undefined ? "-- 项" : `${value} 项`;
}

function labelValue(value: string) {
  const labels: Record<string, string> = {
    ACTIVE: "生效",
    DEPRECATED: "已废弃",
    ARCHIVED: "已归档",
    passed: "通过",
    accepted: "已接受",
    needs_human_review: "待人工复核",
    published: "已发布",
    low: "低",
    medium: "中",
    high: "高",
  };
  return labels[value] ?? value;
}

function labelEvidenceType(value: string) {
  return { ARTICLE: "文章", SOURCE: "资料源", FACT_CARD: "事实卡" }[value] ?? value;
}

function formatScore(value: number) {
  return Number.isFinite(value) ? value.toFixed(4) : "--";
}

function formatDateTime(value: string | null) {
  if (!value) return "--";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "--";
  return new Intl.DateTimeFormat("zh-CN", { dateStyle: "medium", timeStyle: "short" }).format(date);
}

function truncate(value: string, length: number) {
  const normalized = value.replace(/\s+/g, " ").trim();
  return normalized.length > length ? `${normalized.slice(0, length)}...` : normalized;
}

function stripFrontmatter(content: string) {
  if (!content.startsWith("---")) return content;
  const closing = content.indexOf("\n---", 3);
  return closing === -1 ? content : content.slice(closing + 4).trimStart();
}

function resolveErrorMessage(error: unknown) {
  return isApiError(error) ? error.message : "请求未能完成，请重试。";
}
