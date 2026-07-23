import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CheckCircle2, Plus, RotateCcw, Save, Trash2 } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";
import { useSearchParams } from "react-router-dom";

import {
  llmSettingsApi,
  type LlmBinding,
  type LlmScene,
} from "../../api/contracts/llm-settings";
import { queryKeys } from "../../api/query-keys";
import { PageState } from "../../components/page-state";
import { ArticleGovernanceDialog } from "../library/article-governance-dialog";
import {
  bindingIdentity,
  confirmDiscardChanges,
  formatLlmDateTime,
  llmErrorMessage,
  LLM_SCENE_LABELS,
  LLM_SCENE_ROLES,
  modelName,
  useBeforeUnloadWarning,
} from "./llm-settings-utils";

interface BindingForm {
  scene: LlmScene;
  agentRole: string;
  primaryModelProfileId: string;
  fallbackModelProfileId: string;
  routeLabel: string;
  enabled: boolean;
  remarks: string;
  operator: string;
}

const EMPTY_BINDING: BindingForm = {
  scene: "compile",
  agentRole: "writer",
  primaryModelProfileId: "",
  fallbackModelProfileId: "",
  routeLabel: "",
  enabled: true,
  remarks: "",
  operator: "admin",
};

interface LlmBindingsPanelProps {
  onDirtyChange: (dirty: boolean) => void;
}

