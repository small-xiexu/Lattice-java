import { useQuery } from "@tanstack/react-query";
import { CheckCircle2, CircleAlert, Clock3, Search } from "lucide-react";
import { useSearchParams } from "react-router-dom";

import {
  retrievalSettingsApi,
  type RetrievalAuditDetail,
  type RetrievalAuditRun,
} from "../../api/contracts/retrieval-settings";
import { queryKeys } from "../../api/query-keys";
import { PageState } from "../../components/page-state";
import { formatLlmDateTime, llmErrorMessage } from "./llm-settings-utils";

const RECENT_LIMIT = 20;
const HISTORY_LIMIT = 5;

export function RetrievalAuditPanel() {
  const [searchParams, setSearchParams] = useSearchParams();
  const recentQuery = useQuery({
    queryKey: queryKeys.settings.retrieval.recent(RECENT_LIMIT),
    queryFn: ({ signal }) => retrievalSettingsApi.listRecent(RECENT_LIMIT, signal),
  });
  const requestedQueryId = searchParams.get("queryId")?.trim() ?? "";
  const firstQueryId = recentQuery.data?.items.find((item) => item.queryId)?.queryId ?? "";
  const selectedQueryId = requestedQueryId || firstQueryId;
  const detailQuery = useQuery({
    enabled: selectedQueryId !== "",
    queryKey: queryKeys.settings.retrieval.latest(selectedQueryId, HISTORY_LIMIT),
    queryFn: ({ signal }) => retrievalSettingsApi.getLatest(selectedQueryId, HISTORY_LIMIT, signal),
  });

  if (recentQuery.isLoading) return <PageState status="loading" title="正在读取检索审计" />;
  if (recentQuery.error || !recentQuery.data) {
    return (
      <PageState
        actionLabel="重新加载"
        description={recentQuery.error ? llmErrorMessage(recentQuery.error) : "服务端未返回审计列表"}
        onAction={() => void recentQuery.refetch()}
        status="error"
        title="检索审计读取失败"
      />
    );
  }
  if (recentQuery.data.items.length === 0) {
    return <PageState description="完成一次 Query 后，通道运行和融合命中会出现在这里。" status="empty" title="暂无检索审计" />;
  }

  const selectRun = (run: RetrievalAuditRun) => {
    if (!run.queryId) return;
    const next = new URLSearchParams(searchParams);
    next.set("view", "audits");
    next.set("queryId", run.queryId);
    setSearchParams(next);
  };

  return (
    <section aria-labelledby="retrieval-audit-title" className="retrieval-section retrieval-audit-section">
      <header className="retrieval-section-header">
        <div><h2 id="retrieval-audit-title">最近审计</h2><p>最近 {recentQuery.data.count} 次检索运行</p></div>
        <Search aria-hidden="true" size={20} />
      </header>
      <div className="retrieval-audit-layout">
        <ol aria-label="最近检索审计" className="retrieval-audit-list" tabIndex={0}>
          {recentQuery.data.items.map((run, index) => (
            <li key={run.runId ?? `${run.queryId}-${index}`}>
              <button
                className={run.queryId === selectedQueryId ? "is-selected" : ""}
                onClick={() => selectRun(run)}
                type="button"
              >
                <strong>{run.question || "未记录问题"}</strong>
                <span><code>{run.queryId || "--"}</code><time>{formatLlmDateTime(run.createdAt)}</time></span>
                <small>{run.retrievalMode || "--"} · 融合 {run.fusedHitCount} · 通道 {run.channelCount}</small>
              </button>
            </li>
          ))}
        </ol>
        <div className="retrieval-audit-detail">
          {detailQuery.isLoading ? <PageState status="loading" title="正在读取审计详情" /> : null}
          {detailQuery.error ? (
            <PageState
              actionLabel="重新加载"
              description={llmErrorMessage(detailQuery.error)}
              onAction={() => void detailQuery.refetch()}
              status="error"
              title="审计详情读取失败"
            />
          ) : null}
          {detailQuery.data?.found && detailQuery.data.latestRun ? (
            <AuditDetail detail={detailQuery.data} />
          ) : null}
          {detailQuery.data && !detailQuery.data.found ? (
            <PageState status="empty" title="该 Query 暂无审计详情" />
          ) : null}
        </div>
      </div>
    </section>
  );
}

function AuditDetail({ detail }: { detail: RetrievalAuditDetail }) {
  const run = detail.latestRun;
  if (!run) return null;
  return (
    <article aria-label="检索审计详情" className="retrieval-audit-card">
      <header>
        <div><h3>{run.question || "未记录问题"}</h3><code>{detail.queryId}</code></div>
        <span className={`retrieval-coverage is-${coverageTone(run.coverageStatus)}`}>{run.coverageStatus || "未标记"}</span>
      </header>
      <dl className="retrieval-audit-summary">
        <div><dt>实际检索文本</dt><dd>{run.retrievalQuestion || "--"}</dd></div>
        <div><dt>策略</dt><dd>{run.strategyTag || "--"}</dd></div>
        <div><dt>融合命中</dt><dd>{run.fusedHitCount}</dd></div>
        <div><dt>Fact Card</dt><dd>{run.factCardHitCount}</dd></div>
        <div><dt>资料分块</dt><dd>{run.sourceChunkHitCount}</dd></div>
        <div><dt>查询改写</dt><dd>{run.rewriteApplied ? "已应用" : "未应用"}</dd></div>
      </dl>
      <section aria-labelledby="retrieval-channels-title" className="retrieval-channel-section">
        <h4 id="retrieval-channels-title">通道运行</h4>
        <ul>
          {run.channelRuns.map((channel) => (
            <li key={channel.channelName}>
              {channel.status === "SUCCESS" && !channel.zeroHit ? <CheckCircle2 aria-hidden="true" size={16} /> : <CircleAlert aria-hidden="true" size={16} />}
              <strong>{channel.channelName}</strong>
              <span>{channel.hitCount} 命中</span>
              <span><Clock3 aria-hidden="true" size={13} />{channel.durationMillis} ms</span>
              {channel.errorSummary ? <small>{channel.errorSummary}</small> : null}
              {channel.skippedReason ? <small>{channel.skippedReason}</small> : null}
            </li>
          ))}
        </ul>
      </section>
      <section aria-labelledby="retrieval-hits-title" className="retrieval-hit-section">
        <h4 id="retrieval-hits-title">融合命中 <span>{detail.channelHitCount}</span></h4>
        {detail.channelHits.length === 0 ? <p>该 run 未记录通道命中明细</p> : (
          <div aria-label="融合命中表格" className="retrieval-hit-table-wrap" role="region" tabIndex={0}>
            <table>
              <thead><tr><th>融合排名</th><th>通道</th><th>证据</th><th>原始排名</th><th>权重</th></tr></thead>
              <tbody>
                {detail.channelHits.map((hit, index) => (
                  <tr key={hit.hitId ?? index}>
                    <td>{hit.fusedRank ?? "未进入"}</td>
                    <td><code>{hit.channelName || "--"}</code></td>
                    <td>{hit.title || hit.articleKey || hit.conceptId || "--"}</td>
                    <td>{hit.hitRank}</td>
                    <td>{hit.channelWeight}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
      <p className="retrieval-history-note">另有 {detail.historyCount} 条历史 run</p>
    </article>
  );
}

function coverageTone(status: string | null) {
  if (status === "sufficient") return "success";
  if (status === "empty") return "danger";
  return "neutral";
}
