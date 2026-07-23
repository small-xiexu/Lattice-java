import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, ExternalLink, FileText, Link2 } from "lucide-react";
import { Link, useLocation, useParams, useSearchParams } from "react-router-dom";

import { isApiError } from "../../api/api-error";
import {
  articlesApi,
  type ArticleDetail,
  type ArticleSummary,
} from "../../api/contracts/articles";
import { queryKeys } from "../../api/query-keys";
import { MarkdownReport } from "../../components/markdown-report";
import { PageHeader } from "../../components/page-header";
import { PageState } from "../../components/page-state";
import { ArticleGovernance } from "./article-governance";

export default function ArticleDetailPage() {
  const location = useLocation();
  const { articleKey = "" } = useParams();
  const [searchParams] = useSearchParams();
  const sourceId = parseSourceId(searchParams.get("sourceId"));
  const returnTo = resolveReturnLocation(location.state);
  const articleQuery = useQuery({
    enabled: articleKey.length > 0,
    queryKey: queryKeys.articles.detail(articleKey, sourceId),
    queryFn: ({ signal }) => articlesApi.detail(articleKey, sourceId, signal),
  });
  const relationIndexQuery = useQuery({
    enabled: articleQuery.isSuccess,
    queryKey: queryKeys.articles.list({ scope: "relation-index" }),
    queryFn: ({ signal }) => articlesApi.list({ signal }),
  });

  if (articleQuery.isPending) {
    return <ArticlePageFrame articleKey={articleKey} returnTo={returnTo}><PageState status="loading" title="正在加载文章" /></ArticlePageFrame>;
  }
  if (articleQuery.isError) {
    return (
      <ArticlePageFrame articleKey={articleKey} returnTo={returnTo}>
        <PageState actionLabel="重试" description={resolveErrorMessage(articleQuery.error)} onAction={() => void articleQuery.refetch()} status="error" title="文章加载失败" />
      </ArticlePageFrame>
    );
  }

  const article = articleQuery.data;
  return (
    <div className="page-frame article-detail-page">
      <BackLink returnTo={returnTo} />
      <PageHeader context={article.articleKey} title={article.title} />
      <div className="article-detail-statuses">
        <StatusLabel value={article.lifecycle} />
        <span>审核：{labelValue(article.reviewStatus)}</span>
        <span>风险：{labelValue(article.riskLevel)}</span>
        {article.confidence ? <span>置信度：{labelValue(article.confidence)}</span> : null}
        {article.isHotspot ? <span>热点文章</span> : null}
        {article.requiresResultVerification ? <span>需要结果抽检</span> : null}
      </div>
      <ArticleGovernance article={article} />
      <div className="article-detail-layout">
        <section className="article-content-pane" aria-label="文章内容">
          {article.summary ? (
            <section className="article-summary-band" aria-labelledby="article-summary-title">
              <h2 id="article-summary-title">摘要</h2>
              <p>{article.summary}</p>
            </section>
          ) : null}
          <section className="article-body" aria-labelledby="article-body-title">
            <h2 id="article-body-title">正文</h2>
            <MarkdownReport content={stripFrontmatter(article.content)} label="文章正文" />
          </section>
        </section>
        <aside className="article-trace-pane" aria-label="文章追溯信息">
          <TraceSources article={article} />
          <TraceRelations
            article={article}
            candidates={relationIndexQuery.data?.items}
            label="依赖关系"
            loading={relationIndexQuery.isPending}
            relations={article.dependsOn}
          />
          <TraceRelations
            article={article}
            candidates={relationIndexQuery.data?.items}
            label="相关关系"
            loading={relationIndexQuery.isPending}
            relations={article.related}
          />
          <TraceKeywords keywords={article.referentialKeywords} />
          <ArticleMetadata article={article} />
        </aside>
      </div>
    </div>
  );
}

function ArticlePageFrame({ articleKey, children, returnTo }: { articleKey: string; children: React.ReactNode; returnTo: string }) {
  return (
    <div className="page-frame article-detail-page">
      <BackLink returnTo={returnTo} />
      <PageHeader context={articleKey} title="文章详情" />
      {children}
    </div>
  );
}

function BackLink({ returnTo }: { returnTo: string }) {
  return <Link className="source-back-link" to={returnTo}><ArrowLeft aria-hidden="true" size={16} />返回文章列表</Link>;
}

function TraceSources({ article }: { article: ArticleDetail }) {
  return (
    <section className="trace-section">
      <h2><FileText aria-hidden="true" size={17} />来源文件 <span>{article.sourceCount}</span></h2>
      {article.sourcePaths.length ? (
        <ul className="trace-link-list">
          {article.sourcePaths.map((path) => (
            <li key={path}>
              {article.sourceId ? (
                <Link to={`/library/sources/${article.sourceId}?view=files`}><code>{path}</code><ExternalLink aria-hidden="true" size={14} /></Link>
              ) : <code>{path}</code>}
            </li>
          ))}
        </ul>
      ) : <p className="trace-empty">未提供来源路径</p>}
    </section>
  );
}

