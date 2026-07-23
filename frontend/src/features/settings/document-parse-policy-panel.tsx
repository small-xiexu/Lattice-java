import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CheckCircle2, RotateCcw, Save } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";

import {
  documentParseSettingsApi,
  type DocumentParseConnection,
  type DocumentParsePolicy,
} from "../../api/contracts/document-parse-settings";
import { llmSettingsApi, type LlmModel } from "../../api/contracts/llm-settings";
import { queryKeys } from "../../api/query-keys";
import { PageState } from "../../components/page-state";
import {
  formatLlmDateTime,
  llmErrorMessage,
  useBeforeUnloadWarning,
} from "./llm-settings-utils";

interface PolicyForm {
  imageConnectionId: string;
  scannedPdfConnectionId: string;
  cleanupEnabled: boolean;
  cleanupModelProfileId: string;
  fallbackPolicyJson: string;
  operator: string;
}

interface DocumentParsePolicyPanelProps {
  onDirtyChange: (dirty: boolean) => void;
}

const EMPTY_POLICY_FORM: PolicyForm = {
  imageConnectionId: "",
  scannedPdfConnectionId: "",
  cleanupEnabled: false,
  cleanupModelProfileId: "",
  fallbackPolicyJson: "{}",
  operator: "admin",
};

export function DocumentParsePolicyPanel({ onDirtyChange }: DocumentParsePolicyPanelProps) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<PolicyForm>(EMPTY_POLICY_FORM);
  const [baseline, setBaseline] = useState<PolicyForm>(EMPTY_POLICY_FORM);
  const [loadedKey, setLoadedKey] = useState("initial");
  const [message, setMessage] = useState("");
  const [formError, setFormError] = useState("");

  const policyQuery = useQuery({
    queryKey: queryKeys.settings.documentParse.policy,
    queryFn: ({ signal }) => documentParseSettingsApi.getPolicy(signal),
  });
  const connectionsQuery = useQuery({
    queryKey: queryKeys.settings.documentParse.connections,
    queryFn: ({ signal }) => documentParseSettingsApi.listConnections(signal),
  });
  const modelsQuery = useQuery({
    queryKey: queryKeys.settings.llm.models,
    queryFn: ({ signal }) => llmSettingsApi.listModels(signal),
  });
  const policy = policyQuery.data;
  const connections = connectionsQuery.data?.items ?? [];
  const chatModels = (modelsQuery.data?.items ?? []).filter((model) => model.modelKind === "CHAT");
  const identity = policy ? policyIdentity(policy) : "missing";
  const dirty = JSON.stringify(form) !== JSON.stringify(baseline);
  useBeforeUnloadWarning(dirty);

  useEffect(() => {
    onDirtyChange(dirty);
    return () => onDirtyChange(false);
  }, [dirty, onDirtyChange]);

  if (policy && loadedKey !== identity) {
    const next = policyToForm(policy);
    setLoadedKey(identity);
    setForm(next);
    setBaseline(next);
    setMessage("");
    setFormError("");
  }

  const saveMutation = useMutation({
    mutationFn: () => {
      const error = validatePolicyForm(form, connections, chatModels);
      if (error) throw new Error(error);
      return documentParseSettingsApi.updatePolicy({
        imageConnectionId: optionalId(form.imageConnectionId),
        scannedPdfConnectionId: optionalId(form.scannedPdfConnectionId),
        cleanupEnabled: form.cleanupEnabled,
        cleanupModelProfileId: form.cleanupEnabled ? optionalId(form.cleanupModelProfileId) : null,
        fallbackPolicyJson: normalizeJsonObject(form.fallbackPolicyJson),
        operator: form.operator.trim(),
      });
    },
    onSuccess: (saved) => {
      queryClient.setQueryData(queryKeys.settings.documentParse.policy, saved);
      const next = policyToForm(saved, form.operator);
      setLoadedKey(policyIdentity(saved));
      setForm(next);
      setBaseline(next);
      setFormError("");
      setMessage("默认解析策略已保存，将应用于后续新导入任务");
    },
    onError: (error) => {
      setMessage("");
      setFormError(llmErrorMessage(error));
    },
  });

  const loading = policyQuery.isLoading || connectionsQuery.isLoading || modelsQuery.isLoading;
  const queryError = policyQuery.error ?? connectionsQuery.error ?? modelsQuery.error;
  if (loading) return <PageState status="loading" title="正在读取默认解析策略" />;
  if (queryError || !policy) {
    return (
      <PageState
        actionLabel="重新加载"
        description={queryError ? llmErrorMessage(queryError) : "服务端未返回默认策略"}
        onAction={() => void Promise.all([policyQuery.refetch(), connectionsQuery.refetch(), modelsQuery.refetch()])}
        status="error"
        title="默认解析策略读取失败"
      />
    );
  }

  const enabledConnections = connections.filter((connection) => connection.enabled);
  const enabledChatModels = chatModels.filter((model) => model.enabled);

  return (
    <section aria-labelledby="parse-policy-title" className="vector-section parsing-policy-section">
      <header className="vector-section-header">
        <div>
          <h2 id="parse-policy-title">默认路由策略</h2>
          <p>作用域 {policy.policyScope} · 最近更新 {formatLlmDateTime(policy.updatedAt)}</p>
        </div>
        {dirty ? <span className="llm-dirty-mark">未保存</span> : null}
      </header>

      <div className="parsing-policy-status" aria-label="当前解析策略摘要">
        <RouteStatus label="图片 OCR" value={connectionLabel(connections, policy.imageConnectionId)} />
        <RouteStatus label="扫描 PDF OCR" value={connectionLabel(connections, policy.scannedPdfConnectionId)} />
        <RouteStatus label="识别后整理" value={policy.cleanupEnabled ? modelLabel(chatModels, policy.cleanupModelProfileId) : "未启用"} />
      </div>

      <form className="llm-settings-form parsing-policy-form" noValidate onSubmit={(event) => submit(event, saveMutation.mutate)}>
        <label className="form-field">
          <span>图片 OCR 连接</span>
          <select onChange={(event) => setForm({ ...form, imageConnectionId: event.target.value })} value={form.imageConnectionId}>
            <option value="">不设置</option>
            {connectionOptions(connections, enabledConnections, policy.imageConnectionId).map((connection) => (
              <option disabled={!connection.enabled} key={connection.id} value={connection.id}>{connection.connectionCode}{connection.enabled ? "" : "（已停用）"}</option>
            ))}
          </select>
        </label>
        <label className="form-field">
          <span>扫描 PDF OCR 连接</span>
          <select onChange={(event) => setForm({ ...form, scannedPdfConnectionId: event.target.value })} value={form.scannedPdfConnectionId}>
            <option value="">不设置</option>
            {connectionOptions(connections, enabledConnections, policy.scannedPdfConnectionId).map((connection) => (
              <option disabled={!connection.enabled} key={connection.id} value={connection.id}>{connection.connectionCode}{connection.enabled ? "" : "（已停用）"}</option>
            ))}
          </select>
        </label>
        <label className="checkbox-field parsing-cleanup-toggle">
          <input checked={form.cleanupEnabled} onChange={(event) => setForm({ ...form, cleanupEnabled: event.target.checked })} type="checkbox" />
          启用识别后整理
        </label>
        <label className="form-field">
          <span>后整理对话模型</span>
          <select disabled={!form.cleanupEnabled} onChange={(event) => setForm({ ...form, cleanupModelProfileId: event.target.value })} value={form.cleanupModelProfileId}>
            <option value="">请选择</option>
            {modelOptions(chatModels, enabledChatModels, policy.cleanupModelProfileId).map((model) => (
              <option disabled={!model.enabled} key={model.id} value={model.id}>{model.modelCode} · {model.modelName}{model.enabled ? "" : "（已停用）"}</option>
            ))}
          </select>
        </label>
        <label className="form-field field-span-2">
          <span>降级策略 JSON</span>
          <textarea aria-label="降级策略 JSON" className="llm-code-input" onChange={(event) => setForm({ ...form, fallbackPolicyJson: event.target.value })} rows={7} spellCheck={false} value={form.fallbackPolicyJson} />
          <small>必须是 JSON 对象；为空时保存为 {"{}"}</small>
        </label>
        <label className="form-field"><span>操作人</span><input autoComplete="off" onChange={(event) => setForm({ ...form, operator: event.target.value })} required value={form.operator} /></label>

        {connections.length === 0 ? <p className="llm-inline-result is-error" role="status">当前没有解析连接；可先保存空路由，实际启用 OCR 前需创建并启用连接。</p> : null}
        {message ? <p className="llm-inline-result is-success" role="status"><CheckCircle2 aria-hidden="true" size={17} />{message}</p> : null}
        {formError ? <p className="llm-inline-result is-error" role="alert">{formError}</p> : null}
        <div className="llm-settings-form-actions">
          <button className="primary-button" disabled={!dirty || saveMutation.isPending} type="submit"><Save aria-hidden="true" size={16} />{saveMutation.isPending ? "正在保存" : "保存策略"}</button>
          <button className="secondary-button compact-button" disabled={!dirty || saveMutation.isPending} onClick={() => setForm(baseline)} type="button"><RotateCcw aria-hidden="true" size={16} />撤销改动</button>
        </div>
      </form>
    </section>
  );
}

