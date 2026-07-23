import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, RotateCcw, Save } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";

import {
  retrievalSettingsApi,
  type RetrievalConfig,
} from "../../api/contracts/retrieval-settings";
import { queryKeys } from "../../api/query-keys";
import { PageState } from "../../components/page-state";
import {
  llmErrorMessage,
  useBeforeUnloadWarning,
} from "./llm-settings-utils";

interface RetrievalConfigPanelProps {
  onDirtyChange: (dirty: boolean) => void;
}

interface RetrievalConfigForm {
  parallelEnabled: boolean;
  rewriteEnabled: boolean;
  intentAwareVectorEnabled: boolean;
  rrfK: string;
  weights: Record<WeightKey, string>;
}

type WeightKey = Exclude<keyof RetrievalConfig,
  "parallelEnabled" | "rewriteEnabled" | "intentAwareVectorEnabled" | "rrfK">;

const WEIGHT_FIELDS: readonly { key: WeightKey; label: string; detail: string }[] = [
  { key: "ftsWeight", label: "全文检索", detail: "文章 lexical 召回" },
  { key: "refkeyWeight", label: "引用键", detail: "RefKey 精确召回" },
  { key: "articleChunkWeight", label: "文章分块", detail: "文章 chunk lexical" },
  { key: "sourceWeight", label: "资料源", detail: "Source 级召回" },
  { key: "sourceChunkWeight", label: "资料分块", detail: "Source chunk lexical" },
  { key: "factCardWeight", label: "Fact Card", detail: "结构化事实 lexical" },
  { key: "contributionWeight", label: "贡献", detail: "Contribution 通道" },
  { key: "graphWeight", label: "知识图谱", detail: "Graph 通道" },
  { key: "articleVectorWeight", label: "文章向量", detail: "Article vector 通道" },
  { key: "chunkVectorWeight", label: "分块向量", detail: "Chunk vector 通道" },
];

const EMPTY_FORM: RetrievalConfigForm = {
  parallelEnabled: false,
  rewriteEnabled: false,
  intentAwareVectorEnabled: false,
  rrfK: "",
  weights: Object.fromEntries(WEIGHT_FIELDS.map(({ key }) => [key, ""])) as Record<WeightKey, string>,
};

