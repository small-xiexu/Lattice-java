import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Archive,
  CheckCircle2,
  GitCompareArrows,
  History,
  MessageSquareWarning,
  PencilLine,
  RotateCcw,
} from "lucide-react";
import { useState } from "react";

import { isApiError } from "../../api/api-error";
import {
  articleGovernanceApi,
  type ArticleCorrectionResult,
  type ArticleSnapshot,
  type LifecycleAction,
} from "../../api/contracts/article-governance";
import type { ArticleDetail } from "../../api/contracts/articles";
import { queryKeys } from "../../api/query-keys";
import { InlineAlert } from "../../components/inline-alert";
import { ArticleGovernanceDialog } from "./article-governance-dialog";

type DialogState =
  | { type: "review"; action: "approve" | "request-changes" }
  | { type: "lifecycle"; action: LifecycleAction }
  | { type: "correction" }
  | { type: "rollback"; snapshot: ArticleSnapshot };

interface ResultNotice {
  title: string;
  description: string;
  correction?: {
    before: string;
    result: ArticleCorrectionResult;
  };
}

export function ArticleGovernance({ article }: { article: ArticleDetail }) {
  const queryClient = useQueryClient();
  const [dialog, setDialog] = useState<DialogState | null>(null);
  const [reviewedBy, setReviewedBy] = useState("");
  const [comment, setComment] = useState("");
  const [correctionSummary, setCorrectionSummary] = useState("");
  const [lifecycleReason, setLifecycleReason] = useState("");
  const [updatedBy, setUpdatedBy] = useState("");
  const [selectedSnapshot, setSelectedSnapshot] = useState<ArticleSnapshot | null>(null);
  const [resultNotice, setResultNotice] = useState<ResultNotice | null>(null);

  const auditsQuery = useQuery({
    queryKey: queryKeys.articles.audits(article.articleKey, article.sourceId ?? undefined),
    queryFn: ({ signal }) => articleGovernanceApi.audits(article.articleKey, article.sourceId ?? undefined, signal),
  });
  const snapshotsQuery = useQuery({
    queryKey: queryKeys.articles.snapshots(article.articleKey, article.sourceId ?? undefined, 10),
    queryFn: ({ signal }) => articleGovernanceApi.snapshots(article.articleKey, article.sourceId ?? undefined, 10, signal),
  });

  const refreshArticleState = () => queryClient.invalidateQueries({ queryKey: queryKeys.articles.root });
  const reviewMutation = useMutation({
    mutationFn: ({ action }: { action: "approve" | "request-changes" }) => {
      const request = {
        sourceId: article.sourceId ?? undefined,
        reviewedBy: reviewedBy.trim(),
        comment: comment.trim() || undefined,
        expectedReviewStatus: article.reviewStatus,
        correctionSummary: action === "request-changes" ? correctionSummary.trim() : undefined,
      };
      return action === "approve"
        ? articleGovernanceApi.approve(article.articleKey, request)
        : articleGovernanceApi.requestChanges(article.articleKey, request);
    },
    onSuccess: (result, variables) => {
      setResultNotice({
        title: variables.action === "approve" ? "审核已通过" : "修改要求已提交",
        description: `审核状态 ${labelValue(result.previousReviewStatus)} -> ${labelValue(result.reviewStatus)}，审计记录 #${result.auditId}。`,
      });
      setDialog(null);
      void refreshArticleState();
    },
    onError: () => {
      void refreshArticleState();
    },
  });
  const lifecycleMutation = useMutation({
    mutationFn: (action: LifecycleAction) => articleGovernanceApi.transitionLifecycle(
      article.articleKey,
      article.sourceId ?? undefined,
      action,
      { reason: lifecycleReason.trim(), updatedBy: updatedBy.trim() },
    ),
    onSuccess: (result) => {
      setResultNotice({
        title: "生命周期已更新",
        description: `${result.title} 已切换为“${labelValue(result.lifecycle)}”，更新时间 ${formatDateTime(result.updatedAt)}。`,
      });
      setDialog(null);
      void refreshArticleState();
    },
  });
  const correctionMutation = useMutation({
    mutationFn: () => articleGovernanceApi.correct(
      article.articleKey,
      article.sourceId ?? undefined,
      correctionSummary.trim(),
    ),
    onSuccess: (result) => {
      setResultNotice({
        title: "人工修正已完成",
        description: `${result.validationSupported ? "来源证据支持本次修正" : "来源证据未能支持本次修正"}；影响 ${result.downstreamIds.length} 个下游概念。`,
        correction: { before: article.content, result },
      });
      setDialog(null);
      void refreshArticleState();
    },
  });
  const rollbackMutation = useMutation({
    mutationFn: (snapshot: ArticleSnapshot) => articleGovernanceApi.rollback(
      article.articleKey,
      article.sourceId ?? undefined,
      snapshot.snapshotId,
    ),
    onSuccess: (result) => {
      setResultNotice({
        title: "文章已回滚",
        description: `已恢复快照 #${result.restoredSnapshotId}，服务端同时保留了本次回滚快照。`,
      });
      setSelectedSnapshot(null);
      setDialog(null);
      void refreshArticleState();
    },
  });

  const pending = reviewMutation.isPending
    || lifecycleMutation.isPending
    || correctionMutation.isPending
    || rollbackMutation.isPending;
  const mutationError = reviewMutation.error
    ?? lifecycleMutation.error
    ?? correctionMutation.error
    ?? rollbackMutation.error;

  const openDialog = (next: DialogState) => {
    reviewMutation.reset();
    lifecycleMutation.reset();
    correctionMutation.reset();
    rollbackMutation.reset();
    setComment("");
    setCorrectionSummary("");
    setLifecycleReason("");
    setDialog(next);
  };

  function closeDialog() {
    if (!pending) setDialog(null);
  }

  return (
    <section className="article-governance" aria-labelledby="article-governance-title">
      <header className="article-governance-header">
        <div>
          <h2 id="article-governance-title">文章治理</h2>
          <p>{article.articleKey}{article.sourceId ? ` / 资料源 #${article.sourceId}` : " / 多源文章"}</p>
        </div>
        <div className="article-governance-actions">
          <button className="secondary-button governance-action-button" disabled={pending} onClick={() => openDialog({ type: "review", action: "approve" })} type="button">
            <CheckCircle2 aria-hidden="true" size={16} />通过审核
          </button>
          <button className="secondary-button governance-action-button" disabled={pending} onClick={() => openDialog({ type: "review", action: "request-changes" })} type="button">
            <MessageSquareWarning aria-hidden="true" size={16} />要求修改
          </button>
          <button className="secondary-button governance-action-button" disabled={pending} onClick={() => openDialog({ type: "correction" })} type="button">
            <PencilLine aria-hidden="true" size={16} />人工修正
          </button>
        </div>
      </header>

      {resultNotice ? (
        <InlineAlert
          actionLabel="关闭"
          description={resultNotice.description}
          onAction={() => setResultNotice(null)}
          title={resultNotice.title}
          tone="success"
        />
      ) : null}
      {resultNotice?.correction ? (
        <VersionComparison
          currentLabel="修正结果"
          currentValue={resultNotice.correction.result.revisedContent}
          previousLabel="修正前"
          previousValue={resultNotice.correction.before}
        />
      ) : null}

      <div className="article-governance-grid">
        <section className="governance-section" aria-labelledby="lifecycle-actions-title">
          <h3 id="lifecycle-actions-title"><Archive aria-hidden="true" size={17} />生命周期</h3>
          <p className="governance-current-state">当前：<strong>{labelValue(article.lifecycle)}</strong></p>
          <div className="lifecycle-actions">
            {availableLifecycleActions(article.lifecycle).map((action) => (
              <button className="secondary-button governance-action-button" disabled={pending} key={action} onClick={() => openDialog({ type: "lifecycle", action })} type="button">
                {labelLifecycleAction(action)}
              </button>
            ))}
          </div>
        </section>

        <HistorySection
          article={article}
          auditsError={auditsQuery.error}
          auditsLoading={auditsQuery.isPending}
          audits={auditsQuery.data?.items}
          onRetryAudits={() => void auditsQuery.refetch()}
          onRetrySnapshots={() => void snapshotsQuery.refetch()}
          onRollback={(snapshot) => openDialog({ type: "rollback", snapshot })}
          onSelectSnapshot={setSelectedSnapshot}
          selectedSnapshotId={selectedSnapshot?.snapshotId}
          snapshotsError={snapshotsQuery.error}
          snapshotsLoading={snapshotsQuery.isPending}
          snapshots={snapshotsQuery.data?.items}
        />
      </div>

      {selectedSnapshot ? (
        <section className="snapshot-comparison" aria-labelledby="snapshot-comparison-title">
          <header>
            <div>
              <h3 id="snapshot-comparison-title">快照 #{selectedSnapshot.snapshotId} 对比</h3>
              <p>{labelValue(selectedSnapshot.lifecycle)} / {labelValue(selectedSnapshot.reviewStatus)} / {formatDateTime(selectedSnapshot.capturedAt)}</p>
            </div>
            <button className="secondary-button governance-dialog-button" onClick={() => setSelectedSnapshot(null)} type="button">关闭对比</button>
          </header>
          <VersionComparison
            currentLabel="当前版本"
            currentValue={article.content}
            previousLabel="快照版本"
            previousValue={selectedSnapshot.content}
          />
        </section>
      ) : null}

      {dialog ? (
        <GovernanceActionDialog
          article={article}
          comment={comment}
          correctionSummary={correctionSummary}
          dialog={dialog}
          error={mutationError ? resolveErrorMessage(mutationError) : undefined}
          lifecycleReason={lifecycleReason}
          pending={pending}
          reviewedBy={reviewedBy}
          updatedBy={updatedBy}
          onClose={closeDialog}
          onCommentChange={setComment}
          onConfirm={() => {
            if (dialog.type === "review") reviewMutation.mutate({ action: dialog.action });
            else if (dialog.type === "lifecycle") lifecycleMutation.mutate(dialog.action);
            else if (dialog.type === "correction") correctionMutation.mutate();
            else rollbackMutation.mutate(dialog.snapshot);
          }}
          onCorrectionSummaryChange={setCorrectionSummary}
          onLifecycleReasonChange={setLifecycleReason}
          onReviewedByChange={setReviewedBy}
          onUpdatedByChange={setUpdatedBy}
        />
      ) : null}
    </section>
  );
}

