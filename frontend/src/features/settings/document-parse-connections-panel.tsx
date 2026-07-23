import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CheckCircle2, FlaskConical, Plus, RotateCcw, Save, Trash2 } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";
import { useSearchParams } from "react-router-dom";

import {
  documentParseSettingsApi,
  type DocumentParseConnection,
  type DocumentParseConnectionRequest,
  type DocumentParseConnectionTestRequest,
  type DocumentParseProvider,
  type DocumentParseProviderField,
} from "../../api/contracts/document-parse-settings";
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
  credentialValues: Record<string, string>;
  configValues: Record<string, string>;
  enabled: boolean;
  operator: string;
}

interface ConnectionList {
  count: number;
  items: DocumentParseConnection[];
}

interface BuildFieldJsonResult {
  value?: string;
  error?: string;
}

interface BuildConnectionRequestResult {
  request?: DocumentParseConnectionRequest;
  error?: string;
}

interface BuildConnectionTestRequestResult {
  request?: DocumentParseConnectionTestRequest;
  error?: string;
}

interface DocumentParseConnectionsPanelProps {
  policyImageConnectionId: number | null;
  policyScannedPdfConnectionId: number | null;
  onDirtyChange: (dirty: boolean) => void;
}

const EMPTY_PROVIDERS: DocumentParseProvider[] = [];
const EMPTY_CONNECTIONS: DocumentParseConnection[] = [];

