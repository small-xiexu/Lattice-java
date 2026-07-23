import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CheckCircle2, FlaskConical, Plus, RotateCcw, Save, Trash2 } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";
import { useSearchParams } from "react-router-dom";

import { llmSettingsApi, type LlmConnection } from "../../api/contracts/llm-settings";
import { queryKeys } from "../../api/query-keys";
import { PageState } from "../../components/page-state";
import { ArticleGovernanceDialog } from "../library/article-governance-dialog";
import {
  confirmDiscardChanges,
  formatLlmDateTime,
  llmErrorMessage,
  useBeforeUnloadWarning,
} from "./llm-settings-utils";

interface ConnectionForm {
  connectionCode: string;
  providerType: string;
  baseUrl: string;
  apiKey: string;
  enabled: boolean;
  remarks: string;
  operator: string;
}

const EMPTY_CONNECTION: ConnectionForm = {
  connectionCode: "",
  providerType: "openai_compatible",
  baseUrl: "",
  apiKey: "",
  enabled: true,
  remarks: "",
  operator: "admin",
};

interface LlmConnectionsPanelProps {
  onDirtyChange: (dirty: boolean) => void;
}

export function LlmConnectionsPanel({ onDirtyChange }: LlmConnectionsPanelProps) {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState<ConnectionForm>(EMPTY_CONNECTION);
  const [baseline, setBaseline] = useState<ConnectionForm>(EMPTY_CONNECTION);
  const [message, setMessage] = useState("");
  const [formError, setFormError] = useState("");
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [loadedKey, setLoadedKey] = useState("initial");

  const query = useQuery({
    queryKey: queryKeys.settings.llm.connections,
    queryFn: ({ signal }) => llmSettingsApi.listConnections(signal),
  });
  const connections = query.data?.items ?? [];
  const requestedId = Number(searchParams.get("id"));
  const selected = creating
    ? undefined
    : connections.find((connection) => connection.id === requestedId) ?? connections[0];
  const dirty = JSON.stringify(form) !== JSON.stringify(baseline);
  const editorKey = creating ? "new" : selected ? `connection-${selected.id}` : "empty";
  useBeforeUnloadWarning(dirty);

  useEffect(() => {
    onDirtyChange(dirty);
    return () => onDirtyChange(false);
  }, [dirty, onDirtyChange]);

  const saveMutation = useMutation({
    mutationFn: () => {
      const error = validateConnectionForm(form, creating);
      if (error) throw new Error(error);
      const request = {
        ...form,
        connectionCode: form.connectionCode.trim(),
        providerType: form.providerType.trim(),
        baseUrl: form.baseUrl.trim(),
        apiKey: form.apiKey.trim(),
        remarks: form.remarks.trim() || null,
        operator: form.operator.trim() || "admin",
      };
      return selected && !creating
        ? llmSettingsApi.updateConnection(selected.id, request)
        : llmSettingsApi.createConnection(request);
    },
    onSuccess: (saved) => {
      const next = connectionToForm(saved);
      setForm(next);
      setBaseline(next);
      setCreating(false);
      setFormError("");
      setMessage(`连接 ${saved.connectionCode} 已保存`);
      setSelectedId(setSearchParams, searchParams, saved.id);
      void queryClient.invalidateQueries({ queryKey: queryKeys.settings.llm.root });
    },
    onError: (error) => setFormError(llmErrorMessage(error)),
  });

  const testMutation = useMutation({
    mutationFn: () => {
      const error = validateConnectionTest(form);
      if (error) throw new Error(error);
      return llmSettingsApi.testConnection({
        connectionId: selected && !form.apiKey.trim() ? selected.id : null,
        providerType: form.providerType.trim(),
        baseUrl: form.baseUrl.trim(),
        apiKey: form.apiKey.trim(),
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
      if (!selected) throw new Error("未选择连接");
      return llmSettingsApi.deleteConnection(selected.id);
    },
    onSuccess: () => {
      setDeleteOpen(false);
      setCreating(false);
      setSelectedId(setSearchParams, searchParams, null);
      setMessage("连接已删除");
      void queryClient.invalidateQueries({ queryKey: queryKeys.settings.llm.root });
    },
  });

  if (loadedKey !== editorKey) {
    const next = selected ? connectionToForm(selected) : EMPTY_CONNECTION;
    setLoadedKey(editorKey);
    setForm(next);
    setBaseline(next);
    setFormError("");
    setMessage("");
  }

  const choose = (connection: LlmConnection) => {
    if (!confirmDiscardChanges(dirty)) return;
    setCreating(false);
    setSelectedId(setSearchParams, searchParams, connection.id);
  };

  const create = () => {
    if (!confirmDiscardChanges(dirty)) return;
    setCreating(true);
    setSelectedId(setSearchParams, searchParams, null);
  };

  if (query.isLoading) return <PageState status="loading" title="正在读取模型连接" />;
  if (query.isError) {
    return <PageState actionLabel="重新加载" description={llmErrorMessage(query.error)} onAction={() => void query.refetch()} status="error" title="模型连接读取失败" />;
  }

  return (
    <div className="llm-settings-layout">
      <section className="llm-settings-list" aria-label="模型连接列表">
        <header className="llm-settings-list-header">
          <div><strong>模型连接</strong><span>{connections.length} 项</span></div>
          <button className="secondary-button compact-button" onClick={create} type="button">
            <Plus aria-hidden="true" size={16} />新增
          </button>
        </header>
        {connections.length === 0 && !creating ? (
          <div className="llm-settings-empty">暂无模型连接</div>
        ) : (
          <ul className="llm-settings-records">
            {connections.map((connection) => (
              <li key={connection.id}>
                <button className={!creating && selected?.id === connection.id ? "is-selected" : ""} onClick={() => choose(connection)} type="button">
                  <span className="llm-record-title"><strong>{connection.connectionCode}</strong><em className={connection.enabled ? "is-enabled" : "is-disabled"}>{connection.enabled ? "启用" : "停用"}</em></span>
                  <code>{connection.providerType}</code>
                  <small>{connection.baseUrl}</small>
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="llm-settings-editor" aria-label={creating ? "新增模型连接" : "编辑模型连接"}>
        <header className="llm-settings-editor-header">
          <div>
            <h2>{creating ? "新增模型连接" : selected ? `编辑 ${selected.connectionCode}` : "模型连接"}</h2>
            <p>{selected ? `最近更新 ${formatLlmDateTime(selected.updatedAt)} · ${selected.updatedBy ?? "未知操作人"}` : "保存前可先测试当前参数"}</p>
          </div>
          {dirty ? <span className="llm-dirty-mark">未保存</span> : null}
        </header>
        {!creating && !selected ? (
          <div className="llm-settings-empty">选择或新增一个模型连接</div>
        ) : (
          <form className="llm-settings-form" noValidate onSubmit={(event) => submit(event, saveMutation.mutate)}>
            <label className="form-field"><span>连接编码</span><input autoComplete="off" onChange={(event) => setForm({ ...form, connectionCode: event.target.value })} required value={form.connectionCode} /></label>
            <label className="form-field"><span>Provider 类型</span><input autoComplete="off" list="llm-provider-options" onChange={(event) => setForm({ ...form, providerType: event.target.value })} required value={form.providerType} /></label>
            <datalist id="llm-provider-options"><option value="openai_compatible" /><option value="openai" /><option value="anthropic" /><option value="ollama" /></datalist>
            <label className="form-field field-span-2"><span>API 地址</span><input autoComplete="url" onChange={(event) => setForm({ ...form, baseUrl: event.target.value })} required type="url" value={form.baseUrl} /></label>
            <label className="form-field field-span-2">
              <span>API 密钥</span>
              <input aria-describedby="llm-api-key-help" aria-label="API 密钥" autoComplete="new-password" onChange={(event) => setForm({ ...form, apiKey: event.target.value })} placeholder={selected ? selected.apiKeyMask : "输入 API 密钥"} required={creating} type="password" value={form.apiKey} />
              <small id="llm-api-key-help">{selected ? `已保存 ${selected.apiKeyMask}；留空保持不变` : "密钥仅发送到服务端加密保存，不会回填明文"}</small>
            </label>
            <label className="form-field field-span-2"><span>备注</span><textarea onChange={(event) => setForm({ ...form, remarks: event.target.value })} rows={3} value={form.remarks} /></label>
            <label className="form-field"><span>操作人</span><input autoComplete="off" onChange={(event) => setForm({ ...form, operator: event.target.value })} required value={form.operator} /></label>
            <label className="checkbox-field"><input checked={form.enabled} onChange={(event) => setForm({ ...form, enabled: event.target.checked })} type="checkbox" />启用此连接</label>

            {message ? <p className="llm-inline-result is-success" role="status"><CheckCircle2 aria-hidden="true" size={17} />{message}</p> : null}
            {formError ? <p className="llm-inline-result is-error" role="alert">{formError}</p> : null}
            <div className="llm-settings-form-actions">
              <button className="primary-button" disabled={saveMutation.isPending || testMutation.isPending} type="submit"><Save aria-hidden="true" size={16} />{saveMutation.isPending ? "正在保存" : "保存连接"}</button>
              <button className="secondary-button compact-button" disabled={saveMutation.isPending || testMutation.isPending} onClick={() => testMutation.mutate()} type="button"><FlaskConical aria-hidden="true" size={16} />{testMutation.isPending ? "正在测试" : "测试连接"}</button>
              <button className="secondary-button compact-button" disabled={!dirty || saveMutation.isPending} onClick={() => setForm(baseline)} type="button"><RotateCcw aria-hidden="true" size={16} />撤销改动</button>
              {selected && !creating ? <button aria-label="删除模型连接" className="icon-button llm-delete-button" disabled={saveMutation.isPending} onClick={() => setDeleteOpen(true)} title="删除模型连接" type="button"><Trash2 aria-hidden="true" size={17} /></button> : null}
            </div>
          </form>
        )}
      </section>

      {deleteOpen && selected ? (
        <ArticleGovernanceDialog confirmLabel="确认删除连接" description="删除后无法恢复；存在关联模型时服务端将拒绝操作。" destructive error={deleteMutation.isError ? llmErrorMessage(deleteMutation.error) : undefined} onClose={() => setDeleteOpen(false)} onConfirm={() => deleteMutation.mutate()} pending={deleteMutation.isPending} title="删除模型连接">
          <dl className="governance-impact-summary"><div><dt>目标连接</dt><dd>{selected.connectionCode} / #{selected.id}</dd></div><div><dt>关联影响</dt><dd>{selected.enabled ? "当前连接已启用，请先确认没有模型仍在使用" : "当前连接已停用，仍需确认模型引用"}</dd></div></dl>
        </ArticleGovernanceDialog>
      ) : null}
    </div>
  );
}

function connectionToForm(connection: LlmConnection): ConnectionForm {
  return {
    connectionCode: connection.connectionCode,
    providerType: connection.providerType,
    baseUrl: connection.baseUrl,
    apiKey: "",
    enabled: connection.enabled,
    remarks: connection.remarks ?? "",
    operator: connection.updatedBy ?? "admin",
  };
}

function validateConnectionForm(form: ConnectionForm, creating: boolean) {
  if (!form.connectionCode.trim()) return "请填写连接编码";
  if (!form.providerType.trim()) return "请填写 Provider 类型";
  if (!form.baseUrl.trim()) return "请填写 API 地址";
  if (creating && !form.apiKey.trim()) return "新增连接必须填写 API 密钥";
  if (!form.operator.trim()) return "请填写操作人";
  return "";
}

function validateConnectionTest(form: ConnectionForm) {
  if (!form.providerType.trim()) return "测试前请填写 Provider 类型";
  if (!form.baseUrl.trim()) return "测试前请填写 API 地址";
  return "";
}

function submit(event: FormEvent, save: () => void) {
  event.preventDefault();
  save();
}

function setSelectedId(
  update: ReturnType<typeof useSearchParams>[1],
  current: URLSearchParams,
  id: number | null,
) {
  const next = new URLSearchParams(current);
  if (id === null) next.delete("id");
  else next.set("id", String(id));
  update(next, { replace: true });
}
