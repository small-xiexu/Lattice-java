import { useQuery, useQueryClient } from "@tanstack/react-query";
import { RefreshCw } from "lucide-react";
import { useSearchParams } from "react-router-dom";

import { qualityApi } from "../../api/contracts/quality";
import { queryKeys } from "../../api/query-keys";
import { PageHeader } from "../../components/page-header";
import { PageState } from "../../components/page-state";
import { QualityFactCardsPanel } from "./quality-fact-cards-panel";
import { QualityInspectionPanel } from "./quality-inspection-panel";
import { QualityLinkPanel } from "./quality-link-panel";
import { QualityLintPanel } from "./quality-lint-panel";
import {
  MetricGrid,
  QualitySection,
  QueryFailure,
} from "./quality-view-shared";
import { formatDelta, formatRatio, formatTime } from "./quality-utils";

const VIEWS = [
  { value: "overview", label: "总览" },
  { value: "lint", label: "Lint" },
  { value: "inspection", label: "知识检查" },
  { value: "links", label: "链接增强" },
  { value: "fact-cards", label: "Fact Card" },
] as const;
type QualityView = typeof VIEWS[number]["value"];

export default function QualityPage() {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const view = parseView(searchParams.get("view"));
  const selectView = (nextView: QualityView) => {
    const next = new URLSearchParams(searchParams);
    if (nextView === "overview") next.delete("view");
    else next.set("view", nextView);
    if (nextView !== "fact-cards") {
      next.delete("factCardId");
      next.delete("limit");
    }
    setSearchParams(next);
  };

  return (
    <div className="page-frame quality-page">
      <PageHeader
        actions={
          <button
            aria-label="刷新当前质量视图"
            className="icon-button quality-refresh-button"
            onClick={() => void queryClient.invalidateQueries({ queryKey: ["admin"] })}
            title="刷新当前质量视图"
            type="button"
          >
            <RefreshCw aria-hidden="true" size={18} />
          </button>
        }
        title="知识质量"
      />
      <div aria-label="知识质量视图" className="quality-tabs" role="tablist">
        {VIEWS.map((item) => (
          <button
            aria-controls={`quality-panel-${item.value}`}
            aria-selected={view === item.value}
            id={`quality-tab-${item.value}`}
            key={item.value}
            onClick={() => selectView(item.value)}
            role="tab"
            type="button"
          >
            {item.label}
          </button>
        ))}
      </div>
      <div
        aria-labelledby={`quality-tab-${view}`}
        className="quality-tab-panel"
        id={`quality-panel-${view}`}
        role="tabpanel"
      >
        {view === "overview" ? <QualityOverviewPanel /> : null}
        {view === "lint" ? <QualityLintPanel /> : null}
        {view === "inspection" ? <QualityInspectionPanel /> : null}
        {view === "links" ? <QualityLinkPanel /> : null}
        {view === "fact-cards" ? <QualityFactCardsPanel /> : null}
      </div>
    </div>
  );
}

