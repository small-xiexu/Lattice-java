import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Wrench } from "lucide-react";
import { useMemo, useState } from "react";

import { qualityApi } from "../../api/contracts/quality";
import { queryKeys } from "../../api/query-keys";
import { InlineAlert } from "../../components/inline-alert";
import { PageState } from "../../components/page-state";
import { ArticleGovernanceDialog } from "./article-governance-dialog";
import { resolveQualityError } from "./quality-utils";
import { QualitySection, QueryFailure } from "./quality-view-shared";

export function QualityLintPanel() {
  const queryClient = useQueryClient();
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const lintQuery = useQuery({
    queryKey: queryKeys.quality.lint,
    queryFn: ({ signal }) => qualityApi.lint(signal),
  });
  const fixableIds = useMemo(
    () => [...new Set(lintQuery.data?.issues.filter((issue) => issue.fixable).map((issue) => issue.targetId) ?? [])],
    [lintQuery.data],
  );
  const activeIds = selectedIds.filter((id) => fixableIds.includes(id));
  const mutation = useMutation({
    mutationFn: (targetIds: string[]) => qualityApi.fixLint(targetIds),
    onSuccess: async () => {
      setConfirmOpen(false);
      setSelectedIds([]);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.quality.lint }),
        queryClient.invalidateQueries({ queryKey: queryKeys.quality.root }),
        queryClient.invalidateQueries({ queryKey: queryKeys.overview }),
      ]);
    },
  });
  const setSelected = (targetId: string, checked: boolean) => {
    setSelectedIds((current) => checked
      ? [...new Set([...current, targetId])]
      : current.filter((id) => id !== targetId));
  };

  return (
    <QualitySection
      actions={
        <button
          className="primary-button quality-action-button"
          disabled={!activeIds.length || mutation.isPending}
          onClick={() => { mutation.reset(); setConfirmOpen(true); }}
          type="button"
        >
          <Wrench aria-hidden="true" size={16} />
          修复所选 ({activeIds.length})
        </button>
      }
      context="只对服务端标记为可自动修复的目标执行定向修复"
      title="治理 Lint"
    >
      {mutation.data ? (
        <InlineAlert
          description={`修复 ${mutation.data.fixed} 项，跳过 ${mutation.data.skipped} 项${mutation.data.errors.length ? `；失败目标：${mutation.data.errors.join("、")}` : ""}`}
          title={mutation.data.errors.length ? "Lint 修复部分完成" : "Lint 修复完成"}
          tone={mutation.data.errors.length ? "warning" : "success"}
        />
      ) : null}
      {lintQuery.isPending ? <PageState status="loading" title="正在执行 Lint 检查" /> : null}
      {lintQuery.isError ? (
        <QueryFailure error={lintQuery.error} onRetry={() => void lintQuery.refetch()} title="Lint 报告加载失败" />
      ) : null}
      {lintQuery.data ? (
        <>
          <div className="quality-report-summary">
            <span>检查维度：{lintQuery.data.checkedDimensions.join("、") || "无"}</span>
            <strong>{lintQuery.data.totalIssues} 个问题</strong>
          </div>
          {lintQuery.data.issues.length ? (
            <div className="data-table-scroll">
              <table className="data-table quality-lint-table">
                <thead><tr><th>选择</th><th>目标</th><th>维度</th><th>问题与建议</th></tr></thead>
                <tbody>
                  {lintQuery.data.issues.map((issue, index) => (
                    <tr key={`${issue.dimension}-${issue.targetId}-${index}`}>
                      <td data-label="选择">
                        {issue.fixable ? (
                          <input
                            aria-label={`选择 ${issue.targetId}`}
                            checked={activeIds.includes(issue.targetId)}
                            onChange={(event) => setSelected(issue.targetId, event.target.checked)}
                            type="checkbox"
                          />
                        ) : <span className="quality-unavailable">不可修复</span>}
                      </td>
                      <td data-label="目标"><code>{issue.targetId}</code></td>
                      <td data-label="维度"><span className="status-label">{issue.dimension}</span></td>
                      <td data-label="问题与建议"><strong>{issue.message}</strong>{issue.fixSuggestion ? <p>{issue.fixSuggestion}</p> : null}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : <PageState status="empty" title="Lint 检查未发现问题" />}
        </>
      ) : null}
      {confirmOpen ? (
        <ArticleGovernanceDialog
          confirmLabel="确认修复"
          description="服务端会重新执行 Lint，并只处理以下目标。"
          error={mutation.error ? resolveQualityError(mutation.error) : undefined}
          onClose={() => setConfirmOpen(false)}
          onConfirm={() => mutation.mutate(activeIds)}
          pending={mutation.isPending}
          title="确认 Lint 自动修复"
        >
          <dl className="governance-impact-summary">
            <div><dt>目标数量</dt><dd>{activeIds.length}</dd></div>
            <div><dt>目标标识</dt><dd>{activeIds.join("、")}</dd></div>
            <div><dt>执行范围</dt><dd>只修复当前报告中仍标记为可修复的目标</dd></div>
          </dl>
        </ArticleGovernanceDialog>
      ) : null}
    </QualitySection>
  );
}
