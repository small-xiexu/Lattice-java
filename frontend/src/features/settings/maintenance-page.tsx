import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArchiveRestore,
  DatabaseBackup,
  FileDiff,
  FolderSync,
  GitCommitHorizontal,
  RefreshCw,
  ShieldAlert,
} from "lucide-react";
import { useState, type ReactNode } from "react";
import { useSearchParams } from "react-router-dom";

import {
  repositoryMaintenanceApi,
  type RepoBaselineResult,
  type RepoDiff,
  type RepoRollbackResult,
  type RepoSnapshot,
  type VaultExportResult,
  type VaultSyncResult,
} from "../../api/contracts/repository-maintenance";
import { queryKeys } from "../../api/query-keys";
import { PageHeader } from "../../components/page-header";
import { PageState } from "../../components/page-state";
import { ArticleGovernanceDialog } from "../library/article-governance-dialog";
import { formatLlmDateTime, llmErrorMessage } from "./llm-settings-utils";

const HISTORY_LIMIT = 20;

export default function MaintenancePage() {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const [vaultDir, setVaultDir] = useState("");
  const [description, setDescription] = useState("");
  const [baselineOpen, setBaselineOpen] = useState(false);
  const [rollbackOpen, setRollbackOpen] = useState(false);
  const [rollbackConfirmation, setRollbackConfirmation] = useState("");
  const [vaultAction, setVaultAction] = useState<"export" | "sync" | "force-sync" | null>(null);
  const [vaultConfirmation, setVaultConfirmation] = useState("");
  const [actionError, setActionError] = useState("");
  const [diff, setDiff] = useState<RepoDiff | null>(null);
  const [baselineResult, setBaselineResult] = useState<RepoBaselineResult | null>(null);
  const [rollbackResult, setRollbackResult] = useState<RepoRollbackResult | null>(null);
  const [vaultExportResult, setVaultExportResult] = useState<VaultExportResult | null>(null);
  const [vaultSyncResult, setVaultSyncResult] = useState<VaultSyncResult | null>(null);
  const historyQuery = useQuery({
    queryKey: queryKeys.maintenance.repoSnapshots(HISTORY_LIMIT),
    queryFn: ({ signal }) => repositoryMaintenanceApi.listSnapshots(HISTORY_LIMIT, signal),
  });

  const selectedFromUrl = Number(searchParams.get("snapshot"));
  const selected = historyQuery.data?.items.find((item) => item.id === selectedFromUrl)
    ?? historyQuery.data?.items[0]
    ?? null;

  const diffMutation = useMutation({
    mutationFn: (snapshot: RepoSnapshot) => repositoryMaintenanceApi.getDiff(snapshot.id, vaultDir.trim()),
    onSuccess: (result) => {
      setDiff(result);
      setActionError("");
      setRollbackResult(null);
    },
    onError: (cause) => {
      setDiff(null);
      setActionError(llmErrorMessage(cause));
    },
  });
  const baselineMutation = useMutation({
    mutationFn: () => repositoryMaintenanceApi.createBaseline({
      vaultDir: vaultDir.trim(),
      description: description.trim(),
    }),
    onSuccess: (result) => {
      setBaselineResult(result);
      setRollbackResult(null);
      setBaselineOpen(false);
      setActionError("");
      setDiff(null);
      const next = new URLSearchParams(searchParams);
      next.set("snapshot", String(result.snapshotId));
      setSearchParams(next);
      void queryClient.invalidateQueries({ queryKey: ["admin", "maintenance", "repo-snapshots"] });
    },
    onError: (cause) => setActionError(llmErrorMessage(cause)),
  });
  const rollbackMutation = useMutation({
    mutationFn: () => repositoryMaintenanceApi.rollback({
      snapshotId: selected?.id ?? 0,
      vaultDir: vaultDir.trim(),
    }),
    onSuccess: (result) => {
      setRollbackResult(result);
      setBaselineResult(null);
      setRollbackOpen(false);
      setRollbackConfirmation("");
      setActionError("");
      setDiff(null);
      void queryClient.invalidateQueries({ queryKey: ["admin", "maintenance", "repo-snapshots"] });
    },
    onError: (cause) => setActionError(llmErrorMessage(cause)),
  });
  const vaultExportMutation = useMutation({
    mutationFn: () => repositoryMaintenanceApi.exportVault({ vaultDir: vaultDir.trim() }),
    onSuccess: (result) => {
      setVaultExportResult(result);
      setVaultAction(null);
      setActionError("");
    },
    onError: (cause) => setActionError(llmErrorMessage(cause)),
  });
  const vaultSyncMutation = useMutation({
    mutationFn: (force: boolean) => repositoryMaintenanceApi.syncVault({
      vaultDir: vaultDir.trim(),
      force,
    }),
    onSuccess: (result) => {
      setVaultSyncResult(result);
      setVaultAction(null);
      setVaultConfirmation("");
      setActionError("");
    },
    onError: (cause) => setActionError(llmErrorMessage(cause)),
  });

  if (historyQuery.isLoading) return <PageState status="loading" title="正在读取仓库快照" />;
  if (historyQuery.error || !historyQuery.data) {
    return (
      <PageState
        actionLabel="重新加载"
        description={historyQuery.error ? llmErrorMessage(historyQuery.error) : "服务端未返回仓库快照"}
        onAction={() => void historyQuery.refetch()}
        status="error"
        title="仓库快照读取失败"
      />
    );
  }

  const chooseSnapshot = (snapshot: RepoSnapshot) => {
    const next = new URLSearchParams(searchParams);
    next.set("snapshot", String(snapshot.id));
    setSearchParams(next);
    setDiff(null);
    setActionError("");
    setRollbackResult(null);
  };
  const prepareBaseline = () => {
    if (!vaultDir.trim()) {
      setActionError("请输入 Vault 仓库绝对路径");
      return;
    }
    setActionError("");
    setBaselineOpen(true);
  };
  const previewDiff = () => {
    if (!selected?.gitCommit) {
      setActionError("该历史快照没有 Git commit，不能执行差异预览或整库回滚");
      return;
    }
    if (!vaultDir.trim()) {
      setActionError("请输入 Vault 仓库绝对路径");
      return;
    }
    setActionError("");
    diffMutation.mutate(selected);
  };
  const prepareRollback = () => {
    if (!selected || !diff || diff.snapshotId !== selected.id) {
      setActionError("整库回滚前必须先预览当前目标的差异");
      return;
    }
    setActionError("");
    setRollbackConfirmation("");
    setRollbackOpen(true);
  };

  return (
    <div className="page-frame maintenance-page">
      <PageHeader
        actions={(
          <button aria-label="刷新维护记录" className="icon-button" onClick={() => void historyQuery.refetch()} title="刷新维护记录" type="button">
            <RefreshCw aria-hidden="true" size={18} />
          </button>
        )}
        context="git-backed baseline · diff preview · explicit rollback"
        title="系统维护"
      />

      <section aria-labelledby="maintenance-target-title" className="maintenance-section">
        <header className="maintenance-section-header">
          <div><h2 id="maintenance-target-title">Vault 目标</h2><p>路径只随本次请求发送，不写入浏览器存储</p></div>
          <ShieldAlert aria-hidden="true" size={20} />
        </header>
        <div className="maintenance-target-form">
          <label className="form-field maintenance-vault-field">
            <span>Vault 仓库绝对路径</span>
            <input
              autoComplete="off"
              onChange={(event) => {
                setVaultDir(event.target.value);
                setDiff(null);
                setRollbackResult(null);
                setVaultExportResult(null);
                setVaultSyncResult(null);
              }}
              placeholder="/absolute/path/to/vault"
              value={vaultDir}
            />
          </label>
          <label className="form-field">
            <span>Baseline 描述</span>
            <input autoComplete="off" onChange={(event) => setDescription(event.target.value)} value={description} />
          </label>
          <button className="primary-button" onClick={prepareBaseline} type="button">
            <GitCommitHorizontal aria-hidden="true" size={16} />准备创建基线
          </button>
        </div>
      </section>

      {actionError && !baselineOpen && !rollbackOpen && !vaultAction ? (
        <p className="vector-notice is-error maintenance-action-error" role="alert">{actionError}</p>
      ) : null}

      <section aria-labelledby="vault-exchange-title" className="maintenance-section">
        <header className="maintenance-section-header">
          <div><h2 id="vault-exchange-title">Vault 文件交换</h2><p>导出数据库内容，或将 Vault 中允许回写的文章同步回数据库</p></div>
          <DatabaseBackup aria-hidden="true" size={20} />
        </header>
        <div className="vault-action-grid">
          <button className="secondary-button" onClick={() => prepareVaultAction("export")} type="button">
            <DatabaseBackup aria-hidden="true" size={16} />准备导出
          </button>
          <button className="primary-button" onClick={() => prepareVaultAction("sync")} type="button">
            <FolderSync aria-hidden="true" size={16} />安全同步
          </button>
          <button
            className="danger-button"
            disabled={!canForceSync(vaultSyncResult, vaultDir)}
            onClick={() => prepareVaultAction("force-sync")}
            type="button"
          >
            <ShieldAlert aria-hidden="true" size={16} />准备强制同步
          </button>
        </div>
        <p className="vault-action-note">安全同步会写入无冲突文件并返回冲突报告；只有当前目标存在冲突时才开放强制同步。</p>
        {vaultExportResult ? (
          <ActionResult title="Vault 导出完成">
            {vaultExportResult.vaultDir} · 写入 {vaultExportResult.writtenFiles} / 跳过 {vaultExportResult.skippedFiles} / 删除 {vaultExportResult.deletedFiles}
          </ActionResult>
        ) : null}
        {vaultSyncResult ? <VaultSyncResultPanel result={vaultSyncResult} /> : null}
      </section>

      <section aria-labelledby="repo-history-title" className="maintenance-section maintenance-history-section">
        <header className="maintenance-section-header">
          <div><h2 id="repo-history-title">仓库快照</h2><p>{historyQuery.data.count} 条最近记录</p></div>
        </header>
        {historyQuery.data.items.length === 0 ? (
          <PageState description="创建首个 Git-backed baseline 后，可在此预览差异和回滚。" status="empty" title="暂无仓库快照" />
        ) : (
          <div className="maintenance-history-layout">
            <ol aria-label="仓库快照列表" className="maintenance-snapshot-list" tabIndex={0}>
              {historyQuery.data.items.map((snapshot) => (
                <li key={snapshot.id}>
                  <button className={snapshot.id === selected?.id ? "is-selected" : ""} onClick={() => chooseSnapshot(snapshot)} type="button">
                    <span><strong>#{snapshot.id}</strong><time>{formatLlmDateTime(snapshot.createdAt)}</time></span>
                    <code>{snapshot.gitCommit ? shortCommit(snapshot.gitCommit) : "无 Git commit"}</code>
                    <small>{snapshot.description || snapshot.triggerEvent || "未填写描述"}</small>
                  </button>
                </li>
              ))}
            </ol>
            <div className="maintenance-snapshot-detail">
              {selected ? (
                <>
                  <header><div><h3>快照 #{selected.id}</h3><p>{selected.description || "未填写描述"}</p></div><code>{selected.gitCommit || "无 Git commit"}</code></header>
                  <dl className="maintenance-impact-grid">
                    <div><dt>触发事件</dt><dd>{selected.triggerEvent || "--"}</dd></div>
                    <div><dt>文章数量</dt><dd>{selected.articleCount}</dd></div>
                    <div><dt>创建时间</dt><dd>{formatLlmDateTime(selected.createdAt)}</dd></div>
                  </dl>
                  {!selected.gitCommit ? <p className="vector-notice is-warning">该历史记录没有绑定 Git commit，只能用于审计，不能执行差异或整库回滚。</p> : null}
                  <div className="maintenance-actions">
                    <button className="secondary-button" disabled={diffMutation.isPending || !selected.gitCommit} onClick={previewDiff} type="button">
                      <FileDiff aria-hidden="true" size={16} />{diffMutation.isPending ? "正在读取差异" : "预览差异"}
                    </button>
                    <button className="danger-button" disabled={!diff || diff.snapshotId !== selected.id} onClick={prepareRollback} type="button">
                      <ArchiveRestore aria-hidden="true" size={16} />准备整库回滚
                    </button>
                  </div>
                </>
              ) : null}
            </div>
          </div>
        )}
        {diff ? <RepoDiffResult diff={diff} /> : null}
        {baselineResult ? (
          <ActionResult title="基线创建完成">
            快照 #{baselineResult.snapshotId} · {baselineResult.createdNewCommit ? "已创建新 commit" : "复用当前 commit"} · 写入 {baselineResult.writtenFiles} / 跳过 {baselineResult.skippedFiles} / 删除 {baselineResult.deletedFiles}
          </ActionResult>
        ) : null}
        {rollbackResult ? (
          <ActionResult title="整库回滚完成">已恢复到快照 #{rollbackResult.restoredSnapshotId} · {formatLlmDateTime(rollbackResult.restoredAt)}</ActionResult>
        ) : null}
      </section>

      {baselineOpen ? (
        <ArticleGovernanceDialog
          confirmLabel="确认创建基线"
          description="服务端将导出当前知识库到目标 Vault，并建立 Git-backed repo snapshot。"
          error={baselineMutation.isError ? actionError : undefined}
          onClose={() => setBaselineOpen(false)}
          onConfirm={() => baselineMutation.mutate()}
          pending={baselineMutation.isPending}
          title="创建仓库基线"
        >
          <dl className="governance-impact-summary">
            <div><dt>目标目录</dt><dd>{vaultDir.trim()}</dd></div>
            <div><dt>描述</dt><dd>{description.trim() || "未填写"}</dd></div>
            <div><dt>写入范围</dt><dd>当前知识文章及 Vault Git 历史</dd></div>
          </dl>
        </ArticleGovernanceDialog>
      ) : null}

      {rollbackOpen && selected && diff ? (
        <ArticleGovernanceDialog
          confirmLabel="确认整库回滚"
          description="回滚会用目标快照恢复整库内容。输入目标快照 ID 后才可执行。"
          destructive
          error={actionError || undefined}
          onClose={() => setRollbackOpen(false)}
          onConfirm={() => {
            if (rollbackConfirmation.trim() !== String(selected.id)) {
              setActionError(`请输入目标快照 ID：${selected.id}`);
              return;
            }
            rollbackMutation.mutate();
          }}
          pending={rollbackMutation.isPending}
          title="整库回滚"
        >
          <dl className="governance-impact-summary">
            <div><dt>目标快照</dt><dd>#{selected.id}</dd></div>
            <div><dt>目标 commit</dt><dd>{diff.targetCommitId || "--"}</dd></div>
            <div><dt>当前 commit</dt><dd>{diff.currentCommitId || "--"}</dd></div>
            <div><dt>差异文件</dt><dd>{diff.count}</dd></div>
            <div><dt>Vault 目录</dt><dd>{vaultDir.trim()}</dd></div>
          </dl>
          <label className="form-field maintenance-confirmation-field">
            <span>输入快照 ID {selected.id} 确认</span>
            <input autoComplete="off" inputMode="numeric" onChange={(event) => setRollbackConfirmation(event.target.value)} value={rollbackConfirmation} />
          </label>
        </ArticleGovernanceDialog>
      ) : null}

      {vaultAction ? (
        <ArticleGovernanceDialog
          confirmLabel={vaultAction === "export" ? "确认导出" : vaultAction === "sync" ? "确认安全同步" : "确认强制同步"}
          description={vaultAction === "export"
            ? "导出会更新目标目录中的受管文件，并可能删除清单中已退场的文件。"
            : vaultAction === "sync"
              ? "安全同步会写入无冲突文件，冲突文件保持不变并生成报告。"
              : "强制同步会覆盖冲突内容。输入完整目标路径后才可执行。"}
          destructive={vaultAction === "export" || vaultAction === "force-sync"}
          error={actionError || undefined}
          onClose={() => {
            setVaultAction(null);
            setVaultConfirmation("");
            setActionError("");
          }}
          onConfirm={() => {
            if (vaultAction === "export") {
              vaultExportMutation.mutate();
            } else if (vaultAction === "sync") {
              vaultSyncMutation.mutate(false);
            } else if (vaultConfirmation.trim() !== vaultDir.trim()) {
              setActionError(`请输入完整目标路径：${vaultDir.trim()}`);
            } else {
              vaultSyncMutation.mutate(true);
            }
          }}
          pending={vaultExportMutation.isPending || vaultSyncMutation.isPending}
          title={vaultAction === "export" ? "导出 Vault" : vaultAction === "sync" ? "安全同步 Vault" : "强制同步 Vault"}
        >
          <dl className="governance-impact-summary">
            <div><dt>目标目录</dt><dd>{vaultDir.trim()}</dd></div>
            <div><dt>动作</dt><dd>{vaultAction === "export" ? "数据库 -> Vault" : "Vault -> 数据库"}</dd></div>
            <div><dt>冲突策略</dt><dd>{vaultAction === "force-sync" ? "强制覆盖" : vaultAction === "sync" ? "保留冲突" : "按导出清单维护"}</dd></div>
          </dl>
          {vaultAction === "force-sync" ? (
            <label className="form-field maintenance-confirmation-field">
              <span>输入完整目标路径确认</span>
              <input autoComplete="off" onChange={(event) => setVaultConfirmation(event.target.value)} value={vaultConfirmation} />
            </label>
          ) : null}
        </ArticleGovernanceDialog>
      ) : null}
    </div>
  );

  function prepareVaultAction(action: "export" | "sync" | "force-sync") {
    if (!vaultDir.trim()) {
      setActionError("请输入 Vault 仓库绝对路径");
      return;
    }
    if (action === "force-sync" && !canForceSync(vaultSyncResult, vaultDir)) {
      setActionError("请先对当前目标执行安全同步并取得冲突报告");
      return;
    }
    setActionError("");
    setVaultConfirmation("");
    setVaultAction(action);
  }
}