export function RetrievalConfigPanel({ onDirtyChange }: RetrievalConfigPanelProps) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState(EMPTY_FORM);
  const [baseline, setBaseline] = useState(EMPTY_FORM);
  const [loadedKey, setLoadedKey] = useState("initial");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const query = useQuery({
    queryKey: queryKeys.settings.retrieval.config,
    queryFn: ({ signal }) => retrievalSettingsApi.getConfig(signal),
  });
  const dirty = JSON.stringify(form) !== JSON.stringify(baseline);
  useBeforeUnloadWarning(dirty);

  useEffect(() => {
    onDirtyChange(dirty);
    return () => onDirtyChange(false);
  }, [dirty, onDirtyChange]);

  const mutation = useMutation({
    mutationFn: () => {
      const request = formToRequest(form);
      const validationError = validateConfig(request);
      if (validationError) throw new Error(validationError);
      return retrievalSettingsApi.updateConfig(request);
    },
    onSuccess: (saved) => {
      const next = configToForm(saved);
      queryClient.setQueryData(queryKeys.settings.retrieval.config, saved);
      setLoadedKey(configIdentity(saved));
      setForm(next);
      setBaseline(next);
      setError("");
      setMessage("检索参数已保存并应用到后续查询");
    },
    onError: (cause) => {
      setMessage("");
      setError(llmErrorMessage(cause));
    },
  });

  if (query.data && loadedKey !== configIdentity(query.data)) {
    const next = configToForm(query.data);
    setLoadedKey(configIdentity(query.data));
    setForm(next);
    setBaseline(next);
    setMessage("");
    setError("");
  }

  if (query.isLoading) return <PageState status="loading" title="正在读取检索参数" />;
  if (query.error || !query.data) {
    return (
      <PageState
        actionLabel="重新加载"
        description={query.error ? llmErrorMessage(query.error) : "服务端未返回检索参数"}
        onAction={() => void query.refetch()}
        status="error"
        title="检索参数读取失败"
      />
    );
  }

  const submit = (event: FormEvent) => {
    event.preventDefault();
    setError("");
    mutation.mutate();
  };

  return (
    <section aria-labelledby="retrieval-config-title" className="retrieval-section">
      <header className="retrieval-section-header">
        <div>
          <h2 id="retrieval-config-title">运行参数</h2>
          <p>保存后影响下一次 Query，不修改历史审计记录</p>
        </div>
        {dirty ? <span className="llm-dirty-mark">未保存</span> : null}
      </header>
      <form className="retrieval-config-form" noValidate onSubmit={submit}>
        <fieldset className="retrieval-switches">
          <legend>检索流程</legend>
          <label className="checkbox-field">
            <input
              checked={form.parallelEnabled}
              onChange={(event) => setForm({ ...form, parallelEnabled: event.target.checked })}
              type="checkbox"
            />
            并行执行召回通道
          </label>
          <label className="checkbox-field">
            <input
              checked={form.rewriteEnabled}
              onChange={(event) => setForm({ ...form, rewriteEnabled: event.target.checked })}
              type="checkbox"
            />
            启用查询改写
          </label>
          <label className="checkbox-field">
            <input
              checked={form.intentAwareVectorEnabled}
              onChange={(event) => setForm({ ...form, intentAwareVectorEnabled: event.target.checked })}
              type="checkbox"
            />
            启用意图感知向量通道
          </label>
        </fieldset>

        <div className="retrieval-weight-grid">
          {WEIGHT_FIELDS.map((field) => (
            <label className="form-field" key={field.key}>
              <span>{field.label}<small>{field.detail}</small></span>
              <input
                aria-label={`${field.label}权重`}
                inputMode="decimal"
                min="0"
                onChange={(event) => setForm({
                  ...form,
                  weights: { ...form.weights, [field.key]: event.target.value },
                })}
                step="0.05"
                type="number"
                value={form.weights[field.key]}
              />
            </label>
          ))}
          <label className="form-field retrieval-rrf-field">
            <span>RRF K<small>越大排名越平滑</small></span>
            <input
              aria-label="RRF K"
              inputMode="numeric"
              min="1"
              onChange={(event) => setForm({ ...form, rrfK: event.target.value })}
              step="1"
              type="number"
              value={form.rrfK}
            />
          </label>
        </div>

        {message ? <p className="vector-notice is-success" role="status"><Check aria-hidden="true" size={17} />{message}</p> : null}
        {error ? <p className="vector-notice is-error" role="alert">{error}</p> : null}
        <div className="vector-form-actions">
          <button className="primary-button" disabled={!dirty || mutation.isPending} type="submit">
            <Save aria-hidden="true" size={16} />{mutation.isPending ? "正在保存" : "保存参数"}
          </button>
          <button className="secondary-button" disabled={!dirty || mutation.isPending} onClick={() => setForm(baseline)} type="button">
            <RotateCcw aria-hidden="true" size={16} />撤销改动
          </button>
        </div>
      </form>
    </section>
  );
}

function configToForm(config: RetrievalConfig): RetrievalConfigForm {
  return {
    parallelEnabled: config.parallelEnabled,
    rewriteEnabled: config.rewriteEnabled,
    intentAwareVectorEnabled: config.intentAwareVectorEnabled,
    rrfK: String(config.rrfK),
    weights: Object.fromEntries(
      WEIGHT_FIELDS.map(({ key }) => [key, String(config[key])]),
    ) as Record<WeightKey, string>,
  };
}

function formToRequest(form: RetrievalConfigForm): RetrievalConfig {
  return {
    parallelEnabled: form.parallelEnabled,
    rewriteEnabled: form.rewriteEnabled,
    intentAwareVectorEnabled: form.intentAwareVectorEnabled,
    rrfK: Number(form.rrfK),
    ...Object.fromEntries(WEIGHT_FIELDS.map(({ key }) => [key, Number(form.weights[key])])),
  } as RetrievalConfig;
}

function validateConfig(config: RetrievalConfig) {
  const invalidWeight = WEIGHT_FIELDS.find(({ key }) => !Number.isFinite(config[key]) || config[key] < 0);
  if (invalidWeight) return `${invalidWeight.label}权重必须是大于或等于 0 的数字`;
  if (!Number.isInteger(config.rrfK) || config.rrfK <= 0) return "RRF K 必须是大于 0 的整数";
  return "";
}

function configIdentity(config: RetrievalConfig) {
  return JSON.stringify(config);
}