function TraceRelations({
  article,
  candidates,
  label,
  loading,
  relations,
}: {
  article: ArticleDetail;
  candidates?: ArticleSummary[];
  label: string;
  loading: boolean;
  relations: string[];
}) {
  return (
    <section className="trace-section">
      <h2><Link2 aria-hidden="true" size={17} />{label} <span>{relations.length}</span></h2>
      {relations.length ? (
        <ul className="relation-list">
          {relations.map((relation) => (
            <li key={relation}>
              <ResolvedRelation
                article={article}
                candidates={candidates}
                loading={loading}
                relation={relation}
              />
            </li>
          ))}
        </ul>
      ) : <p className="trace-empty">无{label}</p>}
    </section>
  );
}

function ResolvedRelation({
  article,
  candidates,
  loading,
  relation,
}: {
  article: ArticleDetail;
  candidates?: ArticleSummary[];
  loading: boolean;
  relation: string;
}) {
  const target = resolveRelationTarget(article.sourceId, relation, candidates ?? []);
  if (target) {
    return (
      <Link
        to={{
          pathname: `/library/articles/${encodeURIComponent(target.articleKey)}`,
          search: target.sourceId ? `?sourceId=${target.sourceId}` : "",
        }}
      >
        {relation}
      </Link>
    );
  }
  return (
    <span className="unresolved-relation">
      <code>{relation}</code>
      <small>{loading ? "解析中" : "未收录"}</small>
    </span>
  );
}

function TraceKeywords({ keywords }: { keywords: string[] }) {
  if (!keywords.length) return null;
  return (
    <section className="trace-section">
      <h2>明确性关键词 <span>{keywords.length}</span></h2>
      <ul className="keyword-list">{keywords.map((keyword) => <li key={keyword}>{keyword}</li>)}</ul>
    </section>
  );
}

function ArticleMetadata({ article }: { article: ArticleDetail }) {
  return (
    <section className="trace-section">
      <h2>文章信息</h2>
      <dl className="article-metadata-list">
        <div><dt>conceptId</dt><dd><code>{article.conceptId}</code></dd></div>
        <div><dt>资料源</dt><dd>{article.sourceId ? `#${article.sourceId}` : "多源"}</dd></div>
        <div><dt>编译时间</dt><dd>{formatDateTime(article.compiledAt)}</dd></div>
        <div><dt>更新时间</dt><dd>{formatDateTime(article.updatedAt)}</dd></div>
        <div><dt>标题模式</dt><dd>{article.titleProfile?.titleGenerationMode ?? "--"}</dd></div>
      </dl>
      {article.riskReasons.length ? <p className="article-risk-reasons">风险原因：{article.riskReasons.join("、")}</p> : null}
    </section>
  );
}

function StatusLabel({ value }: { value: string }) {
  return <span className={`status-label is-${value.toLowerCase().replaceAll("_", "-")}`}>{labelValue(value)}</span>;
}

function stripFrontmatter(content: string) {
  if (!content.startsWith("---")) return content;
  const closing = content.indexOf("\n---", 3);
  return closing === -1 ? content : content.slice(closing + 4).trimStart();
}

function parseSourceId(value: string | null) {
  if (!value) return undefined;
  const sourceId = Number(value);
  return Number.isInteger(sourceId) && sourceId > 0 ? sourceId : undefined;
}

function resolveRelationTarget(
  currentSourceId: number | null,
  relation: string,
  candidates: ArticleSummary[],
) {
  const matches = candidates.filter((candidate) => candidate.conceptId === relation);
  const scopedMatches = currentSourceId
    ? matches.filter((candidate) => candidate.sourceId === currentSourceId)
    : [];
  if (scopedMatches.length === 1) return scopedMatches[0];
  return matches.length === 1 ? matches[0] : undefined;
}

function resolveReturnLocation(state: unknown) {
  if (typeof state === "object" && state !== null && "from" in state && typeof state.from === "string" && state.from.startsWith("/library/articles")) return state.from;
  return "/library/articles";
}

function labelValue(value: string) {
  return { ACTIVE: "生效", DEPRECATED: "已废弃", ARCHIVED: "已归档", passed: "通过", accepted: "已接受", needs_human_review: "待人工复核", published: "已发布", low: "低", medium: "中", high: "高" }[value] ?? value;
}

function formatDateTime(value: string | null) {
  if (!value) return "--";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "--";
  return new Intl.DateTimeFormat("zh-CN", { dateStyle: "medium", timeStyle: "short" }).format(date);
}

function resolveErrorMessage(error: unknown) {
  return isApiError(error) ? error.message : "请求未能完成，请重试。";
}