function HistorySection({
  article,
  audits,
  auditsError,
  auditsLoading,
  snapshots,
  snapshotsError,
  snapshotsLoading,
  selectedSnapshotId,
  onRetryAudits,
  onRetrySnapshots,
  onRollback,
  onSelectSnapshot,
}: {
  article: ArticleDetail;
  audits?: Array<{ id: number; action: string; previousReviewStatus: string; nextReviewStatus: string; comment: string | null; reviewedBy: string | null; reviewedAt: string | null }>;
  auditsError: unknown;
  auditsLoading: boolean;
  snapshots?: ArticleSnapshot[];
  snapshotsError: unknown;
  snapshotsLoading: boolean;
  selectedSnapshotId?: number;
  onRetryAudits: () => void;
  onRetrySnapshots: () => void;
  onRollback: (snapshot: ArticleSnapshot) => void;
  onSelectSnapshot: (snapshot: ArticleSnapshot) => void;
}) {
  return (
    <>
      <section className="governance-section" aria-labelledby="review-audits-title">
        <h3 id="review-audits-title"><History aria-hidden="true" size={17} />审核记录</h3>
        {auditsLoading ? <p className="governance-empty">正在加载审核记录</p> : auditsError ? (
          <button className="text-button" onClick={onRetryAudits} type="button">审核记录加载失败，重试</button>
        ) : audits?.length ? (
          <ol className="governance-history-list">
            {audits.map((audit) => (
              <li key={audit.id}>
                <strong>{audit.action === "approve" ? "通过审核" : "要求修改"}</strong>
                <span>{labelValue(audit.previousReviewStatus)} {"->"} {labelValue(audit.nextReviewStatus)}</span>
                <small>{audit.reviewedBy ?? "未记录操作人"} / {formatDateTime(audit.reviewedAt)}</small>
                {audit.comment ? <p>{audit.comment}</p> : null}
              </li>
            ))}
          </ol>
        ) : <p className="governance-empty">暂无审核记录</p>}
      </section>

      <section className="governance-section governance-snapshots" aria-labelledby="article-snapshots-title">
        <h3 id="article-snapshots-title"><RotateCcw aria-hidden="true" size={17} />版本与快照</h3>
        {snapshotsLoading ? <p className="governance-empty">正在加载快照</p> : snapshotsError ? (
          <button className="text-button" onClick={onRetrySnapshots} type="button">快照加载失败，重试</button>
        ) : snapshots?.length ? (
          <ol className="snapshot-list">
            {snapshots.map((snapshot) => (
              <li className={selectedSnapshotId === snapshot.snapshotId ? "is-selected" : ""} key={snapshot.snapshotId}>
                <div>
                  <strong>#{snapshot.snapshotId} {snapshot.snapshotReason ?? "未记录原因"}</strong>
                  <span>{labelValue(snapshot.lifecycle)} / {labelValue(snapshot.reviewStatus)}</span>
                  <small>{formatDateTime(snapshot.capturedAt)}</small>
                </div>
                <div className="snapshot-actions">
                  <button aria-label={`对比快照 ${snapshot.snapshotId}`} className="icon-button" onClick={() => onSelectSnapshot(snapshot)} title="对比快照" type="button">
                    <GitCompareArrows aria-hidden="true" size={17} />
                  </button>
                  <button aria-label={`回滚到快照 ${snapshot.snapshotId}`} className="icon-button is-danger" onClick={() => onRollback(snapshot)} title="回滚到此快照" type="button">
                    <RotateCcw aria-hidden="true" size={17} />
                  </button>
                </div>
              </li>
            ))}
          </ol>
        ) : <p className="governance-empty">暂无文章快照</p>}
        <p className="governance-scope">目标：{article.articleKey}{article.sourceId ? ` / source ${article.sourceId}` : ""}</p>
      </section>
    </>
  );
}

