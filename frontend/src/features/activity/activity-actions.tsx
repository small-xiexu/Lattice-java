import { useMutation, useQueryClient } from "@tanstack/react-query";
import { RotateCcw, ShieldCheck } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";

import { activityApi } from "../../api/contracts/activity";
import type { CompileJob, SourceRun } from "../../api/contracts/source-imports";
import { InlineAlert } from "../../components/inline-alert";
import { ArticleGovernanceDialog } from "../library/article-governance-dialog";
import { resolveActivityError } from "./activity-utils";

type SourceRunTarget = Pick<
  SourceRun,
  | "runId"
  | "sourceId"
  | "sourceType"
  | "status"
  | "displayStatusLabel"
  | "actions"
  | "pendingHumanReviewCount"
  | "compileJobId"
>;

type ActivityRequest =
  | { kind: "source-confirm"; runId: number; decision: string; sourceId?: number; label: string }
  | { kind: "source-retry"; runId: number }
  | { kind: "compile-retry"; jobId: string };

export function SourceRunActions({ target }: { target: SourceRunTarget }) {
  const [request, setRequest] = useState<ActivityRequest | null>(null);
  const mutation = useActivityMutation(() => setRequest(null));
  const confirmActions = target.actions.filter(
    (action) => action.runId && action.decision && action.actionKey.startsWith("CONFIRM_"),
  );
  const canRetry = target.status === "FAILED" && target.sourceType === "UPLOAD";
  const resyncAction = target.actions.find((action) => action.actionKey === "RESYNC_SOURCE");

  return (
    <div className="activity-action-region">
      {mutation.data ? (
        <InlineAlert
          description="服务端已返回最新任务状态。"
          title="任务操作已提交"
          tone="success"
        />
      ) : null}
      <div className="activity-actions">
        {confirmActions.map((action) => (
          <button
            className="secondary-button"
            disabled={mutation.isPending}
            key={action.actionKey}
            onClick={() => setRequest({
              kind: "source-confirm",
              runId: action.runId!,
              decision: action.decision!,
              sourceId: action.decisionSourceId ?? undefined,
              label: action.label,
            })}
            type="button"
          >
            <ShieldCheck aria-hidden="true" size={16} />
            {action.label}
          </button>
        ))}
        {canRetry ? (
          <button
            className="secondary-button"
            disabled={mutation.isPending}
            onClick={() => setRequest({ kind: "source-retry", runId: target.runId })}
            type="button"
          >
            <RotateCcw aria-hidden="true" size={16} />
            重试同步运行
          </button>
        ) : null}
        {resyncAction?.sourceId ? (
          <Link className="secondary-button" to={`/library/sources/${resyncAction.sourceId}`}>
            打开资料源重新同步
          </Link>
        ) : null}
        {target.pendingHumanReviewCount > 0 && target.compileJobId ? (
          <Link className="primary-button" to={`/reviews?jobId=${encodeURIComponent(target.compileJobId)}`}>
            处理待人工确认草稿
          </Link>
        ) : null}
      </div>
      {request ? (
        <ActivityActionDialog
          error={mutation.error ? resolveActivityError(mutation.error) : undefined}
          onClose={() => setRequest(null)}
          onConfirm={() => mutation.mutate(request)}
          pending={mutation.isPending}
          request={request}
        />
      ) : null}
    </div>
  );
}

export function CompileJobActions({ job }: { job: CompileJob }) {
  const [request, setRequest] = useState<ActivityRequest | null>(null);
  const mutation = useActivityMutation(() => setRequest(null));
  return (
    <div className="activity-action-region">
      {mutation.data ? <InlineAlert description="作业已重新进入等待队列。" title="编译重试已提交" tone="success" /> : null}
      <div className="activity-actions">
        {job.status === "FAILED" ? (
          <button
            className="secondary-button"
            disabled={mutation.isPending}
            onClick={() => setRequest({ kind: "compile-retry", jobId: job.jobId })}
            type="button"
          >
            <RotateCcw aria-hidden="true" size={16} />
            重试编译作业
          </button>
        ) : null}
        {(job.reviewSummary?.needsHumanReviewCount ?? 0) > 0 ? (
          <Link className="primary-button" to={`/reviews?jobId=${encodeURIComponent(job.jobId)}`}>
            处理待人工确认草稿
          </Link>
        ) : null}
      </div>
      {request ? (
        <ActivityActionDialog
          error={mutation.error ? resolveActivityError(mutation.error) : undefined}
          onClose={() => setRequest(null)}
          onConfirm={() => mutation.mutate(request)}
          pending={mutation.isPending}
          request={request}
        />
      ) : null}
    </div>
  );
}

function useActivityMutation(onSuccess: () => void) {
  const queryClient = useQueryClient();
  return useMutation<SourceRun | CompileJob, Error, ActivityRequest>({
    mutationFn: async (request) => {
      if (request.kind === "source-confirm") {
        return await activityApi.confirmSourceRun(request.runId, {
          decision: request.decision,
          sourceId: request.sourceId,
        });
      }
      if (request.kind === "source-retry") {
        return await activityApi.retrySourceRun(request.runId);
      }
      return await activityApi.retryCompileJob(request.jobId);
    },
    onSuccess: async () => {
      onSuccess();
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["admin", "processing-tasks"] }),
        queryClient.invalidateQueries({ queryKey: ["admin", "source-runs"] }),
        queryClient.invalidateQueries({ queryKey: ["admin", "compile-jobs"] }),
        queryClient.invalidateQueries({ queryKey: ["admin", "activity"] }),
      ]);
    },
  });
}

function ActivityActionDialog({
  request,
  pending,
  error,
  onClose,
  onConfirm,
}: {
  request: ActivityRequest;
  pending: boolean;
  error?: string;
  onClose: () => void;
  onConfirm: () => void;
}) {
  const sourceConfirm = request.kind === "source-confirm";
  const title = sourceConfirm ? "确认同步归属" : "确认重试任务";
  const confirmLabel = sourceConfirm ? "确认并继续处理" : "确认重试";
  return (
    <ArticleGovernanceDialog
      confirmLabel={confirmLabel}
      description={sourceConfirm
        ? "该决策会确定资料归属，并让当前同步继续进入编译流程。"
        : "服务端会复用当前任务记录和已保存输入重新排队，不会创建重复任务。"}
      error={error}
      onClose={onClose}
      onConfirm={onConfirm}
      pending={pending}
      title={title}
    >
      <dl className="governance-impact-summary">
        <div><dt>任务类型</dt><dd>{resolveRequestType(request)}</dd></div>
        <div><dt>任务标识</dt><dd>{resolveRequestId(request)}</dd></div>
        {sourceConfirm ? <div><dt>确认决策</dt><dd>{request.label}</dd></div> : null}
        <div><dt>执行限制</dt><dd>操作只提交一次；失败时保留当前确认信息</dd></div>
      </dl>
    </ArticleGovernanceDialog>
  );
}

function resolveRequestType(request: ActivityRequest) {
  if (request.kind === "source-confirm") return "同步运行人工确认";
  if (request.kind === "source-retry") return "同步运行重试";
  return "编译作业重试";
}

function resolveRequestId(request: ActivityRequest) {
  return request.kind === "compile-retry" ? request.jobId : `#${request.runId}`;
}
