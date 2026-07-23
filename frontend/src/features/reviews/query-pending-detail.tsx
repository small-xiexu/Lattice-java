import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Check, Pencil, Trash2 } from "lucide-react";
import { useState } from "react";

import {
  reviewsApi,
  type PendingQueryItem,
  type PendingQueryList,
} from "../../api/contracts/reviews";
import { queryKeys } from "../../api/query-keys";
import { MarkdownReport } from "../../components/markdown-report";
import { ArticleGovernanceDialog } from "../library/article-governance-dialog";
import { formatReviewTime, pendingStatusLabel, resolveReviewError } from "./review-utils";

export type PendingAction = "correct" | "confirm" | "discard";

export function QueryPendingDetail({
  item,
  onSuccess,
  onFailure,
}: {
  item: PendingQueryItem;
  onSuccess: (action: PendingAction, item: PendingQueryItem) => void;
  onFailure: (description: string) => void;
}) {
  const queryClient = useQueryClient();
  const [action, setAction] = useState<PendingAction | null>(null);
  const mutation = useMutation({
    mutationFn: async ({ requestAction, correction }: {
      requestAction: PendingAction;
      correction: string;
    }) => {
      if (requestAction === "correct") {
        return { requestAction, response: await reviewsApi.correctPendingQuery(item.queryId, correction) };
      }
      if (requestAction === "confirm") {
        return { requestAction, response: await reviewsApi.confirmPendingQuery(item.queryId) };
      }
      return { requestAction, response: await reviewsApi.discardPendingQuery(item.queryId) };
    },
    onSuccess: async ({ requestAction, response }) => {
      queryClient.setQueryData<PendingQueryList>(queryKeys.reviews.pendingQueries, (current) => {
        if (!current) return current;
        if (requestAction === "correct" && "answer" in response) {
          return {
            ...current,
            items: current.items.map((entry) => entry.queryId === item.queryId
              ? { ...entry, answer: response.answer }
              : entry),
          };
        }
        const items = current.items.filter((entry) => entry.queryId !== item.queryId);
        return { count: items.length, items };
      });
      setAction(null);
      onSuccess(requestAction, item);
      await queryClient.invalidateQueries({ queryKey: queryKeys.reviews.pendingQueries });
    },
    onError: async (error) => {
      onFailure(resolveReviewError(error));
      await queryClient.invalidateQueries({ queryKey: queryKeys.reviews.pendingQueries });
    },
  });

  const openAction = (nextAction: PendingAction) => {
    mutation.reset();
    setAction(nextAction);
  };

  return (
    <article className="compile-review-detail query-pending-detail">
      <header>
        <div>
          <span className={`review-status is-${item.reviewStatus.toUpperCase() === "PASSED" ? "success" : "warning"}`}>
            {pendingStatusLabel(item.reviewStatus)}
          </span>
          <h2>{item.question || "未提供问题"}</h2>
          <code>{item.queryId}</code>
        </div>
        <div className="review-detail-actions">
          <button
            className="secondary-button"
            disabled={mutation.isPending}
            onClick={() => openAction("correct")}
            type="button"
          >
            <Pencil aria-hidden="true" size={16} />
            更正答案
          </button>
          <button
            className="primary-button"
            disabled={mutation.isPending}
            onClick={() => openAction("confirm")}
            type="button"
          >
            <Check aria-hidden="true" size={16} />
            确认并沉淀
          </button>
          <button
            className="danger-button"
            disabled={mutation.isPending}
            onClick={() => openAction("discard")}
            type="button"
          >
            <Trash2 aria-hidden="true" size={16} />
            丢弃
          </button>
        </div>
      </header>

      <dl className="review-detail-facts">
        <Fact label="创建时间">{formatReviewTime(item.createdAt)}</Fact>
        <Fact label="过期时间">{formatReviewTime(item.expiresAt)}</Fact>
        <Fact label="关联概念">{item.selectedConceptIds.length}</Fact>
        <Fact label="来源文件">{item.sourceFilePaths.length}</Fact>
      </dl>

      <section aria-labelledby={`pending-answer-${item.queryId}`} className="review-detail-section review-draft-section">
        <div className="review-section-heading">
          <h3 id={`pending-answer-${item.queryId}`}>待确认答案</h3>
        </div>
        <MarkdownReport content={item.answer} label="待确认答案正文" />
      </section>

      <section aria-labelledby={`pending-concepts-${item.queryId}`} className="review-detail-section">
        <div className="review-section-heading">
          <h3 id={`pending-concepts-${item.queryId}`}>关联概念</h3>
          <span>{item.selectedConceptIds.length}</span>
        </div>
        {item.selectedConceptIds.length ? (
          <ul className="review-source-list">
            {item.selectedConceptIds.map((conceptId, index) => (
              <li key={`${conceptId}-${index}`}><code>{conceptId}</code></li>
            ))}
          </ul>
        ) : <p className="review-empty-line">未关联概念</p>}
      </section>

      <section aria-labelledby={`pending-sources-${item.queryId}`} className="review-detail-section">
        <div className="review-section-heading">
          <h3 id={`pending-sources-${item.queryId}`}>来源文件</h3>
          <span>{item.sourceFilePaths.length}</span>
        </div>
        {item.sourceFilePaths.length ? (
          <ul className="review-source-list">
            {item.sourceFilePaths.map((path, index) => (
              <li key={`${path}-${index}`}><code>{path}</code></li>
            ))}
          </ul>
        ) : <p className="review-empty-line">服务端未记录来源路径</p>}
      </section>

      {action ? (
        <PendingActionDialog
          action={action}
          error={mutation.isError ? resolveReviewError(mutation.error) : undefined}
          item={item}
          onClose={() => setAction(null)}
          onConfirm={(correction) => mutation.mutate({ requestAction: action, correction })}
          pending={mutation.isPending}
        />
      ) : null}
    </article>
  );
}