export function LlmBindingsPanel({ onDirtyChange }: LlmBindingsPanelProps) {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState<BindingForm>(EMPTY_BINDING);
  const [baseline, setBaseline] = useState<BindingForm>(EMPTY_BINDING);
  const [message, setMessage] = useState("");
  const [formError, setFormError] = useState("");
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [loadedKey, setLoadedKey] = useState("initial");

  const modelsQuery = useQuery({
    queryKey: queryKeys.settings.llm.models,
    queryFn: ({ signal }) => llmSettingsApi.listModels(signal),
  });
  const bindingsQuery = useQuery({
    queryKey: queryKeys.settings.llm.bindings,
    queryFn: ({ signal }) => llmSettingsApi.listBindings(signal),
  });
  const models = modelsQuery.data?.items ?? [];
  const chatModels = models.filter((model) => model.modelKind === "CHAT");
  const bindings = bindingsQuery.data?.items ?? [];
  const requestedId = Number(searchParams.get("id"));
  const selected = creating ? undefined : bindings.find((binding) => binding.id === requestedId) ?? bindings[0];
  const dirty = JSON.stringify(form) !== JSON.stringify(baseline);
  const editorKey = creating ? "new" : selected ? `binding-${selected.id}` : "empty";
  useBeforeUnloadWarning(dirty);

  useEffect(() => {
    onDirtyChange(dirty);
    return () => onDirtyChange(false);
  }, [dirty, onDirtyChange]);

  const saveMutation = useMutation({
    mutationFn: () => {
      const error = validateBindingForm(form, bindings, selected?.id);
      if (error) throw new Error(error);
      const request = {
        scene: form.scene,
        agentRole: form.agentRole,
        primaryModelProfileId: Number(form.primaryModelProfileId),
        fallbackModelProfileId: form.fallbackModelProfileId ? Number(form.fallbackModelProfileId) : null,
        routeLabel: form.routeLabel.trim(),
        enabled: form.enabled,
        remarks: form.remarks.trim() || null,
        operator: form.operator.trim() || "admin",
      };
      return selected && !creating
        ? llmSettingsApi.updateBinding(selected.id, request)
        : llmSettingsApi.createBinding(request);
    },
    onSuccess: (saved) => {
      const next = bindingToForm(saved);
      setForm(next);
      setBaseline(next);
      setCreating(false);
      setFormError("");
      setMessage(`绑定 ${bindingIdentity(saved)} 已保存`);
      setSelectedId(setSearchParams, searchParams, saved.id);
      void queryClient.invalidateQueries({ queryKey: queryKeys.settings.llm.root });
    },
    onError: (error) => setFormError(llmErrorMessage(error)),
  });

  const deleteMutation = useMutation({
    mutationFn: () => {
      if (!selected) throw new Error("未选择绑定");
      return llmSettingsApi.deleteBinding(selected.id);
    },
    onSuccess: () => {
      setDeleteOpen(false);
      setSelectedId(setSearchParams, searchParams, null);
      setMessage("场景绑定已删除");
      void queryClient.invalidateQueries({ queryKey: queryKeys.settings.llm.root });
    },
  });

  if (loadedKey !== editorKey) {
    const next = selected ? bindingToForm(selected) : { ...EMPTY_BINDING, primaryModelProfileId: chatModels[0] ? String(chatModels[0].id) : "" };
    setLoadedKey(editorKey);
    setForm(next);
    setBaseline(next);
    setFormError("");
    setMessage("");
  }

  const choose = (binding: LlmBinding) => {
    if (!confirmDiscardChanges(dirty)) return;
    setCreating(false);
    setSelectedId(setSearchParams, searchParams, binding.id);
  };

  const create = () => {
    if (!confirmDiscardChanges(dirty)) return;
    setCreating(true);
    setSelectedId(setSearchParams, searchParams, null);
  };

  if (modelsQuery.isLoading || bindingsQuery.isLoading) return <PageState status="loading" title="正在读取场景绑定" />;
  if (modelsQuery.isError || bindingsQuery.isError) {
    const error = modelsQuery.error ?? bindingsQuery.error;
    return <PageState actionLabel="重新加载" description={llmErrorMessage(error)} onAction={() => void Promise.all([modelsQuery.refetch(), bindingsQuery.refetch()])} status="error" title="场景绑定读取失败" />;
  }

  return (
    <div className="llm-settings-layout">
      <section className="llm-settings-list" aria-label="场景绑定列表">
        <header className="llm-settings-list-header"><div><strong>场景绑定</strong><span>{bindings.length} 项</span></div><button className="secondary-button compact-button" disabled={chatModels.length === 0} onClick={create} type="button"><Plus aria-hidden="true" size={16} />新增</button></header>
        {bindings.length === 0 && !creating ? <div className="llm-settings-empty">{chatModels.length === 0 ? "请先创建 CHAT 模型" : "暂无场景绑定"}</div> : (
          <ul className="llm-settings-records">
            {bindings.map((binding) => <li key={binding.id}><button className={!creating && selected?.id === binding.id ? "is-selected" : ""} onClick={() => choose(binding)} type="button"><span className="llm-record-title"><strong>{LLM_SCENE_LABELS[binding.scene]} / {binding.agentRole}</strong><em className={binding.enabled ? "is-enabled" : "is-disabled"}>{binding.enabled ? "启用" : "停用"}</em></span><code>{binding.routeLabel}</code><small>{modelName(models, binding.primaryModelProfileId)}</small></button></li>)}
          </ul>
        )}
      </section>

      <section className="llm-settings-editor" aria-label={creating ? "新增场景绑定" : "编辑场景绑定"}>
        <header className="llm-settings-editor-header"><div><h2>{creating ? "新增场景绑定" : selected ? `编辑 ${bindingIdentity(selected)}` : "场景绑定"}</h2><p>{selected ? `最近更新 ${formatLlmDateTime(selected.updatedAt)} · ${selected.updatedBy ?? "未知操作人"}` : "同一场景和角色只能保存一条绑定"}</p></div>{dirty ? <span className="llm-dirty-mark">未保存</span> : null}</header>
        {!creating && !selected ? <div className="llm-settings-empty">选择或新增一个场景绑定</div> : (
          <form className="llm-settings-form" noValidate onSubmit={(event) => submit(event, saveMutation.mutate)}>
            <label className="form-field"><span>运行场景</span><select onChange={(event) => changeScene(event.target.value as LlmScene, form, setForm)} value={form.scene}>{Object.entries(LLM_SCENE_LABELS).map(([scene, label]) => <option key={scene} value={scene}>{label}</option>)}</select></label>
            <label className="form-field"><span>Agent 角色</span><select onChange={(event) => setForm({ ...form, agentRole: event.target.value })} value={form.agentRole}>{LLM_SCENE_ROLES[form.scene].map((role) => <option key={role} value={role}>{role}</option>)}</select></label>
            <label className="form-field"><span>主模型</span><select onChange={(event) => setForm({ ...form, primaryModelProfileId: event.target.value })} required value={form.primaryModelProfileId}><option value="">请选择</option>{chatModels.map((model) => <option disabled={!model.enabled} key={model.id} value={model.id}>{model.modelCode}{model.enabled ? "" : "（已停用）"}</option>)}</select></label>
            <label className="form-field"><span>降级模型</span><select onChange={(event) => setForm({ ...form, fallbackModelProfileId: event.target.value })} value={form.fallbackModelProfileId}><option value="">不配置</option>{chatModels.map((model) => <option disabled={!model.enabled || String(model.id) === form.primaryModelProfileId} key={model.id} value={model.id}>{model.modelCode}{model.enabled ? "" : "（已停用）"}</option>)}</select></label>
            <label className="form-field field-span-2"><span>路由标签</span><input autoComplete="off" onChange={(event) => setForm({ ...form, routeLabel: event.target.value })} placeholder="留空自动生成" value={form.routeLabel} /></label>
            <label className="form-field field-span-2"><span>备注</span><textarea onChange={(event) => setForm({ ...form, remarks: event.target.value })} rows={3} value={form.remarks} /></label>
            <label className="form-field"><span>操作人</span><input autoComplete="off" onChange={(event) => setForm({ ...form, operator: event.target.value })} required value={form.operator} /></label>
            <label className="checkbox-field"><input checked={form.enabled} onChange={(event) => setForm({ ...form, enabled: event.target.checked })} type="checkbox" />启用此绑定</label>

            {message ? <p className="llm-inline-result is-success" role="status"><CheckCircle2 aria-hidden="true" size={17} />{message}</p> : null}
            {formError ? <p className="llm-inline-result is-error" role="alert">{formError}</p> : null}
            <div className="llm-settings-form-actions">
              <button className="primary-button" disabled={saveMutation.isPending} type="submit"><Save aria-hidden="true" size={16} />{saveMutation.isPending ? "正在保存" : "保存绑定"}</button>
              <button className="secondary-button compact-button" disabled={!dirty || saveMutation.isPending} onClick={() => setForm(baseline)} type="button"><RotateCcw aria-hidden="true" size={16} />撤销改动</button>
              {selected && !creating ? <button aria-label="删除场景绑定" className="icon-button llm-delete-button" onClick={() => setDeleteOpen(true)} title="删除场景绑定" type="button"><Trash2 aria-hidden="true" size={17} /></button> : null}
            </div>
          </form>
        )}
      </section>

      {deleteOpen && selected ? <ArticleGovernanceDialog confirmLabel="确认删除绑定" description="删除后该场景角色将失去显式模型路由，操作无法恢复。" destructive error={deleteMutation.isError ? llmErrorMessage(deleteMutation.error) : undefined} onClose={() => setDeleteOpen(false)} onConfirm={() => deleteMutation.mutate()} pending={deleteMutation.isPending} title="删除场景绑定"><dl className="governance-impact-summary"><div><dt>目标绑定</dt><dd>{bindingIdentity(selected)} / #{selected.id}</dd></div><div><dt>当前路由</dt><dd>{selected.routeLabel} → {modelName(models, selected.primaryModelProfileId)}</dd></div></dl></ArticleGovernanceDialog> : null}
    </div>
  );
}

function bindingToForm(binding: LlmBinding): BindingForm {
  return {
    scene: binding.scene,
    agentRole: binding.agentRole,
    primaryModelProfileId: String(binding.primaryModelProfileId),
    fallbackModelProfileId: binding.fallbackModelProfileId === null ? "" : String(binding.fallbackModelProfileId),
    routeLabel: binding.routeLabel,
    enabled: binding.enabled,
    remarks: binding.remarks ?? "",
    operator: binding.updatedBy ?? "admin",
  };
}

function validateBindingForm(form: BindingForm, bindings: LlmBinding[], selectedId?: number) {
  if (!LLM_SCENE_ROLES[form.scene].includes(form.agentRole)) return "Agent 角色与运行场景不匹配";
  if (!form.primaryModelProfileId) return "请选择主模型";
  if (form.primaryModelProfileId === form.fallbackModelProfileId) return "主模型和降级模型不能相同";
  const duplicate = bindings.find((binding) => binding.id !== selectedId && binding.scene === form.scene && binding.agentRole === form.agentRole);
  if (duplicate) return `该场景角色已存在绑定 #${duplicate.id}`;
  if (!form.operator.trim()) return "请填写操作人";
  return "";
}

function changeScene(scene: LlmScene, form: BindingForm, setForm: (form: BindingForm) => void) {
  setForm({ ...form, scene, agentRole: LLM_SCENE_ROLES[scene][0] });
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
