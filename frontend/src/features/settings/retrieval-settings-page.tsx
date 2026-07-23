import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ChartNoAxesCombined, DatabaseZap, RefreshCw, SlidersHorizontal } from "lucide-react";
import { useCallback, useState } from "react";
import { useSearchParams } from "react-router-dom";

import {
  retrievalSettingsApi,
  type ChunkRebuildResult,
} from "../../api/contracts/retrieval-settings";
import { queryKeys } from "../../api/query-keys";
import { PageHeader } from "../../components/page-header";
import { ArticleGovernanceDialog } from "../library/article-governance-dialog";
import { RetrievalAuditPanel } from "./retrieval-audit-panel";
import { RetrievalConfigPanel } from "./retrieval-config-panel";
import {
  confirmDiscardChanges,
  formatLlmDateTime,
  llmErrorMessage,
} from "./llm-settings-utils";

type RetrievalView = "config" | "audits" | "chunks";

const VIEWS = [
  { value: "config" as const, label: "运行参数", icon: SlidersHorizontal },
  { value: "audits" as const, label: "检索审计", icon: ChartNoAxesCombined },
  { value: "chunks" as const, label: "切片维护", icon: DatabaseZap },
];

export default function RetrievalSettingsPage() {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const [dirty, setDirty] = useState(false);
  const requestedView = searchParams.get("view");
  const view: RetrievalView = VIEWS.some((item) => item.value === requestedView)
    ? requestedView as RetrievalView
    : "config";
  const onDirtyChange = useCallback((next: boolean) => setDirty(next), []);

  const changeView = (nextView: RetrievalView) => {
    if (nextView === view || !confirmDiscardChanges(dirty)) return;
    const next = new URLSearchParams(searchParams);
    next.set("view", nextView);
    if (nextView !== "audits") next.delete("queryId");
    setSearchParams(next);
  };

  const refresh = () => {
    if (!confirmDiscardChanges(dirty)) return;
    void queryClient.invalidateQueries({ queryKey: queryKeys.settings.retrieval.root });
  };

  return (
    <div className="page-frame retrieval-settings-page">
      <PageHeader
        actions={(
          <button aria-label="刷新检索设置" className="icon-button" onClick={refresh} title="刷新检索设置" type="button">
            <RefreshCw aria-hidden="true" size={18} />
          </button>
        )}
        context="runtime weights · retrieval audits · synchronous chunk rebuild"
        title="检索参数"
      />
      <nav aria-label="检索设置视图" className="llm-settings-tabs" role="tablist">
        {VIEWS.map((item) => {
          const Icon = item.icon;
          return (
            <button
              aria-controls="retrieval-settings-panel"
              aria-selected={view === item.value}
              key={item.value}
              onClick={() => changeView(item.value)}
              role="tab"
              type="button"
            >
              <Icon aria-hidden="true" size={16} />{item.label}
            </button>
          );
        })}
      </nav>
      <div aria-label={VIEWS.find((item) => item.value === view)?.label} id="retrieval-settings-panel" role="tabpanel">
        {view === "config" ? <RetrievalConfigPanel onDirtyChange={onDirtyChange} /> : null}
        {view === "audits" ? <RetrievalAuditPanel /> : null}
        {view === "chunks" ? <ChunkMaintenancePanel /> : null}
      </div>
    </div>
  );
}

function ChunkMaintenancePanel() {
  const queryClient = useQueryClient();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [result, setResult] = useState<ChunkRebuildResult | null>(null);
  const [error, setError] = useState("");
  const mutation = useMutation({
    mutationFn: () => retrievalSettingsApi.rebuildChunks(),
    onSuccess: (saved) => {
      setResult(saved);
      setDialogOpen(false);
      setError("");
      void queryClient.invalidateQueries({ queryKey: queryKeys.settings.retrieval.root });
    },
    onError: (cause) => setError(llmErrorMessage(cause)),
  });

  return (
    <section aria-labelledby="chunk-maintenance-title" className="retrieval-section chunk-maintenance-section">
      <header className="retrieval-section-header">
        <div><h2 id="chunk-maintenance-title">切片全量重建</h2><p>同步执行 · 当前无取消与任务轮询契约</p></div>
        <DatabaseZap aria-hidden="true" size={20} />
      </header>
      <div className="chunk-maintenance-body">
        <dl className="maintenance-impact-grid">
          <div><dt>目标数据</dt><dd>全部文章与源文件</dd></div>
          <div><dt>重建产物</dt><dd>Article chunks / Source chunks</dd></div>
          <div><dt>执行方式</dt><dd>同步全量覆盖</dd></div>
        </dl>
        <p className="vector-notice is-warning">执行期间 Query 可能读取到正在更新的切片集合，请在低流量窗口操作。</p>
        <button className="danger-button" onClick={() => { setError(""); setDialogOpen(true); }} type="button">
          <DatabaseZap aria-hidden="true" size={16} />准备重建切片
        </button>
        {result ? (
          <section aria-label="最近切片重建结果" className="chunk-rebuild-result" role="status">
            <header><strong>切片重建完成</strong><time>{formatLlmDateTime(result.rebuiltAt)}</time></header>
            <dl className="maintenance-impact-grid">
              <div><dt>文章</dt><dd>{result.rebuiltArticleCount}</dd></div>
              <div><dt>源文件</dt><dd>{result.rebuiltSourceFileCount}</dd></div>
              <div><dt>文章切片</dt><dd>{result.articleChunkCount}</dd></div>
              <div><dt>源文件切片</dt><dd>{result.sourceFileChunkCount}</dd></div>
            </dl>
          </section>
        ) : null}
      </div>
      {dialogOpen ? (
        <ArticleGovernanceDialog
          confirmLabel="确认重建切片"
          description="该操作会同步重建全部文章与源文件切片，提交后当前请求无法取消。"
          destructive
          error={error || undefined}
          onClose={() => setDialogOpen(false)}
          onConfirm={() => mutation.mutate()}
          pending={mutation.isPending}
          title="重建全部切片"
        >
          <dl className="governance-impact-summary">
            <div><dt>影响范围</dt><dd>全部文章与源文件</dd></div>
            <div><dt>覆盖内容</dt><dd>文章切片、源文件切片</dd></div>
            <div><dt>恢复方式</dt><dd>当前接口不提供取消或自动回滚</dd></div>
          </dl>
        </ArticleGovernanceDialog>
      ) : null}
    </section>
  );
}