function RepoDiffResult({ diff }: { diff: RepoDiff }) {
  return (
    <section aria-label="仓库差异预览" className="maintenance-diff-result">
      <header><div><h3>差异预览</h3><p>快照 #{diff.snapshotId} 到当前 HEAD</p></div><strong>{diff.count} 个文件</strong></header>
      <dl className="maintenance-commit-pair">
        <div><dt>目标 commit</dt><dd><code>{diff.targetCommitId || "--"}</code></dd></div>
        <div><dt>当前 commit</dt><dd><code>{diff.currentCommitId || "--"}</code></dd></div>
      </dl>
      {diff.items.length === 0 ? <p className="maintenance-no-diff">目标快照与当前仓库没有文件差异</p> : (
        <ul className="maintenance-diff-files">
          {diff.items.map((item) => <li key={`${item.changeType}-${item.filePath}`}><strong>{item.changeType}</strong><code>{item.filePath}</code></li>)}
        </ul>
      )}
    </section>
  );
}

function ActionResult({ title, children }: { title: string; children: ReactNode }) {
  return <p className="maintenance-action-result" role="status"><strong>{title}</strong><span>{children}</span></p>;
}

function VaultSyncResultPanel({ result }: { result: VaultSyncResult }) {
  return (
    <section aria-label="Vault 同步结果" className={result.conflictCount > 0 ? "vault-sync-result has-conflicts" : "vault-sync-result"}>
      <header><div><h3>Vault 同步完成</h3><p>{result.vaultDir}</p></div><strong>{result.conflictCount} 个冲突</strong></header>
      <dl className="maintenance-impact-grid">
        <div><dt>已同步</dt><dd>{result.syncedFiles}</dd></div>
        <div><dt>已跳过</dt><dd>{result.skippedFiles}</dd></div>
        <div><dt>冲突</dt><dd>{result.conflictCount}</dd></div>
      </dl>
      {result.conflicts.length > 0 ? (
        <ul className="vault-conflict-list">
          {result.conflicts.map((conflict) => (
            <li key={`${conflict.filePath}-${conflict.reason}`}>
              <strong>{conflict.filePath}</strong>
              <span>{conflict.reason}</span>
              <code>manifest {shortHash(conflict.manifestHash)} · db {shortHash(conflict.currentDbHash)} · file {shortHash(conflict.currentFileHash)}</code>
            </li>
          ))}
        </ul>
      ) : <p className="maintenance-no-diff">本次同步没有冲突</p>}
    </section>
  );
}

function canForceSync(result: VaultSyncResult | null, vaultDir: string) {
  return Boolean(result && result.conflictCount > 0 && result.vaultDir === vaultDir.trim());
}

function shortHash(value: string | null) {
  if (!value) return "--";
  return value.length > 10 ? value.slice(0, 10) : value;
}

function shortCommit(commit: string) {
  return commit.length > 12 ? commit.slice(0, 12) : commit;
}
