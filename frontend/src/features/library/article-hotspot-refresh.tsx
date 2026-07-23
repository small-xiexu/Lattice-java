import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Flame, RefreshCw } from "lucide-react";
import { useState } from "react";

import { isApiError } from "../../api/api-error";
import {
  articleGovernanceApi,
  type ArticleHotspotRefreshResponse,
} from "../../api/contracts/article-governance";
import { queryKeys } from "../../api/query-keys";
import { InlineAlert } from "../../components/inline-alert";
import { ArticleGovernanceDialog } from "./article-governance-dialog";

export function ArticleHotspotRefresh() {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [threshold, setThreshold] = useState(3);
  const [limit, setLimit] = useState(200);
  const [result, setResult] = useState<ArticleHotspotRefreshResponse | null>(null);
  const mutation = useMutation({
    mutationFn: () => articleGovernanceApi.refreshHotspots({
      heatScoreThreshold: threshold,
      limit,
    }),
    onSuccess: (response) => {
      setResult(response);
      setOpen(false);
      void queryClient.invalidateQueries({ queryKey: queryKeys.articles.root });
    },
  });

  return (
    <div className="article-hotspot-control">
      <button className="secondary-button article-hotspot-trigger" disabled={mutation.isPending} onClick={() => { mutation.reset(); setOpen(true); }} type="button">
        <Flame aria-hidden="true" size={17} />刷新热点
      </button>
      {result ? (
        <div className="article-hotspot-result">
          <InlineAlert
            actionLabel="关闭"
            description={`重建 ${result.rebuiltStatsCount} 条统计，命中 ${result.hotspotCandidateCount} 个候选，更新 ${result.updatedArticleCount} 篇文章。`}
            onAction={() => setResult(null)}
            title={`热点刷新完成（阈值 ${result.heatScoreThreshold}）`}
            tone="success"
          />
          {result.candidates.length ? (
            <ul className="hotspot-candidate-list" aria-label="热点候选">
              {result.candidates.map((candidate) => (
                <li key={candidate.articleKey}>
                  <code>{candidate.articleKey}</code>
                  <span>热度 {candidate.heatScore}</span>
                </li>
              ))}
            </ul>
          ) : null}
        </div>
      ) : null}
      {open ? (
        <ArticleGovernanceDialog
          confirmLabel="确认刷新"
          description="将重建全部文章使用统计，并按阈值更新热点与待抽检标记。"
          error={mutation.error ? resolveErrorMessage(mutation.error) : undefined}
          onClose={() => { if (!mutation.isPending) setOpen(false); }}
          onConfirm={() => mutation.mutate()}
          pending={mutation.isPending}
          title="确认刷新文章热点"
        >
          <dl className="governance-impact-summary">
            <div><dt>作用范围</dt><dd>全部文章使用统计</dd></div>
            <div><dt>标记结果</dt><dd>热点候选与待结果抽检</dd></div>
          </dl>
          <div className="hotspot-fields">
            <label className="governance-form-field">
              <span>热度阈值</span>
              <input max="2147483647" min="1" onChange={(event) => setThreshold(Number(event.target.value))} required type="number" value={threshold} />
            </label>
            <label className="governance-form-field">
              <span>候选上限</span>
              <input max="200" min="1" onChange={(event) => setLimit(Number(event.target.value))} required type="number" value={limit} />
            </label>
          </div>
          <p className="governance-impact-note"><RefreshCw aria-hidden="true" size={16} />刷新进行中时不能再次提交；失败后不会自动重试。</p>
        </ArticleGovernanceDialog>
      ) : null}
    </div>
  );
}

function resolveErrorMessage(error: unknown) {
  return isApiError(error) ? error.message : "热点刷新未能完成，请核对参数后重试。";
}