function PendingActionDialog({
  action,
  item,
  pending,
  error,
  onClose,
  onConfirm,
}: {
  action: PendingAction;
  item: PendingQueryItem;
  pending: boolean;
  error?: string;
  onClose: () => void;
  onConfirm: (correction: string) => void;
}) {
  const [correction, setCorrection] = useState("");
  const configuration = actionConfiguration(action);
  const correctionMissing = action === "correct" && !correction.trim();
  return (
    <ArticleGovernanceDialog
      confirmLabel={configuration.confirmLabel}
      description={configuration.description}
      destructive={action === "discard"}
      error={error ?? (correctionMissing ? "请填写具体的更正说明" : undefined)}
      onClose={onClose}
      onConfirm={() => {
        if (!correctionMissing) onConfirm(correction.trim());
      }}
      pending={pending}
      title={configuration.title}
    >
      <dl className="governance-impact-summary">
        <div><dt>查询</dt><dd>{item.question}</dd></div>
        <div><dt>来源文件</dt><dd>{item.sourceFilePaths.length} 个</dd></div>
        <div><dt>当前处理</dt><dd>{configuration.impact}</dd></div>
      </dl>
      {action === "correct" ? (
        <label className="governance-form-field">
          更正说明
          <textarea
            autoFocus
            onChange={(event) => setCorrection(event.target.value)}
            placeholder="说明答案中需要修正或补充的内容"
            rows={5}
            value={correction}
          />
        </label>
      ) : null}
    </ArticleGovernanceDialog>
  );
}

function actionConfiguration(action: PendingAction) {
  if (action === "correct") {
    return {
      title: "确认更正待确认答案",
      confirmLabel: "提交更正并重写",
      description: "服务端会调用当前答案生成链路重写正文；记录保持待确认，并刷新七天有效期。",
      impact: "重写答案，保留在待确认队列",
    };
  }
  if (action === "confirm") {
    return {
      title: "确认沉淀最终答案",
      confirmLabel: "确认并写入贡献",
      description: "当前问题、答案和更正历史会写入贡献记录，随后从待确认队列移除。",
      impact: "写入贡献记录并移出队列",
    };
  }
  return {
    title: "确认丢弃待确认查询",
    confirmLabel: "确认丢弃",
    description: "该记录会直接从待确认队列删除，且不会写入贡献记录。此操作不可撤销。",
    impact: "删除待确认记录，不沉淀贡献",
  };
}

function Fact({ label, children }: { label: string; children: React.ReactNode }) {
  return <div><dt>{label}</dt><dd>{children}</dd></div>;
}
