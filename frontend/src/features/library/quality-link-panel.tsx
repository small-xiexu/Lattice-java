import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Eye, Save } from "lucide-react";
import { useState } from "react";

import { qualityApi, type LinkEnhancementReport } from "../../api/contracts/quality";
import { queryKeys } from "../../api/query-keys";
import { InlineAlert } from "../../components/inline-alert";
import { PageState } from "../../components/page-state";
import { ArticleGovernanceDialog } from "./article-governance-dialog";
import { resolveQualityError } from "./quality-utils";
import { MetricGrid, QualitySection } from "./quality-view-shared";

export function QualityLinkPanel() {
  const queryClient = useQueryClient();
  const [preview, setPreview] = useState<LinkEnhancementReport>();
  const [persisted, setPersisted] = useState<LinkEnhancementReport>();
  const [confirmOpen, setConfirmOpen] = useState(false);
  const mutation = useMutation({
    mutationFn: (persist: boolean) => qualityApi.enhanceLinks(persist),
    onSuccess: async (result, persist) => {
      if (persist) {
        setPersisted(result);
        setConfirmOpen(false);
        await Promise.all([
          queryClient.invalidateQueries({ queryKey: queryKeys.articles.root }),
          queryClient.invalidateQueries({ queryKey: queryKeys.quality.root }),
          queryClient.invalidateQueries({ queryKey: queryKeys.overview }),
        ]);
      } else {
        setPreview(result);
        setPersisted(undefined);
      }
    },
  });
  const runPreview = () => {
    mutation.reset();
    setPreview(undefined);
    setPersisted(undefined);
    mutation.mutate(false);
  };

  return (
    <QualitySection
      actions={
        <div className="quality-inline-actions">
          <button className="secondary-button quality-action-button" disabled={mutation.isPending} onClick={runPreview} type="button">
            <Eye aria-hidden="true" size={16} />
            {mutation.isPending && !confirmOpen ? "正在预览" : "生成预览"}
          </button>
          <button
            className="primary-button quality-action-button"
            disabled={!preview || mutation.isPending}
            onClick={() => { mutation.reset(); setConfirmOpen(true); }}
            type="button"
          >
            <Save aria-hidden="true" size={16} />
            持久化增强
          </button>
        </div>
      }
      context="先以 persist=false 生成当前预览，再显式确认持久化"
      title="链接增强"
    >
      {mutation.isError && !confirmOpen ? <InlineAlert description={resolveQualityError(mutation.error)} title="链接增强预览失败" tone="error" /> : null}
      {persisted ? (
        <InlineAlert
          description={`更新 ${persisted.updatedArticleCount} 篇文章，修复 ${persisted.fixedLinkCount} 个链接，同步 ${persisted.syncedSectionCount} 个关系区块`}
          title="链接增强已持久化"
          tone="success"
        />
      ) : null}
      {!preview && !mutation.isPending ? (
        <PageState description="持久化操作只有在本次页面会话生成预览后才会启用。" status="empty" title="尚未生成链接增强预览" />
      ) : null}
      {mutation.isPending && !preview ? <PageState status="loading" title="正在生成链接增强预览" /> : null}
      {preview ? <LinkReport report={preview} /> : null}
      {confirmOpen && preview ? (
        <ArticleGovernanceDialog
          confirmLabel="确认持久化"
          description="服务端会重新计算并写入链接与受管关系区块。"
          error={mutation.error ? resolveQualityError(mutation.error) : undefined}
          onClose={() => setConfirmOpen(false)}
          onConfirm={() => mutation.mutate(true)}
          pending={mutation.isPending}
          title="确认持久化链接增强"
        >
          <dl className="governance-impact-summary">
            <div><dt>预计文章</dt><dd>{preview.updatedArticleCount} 篇</dd></div>
            <div><dt>预计链接</dt><dd>{preview.fixedLinkCount} 个</dd></div>
            <div><dt>预计区块</dt><dd>{preview.syncedSectionCount} 个</dd></div>
            <div><dt>未解析链接</dt><dd>{preview.unresolvedLinkCount} 个，不会猜测目标</dd></div>
          </dl>
        </ArticleGovernanceDialog>
      ) : null}
    </QualitySection>
  );
}

function LinkReport({ report }: { report: LinkEnhancementReport }) {
  return (
    <div className="link-enhancement-report">
      <MetricGrid
        items={[
          { label: "文章总数", value: report.totalArticles },
          { label: "已扫描", value: report.processedArticleCount },
          { label: "预计更新", value: report.updatedArticleCount, tone: report.updatedArticleCount ? "warning" : "default" },
          { label: "修复链接", value: report.fixedLinkCount },
          { label: "同步区块", value: report.syncedSectionCount },
          { label: "未解析", value: report.unresolvedLinkCount, tone: report.unresolvedLinkCount ? "danger" : "default" },
        ]}
        label="链接增强预览统计"
      />
      {report.items.length ? (
        <div className="data-table-scroll">
          <table className="data-table link-enhancement-table">
            <thead><tr><th>文章</th><th>预计变化</th><th>未解析链接</th></tr></thead>
            <tbody>
              {report.items.map((item) => (
                <tr key={item.conceptId}>
                  <td data-label="文章"><strong>{item.title}</strong><code>{item.conceptId}</code></td>
                  <td data-label="预计变化"><span>{item.updated ? "会更新" : "无变化"}</span><small>链接 {item.fixedLinkCount} / 区块 {item.syncedSectionCount}</small></td>
                  <td data-label="未解析链接">{item.unresolvedLinks.length ? item.unresolvedLinks.join("、") : "无"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : <p className="quality-empty-line">预览未发现需要处理的文章</p>}
    </div>
  );
}
