import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CheckCircle2, FlaskConical, Plus, RotateCcw, Save, Trash2 } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";
import { useSearchParams } from "react-router-dom";

import {
  llmSettingsApi,
  type LlmModel,
  type LlmModelKind,
} from "../../api/contracts/llm-settings";
import { queryKeys } from "../../api/query-keys";
import { PageState } from "../../components/page-state";
import { ArticleGovernanceDialog } from "../library/article-governance-dialog";
import {
  confirmDiscardChanges,
  connectionName,
  formatLlmDateTime,
  llmErrorMessage,
  optionalNumber,
  useBeforeUnloadWarning,
} from "./llm-settings-utils";

interface ModelForm {
  modelCode: string;
  connectionId: string;
  modelName: string;
  modelKind: LlmModelKind;
  expectedDimensions: string;
  supportsDimensionOverride: boolean;
  temperature: string;
  maxTokens: string;
  timeoutSeconds: string;
  inputPricePer1kTokens: string;
  outputPricePer1kTokens: string;
  extraOptionsJson: string;
  enabled: boolean;
  remarks: string;
  operator: string;
}

const EMPTY_MODEL: ModelForm = {
  modelCode: "",
  connectionId: "",
  modelName: "",
  modelKind: "CHAT",
  expectedDimensions: "",
  supportsDimensionOverride: false,
  temperature: "",
  maxTokens: "",
  timeoutSeconds: "",
  inputPricePer1kTokens: "",
  outputPricePer1kTokens: "",
  extraOptionsJson: "{}",
  enabled: true,
  remarks: "",
  operator: "admin",
};

interface LlmModelsPanelProps {
  onDirtyChange: (dirty: boolean) => void;
}