function RouteStatus({ label, value }: { label: string; value: string }) {
  return <div><span>{label}</span><strong>{value}</strong></div>;
}

function policyToForm(policy: DocumentParsePolicy, operator?: string): PolicyForm {
  return {
    imageConnectionId: policy.imageConnectionId === null ? "" : String(policy.imageConnectionId),
    scannedPdfConnectionId: policy.scannedPdfConnectionId === null ? "" : String(policy.scannedPdfConnectionId),
    cleanupEnabled: policy.cleanupEnabled,
    cleanupModelProfileId: policy.cleanupModelProfileId === null ? "" : String(policy.cleanupModelProfileId),
    fallbackPolicyJson: prettyJson(policy.fallbackPolicyJson),
    operator: operator?.trim() || policy.updatedBy || "admin",
  };
}

function validatePolicyForm(form: PolicyForm, connections: DocumentParseConnection[], models: LlmModel[]) {
  if (!form.operator.trim()) return "请填写操作人";
  for (const [label, value] of [["图片 OCR", form.imageConnectionId], ["扫描 PDF OCR", form.scannedPdfConnectionId]] as const) {
    if (!value) continue;
    const connection = connections.find((candidate) => candidate.id === Number(value));
    if (!connection) return `${label}连接不存在`;
    if (!connection.enabled) return `${label}连接已停用，不能保存为默认路由`;
  }
  if (form.cleanupEnabled) {
    if (!form.cleanupModelProfileId) return "启用识别后整理时必须选择对话模型";
    const model = models.find((candidate) => candidate.id === Number(form.cleanupModelProfileId));
    if (!model || model.modelKind !== "CHAT") return "后整理模型必须是对话模型";
    if (!model.enabled) return "后整理模型已停用，不能用于新任务";
  }
  if (!isJsonObject(form.fallbackPolicyJson)) return "降级策略 JSON 必须是合法的 JSON 对象";
  return "";
}

