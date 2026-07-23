import { useQuery } from "@tanstack/react-query";
import { ExternalLink } from "lucide-react";
import { Link, useSearchParams } from "react-router-dom";

import { qualityApi, type FactCard } from "../../api/contracts/quality";
import { queryKeys } from "../../api/query-keys";
import { PageState } from "../../components/page-state";
import { formatTime } from "./quality-utils";
import { MetricGrid, QualitySection, QueryFailure } from "./quality-view-shared";

const LIMITS = [10, 25, 50, 100, 200] as const;

export function QualityFactCardsPanel() {
  const [searchParams, setSearchParams] = useSearchParams();
  const limit = parseLimit(searchParams.get("limit"));
  const factCardId = parseId(searchParams.get("factCardId"));
  const summaryQuery = useQuery({
    queryKey: queryKeys.quality.factCardSummary,
    queryFn: ({ signal }) => qualityApi.factCardSummary(signal),
  });
  const listQuery = useQuery({
    queryKey: queryKeys.quality.factCards(limit),
    queryFn: ({ signal }) => qualityApi.factCards(limit, signal),
  });
  const detailQuery = useQuery({
    enabled: factCardId !== null,
    queryKey: queryKeys.quality.factCardDetail(factCardId ?? 0),
    queryFn: ({ signal }) => qualityApi.factCard(factCardId as number, signal),
  });
  const updateParameters = (changes: Record<string, string | null>) => {
    const next = new URLSearchParams(searchParams);
    Object.entries(changes).forEach(([key, value]) => value ? next.set(key, value) : next.delete(key));
    next.set("view", "fact-cards");
    setSearchParams(next);
  };

  return (
    <div className="fact-card-workspace">
      <QualitySection context="结构化证据卡总量、类型、审查状态和来源完整性" title="Fact Card 汇总">
        {summaryQuery.isPending ? <PageState status="loading" title="正在加载 Fact Card 汇总" /> : null}
        {summaryQuery.isError ? <QueryFailure error={summaryQuery.error} onRetry={() => void summaryQuery.refetch()} title="Fact Card 汇总加载失败" /> : null}
        {summaryQuery.data ? (
          <>
            <MetricGrid
              items={[
                { label: "总数", value: summaryQuery.data.totalCount },
                { label: "缺失来源回指", value: summaryQuery.data.sourceReferenceMissingCount, tone: summaryQuery.data.sourceReferenceMissingCount ? "danger" : "default" },
                { label: "低置信度", value: summaryQuery.data.lowConfidenceCount, tone: summaryQuery.data.lowConfidenceCount ? "warning" : "default" },
              ]}
              label="Fact Card 质量统计"
            />
            <div className="fact-card-distributions">
              <Distribution label="卡片类型" values={summaryQuery.data.countByCardType} />
              <Distribution label="审查状态" values={summaryQuery.data.countByReviewStatus} />
            </div>
          </>
        ) : null}
      </QualitySection>
      <QualitySection
        actions={
          <label className="filter-field">
            <span>返回数量</span>
            <select
              onChange={(event) => updateParameters({ limit: event.target.value, factCardId: null })}
              value={limit}
            >
              {LIMITS.map((value) => <option key={value} value={value}>{value}</option>)}
            </select>
          </label>
        }
        context="服务端按最新记录返回，最多 200 条"
        title="Fact Card 列表"
      >
        {listQuery.isPending ? <PageState status="loading" title="正在加载 Fact Card" /> : null}
        {listQuery.isError ? <QueryFailure error={listQuery.error} onRetry={() => void listQuery.refetch()} title="Fact Card 列表加载失败" /> : null}
        {listQuery.data?.items.length ? (
          <div className="data-table-scroll">
            <table className="data-table fact-card-table">
              <thead><tr><th>标题</th><th>类型</th><th>置信度</th><th>审查状态</th><th>来源</th></tr></thead>
              <tbody>
                {listQuery.data.items.map((card) => (
                  <tr className={factCardId === card.id ? "is-selected" : undefined} key={card.id}>
                    <td data-label="标题"><button className="fact-card-title" onClick={() => updateParameters({ factCardId: String(card.id) })} type="button">{card.title || card.cardId}</button><code>{card.cardId}</code></td>
                    <td data-label="类型"><span>{card.cardType}</span><small>{card.answerShape}</small></td>
                    <td data-label="置信度">{(card.confidence * 100).toFixed(1)}%</td>
                    <td data-label="审查状态"><span className={`status-label is-${card.reviewStatus.replaceAll("_", "-")}`}>{card.reviewStatus}</span></td>
                    <td data-label="来源"><code>{card.sourceFilePath || "未关联源文件"}</code></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
        {listQuery.data && !listQuery.data.items.length ? <PageState status="empty" title="暂无 Fact Card" /> : null}
      </QualitySection>
      {factCardId !== null ? (
        <QualitySection
          actions={<button className="secondary-button quality-action-button" onClick={() => updateParameters({ factCardId: null })} type="button">关闭详情</button>}
          context={`数据库主键 #${factCardId}`}
          title="Fact Card 详情"
        >
          {detailQuery.isPending ? <PageState status="loading" title="正在加载 Fact Card 详情" /> : null}
          {detailQuery.isError ? <QueryFailure error={detailQuery.error} onRetry={() => void detailQuery.refetch()} title="Fact Card 详情加载失败" /> : null}
          {detailQuery.data ? <FactCardDetail card={detailQuery.data} /> : null}
        </QualitySection>
      ) : null}
    </div>
  );
}

function Distribution({ label, values }: { label: string; values: Record<string, number> }) {
  const entries = Object.entries(values);
  return (
    <dl>
      <div className="fact-card-distribution-title"><dt>{label}</dt><dd>{entries.reduce((sum, [, count]) => sum + count, 0)}</dd></div>
      {entries.map(([key, count]) => <div key={key}><dt>{key}</dt><dd>{count}</dd></div>)}
      {!entries.length ? <div><dt>暂无数据</dt><dd>0</dd></div> : null}
    </dl>
  );
}

function FactCardDetail({ card }: { card: FactCard }) {
  return (
    <div className="fact-card-detail">
      <header>
        <div><h3>{card.title || card.cardId}</h3><code>{card.cardId}</code></div>
        <span className={`status-label is-${card.reviewStatus.replaceAll("_", "-")}`}>{card.reviewStatus}</span>
      </header>
      <section><h4>事实结论</h4><p>{card.claim || "未提供"}</p></section>
      <section><h4>证据文本</h4><p>{card.evidenceText || "未提供"}</p></section>
      <section><h4>结构化条目</h4><pre aria-label="结构化条目 JSON" tabIndex={0}>{formatJson(card.itemsJson)}</pre></section>
      <dl className="fact-card-metadata">
        <div><dt>卡片类型</dt><dd>{card.cardType} / {card.answerShape}</dd></div>
        <div><dt>置信度</dt><dd>{(card.confidence * 100).toFixed(1)}%</dd></div>
        <div><dt>Source Chunk</dt><dd>{card.sourceChunkIds.join("、") || "无"}</dd></div>
        <div><dt>关联文章</dt><dd>{card.articleIds.join("、") || "无"}</dd></div>
        <div><dt>内容哈希</dt><dd><code>{card.contentHash || "无"}</code></dd></div>
        <div><dt>更新时间</dt><dd>{formatTime(card.updatedAt)}</dd></div>
      </dl>
      {card.sourceId ? (
        <Link className="fact-card-source-link" to={`/library/sources/${card.sourceId}`}>
          查看资料源 #{card.sourceId}<ExternalLink aria-hidden="true" size={14} />
        </Link>
      ) : null}
    </div>
  );
}

function formatJson(value: string) {
  try { return JSON.stringify(JSON.parse(value), null, 2); } catch { return value || "无"; }
}

function parseLimit(value: string | null): typeof LIMITS[number] {
  const number = Number(value);
  return LIMITS.includes(number as typeof LIMITS[number]) ? number as typeof LIMITS[number] : 50;
}

function parseId(value: string | null) {
  if (!value || !/^\d+$/.test(value)) return null;
  const number = Number(value);
  return Number.isSafeInteger(number) && number > 0 ? number : null;
}
