import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Download } from "lucide-react";
import { useState } from "react";

import { qualityApi, type InspectionQuestion } from "../../api/contracts/quality";
import { queryKeys } from "../../api/query-keys";
import { InlineAlert } from "../../components/inline-alert";
import { PageState } from "../../components/page-state";
import { ArticleGovernanceDialog } from "./article-governance-dialog";
import { resolveQualityError } from "./quality-utils";
import { QualitySection, QueryFailure } from "./quality-view-shared";

export function QualityInspectionPanel() {
  const queryClient = useQueryClient();
  const [selected, setSelected] = useState<InspectionQuestion | null>(null);
  const [finalAnswer, setFinalAnswer] = useState("");
  const [confirmedBy, setConfirmedBy] = useState("");
  const [validationError, setValidationError] = useState<string>();
  const inspectionQuery = useQuery({
    queryKey: queryKeys.quality.inspection,
    queryFn: ({ signal }) => qualityApi.inspect(signal),
  });
  const mutation = useMutation({
    mutationFn: qualityApi.importInspectionAnswer,
    onSuccess: async () => {
      setSelected(null);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.quality.inspection }),
        queryClient.invalidateQueries({ queryKey: queryKeys.quality.root }),
        queryClient.invalidateQueries({ queryKey: queryKeys.overview }),
      ]);
    },
  });
  const openImport = (question: InspectionQuestion) => {
    mutation.reset();
    setSelected(question);
    setFinalAnswer(question.suggestedAnswer);
    setConfirmedBy("");
    setValidationError(undefined);
  };
  const confirmImport = () => {
    if (!selected) return;
    if (!finalAnswer.trim() || !confirmedBy.trim()) {
      setValidationError("最终答案和确认人均为必填项");
      return;
    }
    setValidationError(undefined);
    mutation.mutate({
      inspectionId: selected.id,
      finalAnswer: finalAnswer.trim(),
      confirmedBy: confirmedBy.trim(),
    });
  };

  return (
    <QualitySection context="将服务端待确认问题转为经人工确认的最终答案" title="知识检查">
      {mutation.data ? (
        <InlineAlert
          description={`已解决：${mutation.data.resolvedIds.join("、") || "无"}`}
          title={`已导入 ${mutation.data.importedCount} 条人工答案`}
          tone="success"
        />
      ) : null}
      {inspectionQuery.isPending ? <PageState status="loading" title="正在加载知识检查" /> : null}
      {inspectionQuery.isError ? (
        <QueryFailure error={inspectionQuery.error} onRetry={() => void inspectionQuery.refetch()} title="知识检查加载失败" />
      ) : null}
      {inspectionQuery.data?.questions.length ? (
        <ol className="inspection-list">
          {inspectionQuery.data.questions.map((question) => (
            <li key={question.id}>
              <header>
                <div>
                  <span className="status-label">{question.type}</span>
                  <span className="status-label">{question.reviewStatus}</span>
                </div>
                <code>{question.id}</code>
              </header>
              <h3>{question.question}</h3>
              <p>{question.prompt}</p>
              <div className="inspection-answer"><strong>建议答案</strong><p>{question.suggestedAnswer || "暂无建议答案"}</p></div>
              {question.sourcePaths.length ? <p className="inspection-sources">来源：{question.sourcePaths.join("、")}</p> : null}
              <button className="secondary-button quality-action-button" onClick={() => openImport(question)} type="button">
                <Download aria-hidden="true" size={16} />
                导入人工答案
              </button>
            </li>
          ))}
        </ol>
      ) : null}
      {inspectionQuery.data && !inspectionQuery.data.questions.length ? <PageState status="empty" title="当前没有待确认问题" /> : null}
      {selected ? (
        <ArticleGovernanceDialog
          confirmLabel="确认导入"
          description="最终答案会作为人工确认结果写入知识库贡献记录。"
          error={validationError ?? (mutation.error ? resolveQualityError(mutation.error) : undefined)}
          onClose={() => setSelected(null)}
          onConfirm={confirmImport}
          pending={mutation.isPending}
          title="确认导入人工答案"
        >
          <dl className="governance-impact-summary">
            <div><dt>检查标识</dt><dd>{selected.id}</dd></div>
            <div><dt>原始问题</dt><dd>{selected.question}</dd></div>
            <div><dt>来源</dt><dd>{selected.sourcePaths.join("、") || "未提供"}</dd></div>
          </dl>
          <label className="governance-form-field">
            最终答案
            <textarea onChange={(event) => setFinalAnswer(event.target.value)} rows={5} value={finalAnswer} />
          </label>
          <label className="governance-form-field">
            确认人
            <input onChange={(event) => setConfirmedBy(event.target.value)} value={confirmedBy} />
          </label>
        </ArticleGovernanceDialog>
      ) : null}
    </QualitySection>
  );
}