function GovernanceActionDialog({
  article,
  dialog,
  pending,
  error,
  reviewedBy,
  comment,
  correctionSummary,
  lifecycleReason,
  updatedBy,
  onClose,
  onConfirm,
  onReviewedByChange,
  onCommentChange,
  onCorrectionSummaryChange,
  onLifecycleReasonChange,
  onUpdatedByChange,
}: {
  article: ArticleDetail;
  dialog: DialogState;
  pending: boolean;
  error?: string;
  reviewedBy: string;
  comment: string;
  correctionSummary: string;
  lifecycleReason: string;
  updatedBy: string;
  onClose: () => void;
  onConfirm: () => void;
  onReviewedByChange: (value: string) => void;
  onCommentChange: (value: string) => void;
  onCorrectionSummaryChange: (value: string) => void;
  onLifecycleReasonChange: (value: string) => void;
  onUpdatedByChange: (value: string) => void;
}) {
  const configuration = dialogConfiguration(dialog, article);
  return (
    <ArticleGovernanceDialog
      confirmLabel={configuration.confirmLabel}
      description={configuration.description}
      destructive={configuration.destructive}
      error={error}
      onClose={onClose}
      onConfirm={onConfirm}
      pending={pending}
      title={configuration.title}
    >
      <dl className="governance-impact-summary">
        <div><dt>文章</dt><dd><code>{article.articleKey}</code></dd></div>
        <div><dt>资料源</dt><dd>{article.sourceId ? `#${article.sourceId}` : "多源"}</dd></div>
        {dialog.type === "review" ? <div><dt>审核状态</dt><dd>{labelValue(article.reviewStatus)} {"->"} {dialog.action === "approve" ? "通过" : "需复核"}</dd></div> : null}
        {dialog.type === "lifecycle" ? <div><dt>生命周期</dt><dd>{labelValue(article.lifecycle)} {"->"} {labelLifecycleTarget(dialog.action)}</dd></div> : null}
        {dialog.type === "rollback" ? <div><dt>目标快照</dt><dd>#{dialog.snapshot.snapshotId} / {formatDateTime(dialog.snapshot.capturedAt)}</dd></div> : null}
      </dl>
      {dialog.type === "review" ? (
        <>
          <label className="governance-form-field"><span>复核人</span><input onChange={(event) => onReviewedByChange(event.target.value)} required value={reviewedBy} /></label>
          <label className="governance-form-field"><span>复核意见</span><textarea onChange={(event) => onCommentChange(event.target.value)} required={dialog.action === "request-changes"} rows={3} value={comment} /></label>
          {dialog.action === "request-changes" ? <label className="governance-form-field"><span>修正摘要</span><textarea onChange={(event) => onCorrectionSummaryChange(event.target.value)} required rows={4} value={correctionSummary} /></label> : null}
        </>
      ) : null}
      {dialog.type === "lifecycle" ? (
        <>
          <label className="governance-form-field"><span>操作人</span><input onChange={(event) => onUpdatedByChange(event.target.value)} required value={updatedBy} /></label>
          <label className="governance-form-field"><span>变更原因</span><textarea onChange={(event) => onLifecycleReasonChange(event.target.value)} required rows={3} value={lifecycleReason} /></label>
        </>
      ) : null}
      {dialog.type === "correction" ? (
        <>
          <label className="governance-form-field"><span>修正摘要</span><textarea onChange={(event) => onCorrectionSummaryChange(event.target.value)} required rows={5} value={correctionSummary} /></label>
          <div className="governance-current-preview"><strong>当前正文</strong><pre aria-label="当前正文预览" tabIndex={0}>{truncateContent(article.content)}</pre></div>
        </>
      ) : null}
      {dialog.type === "rollback" ? (
        <div className="governance-current-preview"><strong>快照正文</strong><pre aria-label="快照正文预览" tabIndex={0}>{truncateContent(dialog.snapshot.content)}</pre></div>
      ) : null}
    </ArticleGovernanceDialog>
  );
}

function VersionComparison({ previousLabel, previousValue, currentLabel, currentValue }: { previousLabel: string; previousValue: string; currentLabel: string; currentValue: string }) {
  const change = changedBlock(previousValue, currentValue);
  return (
    <div className="version-comparison">
      <section><h4>{previousLabel}</h4><pre aria-label={`${previousLabel}差异`} tabIndex={0}>{change.previous || "无差异"}</pre></section>
      <section><h4>{currentLabel}</h4><pre aria-label={`${currentLabel}差异`} tabIndex={0}>{change.current || "无差异"}</pre></section>
    </div>
  );
}

function dialogConfiguration(dialog: DialogState, article: ArticleDetail) {
  if (dialog.type === "review" && dialog.action === "approve") return { title: "确认通过审核", description: "将更新审核状态，并写入文章快照与审核审计。", confirmLabel: "确认通过", destructive: false };
  if (dialog.type === "review") return { title: "确认要求修改", description: "将调用文章纠错链路、更新审核状态并写入审计。", confirmLabel: "提交修改要求", destructive: true };
  if (dialog.type === "correction") return { title: "确认人工修正", description: "服务端将基于修正摘要重新生成正文、创建快照并计算下游影响。", confirmLabel: "执行修正", destructive: true };
  if (dialog.type === "rollback") return { title: "确认文章回滚", description: `当前版本将恢复为快照 #${dialog.snapshot.snapshotId}，并保留回滚留痕。`, confirmLabel: "确认回滚", destructive: true };
  return { title: `确认${labelLifecycleAction(dialog.action)}`, description: `生命周期将从“${labelValue(article.lifecycle)}”切换为“${labelLifecycleTarget(dialog.action)}”。`, confirmLabel: `确认${labelLifecycleAction(dialog.action)}`, destructive: dialog.action !== "activate" };
}

function availableLifecycleActions(lifecycle: string): LifecycleAction[] {
  const current = lifecycle.toLowerCase();
  return (["activate", "deprecate", "archive"] as LifecycleAction[]).filter((action) => lifecycleTarget(action) !== current);
}

function lifecycleTarget(action: LifecycleAction) {
  return { activate: "active", deprecate: "deprecated", archive: "archived" }[action];
}

function labelLifecycleAction(action: LifecycleAction) {
  return { activate: "恢复生效", deprecate: "标记废弃", archive: "归档" }[action];
}

function labelLifecycleTarget(action: LifecycleAction) {
  return labelValue(lifecycleTarget(action));
}

function labelValue(value: string) {
  return { active: "生效", ACTIVE: "生效", deprecated: "已废弃", DEPRECATED: "已废弃", archived: "已归档", ARCHIVED: "已归档", passed: "通过", needs_review: "需复核", needs_human_review: "待人工复核", accepted: "已接受", published: "已发布" }[value] ?? value;
}

function formatDateTime(value: string | null) {
  if (!value) return "--";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "--" : new Intl.DateTimeFormat("zh-CN", { dateStyle: "medium", timeStyle: "short" }).format(date);
}

function truncateContent(value: string) {
  const lines = value.split("\n");
  return lines.length > 32 ? `${lines.slice(0, 32).join("\n")}\n...` : value;
}

function changedBlock(previous: string, current: string) {
  if (previous === current) return { previous: "", current: "" };
  const previousLines = previous.split("\n");
  const currentLines = current.split("\n");
  let start = 0;
  while (start < previousLines.length && start < currentLines.length && previousLines[start] === currentLines[start]) start += 1;
  let previousEnd = previousLines.length;
  let currentEnd = currentLines.length;
  while (previousEnd > start && currentEnd > start && previousLines[previousEnd - 1] === currentLines[currentEnd - 1]) {
    previousEnd -= 1;
    currentEnd -= 1;
  }
  return {
    previous: truncateContent(previousLines.slice(start, previousEnd).join("\n")),
    current: truncateContent(currentLines.slice(start, currentEnd).join("\n")),
  };
}

function resolveErrorMessage(error: unknown) {
  return isApiError(error) ? error.message : "操作未能完成，请重新核对当前状态。";
}