export function LlmModelsPanel({ onDirtyChange }: LlmModelsPanelProps) {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState<ModelForm>(EMPTY_MODEL);
  const [baseline, setBaseline] = useState<ModelForm>(EMPTY_MODEL);
  const [message, setMessage] = useState("");
  const [formError, setFormError] = useState("");
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [loadedKey, setLoadedKey] = useState("initial");

  const connectionsQuery = useQuery({
    queryKey: queryKeys.settings.llm.connections,
    queryFn: ({ signal }) => llmSettingsApi.listConnections(signal),
  });
  const modelsQuery = useQuery({
    queryKey: queryKeys.settings.llm.models,
    queryFn: ({ signal }) => llmSettingsApi.listModels(signal),
  });
  const bindingsQuery = useQuery({
    queryKey: queryKeys.settings.llm.bindings,
    queryFn: ({ signal }) => llmSettingsApi.listBindings(signal),
  });
  const connections = connectionsQuery.data?.items ?? [];
  const models = modelsQuery.data?.items ?? [];
  const requestedId = Number(searchParams.get("id"));
  const selected = creating ? undefined : models.find((model) => model.id === requestedId) ?? models[0];
  const dirty = JSON.stringify(form) !== JSON.stringify(baseline);
  const editorKey = creating ? "new" : selected ? `model-${selected.id}` : "empty";
  const selectedBindingCount = selected
    ? (bindingsQuery.data?.items ?? []).filter((binding) => binding.primaryModelProfileId === selected.id || binding.fallbackModelProfileId === selected.id).length
    : 0;
  useBeforeUnloadWarning(dirty);

  useEffect(() => {
    onDirtyChange(dirty);
    return () => onDirtyChange(false);
  }, [dirty, onDirtyChange]);

  const saveMutation = useMutation({
    mutationFn: () => {
      const error = validateModelForm(form);
      if (error) throw new Error(error);
      const request = modelRequest(form);
      return selected && !creating
        ? llmSettingsApi.updateModel(selected.id, request)
        : llmSettingsApi.createModel(request);
    },
    onSuccess: (saved) => {
      const next = modelToForm(saved);
      setForm(next);
      setBaseline(next);
      setCreating(false);
      setFormError("");
      setMessage(`模型 ${saved.modelCode} 已保存`);
      setSelectedId(setSearchParams, searchParams, saved.id);
      void queryClient.invalidateQueries({ queryKey: queryKeys.settings.llm.root });
    },
    onError: (error) => setFormError(llmErrorMessage(error)),
  });

  const testMutation = useMutation({
    mutationFn: () => {
      const error = validateModelTest(form);
      if (error) throw new Error(error);
      return llmSettingsApi.testModel({
        modelId: selected?.id ?? null,
        connectionId: Number(form.connectionId),
        modelName: form.modelName.trim(),
        modelKind: form.modelKind,
        expectedDimensions: form.modelKind === "EMBEDDING" ? optionalNumber(form.expectedDimensions) : null,
        timeoutSeconds: optionalNumber(form.timeoutSeconds),
      });
    },
    onSuccess: (result) => {
      setFormError("");
      setMessage(result.message);
    },
    onError: (error) => {
      setMessage("");
      setFormError(llmErrorMessage(error));
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => {
      if (!selected) throw new Error("未选择模型");
      return llmSettingsApi.deleteModel(selected.id);
    },
    onSuccess: () => {
      setDeleteOpen(false);
      setSelectedId(setSearchParams, searchParams, null);
      setMessage("模型已删除");
      void queryClient.invalidateQueries({ queryKey: queryKeys.settings.llm.root });
    },
  });

  if (loadedKey !== editorKey) {
    const next = selected ? modelToForm(selected) : { ...EMPTY_MODEL, connectionId: connections[0] ? String(connections[0].id) : "" };
    setLoadedKey(editorKey);
    setForm(next);
    setBaseline(next);
    setFormError("");
    setMessage("");
  }

  const choose = (model: LlmModel) => {
    if (!confirmDiscardChanges(dirty)) return;
    setCreating(false);
    setSelectedId(setSearchParams, searchParams, model.id);
  };

  const create = () => {
    if (!confirmDiscardChanges(dirty)) return;
    setCreating(true);
    setSelectedId(setSearchParams, searchParams, null);
  };

  if (connectionsQuery.isLoading || modelsQuery.isLoading) return <PageState status="loading" title="正在读取模型配置" />;
  if (connectionsQuery.isError || modelsQuery.isError) {
    const error = connectionsQuery.error ?? modelsQuery.error;
    return <PageState actionLabel="重新加载" description={llmErrorMessage(error)} onAction={() => void Promise.all([connectionsQuery.refetch(), modelsQuery.refetch()])} status="error" title="模型配置读取失败" />;
  }

  return (
    <div className="llm-settings-layout">
      <section className="llm-settings-list" aria-label="模型档案列表">
        <header className="llm-settings-list-header"><div><strong>模型档案</strong><span>{models.length} 项</span></div><button className="secondary-button compact-button" disabled={connections.length === 0} onClick={create} type="button"><Plus aria-hidden="true" size={16} />新增</button></header>
        {models.length === 0 && !creating ? <div className="llm-settings-empty">{connections.length === 0 ? "请先创建模型连接" : "暂无模型档案"}</div> : (
          <ul className="llm-settings-records">
            {models.map((model) => <li key={model.id}><button className={!creating && selected?.id === model.id ? "is-selected" : ""} onClick={() => choose(model)} type="button"><span className="llm-record-title"><strong>{model.modelCode}</strong><em className={model.enabled ? "is-enabled" : "is-disabled"}>{model.enabled ? "启用" : "停用"}</em></span><code>{model.modelName} · {model.modelKind}</code><small>{connectionName(connections, model.connectionId)}</small></button></li>)}
          </ul>
        )}
      </section>

      <section className="llm-settings-editor" aria-label={creating ? "新增模型档案" : "编辑模型档案"}>
        <header className="llm-settings-editor-header"><div><h2>{creating ? "新增模型档案" : selected ? `编辑 ${selected.modelCode}` : "模型档案"}</h2><p>{selected ? `最近更新 ${formatLlmDateTime(selected.updatedAt)} · ${selected.updatedBy ?? "未知操作人"}` : "模型测试会执行一次最小调用"}</p></div>{dirty ? <span className="llm-dirty-mark">未保存</span> : null}</header>
        {!creating && !selected ? <div className="llm-settings-empty">选择或新增一个模型档案</div> : (
          <form className="llm-settings-form" noValidate onSubmit={(event) => submit(event, saveMutation.mutate)}>
            <label className="form-field"><span>模型编码</span><input autoComplete="off" onChange={(event) => setForm({ ...form, modelCode: event.target.value })} placeholder="留空自动生成" value={form.modelCode} /></label>
            <label className="form-field"><span>所属连接</span><select onChange={(event) => setForm({ ...form, connectionId: event.target.value })} required value={form.connectionId}><option value="">请选择</option>{connections.map((connection) => <option disabled={!connection.enabled} key={connection.id} value={connection.id}>{connection.connectionCode}{connection.enabled ? "" : "（已停用）"}</option>)}</select></label>
            <label className="form-field"><span>Provider 模型名</span><input autoComplete="off" onChange={(event) => setForm({ ...form, modelName: event.target.value })} required value={form.modelName} /></label>
            <fieldset className="segmented-field"><legend>模型类型</legend><div className="segmented-control"><button aria-pressed={form.modelKind === "CHAT"} onClick={() => setForm({ ...form, modelKind: "CHAT", expectedDimensions: "", supportsDimensionOverride: false })} type="button">CHAT</button><button aria-pressed={form.modelKind === "EMBEDDING"} onClick={() => setForm({ ...form, modelKind: "EMBEDDING" })} type="button">EMBEDDING</button></div></fieldset>
            {form.modelKind === "EMBEDDING" ? <label className="form-field"><span>期望向量维度</span><input min="1" onChange={(event) => setForm({ ...form, expectedDimensions: event.target.value })} required step="1" type="number" value={form.expectedDimensions} /></label> : <label className="form-field"><span>温度</span><input max="2" min="0" onChange={(event) => setForm({ ...form, temperature: event.target.value })} step="0.01" type="number" value={form.temperature} /></label>}
            {form.modelKind === "EMBEDDING" ? <label className="checkbox-field"><input checked={form.supportsDimensionOverride} onChange={(event) => setForm({ ...form, supportsDimensionOverride: event.target.checked })} type="checkbox" />支持维度覆写</label> : <label className="form-field"><span>最大输出 Token</span><input min="1" onChange={(event) => setForm({ ...form, maxTokens: event.target.value })} step="1" type="number" value={form.maxTokens} /></label>}
            <label className="form-field"><span>超时秒数</span><input min="1" onChange={(event) => setForm({ ...form, timeoutSeconds: event.target.value })} step="1" type="number" value={form.timeoutSeconds} /></label>
            <label className="form-field"><span>输入价格 / 1K Token</span><input min="0" onChange={(event) => setForm({ ...form, inputPricePer1kTokens: event.target.value })} step="0.000001" type="number" value={form.inputPricePer1kTokens} /></label>
            <label className="form-field"><span>输出价格 / 1K Token</span><input min="0" onChange={(event) => setForm({ ...form, outputPricePer1kTokens: event.target.value })} step="0.000001" type="number" value={form.outputPricePer1kTokens} /></label>
            <label className="form-field field-span-2"><span>Provider 扩展配置 JSON</span><textarea className="llm-code-input" onChange={(event) => setForm({ ...form, extraOptionsJson: event.target.value })} rows={4} spellCheck={false} value={form.extraOptionsJson} /></label>
            <label className="form-field field-span-2"><span>备注</span><textarea onChange={(event) => setForm({ ...form, remarks: event.target.value })} rows={2} value={form.remarks} /></label>
            <label className="form-field"><span>操作人</span><input autoComplete="off" onChange={(event) => setForm({ ...form, operator: event.target.value })} required value={form.operator} /></label>
            <label className="checkbox-field"><input checked={form.enabled} onChange={(event) => setForm({ ...form, enabled: event.target.checked })} type="checkbox" />启用此模型</label>

            {message ? <p className="llm-inline-result is-success" role="status"><CheckCircle2 aria-hidden="true" size={17} />{message}</p> : null}
            {formError ? <p className="llm-inline-result is-error" role="alert">{formError}</p> : null}
            <div className="llm-settings-form-actions">
              <button className="primary-button" disabled={saveMutation.isPending || testMutation.isPending} type="submit"><Save aria-hidden="true" size={16} />{saveMutation.isPending ? "正在保存" : "保存模型"}</button>
              <button className="secondary-button compact-button" disabled={saveMutation.isPending || testMutation.isPending} onClick={() => testMutation.mutate()} type="button"><FlaskConical aria-hidden="true" size={16} />{testMutation.isPending ? "正在测试" : "测试模型"}</button>
              <button className="secondary-button compact-button" disabled={!dirty || saveMutation.isPending} onClick={() => setForm(baseline)} type="button"><RotateCcw aria-hidden="true" size={16} />撤销改动</button>
              {selected && !creating ? <button aria-label="删除模型档案" className="icon-button llm-delete-button" onClick={() => setDeleteOpen(true)} title="删除模型档案" type="button"><Trash2 aria-hidden="true" size={17} /></button> : null}
            </div>
          </form>
        )}
      </section>

      {deleteOpen && selected ? <ArticleGovernanceDialog confirmLabel="确认删除模型" description="删除后无法恢复；仍被场景绑定引用时服务端将拒绝操作。" destructive error={deleteMutation.isError ? llmErrorMessage(deleteMutation.error) : undefined} onClose={() => setDeleteOpen(false)} onConfirm={() => deleteMutation.mutate()} pending={deleteMutation.isPending} title="删除模型档案"><dl className="governance-impact-summary"><div><dt>目标模型</dt><dd>{selected.modelCode} / #{selected.id}</dd></div><div><dt>绑定引用</dt><dd>{selectedBindingCount} 个场景绑定引用主模型或降级模型</dd></div></dl></ArticleGovernanceDialog> : null}
    </div>
  );
}

function modelToForm(model: LlmModel): ModelForm {
  return {
    modelCode: model.modelCode,
    connectionId: String(model.connectionId),
    modelName: model.modelName,
    modelKind: model.modelKind,
    expectedDimensions: model.expectedDimensions === null ? "" : String(model.expectedDimensions),
    supportsDimensionOverride: model.supportsDimensionOverride,
    temperature: model.temperature === null ? "" : String(model.temperature),
    maxTokens: model.maxTokens === null ? "" : String(model.maxTokens),
    timeoutSeconds: model.timeoutSeconds === null ? "" : String(model.timeoutSeconds),
    inputPricePer1kTokens: model.inputPricePer1kTokens === null ? "" : String(model.inputPricePer1kTokens),
    outputPricePer1kTokens: model.outputPricePer1kTokens === null ? "" : String(model.outputPricePer1kTokens),
    extraOptionsJson: model.extraOptionsJson || "{}",
    enabled: model.enabled,
    remarks: model.remarks ?? "",
    operator: model.updatedBy ?? "admin",
  };
}

function modelRequest(form: ModelForm) {
  return {
    modelCode: form.modelCode.trim(),
    connectionId: Number(form.connectionId),
    modelName: form.modelName.trim(),
    modelKind: form.modelKind,
    expectedDimensions: form.modelKind === "EMBEDDING" ? optionalNumber(form.expectedDimensions) : null,
    supportsDimensionOverride: form.modelKind === "EMBEDDING" && form.supportsDimensionOverride,
    temperature: form.modelKind === "CHAT" ? optionalNumber(form.temperature) : null,
    maxTokens: form.modelKind === "CHAT" ? optionalNumber(form.maxTokens) : null,
    timeoutSeconds: optionalNumber(form.timeoutSeconds),
    inputPricePer1kTokens: optionalNumber(form.inputPricePer1kTokens),
    outputPricePer1kTokens: optionalNumber(form.outputPricePer1kTokens),
    extraOptionsJson: form.extraOptionsJson.trim() || "{}",
    enabled: form.enabled,
    remarks: form.remarks.trim() || null,
    operator: form.operator.trim() || "admin",
  };
}

function validateModelForm(form: ModelForm) {
  if (!form.connectionId) return "请选择所属连接";
  if (!form.modelName.trim()) return "请填写 Provider 模型名";
  if (form.modelKind === "EMBEDDING" && !positiveInteger(form.expectedDimensions)) return "Embedding 模型必须填写正整数向量维度";
  if (form.timeoutSeconds && !positiveInteger(form.timeoutSeconds)) return "超时秒数必须为正整数";
  if (form.maxTokens && !positiveInteger(form.maxTokens)) return "最大输出 Token 必须为正整数";
  if (form.temperature && (Number(form.temperature) < 0 || Number(form.temperature) > 2)) return "温度必须在 0 到 2 之间";
  if ([form.inputPricePer1kTokens, form.outputPricePer1kTokens].some((value) => value && Number(value) < 0)) return "价格不能小于 0";
  try {
    const parsed = JSON.parse(form.extraOptionsJson || "{}");
    if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) return "Provider 扩展配置必须是 JSON 对象";
  } catch {
    return "Provider 扩展配置不是有效 JSON";
  }
  if (!form.operator.trim()) return "请填写操作人";
  return "";
}

function validateModelTest(form: ModelForm) {
  if (!form.connectionId) return "测试前请选择所属连接";
  if (!form.modelName.trim()) return "测试前请填写 Provider 模型名";
  if (form.modelKind === "EMBEDDING" && !positiveInteger(form.expectedDimensions)) return "测试向量模型前请填写正整数维度";
  if (form.timeoutSeconds && !positiveInteger(form.timeoutSeconds)) return "超时秒数必须为正整数";
  return "";
}

function positiveInteger(value: string) {
  return Number.isInteger(Number(value)) && Number(value) > 0;
}

function submit(event: FormEvent, save: () => void) {
  event.preventDefault();
  save();
}

function setSelectedId(update: ReturnType<typeof useSearchParams>[1], current: URLSearchParams, id: number | null) {
  const next = new URLSearchParams(current);
  if (id === null) next.delete("id");
  else next.set("id", String(id));
  update(next, { replace: true });
}
