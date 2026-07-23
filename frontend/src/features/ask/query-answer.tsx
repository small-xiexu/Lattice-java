import type { QueryResponse } from "../../api/contracts/query";
import { CitedMarkdownReport } from "../../components/cited-markdown-report";
import { InlineAlert } from "../../components/inline-alert";
import { PageState } from "../../components/page-state";
import { QueryFeedback } from "./query-feedback";

interface QueryAnswerProps {
  question: string;
  response: QueryResponse;
  activeMarkerId: string | null;
  onCitationActivate: (markerId: string) => void;
}

export function QueryAnswer({
  question,
  response,
  activeMarkerId,
  onCitationActivate,
}: QueryAnswerProps) {
  const answer = response.answer?.trim() ?? "";
  const status = resolvePresentation(response);

  return (
    <section aria-label="查询回答" className="query-answer">
      <header className="query-answer-header">
        <h2>回答</h2>
        <div className={`query-outcome is-${status.tone}`}>{status.label}</div>
      </header>
      {status.alert ? (
        <InlineAlert
          description={status.description}
          title={status.label}
          tone={status.tone}
        />
      ) : null}
      {answer || response.citationMarkers.length > 0 ? (
        <div className="query-answer-content">
          <CitedMarkdownReport
            activeMarkerId={activeMarkerId}
            content={answer}
            markers={response.citationMarkers}
            onCitationActivate={onCitationActivate}
          />
        </div>
      ) : (
        <PageState status="empty" title="没有可展示的回答" />
      )}
      <footer className="query-answer-meta">
        {response.reviewStatus ? <span>审查：{response.reviewStatus}</span> : null}
        {response.generationMode ? <span>生成：{response.generationMode}</span> : null}
        {response.queryId ? <code>{response.queryId}</code> : null}
      </footer>
      <QueryFeedback
        key={response.queryId ?? question}
        question={question}
        response={response}
      />
    </section>
  );
}

function resolvePresentation(response: QueryResponse): {
  label: string;
  tone: "info" | "success" | "warning" | "error";
  alert: boolean;
  description?: string;
} {
  switch (response.answerOutcome) {
    case "PARTIAL_ANSWER":
      return {
        label: "部分回答",
        tone: "warning",
        alert: true,
        description:
          resolveFallbackDescription(response.fallbackReason) ??
          "当前证据只覆盖了问题的一部分。",
      };
    case "INSUFFICIENT_EVIDENCE":
      return {
        label: "证据不足",
        tone: "warning",
        alert: true,
        description: "当前知识库没有足够证据形成可靠回答。",
      };
    case "NO_RELEVANT_KNOWLEDGE":
      return {
        label: "无相关知识",
        tone: "warning",
        alert: true,
        description: "当前知识库中没有找到与问题相关的内容。",
      };
    case "MODEL_FAILURE":
      return {
        label: "生成失败",
        tone: "error",
        alert: true,
        description: "模型调用未完成，当前没有可用的生成结果。",
      };
    case "SUCCESS":
      if (
        response.generationMode === "FALLBACK" ||
        response.modelExecutionStatus === "DEGRADED" ||
        response.modelExecutionStatus === "FAILED"
      ) {
        return {
          label: "降级回答",
          tone: "warning",
          alert: true,
          description:
            resolveFallbackDescription(response.fallbackReason) ??
            "系统返回了降级结果，请结合引用证据核对。",
        };
      }
      return { label: "回答完成", tone: "success", alert: false };
    case null:
      return { label: "状态未知", tone: "info", alert: true };
    default:
      return {
        label: "状态未知",
        tone: "info",
        alert: true,
        description: "服务返回了当前前端尚未识别的结果状态。",
      };
  }
}

function resolveFallbackDescription(reason: string | null) {
  switch (reason) {
    case "LLM_CALL_FAILED":
      return "模型调用失败，当前展示由检索证据生成的降级回答。";
    case "LLM_OUTPUT_INVALID":
      return "模型结果未通过格式校验，当前展示可复核的降级回答。";
    case "LLM_UNSTRUCTURED_FALLBACK":
      return "模型结果未满足结构要求，当前仅展示可用部分。";
    case "REWRITE_FAILED":
      return "问题改写未完成，当前基于原始问题返回可用结果。";
    case "DETERMINISTIC_EXACT_LOOKUP_PREFERRED":
      return "系统优先采用了可复核的确定性查询结果。";
    case "CITATION_QUALITY_INSUFFICIENT":
      return "引用质量未达到完整回答要求，请结合证据核对。";
    case null:
      return null;
    default:
      return "系统返回了降级结果，请结合引用证据核对。";
  }
}