export function DocumentParseConnectionsPanel({
  policyImageConnectionId,
  policyScannedPdfConnectionId,
  onDirtyChange,
}: DocumentParseConnectionsPanelProps) {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState<ConnectionForm>(() => emptyConnectionForm());
  const [baseline, setBaseline] = useState<ConnectionForm>(() => emptyConnectionForm());
  const [loadedKey, setLoadedKey] = useState("initial");
  const [message, setMessage] = useState("");
  const [formError, setFormError] = useState("");
  const [deleteOpen, setDeleteOpen] = useState(false);

  const providersQuery = useQuery({
    queryKey: queryKeys.settings.documentParse.providers,
    queryFn: ({ signal }) => documentParseSettingsApi.listProviders(signal),
  });
  const connectionsQuery = useQuery({
    queryKey: queryKeys.settings.documentParse.connections,
    queryFn: ({ signal }) => documentParseSettingsApi.listConnections(signal),
  });
  const providers = providersQuery.data?.items ?? EMPTY_PROVIDERS;
  const connections = connectionsQuery.data?.items ?? EMPTY_CONNECTIONS;
  const requestedId = Number(searchParams.get("id"));
  const isCreating = creating || connections.length === 0;
  const selected = isCreating
    ? undefined
    : connections.find((connection) => connection.id === requestedId) ?? connections[0];
  const descriptor = providers.find((provider) => provider.providerType === form.providerType) ?? providers[0];
  const editorKey = isCreating ? `new-${providers[0]?.providerType ?? "none"}` : `connection-${selected?.id ?? "none"}`;
  const dirty = JSON.stringify(form) !== JSON.stringify(baseline);
  useBeforeUnloadWarning(dirty);

  useEffect(() => {
    onDirtyChange(dirty);
    return () => onDirtyChange(false);
  }, [dirty, onDirtyChange]);

  if (loadedKey !== editorKey && providers.length > 0) {
    const next = selected
      ? connectionToForm(selected, providers)
      : emptyConnectionForm(providers[0]);
    setLoadedKey(editorKey);
    setForm(next);
    setBaseline(next);
    setMessage("");
    setFormError("");
  }

  const saveMutation = useMutation({
    mutationFn: () => {
      const result = buildConnectionRequest(form, descriptor, isCreating);
      if (result.error) throw new Error(result.error);
      if (!result.request) throw new Error("无法生成连接请求");
      return selected && !isCreating
        ? documentParseSettingsApi.updateConnection(selected.id, result.request)
        : documentParseSettingsApi.createConnection(result.request);
    },
    onSuccess: (saved) => {
      queryClient.setQueryData<ConnectionList>(queryKeys.settings.documentParse.connections, (current) => {
        const items = current?.items ?? [];
        const exists = items.some((item) => item.id === saved.id);
        const nextItems = exists
          ? items.map((item) => item.id === saved.id ? saved : item)
          : [...items, saved];
        return { count: nextItems.length, items: nextItems };
      });
      const next = connectionToForm(saved, providers);
      setCreating(false);
      setLoadedKey(`connection-${saved.id}`);
      setForm(next);
      setBaseline(next);
      setFormError("");
      setMessage(`解析连接 ${saved.connectionCode} 已保存`);
      setSelectedId(setSearchParams, searchParams, saved.id);
      void queryClient.invalidateQueries({ queryKey: queryKeys.settings.documentParse.root });
    },
    onError: (error) => {
      setMessage("");
      setFormError(llmErrorMessage(error));
    },
  });

  const testMutation = useMutation({
    mutationFn: () => {
      const result = buildConnectionTestRequest(form, baseline, descriptor, selected, isCreating);
      if (result.error) throw new Error(result.error);
      if (!result.request) throw new Error("无法生成测试请求");
      return documentParseSettingsApi.testConnection(result.request);
    },
    onSuccess: (result) => {
      if (result.success) {
        setFormError("");
        setMessage(`${result.message}${result.latencyMs === null ? "" : ` · ${result.latencyMs} ms`}`);
      } else {
        setMessage("");
        setFormError(result.message);
      }
    },
    onError: (error) => {
      setMessage("");
      setFormError(llmErrorMessage(error));
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => documentParseSettingsApi.deleteConnection(id),
    onSuccess: (_, deletedId) => {
      queryClient.setQueryData<ConnectionList>(queryKeys.settings.documentParse.connections, (current) => {
        const items = (current?.items ?? []).filter((item) => item.id !== deletedId);
        return { count: items.length, items };
      });
      setDeleteOpen(false);
      setCreating(false);
      setLoadedKey("deleted");
      setSelectedId(setSearchParams, searchParams, null);
      setMessage("解析连接已删除，默认策略引用已同步刷新");
      void queryClient.invalidateQueries({ queryKey: queryKeys.settings.documentParse.root });
    },
  });

  const choose = (connection: DocumentParseConnection) => {
    if (!confirmDiscardChanges(dirty)) return;
    setCreating(false);
    setSelectedId(setSearchParams, searchParams, connection.id);
  };

  const create = () => {
    if (!confirmDiscardChanges(dirty)) return;
    setCreating(true);
    setLoadedKey("create-requested");
    setSelectedId(setSearchParams, searchParams, null);
  };

  const changeProvider = (providerType: string) => {
    const previous = providers.find((provider) => provider.providerType === form.providerType);
    const next = providers.find((provider) => provider.providerType === providerType);
    if (!next) return;
    const canApplyDefault = !form.baseUrl.trim() || form.baseUrl === previous?.defaultBaseUrl;
    setForm({
      ...form,
      providerType,
      baseUrl: canApplyDefault ? next.defaultBaseUrl : form.baseUrl,
      credentialValues: fieldValues(next.credentialFields),
      configValues: fieldValues(next.configFields),
    });
    setMessage("");
    setFormError("");
  };

  const loading = providersQuery.isLoading || connectionsQuery.isLoading;
  const queryError = providersQuery.error ?? connectionsQuery.error;
  if (loading) return <PageState status="loading" title="正在读取文档解析连接" />;
  if (queryError || providers.length === 0) {
    return (
      <PageState
        actionLabel="重新加载"
        description={queryError ? llmErrorMessage(queryError) : "服务端未返回 Provider 描述"}
        onAction={() => void Promise.all([providersQuery.refetch(), connectionsQuery.refetch()])}
        status="error"
        title="文档解析连接读取失败"
      />
    );
  }

  const mutationsBusy = saveMutation.isPending || testMutation.isPending || deleteMutation.isPending;
  const routeUses = selected ? connectionRouteUses(selected.id, policyImageConnectionId, policyScannedPdfConnectionId) : [];

  return (
    <div className="llm-settings-layout parsing-connections-layout">
      <section aria-label="文档解析连接列表" className="llm-settings-list">
        <header className="llm-settings-list-header">
          <div><strong>解析连接</strong><span>{connections.length} 项</span></div>
          <button className="secondary-button compact-button" onClick={create} type="button">
            <Plus aria-hidden="true" size={16} />新增
          </button>
        </header>
        {connections.length === 0 ? (
          <div className="llm-settings-empty">尚未配置解析连接</div>
        ) : (
          <ul className="llm-settings-records">
            {connections.map((connection) => (
              <li key={connection.id}>
                <button className={!isCreating && selected?.id === connection.id ? "is-selected" : ""} onClick={() => choose(connection)} type="button">
                  <span className="llm-record-title"><strong>{connection.connectionCode}</strong><em className={connection.enabled ? "is-enabled" : "is-disabled"}>{connection.enabled ? "启用" : "停用"}</em></span>
                  <code>{providerName(providers, connection.providerType)}</code>
                  <small>{connection.baseUrl}</small>
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section aria-label={isCreating ? "新增文档解析连接" : "编辑文档解析连接"} className="llm-settings-editor">
        <header className="llm-settings-editor-header">
          <div>
            <h2>{isCreating ? "新增解析连接" : selected ? `编辑 ${selected.connectionCode}` : "解析连接"}</h2>
            <p>{selected ? `最近更新 ${formatLlmDateTime(selected.updatedAt)} · ${selected.updatedBy ?? "未知操作人"}` : "动态字段来自服务端 Provider 描述"}</p>
          </div>
          {dirty ? <span className="llm-dirty-mark">未保存</span> : null}
        </header>
        <form className="llm-settings-form parsing-connection-form" noValidate onSubmit={(event) => submit(event, saveMutation.mutate)}>
          <label className="form-field"><span>连接编码</span><input autoComplete="off" onChange={(event) => setForm({ ...form, connectionCode: event.target.value })} required value={form.connectionCode} /></label>
          <label className="form-field"><span>Provider</span><select onChange={(event) => changeProvider(event.target.value)} value={form.providerType}>{providers.map((provider) => <option key={provider.providerType} value={provider.providerType}>{provider.displayName}</option>)}</select></label>
          <label className="form-field field-span-2"><span>API 地址</span><input autoComplete="url" onChange={(event) => setForm({ ...form, baseUrl: event.target.value })} required type="url" value={form.baseUrl} /></label>

          {descriptor ? <ProviderSummary provider={descriptor} /> : null}
          <fieldset className="parsing-dynamic-fields field-span-2">
            <legend>访问凭证</legend>
            <div className="parsing-dynamic-grid">
              {descriptor?.credentialFields.map((field) => (
                <DynamicField
                  existingSecret={!isCreating && !!selected?.credentialConfigured}
                  field={field}
                  key={field.fieldKey}
                  onChange={(value) => setForm({ ...form, credentialValues: { ...form.credentialValues, [field.fieldKey]: value } })}
                  scope="credential"
                  value={form.credentialValues[field.fieldKey] ?? ""}
                />
              ))}
            </div>
            {!isCreating && selected?.credentialConfigured ? <small className="parsing-secret-state">已保存 {selected.credentialMask}；所有凭证字段留空表示保持不变</small> : null}
          </fieldset>
          <fieldset className="parsing-dynamic-fields field-span-2">
            <legend>Provider 配置</legend>
            <div className="parsing-dynamic-grid">
              {descriptor?.configFields.map((field) => (
                <DynamicField
                  field={field}
                  key={field.fieldKey}
                  onChange={(value) => setForm({ ...form, configValues: { ...form.configValues, [field.fieldKey]: value } })}
                  scope="config"
                  value={form.configValues[field.fieldKey] ?? ""}
                />
              ))}
            </div>
          </fieldset>
          <label className="form-field"><span>操作人</span><input autoComplete="off" onChange={(event) => setForm({ ...form, operator: event.target.value })} required value={form.operator} /></label>
          <label className="checkbox-field"><input checked={form.enabled} onChange={(event) => setForm({ ...form, enabled: event.target.checked })} type="checkbox" />启用此连接</label>

          {message ? <p className="llm-inline-result is-success" role="status"><CheckCircle2 aria-hidden="true" size={17} />{message}</p> : null}
          {formError ? <p className="llm-inline-result is-error" role="alert">{formError}</p> : null}
          <div className="llm-settings-form-actions">
            <button className="primary-button" disabled={mutationsBusy} type="submit"><Save aria-hidden="true" size={16} />{saveMutation.isPending ? "正在保存" : "保存连接"}</button>
            <button className="secondary-button compact-button" disabled={mutationsBusy} onClick={() => testMutation.mutate()} type="button"><FlaskConical aria-hidden="true" size={16} />{testMutation.isPending ? "正在测试" : "测试连接"}</button>
            <button className="secondary-button compact-button" disabled={!dirty || mutationsBusy} onClick={() => setForm(baseline)} type="button"><RotateCcw aria-hidden="true" size={16} />撤销改动</button>
            {selected && !isCreating ? <button aria-label="删除解析连接" className="icon-button llm-delete-button" disabled={mutationsBusy} onClick={() => setDeleteOpen(true)} title="删除解析连接" type="button"><Trash2 aria-hidden="true" size={17} /></button> : null}
          </div>
        </form>
      </section>

      {deleteOpen && selected ? (
        <ArticleGovernanceDialog
          confirmLabel="确认删除连接"
          description="删除后无法恢复；默认路由策略中的关联引用会被服务端同步清除。"
          destructive
          error={deleteMutation.isError ? llmErrorMessage(deleteMutation.error) : undefined}
          onClose={() => setDeleteOpen(false)}
          onConfirm={() => deleteMutation.mutate(selected.id)}
          pending={deleteMutation.isPending}
          title="删除解析连接"
        >
          <dl className="governance-impact-summary">
            <div><dt>目标连接</dt><dd>{selected.connectionCode} / #{selected.id}</dd></div>
            <div><dt>Provider</dt><dd>{providerName(providers, selected.providerType)}</dd></div>
            <div><dt>策略引用</dt><dd>{routeUses.length > 0 ? `将清除：${routeUses.join("、")}` : "当前默认策略未引用此连接"}</dd></div>
          </dl>
        </ArticleGovernanceDialog>
      ) : null}
    </div>
  );
}

function ProviderSummary({ provider }: { provider: DocumentParseProvider }) {
  return (
    <dl className="parsing-provider-summary field-span-2">
      <div><dt>探测模式</dt><dd>{provider.probeMode}</dd></div>
      <div><dt>默认地址</dt><dd>{provider.defaultBaseUrl || "未预置"}</dd></div>
      <div><dt>支持能力</dt><dd>{provider.supportedCapabilities.map(capabilityLabel).join(" / ") || "未声明"}</dd></div>
    </dl>
  );
}

function DynamicField({
  field,
  value,
  scope,
  existingSecret = false,
  onChange,
}: {
  field: DocumentParseProviderField;
  value: string;
  scope: "credential" | "config";
  existingSecret?: boolean;
  onChange: (value: string) => void;
}) {
  const label = `${field.label}${field.required ? " *" : ""}`;
  const placeholder = existingSecret
    ? `${field.placeholder}${field.placeholder ? "；" : ""}留空保持不变`
    : field.placeholder;
  return (
    <label className={`form-field${field.inputType === "textarea" ? " field-span-2" : ""}`}>
      <span>{label}</span>
      {field.inputType === "textarea" ? (
        <textarea aria-label={field.label} onChange={(event) => onChange(event.target.value)} placeholder={placeholder} rows={4} value={value} />
      ) : (
        <input
          aria-label={field.label}
          autoComplete={scope === "credential" ? "new-password" : "off"}
          onChange={(event) => onChange(event.target.value)}
          placeholder={placeholder}
          type={field.inputType}
          value={value}
        />
      )}
      {field.description ? <small>{field.description}</small> : null}
    </label>
  );
}

function emptyConnectionForm(provider?: DocumentParseProvider): ConnectionForm {
  return {
    connectionCode: "",
    providerType: provider?.providerType ?? "",
    baseUrl: provider?.defaultBaseUrl ?? "",
    credentialValues: fieldValues(provider?.credentialFields ?? []),
    configValues: fieldValues(provider?.configFields ?? []),
    enabled: true,
    operator: "admin",
  };
}

function connectionToForm(connection: DocumentParseConnection, providers: DocumentParseProvider[]): ConnectionForm {
  const provider = providers.find((candidate) => candidate.providerType === connection.providerType);
  return {
    connectionCode: connection.connectionCode,
    providerType: connection.providerType,
    baseUrl: connection.baseUrl,
    credentialValues: fieldValues(provider?.credentialFields ?? [], false),
    configValues: fieldValues(provider?.configFields ?? [], true, parseObject(connection.configJson)),
    enabled: connection.enabled,
    operator: connection.updatedBy ?? "admin",
  };
}

function fieldValues(fields: DocumentParseProviderField[], useDefaults = true, current: Record<string, unknown> = {}) {
  return Object.fromEntries(fields.map((field) => [
    field.fieldKey,
    current[field.fieldKey] === undefined
      ? useDefaults ? field.defaultValue : ""
      : String(current[field.fieldKey] ?? ""),
  ]));
}

function buildConnectionRequest(
  form: ConnectionForm,
  provider: DocumentParseProvider | undefined,
  creating: boolean,
): BuildConnectionRequestResult {
  if (!form.connectionCode.trim()) return { error: "请填写连接编码" };
  if (!provider) return { error: "请选择受支持的 Provider" };
  if (!isHttpUrl(form.baseUrl)) return { error: "API 地址必须是有效的 HTTP 或 HTTPS 地址" };
  if (!form.operator.trim()) return { error: "请填写操作人" };
  const credential = buildFieldJson(provider.credentialFields, form.credentialValues, creating, true);
  if (credential.error) return { error: credential.error };
  const config = buildFieldJson(provider.configFields, form.configValues, true, false);
  if (config.error) return { error: config.error };
  return {
    request: {
      connectionCode: form.connectionCode.trim(),
      providerType: provider.providerType,
      baseUrl: form.baseUrl.trim(),
      credentialJson: credential.value ?? "",
      configJson: config.value ?? "{}",
      enabled: form.enabled,
      operator: form.operator.trim(),
    },
  };
}

function buildConnectionTestRequest(
  form: ConnectionForm,
  baseline: ConnectionForm,
  provider: DocumentParseProvider | undefined,
  selected: DocumentParseConnection | undefined,
  creating: boolean,
): BuildConnectionTestRequestResult {
  if (!provider) return { error: "请选择受支持的 Provider" };
  if (!isHttpUrl(form.baseUrl)) return { error: "测试前请填写有效的 HTTP 或 HTTPS 地址" };
  const allCredentialsBlank = provider.credentialFields.every((field) => !form.credentialValues[field.fieldKey]?.trim());
  const savedParametersUnchanged = selected
    && form.providerType === baseline.providerType
    && form.baseUrl === baseline.baseUrl
    && JSON.stringify(form.configValues) === JSON.stringify(baseline.configValues);
  if (!creating && selected && allCredentialsBlank && savedParametersUnchanged) {
    return {
      request: {
        connectionId: selected.id,
        providerType: form.providerType,
        baseUrl: form.baseUrl.trim(),
        credentialJson: "",
        configJson: JSON.stringify(form.configValues),
      },
    };
  }
  if (!creating && selected && allCredentialsBlank) {
    return { error: "测试未保存参数时需重新填写完整凭证；留空只能测试已保存连接" };
  }
  const credential = buildFieldJson(provider.credentialFields, form.credentialValues, true, true);
  if (credential.error) return { error: credential.error };
  const config = buildFieldJson(provider.configFields, form.configValues, true, false);
  if (config.error) return { error: config.error };
  return {
    request: {
      connectionId: null,
      providerType: provider.providerType,
      baseUrl: form.baseUrl.trim(),
      credentialJson: credential.value ?? "{}",
      configJson: config.value ?? "{}",
    },
  };
}

function buildFieldJson(
  fields: DocumentParseProviderField[],
  values: Record<string, string>,
  requireValues: boolean,
  credentials: boolean,
): BuildFieldJsonResult {
  const allBlank = fields.every((field) => !values[field.fieldKey]?.trim());
  if (credentials && allBlank && !requireValues) return { value: "" };
  const payload: Record<string, string> = {};
  for (const field of fields) {
    const value = (values[field.fieldKey] ?? field.defaultValue).trim();
    if (!value && field.required) return { error: `请填写${credentials ? "凭证" : "配置"}字段：${field.label}` };
    if (value && field.inputType === "textarea" && field.fieldKey.toLowerCase().endsWith("json") && !isJsonObject(value)) {
      return { error: `${field.label}必须是合法的 JSON 对象` };
    }
    if (value) payload[field.fieldKey] = value;
  }
  return { value: JSON.stringify(payload) };
}

function parseObject(value: string): Record<string, unknown> {
  try {
    const parsed: unknown = JSON.parse(value);
    return parsed !== null && typeof parsed === "object" && !Array.isArray(parsed)
      ? parsed as Record<string, unknown>
      : {};
  } catch {
    return {};
  }
}

function isJsonObject(value: string) {
  try {
    const parsed: unknown = JSON.parse(value);
    return parsed !== null && typeof parsed === "object" && !Array.isArray(parsed);
  } catch {
    return false;
  }
}

function isHttpUrl(value: string) {
  try {
    const url = new URL(value.trim());
    return url.protocol === "http:" || url.protocol === "https:";
  } catch {
    return false;
  }
}

function providerName(providers: DocumentParseProvider[], providerType: string) {
  return providers.find((provider) => provider.providerType === providerType)?.displayName ?? providerType;
}

function capabilityLabel(capability: string) {
  if (capability === "IMAGE_OCR") return "图片 OCR";
  if (capability === "SCANNED_PDF_OCR") return "扫描 PDF OCR";
  return capability;
}

function connectionRouteUses(id: number, imageId: number | null, scannedPdfId: number | null) {
  const routes: string[] = [];
  if (imageId === id) routes.push("图片 OCR");
  if (scannedPdfId === id) routes.push("扫描 PDF OCR");
  return routes;
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