function connectionOptions(all: DocumentParseConnection[], enabled: DocumentParseConnection[], currentId: number | null) {
  const options = [...enabled];
  const current = all.find((connection) => connection.id === currentId);
  if (current && !options.some((connection) => connection.id === current.id)) options.push(current);
  return options;
}

function modelOptions(all: LlmModel[], enabled: LlmModel[], currentId: number | null) {
  const options = [...enabled];
  const current = all.find((model) => model.id === currentId);
  if (current && !options.some((model) => model.id === current.id)) options.push(current);
  return options;
}

function connectionLabel(connections: DocumentParseConnection[], id: number | null) {
  if (id === null) return "未设置";
  const connection = connections.find((candidate) => candidate.id === id);
  return connection ? `${connection.connectionCode}${connection.enabled ? "" : "（已停用）"}` : `连接 #${id}`;
}

function modelLabel(models: LlmModel[], id: number | null) {
  if (id === null) return "未选择模型";
  const model = models.find((candidate) => candidate.id === id);
  return model ? `${model.modelCode} · ${model.modelName}` : `模型 #${id}`;
}

function optionalId(value: string) {
  return value.trim() ? Number(value) : null;
}

function normalizeJsonObject(value: string) {
  const normalized = value.trim() || "{}";
  return JSON.stringify(JSON.parse(normalized));
}

function prettyJson(value: string) {
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value || "{}";
  }
}

function isJsonObject(value: string) {
  try {
    const parsed: unknown = JSON.parse(value.trim() || "{}");
    return parsed !== null && typeof parsed === "object" && !Array.isArray(parsed);
  } catch {
    return false;
  }
}

function policyIdentity(policy: DocumentParsePolicy) {
  return JSON.stringify(policy);
}

function submit(event: FormEvent, save: () => void) {
  event.preventDefault();
  save();
}
