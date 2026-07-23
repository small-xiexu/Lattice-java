import { isApiError } from "../../api/api-error";
import { useEffect } from "react";
import type {
  LlmBinding,
  LlmConnection,
  LlmModel,
  LlmScene,
} from "../../api/contracts/llm-settings";

export const LLM_SCENE_ROLES: Record<LlmScene, readonly string[]> = {
  compile: ["writer", "reviewer", "fixer", "field-alias-enricher"],
  query: ["answer", "reviewer", "rewrite"],
  deep_research: ["planner", "researcher", "synthesizer", "reviewer"],
};

export const LLM_SCENE_LABELS: Record<LlmScene, string> = {
  compile: "知识编译",
  query: "问答",
  deep_research: "深度研究",
};

export function formatLlmDateTime(value: string | null) {
  if (!value) return "--";
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat("zh-CN", {
      dateStyle: "medium",
      timeStyle: "short",
      hour12: false,
    }).format(date);
}

export function llmErrorMessage(error: unknown) {
  return isApiError(error) || error instanceof Error
    ? error.message
    : "操作失败，请稍后重试";
}

export function optionalNumber(value: string) {
  return value.trim() === "" ? null : Number(value);
}

export function connectionName(connections: LlmConnection[], id: number) {
  return connections.find((connection) => connection.id === id)?.connectionCode ?? `连接 #${id}`;
}

export function modelName(models: LlmModel[], id: number | null) {
  if (id === null) return "未配置";
  const model = models.find((candidate) => candidate.id === id);
  return model ? `${model.modelCode} · ${model.modelKind}` : `模型 #${id}`;
}

export function bindingIdentity(binding: LlmBinding) {
  return `${LLM_SCENE_LABELS[binding.scene]} / ${binding.agentRole}`;
}

export function confirmDiscardChanges(dirty: boolean) {
  return !dirty || window.confirm("当前表单有未保存改动，确定放弃并继续吗？");
}

export function useBeforeUnloadWarning(dirty: boolean) {
  useEffect(() => {
    const listener = (event: BeforeUnloadEvent) => {
      if (dirty) event.preventDefault();
    };
    window.addEventListener("beforeunload", listener);
    return () => window.removeEventListener("beforeunload", listener);
  }, [dirty]);
}
