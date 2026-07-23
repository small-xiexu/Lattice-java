import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Save } from "lucide-react";
import { useState, type FormEvent } from "react";

import {
  reviewsApi,
  type CompileReviewConfig,
  type CompileReviewConfigRequest,
} from "../../api/contracts/reviews";
import { queryKeys } from "../../api/query-keys";
import { InlineAlert } from "../../components/inline-alert";
import { PageState } from "../../components/page-state";
import { ArticleGovernanceDialog } from "../library/article-governance-dialog";
import { formatReviewTime, resolveReviewError } from "./review-utils";

export function CompileReviewPolicy() {
  const query = useQuery({
    queryKey: queryKeys.settings.compileReview,
    queryFn: ({ signal }) => reviewsApi.getCompileReviewConfig(signal),
  });

  if (query.isPending) return <PageState status="loading" title="正在加载编译审核策略" />;
  if (query.isError) {
    return (
      <PageState
        actionLabel="重试"
        description={resolveReviewError(query.error)}
        onAction={() => void query.refetch()}
        status="error"
        title="编译审核策略加载失败"
      />
    );
  }
  return <CompileReviewPolicyForm config={query.data} />;
}

function CompileReviewPolicyForm({ config }: { config: CompileReviewConfig }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState(() => toForm(config));
  const [confirmOpen, setConfirmOpen] = useState(false);
  const mutation = useMutation({
    mutationFn: (request: CompileReviewConfigRequest) =>
      reviewsApi.updateCompileReviewConfig(request),
    onSuccess: async (saved) => {
      queryClient.setQueryData(queryKeys.settings.compileReview, saved);
      setForm(toForm(saved));
      setConfirmOpen(false);
      await queryClient.invalidateQueries({ queryKey: queryKeys.settings.compileReview });
    },
  });

  const parsedRounds = Number(form.maxFixRounds);
  const validation = !Number.isInteger(parsedRounds) || parsedRounds < 0 || parsedRounds > 5
    ? "自动修复轮次必须是 0 到 5 的整数"
    : !form.operator.trim()
      ? "操作人不能为空"
      : null;
  const request: CompileReviewConfigRequest = {
    autoFixEnabled: form.autoFixEnabled,
    maxFixRounds: parsedRounds,
    allowPersistNeedsHumanReview: form.allowPersistNeedsHumanReview,
    humanReviewSeverityThreshold: form.humanReviewSeverityThreshold,
    operator: form.operator.trim(),
  };
  const dirty = JSON.stringify(request) !== JSON.stringify({
    autoFixEnabled: config.autoFixEnabled,
    maxFixRounds: config.maxFixRounds,
    allowPersistNeedsHumanReview: config.allowPersistNeedsHumanReview,
    humanReviewSeverityThreshold: config.humanReviewSeverityThreshold,
    operator: config.updatedBy || config.createdBy || "admin",
  });

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!validation && dirty) setConfirmOpen(true);
  };

  return (
    <section className="review-policy" aria-labelledby="review-policy-title">
      <div className="review-policy-heading">
        <div>
          <h2 id="review-policy-title">编译审核策略</h2>
          <p>当前来源：{config.configSource} · 最近更新：{formatReviewTime(config.updatedAt)}</p>
        </div>
        <span className="review-policy-source">{config.configSource}</span>
      </div>

      {mutation.isSuccess ? <InlineAlert title="编译审核策略已保存并立即生效" tone="success" /> : null}
      {mutation.isError && !confirmOpen ? (
        <InlineAlert description={resolveReviewError(mutation.error)} title="策略保存失败，表单内容已保留" tone="error" />
      ) : null}

      <form className="review-policy-form" onSubmit={submit}>
        <label className="review-policy-toggle">
          <span><strong>启用自动修复</strong><small>审查失败后按最大轮次自动修正并重新审核</small></span>
          <input
            checked={form.autoFixEnabled}
            onChange={(event) => setForm({ ...form, autoFixEnabled: event.target.checked })}
            type="checkbox"
          />
        </label>
        <label className="review-policy-field">
          <span>最大自动修复轮次</span>
          <input
            inputMode="numeric"
            max={5}
            min={0}
            onChange={(event) => setForm({ ...form, maxFixRounds: event.target.value })}
            type="number"
            value={form.maxFixRounds}
          />
          <small>允许范围 0 到 5</small>
        </label>
        <label className="review-policy-field">
          <span>触发人工复核的最低严重度</span>
          <select
            onChange={(event) => setForm({
              ...form,
              humanReviewSeverityThreshold: event.target.value as CompileReviewConfigRequest["humanReviewSeverityThreshold"],
            })}
            value={form.humanReviewSeverityThreshold}
          >
            <option value="HIGH">HIGH</option>
            <option value="MEDIUM">MEDIUM</option>
            <option value="LOW">LOW</option>
          </select>
        </label>
        <label className="review-policy-toggle is-warning">
          <span><strong>允许待人工复核文章落库</strong><small>开启后，未完成人工确认的文章也可进入知识库</small></span>
          <input
            checked={form.allowPersistNeedsHumanReview}
            onChange={(event) => setForm({ ...form, allowPersistNeedsHumanReview: event.target.checked })}
            type="checkbox"
          />
        </label>
        <label className="review-policy-field">
          <span>操作人</span>
          <input
            autoComplete="off"
            onChange={(event) => setForm({ ...form, operator: event.target.value })}
            value={form.operator}
          />
        </label>
        {validation ? <p className="review-policy-validation" role="alert">{validation}</p> : null}
        <div className="review-policy-actions">
          <button
            className="secondary-button"
            disabled={!dirty || mutation.isPending}
            onClick={() => setForm(toForm(config))}
            type="button"
          >
            放弃修改
          </button>
          <button className="primary-button" disabled={!dirty || Boolean(validation) || mutation.isPending} type="submit">
            <Save aria-hidden="true" size={16} />
            保存策略
          </button>
        </div>
      </form>

      <dl className="review-policy-audit">
        <div><dt>创建人</dt><dd>{config.createdBy || "--"}</dd></div>
        <div><dt>更新人</dt><dd>{config.updatedBy || "--"}</dd></div>
        <div><dt>创建时间</dt><dd>{formatReviewTime(config.createdAt)}</dd></div>
        <div><dt>更新时间</dt><dd>{formatReviewTime(config.updatedAt)}</dd></div>
      </dl>

      {confirmOpen ? (
        <ArticleGovernanceDialog
          confirmLabel="确认保存并立即生效"
          description="保存后配置会立即作用于后续编译任务，已运行任务不回溯修改。"
          error={mutation.error ? resolveReviewError(mutation.error) : undefined}
          onClose={() => setConfirmOpen(false)}
          onConfirm={() => mutation.mutate(request)}
          pending={mutation.isPending}
          title="确认更新编译审核策略"
        >
          <dl className="governance-impact-summary">
            <div><dt>自动修复</dt><dd>{request.autoFixEnabled ? "启用" : "停用"}，最多 {request.maxFixRounds} 轮</dd></div>
            <div><dt>人工阈值</dt><dd>{request.humanReviewSeverityThreshold}</dd></div>
            <div><dt>待审落库</dt><dd>{request.allowPersistNeedsHumanReview ? "允许" : "阻止"}</dd></div>
            <div><dt>操作人</dt><dd>{request.operator}</dd></div>
          </dl>
        </ArticleGovernanceDialog>
      ) : null}
    </section>
  );
}

function toForm(config: CompileReviewConfig) {
  return {
    autoFixEnabled: config.autoFixEnabled,
    maxFixRounds: String(config.maxFixRounds),
    allowPersistNeedsHumanReview: config.allowPersistNeedsHumanReview,
    humanReviewSeverityThreshold: config.humanReviewSeverityThreshold,
    operator: config.updatedBy || config.createdBy || "admin",
  };
}