function QualityOverviewPanel() {
  const overviewQuery = useQuery({
    queryKey: queryKeys.overview,
    queryFn: ({ signal }) => qualityApi.overview(signal),
  });
  const qualityQuery = useQuery({
    queryKey: queryKeys.quality.root,
    queryFn: ({ signal }) => qualityApi.quality(7, signal),
  });
  const coverageQuery = useQuery({
    queryKey: queryKeys.quality.coverage,
    queryFn: ({ signal }) => qualityApi.coverage(signal),
  });
  const omissionQuery = useQuery({
    queryKey: queryKeys.quality.omissions,
    queryFn: ({ signal }) => qualityApi.omissions(signal),
  });

  return (
    <div className="quality-overview">
      <QualitySection context="系统状态与待处理工作量" title="知识准备度">
        {overviewQuery.isPending ? <PageState status="loading" title="正在加载系统概览" /> : null}
        {overviewQuery.isError ? (
          <QueryFailure error={overviewQuery.error} onRetry={() => void overviewQuery.refetch()} title="系统概览加载失败" />
        ) : null}
        {overviewQuery.data ? (
          <>
            <MetricGrid
              items={[
                { label: "文章", value: overviewQuery.data.status.articleCount },
                { label: "源文件", value: overviewQuery.data.status.sourceFileCount },
                { label: "贡献记录", value: overviewQuery.data.status.contributionCount },
                { label: "待确认查询", value: overviewQuery.data.status.pendingQueryCount, tone: overviewQuery.data.status.pendingQueryCount ? "warning" : "default" },
                { label: "待复核文章", value: overviewQuery.data.status.reviewPendingArticleCount, tone: overviewQuery.data.status.reviewPendingArticleCount ? "warning" : "default" },
                { label: "高风险文章", value: overviewQuery.data.status.highRiskArticleCount, tone: overviewQuery.data.status.highRiskArticleCount ? "danger" : "default" },
                { label: "待确认草稿", value: overviewQuery.data.status.humanReviewDraftPendingCount },
                { label: "热点待抽检", value: overviewQuery.data.status.hotspotPendingVerificationCount },
                { label: "用户报告", value: overviewQuery.data.status.userReportedAnswerCount },
                { label: "反馈待处理", value: overviewQuery.data.status.answerFeedbackPendingCount },
              ]}
              label="系统状态指标"
            />
            {overviewQuery.data.pending.items.length ? (
              <div className="quality-pending-list">
                <h3>待确认查询</h3>
                <ul>
                  {overviewQuery.data.pending.items.map((item) => (
                    <li key={item.queryId}>
                      <span>{item.question}</span>
                      <code>{item.queryId}</code>
                      <span className="status-label">{item.reviewStatus}</span>
                    </li>
                  ))}
                </ul>
              </div>
            ) : <p className="quality-empty-line">当前没有待确认查询</p>}
          </>
        ) : null}
      </QualitySection>

      <QualitySection context="最近 7 天服务端质量采样" title="质量指标">
        {qualityQuery.isPending ? <PageState status="loading" title="正在加载质量指标" /> : null}
        {qualityQuery.isError ? (
          <QueryFailure error={qualityQuery.error} onRetry={() => void qualityQuery.refetch()} title="质量指标加载失败" />
        ) : null}
        {qualityQuery.data ? (
          <>
            <MetricGrid
              items={[
                { label: "文章总数", value: qualityQuery.data.report.totalArticles },
                { label: "已通过", value: qualityQuery.data.report.passedArticles },
                { label: "待审核", value: qualityQuery.data.report.pendingReviewArticles, tone: qualityQuery.data.report.pendingReviewArticles ? "warning" : "default" },
                { label: "需人工处理", value: qualityQuery.data.report.needsHumanReviewArticles, tone: qualityQuery.data.report.needsHumanReviewArticles ? "danger" : "default" },
                { label: "贡献记录", value: qualityQuery.data.report.contributionCount },
                { label: "源文件", value: qualityQuery.data.report.sourceFileCount },
              ]}
              label="质量计数"
            />
            <dl className="quality-trend-list">
              <div><dt>审核通过率变化</dt><dd>{formatDelta(qualityQuery.data.trend.reviewPassRateDelta)}</dd></div>
              <div><dt>证据支撑率变化</dt><dd>{formatDelta(qualityQuery.data.trend.groundingRateDelta)}</dd></div>
              <div><dt>引用可用率变化</dt><dd>{formatDelta(qualityQuery.data.trend.referentialRateDelta)}</dd></div>
              <div><dt>文章数变化</dt><dd>{formatDelta(qualityQuery.data.trend.totalArticlesDelta, false)}</dd></div>
              <div><dt>最近采样</dt><dd>{formatTime(qualityQuery.data.trend.latestMeasuredAt)}</dd></div>
            </dl>
          </>
        ) : null}
      </QualitySection>

      <div className="quality-two-column">
        <QualitySection context="源文件是否被知识文章覆盖" title="资料覆盖">
          {coverageQuery.isPending ? <PageState status="loading" title="正在加载覆盖率" /> : null}
          {coverageQuery.isError ? (
            <QueryFailure error={coverageQuery.error} onRetry={() => void coverageQuery.refetch()} title="覆盖率加载失败" />
          ) : null}
          {coverageQuery.data ? (
            <>
              <div className="coverage-value">
                <strong>{formatRatio(coverageQuery.data.coverageRatio)}</strong>
                <span>{coverageQuery.data.coveredSourceFileCount} / {coverageQuery.data.totalSourceFileCount} 个源文件</span>
              </div>
              <progress aria-label="资料覆盖率" max={1} value={coverageQuery.data.coverageRatio} />
              <PathList emptyLabel="暂无已覆盖路径" items={coverageQuery.data.coveredSourcePaths} label="已覆盖源文件路径" />
            </>
          ) : null}
        </QualitySection>
        <QualitySection context="未被任何文章引用的源文件" title="遗漏清单">
          {omissionQuery.isPending ? <PageState status="loading" title="正在加载遗漏清单" /> : null}
          {omissionQuery.isError ? (
            <QueryFailure error={omissionQuery.error} onRetry={() => void omissionQuery.refetch()} title="遗漏清单加载失败" />
          ) : null}
          {omissionQuery.data ? (
            <>
              <div className="coverage-value">
                <strong>{omissionQuery.data.omittedSourceFileCount}</strong>
                <span>共 {omissionQuery.data.totalSourceFileCount} 个源文件</span>
              </div>
              <PathList emptyLabel="当前没有遗漏文件" items={omissionQuery.data.items} label="遗漏源文件路径" />
            </>
          ) : null}
        </QualitySection>
      </div>
    </div>
  );
}

function PathList({ items, emptyLabel, label }: { items: string[]; emptyLabel: string; label: string }) {
  if (!items.length) return <p className="quality-empty-line">{emptyLabel}</p>;
  return <ul aria-label={label} className="quality-path-list" tabIndex={0}>{items.map((item) => <li key={item}><code>{item}</code></li>)}</ul>;
}

function parseView(value: string | null): QualityView {
  return VIEWS.some((item) => item.value === value) ? value as QualityView : "overview";
}
